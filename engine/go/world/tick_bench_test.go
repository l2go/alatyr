// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// tickScene contains 32 moving NPCs. Setup, I/O and retained observation history
// are excluded; each step resets bounded state and exercises tickMovement.
func tickScene(observe bool) func() {
	w := New(nil, nil)
	s := w.s
	s.timers = nil
	moves := make([]moveData, 32)
	if observe {
		s.observe = func(Commit) {}
	}
	for i := int32(1); i <= 32; i++ {
		n := &npc{creature: creature{id: i, generation: uint64(i)}, tmpl: catalog.NPCTemplate{WalkSpeed: 100}}
		s.npcs[i] = n
		s.moving = append(s.moving, i)
	}
	return func() {
		s.ticks = 0
		s.timers = s.timers[:0]
		for id, n := range s.npcs {
			n.pos = Point{}
			m := &moves[id-1]
			*m = moveData{dest: Point{X: 1000}}
			n.move = m
		}
		s.rule(RuleMovement, func() { w.tickMovement(s) })
	}
}

func BenchmarkTick(b *testing.B) {
	for _, observe := range []bool{false, true} {
		name := "unobserved"
		if observe {
			name = "observed"
		}
		b.Run(name, func(b *testing.B) {
			step := tickScene(observe)
			b.ReportAllocs()
			for b.Loop() {
				step()
			}
		})
	}
}

func TestTickAllocations(t *testing.T) {
	for _, observe := range []bool{false, true} {
		name := "unobserved"
		if observe {
			name = "observed"
		}
		t.Run(name, func(t *testing.T) {
			step := tickScene(observe)
			got := testing.AllocsPerRun(10, step)
			budget := 42.0
			if observe {
				budget = 75
			}
			t.Logf("32 moving NPCs: %.0f allocations/tick", got)
			if got > budget {
				t.Fatalf("allocations/tick: got %.0f, budget %.0f", got, budget)
			}
		})
	}
}
