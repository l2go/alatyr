// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"errors"
	"testing"
	"testing/synctest"
)

// An NPC learns nearby players in its own visibility pass, which can run
// before the player's pass learns the NPC. The soak saw a creature change gait
// in that window and tell a player about an object the player did not know.
func TestNPCEventsReachOnlyPlayersWhoKnowIt(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		id := objectOf(t, w, ctx, 9101)
		var setup error
		if err := w.do(ctx, func(s *state) {
			p, n := s.players[1], s.npcs[id]
			w.playerForgetObject(s, p, id)
			n.pos = Point{X: p.pos.X + 100, Y: p.pos.Y, Z: p.pos.Z}
			w.learnPlayer(s, n, p)
			if p.known[id] || !n.knownPlayers[p.id] {
				setup = errors.New("the NPC does not know the player ahead of the player's pass")
			}
		}); err != nil {
			t.Fatal(err)
		}
		if setup != nil {
			t.Fatal(setup)
		}
		drainEvents(entry.Events)
		if err := w.do(ctx, func(s *state) {
			n := s.npcs[id]
			w.setRunning(s, n, !n.running)
			w.fullInfo(s, n)
		}); err != nil {
			t.Fatal(err)
		}
		for _, e := range drainEvents(entry.Events) {
			switch v := e.(type) {
			case GaitChanged:
				if v.ObjectID == id {
					t.Fatalf("player received %T for an NPC it does not know", e)
				}
			case NPCSeen:
				if v.NPC.ObjectID == id {
					t.Fatalf("player received %T for an NPC it does not know", e)
				}
			}
		}
	})
}
