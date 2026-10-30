// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"testing"
	"testing/synctest"
	"time"
)

// Forgetting an NPC's target can broadcast Stop to remaining observers. The
// target must be forgotten in a stable order even though each removal names
// just one player; this loop is not an independent per-recipient fan-out.
func TestNPCForgetOrderAffectsRemainingObserver(t *testing.T) {
	for _, tc := range []struct {
		name     string
		order    []int32
		update   bool
		wantStop bool
	}{
		{"target-first", []int32{1, 2}, false, true},
		{"target-last", []int32{2, 1}, false, false},
		{"visibility-update", nil, true, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				w, _, ctx := startTestWorld(t, syntheticCatalog(t))
				first := enterTest(t, w, ctx, syntheticCharacter(1))
				second := enterTest(t, w, ctx, syntheticCharacter(2))
				id := objectOf(t, w, ctx, 9101)
				drainEvents(first.Events)
				drainEvents(second.Events)
				if err := w.do(ctx, func(s *state) {
					n := s.npcs[id]
					n.knownPlayers = map[int32]bool{1: true, 2: true}
					n.ai = ai{intention: intentAttack, object: 1, target: 1, follow: 1, clientMoving: true}
					// Both positions stay in their indexed region, beyond the NPC's
					// forget radius, while the players still observe the NPC.
					s.players[1].pos = Point{X: 3000}
					s.players[2].pos = Point{X: 3000}
					if tc.update {
						n.lastKnown = time.Now().Add(-time.Second)
						w.updateKnown(s, n)
					} else {
						for _, playerID := range tc.order {
							w.forgetPlayer(s, n, playerID)
						}
					}
				}); err != nil {
					t.Fatal(err)
				}
				gotStop := false
				for _, e := range drainEvents(second.Events) {
					if v, ok := e.(Stopped); ok && v.ObjectID == id {
						gotStop = true
					}
				}
				if gotStop != tc.wantStop {
					t.Fatalf("remaining observer received Stop: got %v, want %v", gotStop, tc.wantStop)
				}
			})
		})
	}
}
