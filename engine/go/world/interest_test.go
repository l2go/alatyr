// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"fmt"
	"maps"
	"slices"
	"testing"
	"testing/synctest"
	"time"
)

// interestWorld enters one player into a world that shows at most limit
// objects and moves every NPC out of sight, so that only the ground items a
// test places compete for the player's interest set.
func interestWorld(t *testing.T, limit int) (*World, Entry, func(func(*state, *player))) {
	t.Helper()
	w, _, ctx := startTestWorld(t, syntheticCatalog(t), WithInterest(limit))
	entry := enterTest(t, w, ctx, syntheticCharacter(1))
	do := func(f func(*state, *player)) {
		t.Helper()
		if err := w.do(ctx, func(s *state) { f(s, s.players[1]) }); err != nil {
			t.Fatal(err)
		}
	}
	do(func(s *state, p *player) {
		for _, n := range s.npcs {
			n.pos = Point{X: p.pos.X + 100000, Y: p.pos.Y, Z: p.pos.Z}
		}
		pass(w, s, p)
	})
	return w, entry, do
}

// pass runs the player's visibility pass now.
func pass(w *World, s *state, p *player) {
	p.lastKnown = time.Now().Add(-time.Second)
	w.updateKnown(s, p)
}

// groundAt places an item without showing it; the next pass decides.
func groundAt(s *state, p *player, step int32) int32 {
	g := &groundItem{id: s.newID(), templateID: 9001, count: 1, generation: s.incarnation(), pos: Point{X: p.pos.X + step*interestStep + interestStep/2, Y: p.pos.Y, Z: p.pos.Z}}
	s.ground[g.id] = g
	return g.id
}

// observed follows the participant's count of observed objects through the
// event stream and fails if it ever exceeds limit, even between two events.
type observed struct {
	t     *testing.T
	limit int
	ids   map[int32]bool
}

func newObserved(t *testing.T, limit int, events []Event) *observed {
	o := &observed{t: t, limit: limit, ids: map[int32]bool{}}
	o.read(events)
	return o
}

func (o *observed) read(events []Event) (gone, appeared []int32) {
	o.t.Helper()
	for _, e := range events {
		var id int32
		switch v := e.(type) {
		case ObjectGone:
			delete(o.ids, v.ObjectID)
			gone = append(gone, v.ObjectID)
			continue
		case ItemDropped:
			id = v.ObjectID
		case NPCSeen:
			id = v.NPC.ObjectID
		case PlayerSeen:
			id = v.Player.Char.ObjectID
		default:
			continue
		}
		if !o.ids[id] {
			appeared = append(appeared, id)
		}
		o.ids[id] = true
		if len(o.ids) > o.limit {
			o.t.Fatalf("participant observes %d objects, limit %d", len(o.ids), o.limit)
		}
	}
	return gone, appeared
}

func knownIDs(p *player) []int32 { return slices.Sorted(maps.Keys(p.known)) }

func TestInterestKeepsTheNearestWithinBudget(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 4)
		o := newObserved(t, 4, drainEvents(entry.Events))
		var nearest []int32
		do(func(s *state, p *player) {
			// Later IDs lie nearer, so the order is not the ID order.
			ids := make([]int32, 12)
			for i := range ids {
				ids[i] = groundAt(s, p, int32(len(ids)-1-i))
			}
			nearest = slices.Sorted(slices.Values(ids[len(ids)-4:]))
			pass(w, s, p)
		})
		o.read(drainEvents(entry.Events))
		do(func(s *state, p *player) {
			if got := knownIDs(p); !slices.Equal(got, nearest) {
				t.Fatalf("known %v, want the four nearest %v", got, nearest)
			}
		})
		if got := slices.Sorted(maps.Keys(o.ids)); !slices.Equal(got, nearest) {
			t.Fatalf("participant observes %v, want %v", got, nearest)
		}
	})
}

