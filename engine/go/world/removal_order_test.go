// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"fmt"
	"slices"
	"testing"
	"testing/synctest"
)

func TestNPCDecayOrdersTargetClearAndGone(t *testing.T) {
	for _, targeter := range []int32{1, 2} {
		t.Run(fmt.Sprint(targeter), func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				w, _, ctx := startTestWorld(t, syntheticCatalog(t))
				entries := []Entry{enterTest(t, w, ctx, syntheticCharacter(1)), enterTest(t, w, ctx, syntheticCharacter(2))}
				id := objectOf(t, w, ctx, 9101)
				if err := w.do(ctx, func(s *state) { w.setTarget(s, s.players[targeter], id) }); err != nil {
					t.Fatal(err)
				}
				for _, e := range entries {
					drainEvents(e.Events)
				}
				if err := w.do(ctx, func(s *state) { w.decay(s, s.npcs[id]) }); err != nil {
					t.Fatal(err)
				}
				observer := int32(3) - targeter
				var got []string
				for _, e := range drainEvents(entries[observer-1].Events) {
					switch v := e.(type) {
					case TargetCleared:
						if v.ObjectID == targeter {
							got = append(got, "clear")
						}
					case ObjectGone:
						if v.ObjectID == id {
							got = append(got, "gone")
						}
					}
				}
				want := []string{"clear", "gone"}
				if observer < targeter {
					want = []string{"gone", "clear"}
				}
				if !slices.Equal(got, want) {
					t.Fatalf("observer %d: got %v, want %v", observer, got, want)
				}
			})
		})
	}
}

func TestGroundErasureOrdersStopAndGone(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		second := enterTest(t, w, ctx, syntheticCharacter(2))
		drainEvents(first.Events)
		drainEvents(second.Events)
		const item = 1000
		if err := w.do(ctx, func(s *state) {
			g := &groundItem{id: item, generation: s.incarnation(), templateID: 9002, count: 1}
			s.ground[item] = g
			for _, id := range []int32{1, 2} {
				p := s.players[id]
				p.known[item] = true
				p.ai = ai{intention: intentPickUp, target: item, clientMoving: true}
			}
			w.eraseGround(s, g)
		}); err != nil {
			t.Fatal(err)
		}
		var got []string
		for _, e := range drainEvents(second.Events) {
			switch v := e.(type) {
			case Stopped:
				if v.ObjectID == 1 {
					got = append(got, "stop")
				}
			case ObjectGone:
				if v.ObjectID == item {
					got = append(got, "gone")
				}
			}
		}
		if want := []string{"stop", "gone"}; !slices.Equal(got, want) {
			t.Fatalf("remaining picker: got %v, want %v", got, want)
		}
	})
}

func TestRespawnActivatesForFirstObserverInIDOrder(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		second := enterTest(t, w, ctx, syntheticCharacter(2))
		id := objectOf(t, w, ctx, 9101)
		drainEvents(first.Events)
		drainEvents(second.Events)
		var spawned int32
		if err := w.do(ctx, func(s *state) {
			n := s.npcs[id]
			w.respawn(s, n.spawn, n.tmpl)
			spawned = s.region(n.region).objects[len(s.region(n.region).objects)-1]
		}); err != nil {
			t.Fatal(err)
		}
		for i, entry := range []Entry{first, second} {
			var got []string
			for _, e := range drainEvents(entry.Events) {
				switch v := e.(type) {
				case NPCSeen:
					if v.NPC.ObjectID == spawned {
						got = append(got, "seen")
					}
				case Animation:
					if v.ObjectID == spawned {
						got = append(got, "animation")
					}
				}
			}
			want := []string{"seen"}
			if i == 0 {
				want = append(want, "animation")
			}
			if !slices.Equal(got, want) {
				t.Fatalf("respawn observer %d: got %v, want %v", i+1, got, want)
			}
		}
	})
}

func TestDepartureNotifiesNeighborOnce(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		second := enterTest(t, w, ctx, syntheticCharacter(2))
		drainEvents(second.Events)
		if err := w.Leave(ctx, first.Self.Entity); err != nil {
			t.Fatal(err)
		}
		count := 0
		for _, e := range drainEvents(second.Events) {
			if v, ok := e.(ObjectGone); ok && v.ObjectID == first.Self.Entity.ID {
				count++
			}
		}
		if count != 1 {
			t.Fatalf("neighbor departure notifications: got %d, want 1", count)
		}
	})
}
