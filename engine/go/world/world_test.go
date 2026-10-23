// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world_test

import (
	"context"
	"errors"
	world "github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"path/filepath"
	"reflect"
	"runtime"
	"sync"
	"testing"
	"testing/synctest"
	"time"
)

func authored(t *testing.T) *catalog.Catalog {
	t.Helper()
	_, file, _, _ := runtime.Caller(0)
	// The repository's authored world is CC-BY-SA-4.0; no fixture is copied.
	c, err := catalog.Load(filepath.Join(filepath.Dir(file), "../../../world"))
	if err != nil {
		t.Fatal(err)
	}
	return c
}
func ready() world.Character {
	return world.Character{ObjectID: 1, Account: "test", Name: "Walker", ClassID: 0, Level: 1, CurHP: 50, CurMP: 20, CurCP: 10, X: 10, Y: 20, Z: 30, Items: []world.Item{{ObjectID: 2, TemplateID: 1, Count: 1, Slot: 9}}, Skills: []world.Skill{{ID: 257, Level: 1}}, QuestVars: []world.QuestVar{{Quest: "custom", Name: "step", Value: "one"}}}
}
func TestEnterMoveLeaveSave(t *testing.T) {
	cat := authored(t)
	synctest.Test(t, func(t *testing.T) {
		store := memstore.New()
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		want := ready()
		if err := store.PutCharacter(ctx, want); err != nil {
			t.Fatal(err)
		}
		chars, err := store.Characters(ctx, "test")
		if err != nil {
			t.Fatal(err)
		}
		w := world.New(cat, store)
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		entry, err := w.Enter(ctx, chars[0])
		if err != nil {
			t.Fatal(err)
		}
		if entry.Self.Char.X != 10 || entry.Self.RunSpeed <= 0 {
			t.Fatal("entry changed persisted location")
		}
		chars[0].Items[0].Count = 999
		entry.Self.Char.Skills[0].Level = 99
		if _, err = w.Enter(ctx, want); !errors.Is(err, world.ErrPresent) {
			t.Fatalf("duplicate entry: %v", err)
		}
		to := world.Point{X: 210, Y: 20, Z: 30}
		if err = w.MoveTo(ctx, 1, to); err != nil {
			t.Fatal(err)
		}
		if _, ok := (<-entry.Events).(world.Moving); !ok {
			t.Fatal("missing movement event")
		}
		time.Sleep(500 * time.Millisecond)
		mid, err := w.Snapshot(ctx, 1)
		if err != nil {
			t.Fatal(err)
		}
		if mid.Char.X <= 10 || mid.Char.X >= 210 {
			t.Fatalf("not moving: %d", mid.Char.X)
		}
		time.Sleep(3 * time.Second)
		end, err := w.Snapshot(ctx, 1)
		if err != nil {
			t.Fatal(err)
		}
		if end.Char.X != to.X || end.Char.Y != to.Y || end.Char.Z != to.Z {
			t.Fatalf("arrival: %+v", end.Char)
		}
		if _, ok := (<-entry.Events).(world.Stopped); !ok {
			t.Fatal("missing stop")
		}
		if err = w.LeaveRef(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		left := false
		for event := range entry.Events {
			if _, ok := event.(world.LeftWorld); ok {
				left = true
			}
		}
		if !left {
			t.Fatal("missing departure")
		}
		saved, err := store.Characters(ctx, "test")
		if err != nil {
			t.Fatal(err)
		}
		if len(saved) != 1 || saved[0].X != 210 || saved[0].LastAccess == 0 || saved[0].Items[0].Count != 1 || saved[0].Skills[0].Level != 1 {
			t.Fatalf("bad persistence: %+v", saved)
		}
		if !reflect.DeepEqual(saved[0].QuestVars, want.QuestVars) {
			t.Fatal("quest variables changed")
		}
		if _, err = w.Snapshot(ctx, 1); !errors.Is(err, world.ErrNotPresent) {
			t.Fatal(err)
		}
		cancel()
		if err = <-done; err != nil {
			t.Fatal(err)
		}
		if _, err = w.Enter(context.Background(), want); !errors.Is(err, world.ErrStopped) {
			t.Fatal(err)
		}
		if err = w.Run(context.Background()); !errors.Is(err, world.ErrRunning) {
			t.Fatal(err)
		}
	})
}

type failingStore struct {
	mu    sync.Mutex
	fail  bool
	saved world.Character
}

func (s *failingStore) PutCharacter(_ context.Context, c world.Character) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.fail {
		s.fail = false
		return errors.New("temporary save failure")
	}
	s.saved = c.Clone()
	return nil
}
func TestFailedSaveCanRetry(t *testing.T) {
	cat := authored(t)
	synctest.Test(t, func(t *testing.T) {
		store := &failingStore{fail: true}
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := world.New(cat, store)
		runDone := make(chan error, 1)
		go func() { runDone <- w.Run(ctx) }()
		defer func() {
			cancel()
			if err := <-runDone; err != nil {
				t.Errorf("Run: got %v, want nil", err)
			}
		}()
		c := ready()
		entry, err := w.Enter(ctx, c)
		if err != nil {
			t.Fatal(err)
		}
		if err := w.LeaveRef(ctx, entry.Self.Entity); err == nil {
			t.Fatal("save failure lost")
		}
		if _, err := w.Enter(ctx, c); !errors.Is(err, world.ErrPresent) {
			t.Fatal("unsaved character re-entered")
		}
		if err := w.LeaveRef(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		entry, err = w.Enter(ctx, c)
		if err != nil {
			t.Fatal(err)
		}
		if err := w.LeaveRef(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
	})
}
func TestCommandsBeforeRunRespectCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	w := world.New(nil, nil)
	if _, err := w.Snapshot(ctx, 1); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
}

type blockedStore struct {
	started chan struct{}
	release chan struct{}
}

func (s *blockedStore) PutCharacter(context.Context, world.Character) error {
	close(s.started)
	<-s.release
	return nil
}
func TestConcurrentLeaveCannotOverlapSaves(t *testing.T) {
	cat := authored(t)
	synctest.Test(t, func(t *testing.T) {
		store := &blockedStore{started: make(chan struct{}), release: make(chan struct{})}
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := world.New(cat, store)
		runDone := make(chan error, 1)
		go func() { runDone <- w.Run(ctx) }()
		defer func() {
			cancel()
			if err := <-runDone; err != nil {
				t.Errorf("Run: got %v, want nil", err)
			}
		}()
		entry, err := w.Enter(ctx, ready())
		if err != nil {
			t.Fatal(err)
		}
		done := make(chan error, 1)
		go func() { done <- w.LeaveRef(ctx, entry.Self.Entity) }()
		<-store.started
		if err := w.LeaveRef(ctx, entry.Self.Entity); !errors.Is(err, world.ErrSaving) {
			t.Fatal(err)
		}
		if _, err := w.Enter(ctx, ready()); !errors.Is(err, world.ErrPresent) {
			t.Fatal(err)
		}
		// A slow store must not block commands to the loop.
		if _, err := w.Snapshot(ctx, 1); !errors.Is(err, world.ErrNotPresent) {
			t.Fatal(err)
		}
		close(store.release)
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		if _, err := w.Enter(ctx, ready()); err != nil {
			t.Fatal(err)
		}
	})
}
