// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"testing/synctest"
)

func TestLeaveRejectsAnotherIncarnation(t *testing.T) {
	for _, voluntary := range []bool{false, true} {
		t.Run(map[bool]string{false: "leave", true: "logout"}[voluntary], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, store, ctx := startObservedWorld(t, &log)
				first := enterTest(t, w, ctx, syntheticCharacter(1))
				if err := w.LeaveRef(ctx, first.Self.Entity); err != nil {
					t.Fatal(err)
				}
				replacement := enterTest(t, w, ctx, syntheticCharacter(1))
				before := captureTest(t, w, ctx, replacement.Self.Entity)
				saved := store.saved.Clone()
				drainEvents(replacement.Events)
				log.reset()
				var err error
				if voluntary {
					err = w.LogoutRef(ctx, first.Self.Entity)
				} else {
					err = w.LeaveRef(ctx, first.Self.Entity)
				}
				assertLeaveRefusal(t, err, first.Self.Entity)
				after := captureTest(t, w, ctx, replacement.Self.Entity)
				if !reflect.DeepEqual(before.Self, after.Self) || !reflect.DeepEqual(saved, store.saved) || len(drainEvents(replacement.Events)) != 0 {
					t.Fatal("stale departure changed the replacement or its persistence")
				}
				for _, commit := range log.read() {
					if commit.Intent.ID.Owner != first.Self.Entity || commit.Outcome.Reason != ReasonStale || commit.Outcome.Stage != StageRefused || len(commit.Groups) != 0 {
						t.Fatalf("stale departure commitment: got %+v", commit)
					}
				}
			})
		})
	}
}

type retrySaveStore struct {
	failure error
	calls   int
	saved   Character
}

func (s *retrySaveStore) PutCharacter(_ context.Context, c Character) error {
	s.calls++
	if s.calls == 1 {
		return s.failure
	}
	s.saved = c.Clone()
	return nil
}

func TestLeaveRetryBelongsToOriginalIncarnation(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		failure := errors.New("temporary write failure")
		store := &retrySaveStore{failure: failure}
		ctx, cancel := context.WithCancel(t.Context())
		w := New(syntheticCatalog(t), store)
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		t.Cleanup(func() {
			cancel()
			if err := <-done; err != nil {
				t.Errorf("Run: %v", err)
			}
		})
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		owner := entry.Self.Entity
		if err := w.LeaveRef(ctx, owner); !errors.Is(err, failure) {
			t.Fatalf("first save: got %v, want wrapped failure", err)
		}
		for _, foreign := range []Ref{{ID: owner.ID}, {ID: owner.ID, Gen: owner.Gen + 1}} {
			assertLeaveRefusal(t, w.LeaveRef(ctx, foreign), foreign)
		}
		if store.calls != 1 {
			t.Fatalf("foreign retries reached store: got %d calls, want 1", store.calls)
		}
		entry.Self.Char.Items[0].Count = 999
		if err := w.LeaveRef(ctx, owner); err != nil {
			t.Fatal(err)
		}
		if store.calls != 2 || store.saved.Items[0].Count != 1 {
			t.Fatalf("original retry: calls %d, saved %+v", store.calls, store.saved)
		}
		if err := w.LeaveRef(ctx, owner); err != nil || store.calls != 2 {
			t.Fatalf("idempotent departure: error %v, calls %d", err, store.calls)
		}
		replacement := enterTest(t, w, ctx, syntheticCharacter(1))
		assertLeaveRefusal(t, w.LeaveRef(ctx, owner), owner)
		if _, err := w.Capture(ctx, replacement.Self.Entity); err != nil {
			t.Fatalf("replacement lost: %v", err)
		}
	})
}

func assertLeaveRefusal(t *testing.T, err error, owner Ref) {
	t.Helper()
	var refusal *RefusalError
	if !errors.As(err, &refusal) || refusal.Owner != owner || refusal.Reason != ReasonStale {
		t.Fatalf("departure: got %v, want typed stale refusal for %+v", err, owner)
	}
}
