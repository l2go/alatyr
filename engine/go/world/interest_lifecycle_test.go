// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

// Interest-set lifecycle: pins follow the relationships that hold now, and
// visibility that is no longer symmetric is still undone on both sides.

import (
	"testing"
	"testing/synctest"
	"time"
)

func TestFinishedActionReleasesItsPin(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 1)
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				if v.tmpl.ID == 9101 {
					n = v
				}
			}
			w.setPos(s, n, Point{X: p.pos.X + 1000})
			pass(w, s, p)
			w.moveToPawn(s, p, n.id, 60)
			w.setIntention(s, p, order{intent: intentIdle})
			w.setTarget(s, p, 0)
			if p.ai.clientMoving || p.ai.intention != intentIdle || p.target != 0 {
				t.Fatal("fixture still acts on NPC")
			}
			closer := groundAt(s, p, 0)
			pass(w, s, p)
			if !p.known[closer] {
				t.Errorf("stopped action still pins NPC %d: ai=%+v, known=%v", n.id, p.ai, knownIDs(p))
			}
		})
	})
}

func TestAttackingSomeoneElseIsNotPinned(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 1)
		do(func(s *state, p *player) {
			for _, n := range s.npcs {
				n.target = 2
				n.ai = ai{object: 2, target: 2}
				n.hate = nil
				n.attacking = 1
				if w.pinned(s, p, n.id) {
					t.Errorf("NPC %d attacking someone else is pinned for observer ID %d", n.id, p.id)
				}
			}
		})
	})
}

func TestStationaryObserverForgetsAPlayerWhoCannotSeeIt(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t), WithInterest(1))
		if err := w.do(ctx, func(s *state) {
			for _, n := range s.npcs {
				w.setPos(s, n, Point{X: 100000})
			}
		}); err != nil {
			t.Fatal(err)
		}
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		if err := w.do(ctx, func(s *state) { groundAt(s, s.players[1], 0); pass(w, s, s.players[1]) }); err != nil {
			t.Fatal(err)
		}
		ch := syntheticCharacter(2)
		ch.X = -1000
		second := enterTest(t, w, ctx, ch)
		drainEvents(first.Events)
		drainEvents(second.Events)
		if err := w.do(ctx, func(s *state) {
			p, q := s.players[1], s.players[2]
			if p.known[q.id] || !q.known[p.id] {
				t.Errorf("fixture must have asymmetric visibility: p=%v q=%v", knownIDs(p), knownIDs(q))
				return
			}
			w.setPos(s, p, Point{X: 10000})
			pass(w, s, p)
			w.tickRevalidate(s)
			if q.known[p.id] {
				t.Errorf("stationary observer still sees departed player: distance=%v, forget=%v", distance3D(q.pos, p.pos), playerForgetDistance(len(q.known)))
			}
			if err := indexesMirror(s); err != nil {
				t.Error(err)
			}
		}); err != nil {
			t.Fatal(err)
		}
	})
}

func TestTeleportEndsDisplacedNPCPerception(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 1)
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				if v.tmpl.ID == 9101 {
					n = v
				}
			}
			w.setPos(s, n, Point{X: p.pos.X + 1000})
			pass(w, s, p)
			groundAt(s, p, 0)
			pass(w, s, p)
			if p.known[n.id] || !n.knownPlayers[p.id] {
				t.Fatal("fixture needs displaced NPC retaining perception")
			}
			if err := w.teleport(s, p, Point{X: 10000}, false); err != nil {
				t.Fatal(err)
			}
			if n.knownPlayers[p.id] {
				t.Errorf("NPC still perceives teleported player: intention=%v, distance=%v", n.ai.intention, distance3D(n.pos, p.pos))
			}
		})
	})
}

