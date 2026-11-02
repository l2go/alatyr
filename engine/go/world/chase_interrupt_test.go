// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// TestTalkOrPickupDuringChaseTurnsAround attacks a creature to the east and,
// mid-chase, talks to the tutor or picks up an item to the west. A talk used
// to keep the chase path: both approaches use offset 60, and the movement guard
// compared the speaker with an AI target the talk had just set. A pickup uses
// its own offset and is checked the same way. The character must turn around
// at once and complete within the walk time.
func TestTalkOrPickupDuringChaseTurnsAround(t *testing.T) {
	cat, err := catalog.Load(repositoryWorld())
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []string{"talk", "pickup"} {
		t.Run(tc, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				w := New(cat, &testStore{}, WithReservedIDs([]int32{1, 10, 11}))
				ctx, cancel := context.WithCancel(t.Context())
				defer cancel()
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				c := Character{ObjectID: 1, Account: "one", Name: "One", ClassID: 0, Level: 2, Exp: 68, SP: 100, CurHP: 200, CurMP: 50, CurCP: 10, X: -70700, Y: 258163, Z: -3104,
					Items: []Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}}}
				entry := enterTest(t, w, ctx, c)
				owner := entry.Self.Entity
				mob, tutor := objectOf(t, w, ctx, 18003), objectOf(t, w, ctx, 30001)
				var mobRef, item Ref
				var goal Point
				if err := w.do(ctx, func(s *state) {
					n := s.npcs[mob]
					n.pos = Point{X: -70100, Y: 258163, Z: -3104}
					mobRef = n.ref()
					goal = s.npcs[tutor].pos
					if tc == "pickup" {
						goal = Point{X: -71000, Y: 258000, Z: -3104}
						s.rule(RuleReward, func() { w.createGround(s, n, s.players[1], 57, 5, goal, true) })
						for _, g := range s.ground {
							item = g.ref()
						}
					}
				}); err != nil {
					t.Fatal(err)
				}
				advanceVirtual(500 * time.Millisecond)
				executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAttack, Target: mobRef})
				advanceVirtual(750 * time.Millisecond)
				drainEvents(entry.Events)
				req := Request{Owner: owner, Verb: VerbTalk, Target: refTest(t, w, ctx, tutor)}
				if tc == "pickup" {
					req = Request{Owner: owner, Verb: VerbPickup, Target: item}
				}
				commit := executeTest(t, w, ctx, req)
				var start float64
				if err := w.do(ctx, func(s *state) { start = distance3D(s.players[1].pos, goal) }); err != nil {
					t.Fatal(err)
				}
				completed := false
				// About 135 units per second; allow one second beyond the walk.
				for i := range int(start/135*4) + 4 {
					advanceVirtual(250 * time.Millisecond)
					var d float64
					if err := w.do(ctx, func(s *state) { d = distance3D(s.players[1].pos, goal) }); err != nil {
						t.Fatal(err)
					}
					if d > start+10 {
						t.Fatalf("%s at %dms walked away: %.0f from the goal, started at %.0f", tc, (i+1)*250, d, start)
					}
					events := drainEvents(entry.Events)
					completed = completed || containsEvent(events, func(e DialoguePage) bool { return e.SpeechIntent == commit.Intent.ID }) ||
						containsEvent(events, func(e ItemPicked) bool { return e.PickupIntent == commit.Intent.ID })
				}
				if !completed {
					t.Fatalf("%s did not complete within its walk from %.0f units", tc, start)
				}
			})
		})
	}
}