func TestInterestDisplacesOnlyFromANearerStep(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 4)
		o := newObserved(t, 4, drainEvents(entry.Events))
		var worst int32
		do(func(s *state, p *player) {
			for step := range int32(4) {
				worst = groundAt(s, p, step)
			}
			pass(w, s, p)
		})
		o.read(drainEvents(entry.Events))
		var same, nearer int32
		do(func(s *state, p *player) {
			same = groundAt(s, p, 3)
			pass(w, s, p)
		})
		if gone, appeared := o.read(drainEvents(entry.Events)); len(gone)+len(appeared) != 0 {
			t.Fatalf("an object at the same step churned the view: gone %v, appeared %v", gone, appeared)
		}
		do(func(s *state, p *player) {
			nearer = groundAt(s, p, 1)
			pass(w, s, p)
		})
		events := drainEvents(entry.Events)
		gone, appeared := o.read(events)
		if !slices.Equal(gone, []int32{worst}) || !slices.Equal(appeared, []int32{nearer}) {
			t.Fatalf("gone %v, appeared %v; want %d replaced by %d (same step %d stays hidden)", gone, appeared, worst, nearer, same)
		}
		goneAt := slices.IndexFunc(events, func(e Event) bool { v, ok := e.(ObjectGone); return ok && v.ObjectID == worst })
		seenAt := slices.IndexFunc(events, func(e Event) bool { v, ok := e.(ItemDropped); return ok && v.ObjectID == nearer })
		if goneAt > seenAt {
			t.Fatalf("appearance at %d preceded the displaced object's departure at %d", seenAt, goneAt)
		}
	})
}

func TestInterestLimitsDisplacementsPerPass(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		const limit = 16
		w, entry, do := interestWorld(t, limit)
		o := newObserved(t, limit, drainEvents(entry.Events))
		do(func(s *state, p *player) {
			for range limit {
				groundAt(s, p, 6)
			}
			pass(w, s, p)
		})
		o.read(drainEvents(entry.Events))
		do(func(s *state, p *player) {
			for range 12 {
				groundAt(s, p, 0)
			}
			pass(w, s, p)
		})
		if gone, appeared := o.read(drainEvents(entry.Events)); len(gone) != maxDisplaced || len(appeared) != maxDisplaced {
			t.Fatalf("one pass replaced %d objects with %d, want %d", len(gone), len(appeared), maxDisplaced)
		}
		do(func(s *state, p *player) { pass(w, s, p) })
		if gone, appeared := o.read(drainEvents(entry.Events)); len(gone) != 12-maxDisplaced || len(appeared) != 12-maxDisplaced {
			t.Fatalf("the next pass replaced %d objects with %d, want %d", len(gone), len(appeared), 12-maxDisplaced)
		}
	})
}

func TestInterestKeepsPinnedObjects(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 4)
		o := newObserved(t, 4, drainEvents(entry.Events))
		var hunter int32
		do(func(s *state, p *player) {
			for step := range int32(4) {
				groundAt(s, p, step)
			}
			pass(w, s, p)
			// A creature that hunts the player, farther than everything seen.
			for _, n := range s.npcs {
				hunter = n.id
				n.pos = Point{X: p.pos.X + 6*interestStep, Y: p.pos.Y, Z: p.pos.Z}
				n.ai.target = p.id
				break
			}
		})
		o.read(drainEvents(entry.Events))
		do(func(s *state, p *player) {
			pass(w, s, p)
			if !p.known[hunter] {
				t.Fatal("a creature hunting the player stayed hidden")
			}
			for range 4 {
				groundAt(s, p, 0)
			}
			pass(w, s, p)
			if !p.known[hunter] || len(p.known) != 4 {
				t.Fatalf("known %v: the hunter %d was displaced or the set grew", knownIDs(p), hunter)
			}
		})
		o.read(drainEvents(entry.Events))
	})
}

func TestInterestBoundsDrops(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 4)
		o := newObserved(t, 4, drainEvents(entry.Events))
		do(func(s *state, p *player) {
			for step := range int32(4) {
				groundAt(s, p, step)
			}
			pass(w, s, p)
		})
		o.read(drainEvents(entry.Events))
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				n = v
				break
			}
			for _, step := range []int32{5, 0} {
				w.createGround(s, n, p, 9001, 1, Point{X: p.pos.X + step*interestStep + interestStep/2, Y: p.pos.Y, Z: p.pos.Z}, true)
			}
			if len(p.known) != 4 {
				t.Fatalf("drops grew the set to %d", len(p.known))
			}
		})
		gone, appeared := o.read(drainEvents(entry.Events))
		if len(gone) != 1 || len(appeared) != 1 {
			t.Fatalf("drops: gone %v, appeared %v; want only the nearer drop to replace one object", gone, appeared)
		}
	})
}