func TestQueuedActionKeepsItsObjectPinned(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 2)
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				if v.tmpl.ID == 9101 {
					n = v
				}
			}
			w.setPos(s, n, Point{X: 1000})
			pass(w, s, p)
			// A protected action queues a pickup; an independent selection does not
			// replace that queued order.
			item := groundAt(s, p, 4)
			w.reveal(s, p, item, s.ground[item].pos, time.Now())
			p.attackEnd = time.Now().Add(time.Second)
			w.setIntention(s, p, order{intent: intentPickUp, object: item})
			if p.ai.next == nil || p.ai.next.object != item {
				t.Fatal("pickup was not queued")
			}
			closer := groundAt(s, p, 0)
			pass(w, s, p)
			if !p.known[item] {
				t.Errorf("queued pickup %d displaced by %d before execution; next=%+v", item, closer, p.ai.next)
			}
		})
	})
}

func TestLeaveEndsDisplacedNPCPerception(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 1)
		var trainer *npc
		do(func(s *state, p *player) {
			for _, n := range s.npcs {
				if n.tmpl.ID == 9102 {
					trainer = n
				}
			}
			trainer.pos = Point{X: 1000}
			pass(w, s, p)
			groundAt(s, p, 0)
			pass(w, s, p)
			if p.known[trainer.id] || !trainer.knownPlayers[p.id] {
				t.Error("fixture needs displaced trainer")
				return
			}
		})
		if err := w.Leave(t.Context(), entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		// A trainer has no movement/AI turn that would queue its visibility pass.
		time.Sleep(time.Minute)
		if err := w.do(t.Context(), func(s *state) {
			if trainer.knownPlayers[entry.Self.Entity.ID] {
				t.Errorf("trainer retains departed player %d after one minute; map=%v", entry.Self.Entity.ID, trainer.knownPlayers)
			}
			if err := indexesMirror(s); err != nil {
				t.Error(err)
			}
		}); err != nil {
			t.Fatal(err)
		}
	})
}

func TestRevealCarriesTheActorsTarget(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t), WithInterest(1))
		if err := w.do(ctx, func(s *state) {
			for _, n := range s.npcs {
				n.pos = Point{X: 100000}
			}
		}); err != nil {
			t.Fatal(err)
		}
		pentry := enterTest(t, w, ctx, syntheticCharacter(1))
		var drop int32
		if err := w.do(ctx, func(s *state) { drop = groundAt(s, s.players[1], 0); pass(w, s, s.players[1]) }); err != nil {
			t.Fatal(err)
		}
		qchar := syntheticCharacter(2)
		qchar.X = 1000
		qentry := enterTest(t, w, ctx, qchar)
		rchar := syntheticCharacter(3)
		rchar.X = 3000
		rentry := enterTest(t, w, ctx, rchar)
		if err := w.do(ctx, func(s *state) {
			p, q, r := s.players[1], s.players[2], s.players[3]
			if p.known[q.id] || p.known[r.id] {
				t.Error("fixture should hide both other players")
				return
			}
			for id := range q.known {
				w.hide(s, q, id, false)
			}
			w.reveal(s, q, r.id, r.pos, time.Now())
			w.setTarget(s, q, r.id)
		}); err != nil {
			t.Fatal(err)
		}
		drainEvents(pentry.Events)
		drainEvents(qentry.Events)
		drainEvents(rentry.Events)
		if err := w.do(ctx, func(s *state) {
			w.removeFromGround(s, s.ground[drop])
			p, q := s.players[1], s.players[2]
			w.reveal(s, p, q.id, q.pos, time.Now())
		}); err != nil {
			t.Fatal(err)
		}
		events := drainEvents(pentry.Events)
		chosen := false
		for _, e := range events {
			if v, ok := e.(TargetChosen); ok && v.ObjectID == 2 && v.TargetID == 3 {
				chosen = true
			}
		}
		scene := captureTest(t, w, ctx, pentry.Self.Entity)
		for _, o := range scene.Objects {
			if o.Entity.ID == 2 && o.Target.ID == 3 && !chosen {
				t.Errorf("capture says player 2 targets hidden player 3; live reveal sent no TargetChosen: %v", events)
			}
		}
	})
}
