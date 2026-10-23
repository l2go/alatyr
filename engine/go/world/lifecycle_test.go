// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
	"slices"
	"strconv"
	"testing"
	"testing/synctest"
	"time"
)

func TestObjectIDsReserveAndExhaustion(t *testing.T) {
	used := []int32{firstObjectID, firstObjectID + 2}
	ids := newIDs(used)
	used[0] = 0
	if ids.next() != firstObjectID+1 || ids.next() != firstObjectID+3 {
		t.Fatal("reserved ID allocated")
	}
	ids.reserve(firstObjectID + 4)
	if ids.next() != firstObjectID+5 {
		t.Fatal("late reserved ID allocated")
	}
	ids.last = math.MaxInt32 - 1
	if ids.next() != math.MaxInt32 || ids.next() != 0 || ids.next() != 0 {
		t.Fatal("ID exhaustion wrapped")
	}
}
func TestItemOwnerProtectionAndLateVisibility(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		a := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(a.Events)
		c := syntheticCharacter(2)
		c.X = 7000
		b := enterTest(t, w, ctx, c)
		drainEvents(b.Events)
		mob := objectOf(t, w, ctx, 9101)
		var id int32
		var at Point
		w.do(ctx, func(s *state) {
			w.dropOnGround(s, s.npcs[mob], s.players[1], 9002, 2)
			for _, g := range s.ground {
				id = g.id
				at = g.pos
			}
		})
		w.Teleport(ctx, 2, at, false)
		drainEvents(b.Events)
		w.Appear(ctx, 2)
		if !containsEvent(drainEvents(b.Events), func(e ItemDropped) bool { return e.ObjectID == id }) {
			t.Fatal("late observer does not see ground item")
		}
		w.Pickup(ctx, 2, id)
		w.do(ctx, func(s *state) {
			if s.ground[id] == nil {
				t.Fatal("protected loot taken")
			}
		})
		drainEvents(a.Events)
		drainEvents(b.Events)
		time.Sleep(16 * time.Second)
		drainEvents(a.Events)
		drainEvents(b.Events)
		w.Pickup(ctx, 2, id)
		time.Sleep(100 * time.Millisecond)
		self, _ := w.Snapshot(ctx, 2)
		if !slices.ContainsFunc(self.Char.Items, func(it Item) bool { return it.TemplateID == 9002 && it.Count == 2 }) {
			t.Fatal("expired loot protection still blocks")
		}
	})
}

type blockingSkillStore struct {
	testStore
	started, release chan struct{}
}

func (store *blockingSkillStore) PutSkill(ctx context.Context, _ int32, k Skill) error {
	close(store.started)
	select {
	case <-store.release:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
func TestLearningStoreDoesNotBlockLoopOrOverlapLeave(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := &blockingSkillStore{started: make(chan struct{}), release: make(chan struct{})}
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := New(cat, store)
		runDone := make(chan error, 1)
		go func() { runDone <- w.Run(ctx) }()
		defer func() {
			cancel()
			if err := <-runDone; err != nil {
				t.Errorf("Run: got %v, want nil", err)
			}
		}()
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		teacher := objectOf(t, w, ctx, 9102)
		w.Action(ctx, 1, teacher)
		w.Action(ctx, 1, teacher)
		w.Bypass(ctx, 1, fmtChoice(teacher))
		drainEvents(entry.Events)
		done := make(chan error, 1)
		go func() { done <- w.Learn(ctx, 1, 9202, 1) }()
		<-store.started
		if _, err := w.Snapshot(ctx, 1); err != nil {
			t.Fatal("store blocked loop")
		}
		if err := w.Leave(ctx, entry.Self.Entity); !errors.Is(err, ErrSaving) {
			t.Fatal("Leave overlaps skill write")
		}
		close(store.release)
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		if err := w.Leave(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
	})
}
func fmtChoice(id int32) string { return "npc_" + strconv.FormatInt(int64(id), 10) + "_SkillList" }
func TestFailedLearningWriteRemainsSaveable(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, store, ctx := startTestWorld(t, cat)
		store.failSkill = true
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		teacher := objectOf(t, w, ctx, 9102)
		w.Action(ctx, 1, teacher)
		w.Action(ctx, 1, teacher)
		w.Bypass(ctx, 1, fmtChoice(teacher))
		if w.Learn(ctx, 1, 9202, 1) == nil {
			t.Fatal("write failure hidden")
		}
		if err := w.Leave(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		if store.saved.SP != 17 || !slices.Contains(store.saved.Skills, Skill{ID: 9202, Level: 1}) {
			t.Fatal("failed learned snapshot lost")
		}
	})
}
func TestGameClockAndPostureRegen(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.CurHP = 40
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		if w.GameTime() != 23*60+46 {
			t.Fatal("game time start")
		}
		w.SetSitting(ctx, 1, true)
		time.Sleep(4 * time.Second)
		drainEvents(entry.Events)
		self, _ := w.Snapshot(ctx, 1)
		if !self.Sitting || self.Char.CurHP <= 40 {
			t.Fatal("sitting regeneration")
		}
		w.SetSitting(ctx, 1, false)
		for range 1400 {
			time.Sleep(100 * time.Millisecond)
			events := drainEvents(entry.Events)
			if containsEvent(events, func(e GameClock) bool { return e.Minute == 0 && e.Multiplier == 6 }) {
				return
			}
		}
		t.Fatal("hour rollover event missing")
	})
}
