// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"testing"
	"testing/synctest"
	"time"
)

// Cast and talk select their target within the same admission, as attack does.
// The participant gateway sends one command per attempt; without this a cast
// left the old selection in place and never continued into an attack.
func TestCastAndTalkSelectTheirTarget(t *testing.T) {
	t.Run("cast", func(t *testing.T) {
		synctest.Test(t, func(t *testing.T) {
			w, _, ctx := startTestWorld(t, syntheticCatalogWithCoolTime(t, 400*time.Millisecond))
			c := syntheticCharacter(1)
			c.X = 280
			c.Skills = []Skill{{ID: 9201, Level: 1}}
			entry := enterTest(t, w, ctx, c)
			target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
			if err := w.do(ctx, func(s *state) {
				n := s.npcs[target.ID]
				n.tmpl.MaxHP, n.hp = 10000, 10000
			}); err != nil {
				t.Fatal(err)
			}
			executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: Skill{ID: 9201, Level: 1}})
			checkTarget(t, w, ctx, target.ID)
			advanceVirtual(2 * time.Second)
			if !containsEvent(drainEvents(entry.Events), func(e AttackStarted) bool { return e.AttackerID == entry.Self.Entity.ID && e.TargetID == target.ID }) {
				t.Fatal("the cast did not continue into an attack on its target")
			}
		})
	})
	t.Run("talk", func(t *testing.T) {
		synctest.Test(t, func(t *testing.T) {
			w, _, ctx := startTestWorld(t, syntheticCatalog(t))
			entry := enterTest(t, w, ctx, syntheticCharacter(1))
			teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
			executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbTalk, Target: teacher})
			checkTarget(t, w, ctx, teacher.ID)
		})
	})
}

func checkTarget(t *testing.T, w *World, ctx context.Context, want int32) {
	t.Helper()
	var got int32
	if err := w.do(ctx, func(s *state) { got = s.players[1].target }); err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Fatalf("selected target: got %d, want %d", got, want)
	}
}
