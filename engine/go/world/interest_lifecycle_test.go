// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

// Interest-set lifecycle: pins follow the relationships that hold now, and
// visibility that is no longer symmetric is still undone on both sides.

import (
	"slices"
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

func TestWalkingAwayEndsDisplacedNPCPerception(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 1)
		do(func(s *state, p *player) {
			var trainer *npc
			for _, n := range s.npcs {
				if n.tmpl.ID == 9102 {
					trainer = n
				}
			}
			w.setPos(s, trainer, Point{X: 1000})
			pass(w, s, p)
			groundAt(s, p, 0)
			pass(w, s, p)
			if p.known[trainer.id] || !trainer.knownPlayers[p.id] {
				t.Fatal("need displaced perceiving trainer")
			}
			w.setPos(s, p, Point{X: -2500})
			pass(w, s, p)
			if trainer.knownPlayers[p.id] {
				t.Fatal("trainer retained walking player past its own forget distance")
			}
		})
	})
}

func TestDecayAndErasureKeepIndexesMirrored(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, do := interestWorld(t, 1)
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				if v.tmpl.ID == 9101 {
					n = v
				}
			}
			w.setPos(s, n, Point{X: 1000})
			pass(w, s, p)
			item := groundAt(s, p, 0)
			pass(w, s, p)
			if p.known[n.id] || !n.knownPlayers[p.id] {
				t.Fatal("need displaced NPC")
			}
			w.decay(s, n)
			if err := indexesMirror(s); err != nil {
				t.Fatal(err)
			}
			if s.watchers[n.id] != nil || s.perceivers[p.id][n.id] {
				t.Fatal("decayed NPC still indexed")
			}
			w.removeFromGround(s, s.ground[item])
			if err := indexesMirror(s); err != nil {
				t.Fatal(err)
			}
			if s.watchers[item] != nil {
				t.Fatal("erased ground still indexed")
			}
			w.respawn(s, n.spawn, n.tmpl)
			if err := indexesMirror(s); err != nil {
				t.Fatal(err)
			}
			for _, fresh := range s.npcs {
				if p.known[fresh.id] {
					w.decay(s, fresh)
					break
				}
			}
			if err := indexesMirror(s); err != nil {
				t.Fatal(err)
			}
		})
	})
}

func TestLifecycleKeepsIndexesMirrored(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t), WithInterest(2))
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		second := enterTest(t, w, ctx, syntheticCharacter(2))
		check := func() {
			t.Helper()
			if err := w.do(ctx, func(s *state) {
				if err := indexesMirror(s); err != nil {
					t.Error(err)
				}
			}); err != nil {
				t.Fatal(err)
			}
		}
		check()
		if err := w.Teleport(ctx, 1, Point{X: 9000}, false); err != nil {
			t.Fatal(err)
		}
		check()
		if err := w.do(ctx, func(s *state) {
			if len(s.perceivers[1]) != 0 || len(s.watchers[1]) != 0 || len(s.players[1].known) != 0 {
				t.Fatal("teleport retained relationships")
			}
		}); err != nil {
			t.Fatal(err)
		}
		if err := w.Appear(ctx, 1); err != nil {
			t.Fatal(err)
		}
		check()
		if err := w.Teleport(ctx, 1, Point{}, false); err != nil {
			t.Fatal(err)
		}
		if err := w.Appear(ctx, 1); err != nil {
			t.Fatal(err)
		}
		check()
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			for range eventBacklog + 1 {
				p.send(ActionIgnored{})
			}
		}); err != nil {
			t.Fatal(err)
		}
		if _, _, err := w.Subscribe(ctx, first.Self.Entity); err != nil {
			t.Fatal(err)
		}
		check()
		if err := w.Leave(ctx, first.Self.Entity); err != nil {
			t.Fatal(err)
		}
		check()
		if err := w.do(ctx, func(s *state) {
			if s.watchers[1] != nil || s.perceivers[1] != nil {
				t.Fatal("leave retained relationships")
			}
		}); err != nil {
			t.Fatal(err)
		}
		replacement := enterTest(t, w, ctx, syntheticCharacter(1))
		if replacement.Self.Entity == first.Self.Entity {
			t.Fatal("reentry reused incarnation")
		}
		check()
		drainEvents(second.Events)
		drainEvents(replacement.Events)
	})
}