func TestInterestSelectionIsDeterministic(t *testing.T) {
	selection := func() string {
		var got string
		synctest.Test(t, func(t *testing.T) {
			w, _, do := interestWorld(t, 8)
			do(func(s *state, p *player) {
				for i := range int32(30) {
					groundAt(s, p, i%7)
				}
				pass(w, s, p)
				got = fmt.Sprint(knownIDs(p))
			})
		})
		return got
	}
	if a, b := selection(), selection(); a != b {
		t.Fatalf("two identical worlds selected %s and %s", a, b)
	}
}

// Events keep their references closed over the observer's interest set,
// except the target an actor holds, which a scene capture also reports.
func TestDeliveryKeepsReferencesClosed(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		second := enterTest(t, w, ctx, syntheticCharacter(2))
		id := objectOf(t, w, ctx, 9101)
		var drop int32
		if err := w.do(ctx, func(s *state) {
			p, q := s.players[1], s.players[2]
			if !q.known[p.id] || !p.known[id] {
				t.Fatal("players do not see each other and the creature")
			}
			w.playerForgetObject(s, q, id)
			drop = groundAt(s, q, 0)
			w.admit(s, q, drop, s.ground[drop].pos)
		}); err != nil {
			t.Fatal(err)
		}
		drainEvents(first.Events)
		drainEvents(second.Events)
		if err := w.do(ctx, func(s *state) {
			p, q := s.players[1], s.players[2]
			w.broadcastToKnown(s, p, AttackStarted{AttackerID: p.id, TargetID: id})
			w.broadcastToKnown(s, p, SkillCast{CasterID: p.id, TargetID: id})
			w.broadcastToKnown(s, p, TargetChosen{ObjectID: p.id, TargetID: id})
			w.broadcastToKnown(s, s.npcs[id], Moving{ObjectID: id})
			w.deliver(q, ItemDropped{ObjectID: drop, DropperID: id})
			w.deliver(q, SkillLaunched{CasterID: p.id, Targets: []int32{id, q.id}})
		}); err != nil {
			t.Fatal(err)
		}
		if got := drainEvents(first.Events); len(got) != 4 {
			t.Fatalf("the observer of every object received %d of 4 events: %v", len(got), got)
		}
		got := drainEvents(second.Events)
		for i, e := range got {
			switch v := e.(type) {
			case TargetChosen:
				v.stamp = stamp{}
				got[i] = v
			case ItemDropped:
				v.stamp = stamp{}
				got[i] = v
			case SkillLaunched:
				v.stamp = stamp{}
				got[i] = v
			}
		}
		want := []Event{
			TargetChosen{ObjectID: 1, TargetID: id},
			ItemDropped{ObjectID: drop},
			SkillLaunched{CasterID: 1, Targets: []int32{2}},
		}
		if fmt.Sprint(got) != fmt.Sprint(want) {
			t.Fatalf("observer without the creature received %v, want %v", got, want)
		}
	})
}

// Displacement changes only what the player is shown. A creature that left
// the player's view in a crowd still perceives the player.
func TestDisplacementKeepsAIPerception(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, entry, do := interestWorld(t, 1)
		o := newObserved(t, 1, drainEvents(entry.Events))
		do(func(s *state, p *player) {
			var n *npc
			for _, v := range s.npcs {
				n = v
				break
			}
			n.pos = Point{X: p.pos.X + 4*interestStep, Y: p.pos.Y, Z: p.pos.Z}
			pass(w, s, p)
			if !p.known[n.id] || !n.knownPlayers[p.id] {
				t.Fatal("the creature and the player do not see each other")
			}
			groundAt(s, p, 0)
			pass(w, s, p)
			if p.known[n.id] {
				t.Fatal("a nearer item did not displace the creature")
			}
			if !n.knownPlayers[p.id] {
				t.Fatal("displacement from the player's view made the creature forget the player")
			}
		})
		o.read(drainEvents(entry.Events))
	})
}
