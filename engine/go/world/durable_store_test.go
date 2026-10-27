// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
)

type durableStore struct {
	testStore
	started chan Character
	release chan struct{}
	failure error
}

func (s *durableStore) PutLearning(ctx context.Context, c Character) error {
	s.started <- c.Clone()
	select {
	case <-s.release:
		return s.failure
	case <-ctx.Done():
		return ctx.Err()
	}
}

func TestLearningCapturesAtomicBalanceOutsideWorldLoop(t *testing.T) {
	for _, failed := range []bool{false, true} {
		t.Run(map[bool]string{false: "committed", true: "failed"}[failed], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				store := &durableStore{started: make(chan Character, 1), release: make(chan struct{})}
				if failed {
					store.failure = errors.New("write failed")
				}
				ctx, cancel := context.WithCancel(t.Context())
				defer cancel()
				w := New(syntheticCatalog(t), store)
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				defer func() {
					cancel()
					if err := <-done; err != nil {
						t.Error(err)
					}
				}()
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				teacher := objectOf(t, w, ctx, 9102)
				w.Action(ctx, 1, teacher)
				w.Action(ctx, 1, teacher)
				w.Bypass(ctx, 1, fmtChoice(teacher))
				learned := make(chan error, 1)
				go func() { learned <- w.Learn(ctx, 1, 9202, 1) }()
				c := <-store.started
				if c.SP != 17 || !slices.Contains(c.Skills, Skill{ID: 9202, Level: 1}) {
					t.Fatalf("atomic snapshot: %+v", c)
				}
				if _, err := w.Capture(ctx, entry.Self.Entity); err != nil {
					t.Fatal(err)
				}
				if err := w.Leave(ctx, entry.Self.Entity); !errors.Is(err, ErrSaving) {
					t.Fatalf("overlapping departure: %v", err)
				}
				close(store.release)
				if err := <-learned; !errors.Is(err, store.failure) {
					t.Fatalf("learning got %v, want %v", err, store.failure)
				}
				if err := w.Leave(ctx, entry.Self.Entity); err != nil {
					t.Fatal(err)
				}
				if store.saved.SP != 17 || !reflect.DeepEqual(store.saved.Skills, c.Skills) {
					t.Fatalf("departure lost learning: %+v", store.saved)
				}
				replacement := enterTest(t, w, ctx, syntheticCharacter(1))
				assertLeaveusal(t, w.Leave(ctx, entry.Self.Entity), entry.Self.Entity)
				if _, err := w.Capture(ctx, replacement.Self.Entity); err != nil {
					t.Fatal(err)
				}
			})
		})
	}
}

func TestDurableIDRangeNeverEscapesReservation(t *testing.T) {
	for _, tc := range []struct {
		name        string
		first, last int32
		reserved    []int32
		want        []int32
	}{
		{"one", firstObjectID, firstObjectID, nil, []int32{firstObjectID, 0, 0}},
		{"reserved-end", firstObjectID, firstObjectID + 1, []int32{firstObjectID + 1}, []int32{firstObjectID, 0}},
		{"invalid", firstObjectID - 1, firstObjectID, nil, []int32{0}},
		{"empty", firstObjectID + 1, firstObjectID, nil, []int32{0}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			w := New(nil, nil, WithReservedIDs(tc.reserved), WithIDRange(tc.first, tc.last))
			for _, want := range tc.want {
				if got := w.s.ids.next(); got != want {
					t.Fatalf("next got %d, want %d", got, want)
				}
			}
		})
	}
}

func TestIDExhaustionStopsRunAndPublishesFinalState(t *testing.T) {
	for _, path := range []string{"ground", "reward", "spawn"} {
		t.Run(path, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				w := New(syntheticCatalog(t), &testStore{})
				ctx, cancel := context.WithCancel(t.Context())
				defer cancel()
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				err := w.do(ctx, func(s *state) {
					s.ids.limit = s.ids.last
					p := s.players[1]
					p.char.SP = 17
					n := &npc{}
					switch path {
					case "ground":
						w.dropOnGround(s, n, p, 9001, 1)
					case "reward":
						w.giveItem(s, p, n, 9001, 1)
					case "spawn":
						w.spawnNPC(s, catalog.Spawn{}, catalog.NPCTemplate{})
					}
				})
				if !errors.Is(err, ErrExhausted) {
					t.Fatalf("allocation: %v", err)
				}
				if err := <-done; !errors.Is(err, ErrExhausted) {
					t.Fatalf("Run: %v", err)
				}
				if _, err := w.Capture(ctx, entry.Self.Entity); !errors.Is(err, ErrStopped) {
					t.Fatalf("loop still ready: %v", err)
				}
				final, err := w.Final(ctx)
				if err != nil || len(final) != 1 || final[0].Owner != entry.Self.Entity || final[0].Character.SP != 17 {
					t.Fatalf("final: %+v/%v", final, err)
				}
				final[0].Character.SP = 999
				originalCount := final[0].Character.Items[0].Count
				final[0].Character.Items[0].Count++
				again, err := w.Final(ctx)
				if err != nil || again[0].Character.SP != 17 || len(again[0].Character.Items) == 0 || again[0].Character.Items[0].Count != originalCount {
					t.Fatalf("final snapshot aliases caller: %+v/%v", again, err)
				}
			})
		})
	}
}

func TestFinalCheckpointIncludesInFlightLearning(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		store := &durableStore{started: make(chan Character, 1), release: make(chan struct{})}
		w := New(syntheticCatalog(t), store)
		ctx, cancel := context.WithCancel(t.Context())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		teacher := objectOf(t, w, ctx, 9102)
		w.Action(ctx, 1, teacher)
		w.Action(ctx, 1, teacher)
		w.Bypass(ctx, 1, fmtChoice(teacher))
		learning := make(chan error, 1)
		go func() { learning <- w.Learn(t.Context(), 1, 9202, 1) }()
		captured := <-store.started
		// Freeze while external I/O owns a detached snapshot. The final world
		// snapshot must already contain the committed SP and skill.
		cancel()
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		final, err := w.Final(t.Context())
		if err != nil || len(final) != 1 || final[0].Owner != entry.Self.Entity || final[0].Character.SP != captured.SP || !reflect.DeepEqual(final[0].Character.Skills, captured.Skills) {
			t.Fatalf("in-flight checkpoint: %+v/%v", final, err)
		}
		close(store.release)
		if err := <-learning; !errors.Is(err, ErrStopped) {
			t.Fatalf("late write did not join stopped world: %v", err)
		}
	})
}

type failedDepartureStore struct{}

func (failedDepartureStore) PutCharacter(context.Context, Character) error {
	return errors.New("departure failed")
}

func TestFinalCheckpointRetainsFailedDeparture(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w := New(syntheticCatalog(t), failedDepartureStore{})
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		if err := w.Leave(ctx, entry.Self.Entity); err == nil {
			t.Fatal("departure unexpectedly saved")
		}
		if _, err := w.Capture(ctx, entry.Self.Entity); !errors.Is(err, ErrNotPresent) {
			t.Fatalf("failed departure still live: %v", err)
		}
		cancel()
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		final, err := w.Final(t.Context())
		if err != nil || len(final) != 1 || final[0].Owner != entry.Self.Entity || final[0].Character.ObjectID != 1 {
			t.Fatalf("pending departure lost: %+v/%v", final, err)
		}
	})
}