func TestReverseIndexHelpersSortAndDropStale(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		for _, id := range []int32{3, 1, 2} {
			enterTest(t, w, ctx, syntheticCharacter(id))
		}
		if err := w.do(ctx, func(s *state) {
			item := groundAt(s, s.players[1], 0)
			for _, p := range s.players {
				w.reveal(s, p, item, s.ground[item].pos, time.Now())
			}
			link(s.watchers, item, 999)
			var got []int32
			for _, p := range s.watching(item) {
				got = append(got, p.id)
			}
			if !slices.Equal(got, []int32{1, 2, 3}) {
				t.Fatalf("watching=%v", got)
			}
			link(s.perceivers, 1, 999)
			got = nil
			for _, n := range s.perceiving(1) {
				got = append(got, n.id)
			}
			if !slices.IsSorted(got) || s.perceivers[1][999] {
				t.Fatalf("perceiving=%v; index=%v", got, s.perceivers[1])
			}
			link(s.watchers, 998, 999)
			link(s.perceivers, 998, 999)
			if len(s.watching(998)) != 0 || len(s.perceiving(998)) != 0 || s.watchers[998] != nil || s.perceivers[998] != nil {
				t.Fatal("empty stale buckets retained")
			}
			if err := indexesMirror(s); err != nil {
				t.Fatal(err)
			}
		}); err != nil {
			t.Fatal(err)
		}
	})
}

func TestActsOnlyOnLiveRelationships(t *testing.T) {
	for _, tc := range []struct {
		name string
		ai   ai
		want bool
	}{
		{"attack", ai{intention: intentAttack, object: 42}, true},
		{"cast", ai{intention: intentCast, object: 42}, true},
		{"pickup", ai{intention: intentPickUp, object: 42}, true},
		{"interact", ai{intention: intentInteract, object: 42}, true},
		{"approach", ai{clientMoving: true, pawn: 42}, true},
		{"follow", ai{followTask: true, follow: 42}, true},
		{"queued-cast", ai{next: &order{intent: intentCast, cast: &castRequest{target: Ref{ID: 42, Gen: 1}}}}, true},
		{"finished", ai{intention: intentIdle, object: 42, target: 42, pawn: 42, follow: 42}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := acts(&tc.ai, 42); got != tc.want {
				t.Fatalf("acts=%v want %v", got, tc.want)
			}
		})
	}
}

func TestRevealResolvesAHeldTargetThatLeft(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		victim := enterTest(t, w, ctx, syntheticCharacter(1))
		nid := objectOf(t, w, ctx, 9101)
		if err := w.do(ctx, func(s *state) {
			n := s.npcs[nid]
			w.setIntention(s, n, order{intent: intentAttack, object: 1})
			if n.target != 1 {
				t.Fatal("NPC did not select victim")
			}
		}); err != nil {
			t.Fatal(err)
		}
		if err := w.Leave(ctx, victim.Self.Entity); err != nil {
			t.Fatal(err)
		}
		observer := enterTest(t, w, ctx, syntheticCharacter(2))
		var streamed int32
		for _, e := range drainEvents(observer.Events) {
			if v, ok := e.(TargetChosen); ok && v.ObjectID == nid {
				streamed = v.TargetID
			}
		}
		scene := captureTest(t, w, ctx, observer.Self.Entity)
		for _, o := range scene.Objects {
			if o.Entity.ID == nid && streamed != o.Target.ID {
				t.Errorf("NPC reveal target=%d, capture target=%+v after victim Leave", streamed, o.Target)
			}
		}
	})
}
