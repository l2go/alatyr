// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
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
