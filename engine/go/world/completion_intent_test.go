// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"testing"
	"testing/synctest"
	"time"
)

// TestDeferredCompletionNamesItsIntent walks to a speaker and to an item before
// completing. The completing event must name the original intent, so a
// participant can be told the walk ended in speech or a pickup.
func TestDeferredCompletionNamesItsIntent(t *testing.T) {
	for _, tc := range []string{"talk", "pickup"} {
		t.Run(tc, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				c := syntheticCharacter(1)
				c.X = -300 // Beyond interaction range of the teacher at x=100 and of the item.
				entry := enterTest(t, w, ctx, c)
				owner := entry.Self.Entity
				req := Request{Owner: owner, Verb: VerbTalk, Target: refTest(t, w, ctx, objectOf(t, w, ctx, 9102))}
				if tc == "pickup" {
					mob := objectOf(t, w, ctx, 9101)
					if err := w.do(ctx, func(s *state) {
						s.rule(RuleReward, func() { w.dropOnGround(s, s.npcs[mob], s.players[1], 9002, 1) })
						for _, g := range s.ground {
							req = Request{Owner: owner, Verb: VerbPickup, Target: g.ref()}
						}
					}); err != nil {
						t.Fatal(err)
					}
				}
				drainEvents(entry.Events)
				commit := executeTest(t, w, ctx, req)
				if commit.Outcome.Stage != StageUnknown {
					t.Fatalf("deferred %s: got %+v, want a started intent", tc, commit.Outcome)
				}
				var events []Event
				for range 40 {
					advanceVirtual(250 * time.Millisecond)
					events = append(events, drainEvents(entry.Events)...)
				}
				named := false
				switch tc {
				case "talk":
					named = containsEvent(events, func(e DialoguePage) bool { return e.SpeechIntent == commit.Intent.ID })
				case "pickup":
					named = containsEvent(events, func(e ItemPicked) bool { return e.PickupIntent == commit.Intent.ID && e.PlayerID == owner.ID })
				}
				if !named {
					t.Fatalf("deferred %s completion does not name intent %v: %+v", tc, commit.Intent.ID, events)
				}
			})
		})
	}
}
