// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math/rand/v2"
	"reflect"
	"testing"
	"testing/synctest"
	"time"
)

type fixedRandomSource uint64

func (r fixedRandomSource) Uint64() uint64 { return uint64(r) }

func TestCommitMissDoesNotRepeatAttackingFact(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		mob := objectOf(t, w, ctx, 9101)
		log.reset()
		var duration time.Duration
		if err := w.doCommand(ctx, 1, VerbAttack, func(s *state) {
			p, n := s.players[1], s.npcs[mob]
			// A front-facing target and a maximum draw force the miss branch.
			n.heading = headingTo(n.pos, p.pos)
			s.rng = rand.New(fixedRandomSource(^uint64(0)))
			duration = time.Duration(500000/max(p.pAtkSpd(), 10)) * time.Millisecond
			w.doAttack(s, p, n)
			if p.attacking != 0 {
				t.Fatalf("attacking after miss: got %d, want 0", p.attacking)
			}
		}); err != nil {
			t.Fatal(err)
		}
		advanceVirtual(duration)
		var got [][2]Value
		for _, commit := range log.read() {
			for _, group := range commit.Groups {
				for _, fact := range group.Facts {
					if fact.Subject != entry.Self.Entity || fact.Property != PropertyAttacking {
						continue
					}
					if group.Cause.Rule == RuleAttackReady {
						t.Fatalf("attack-ready published unchanged state: %+v", fact)
					}
					got = append(got, [2]Value{fact.Before, fact.After})
				}
			}
		}
		want := [][2]Value{{boolValue(false), boolValue(true)}, {boolValue(true), boolValue(false)}}
		if !reflect.DeepEqual(got, want) {
			t.Fatalf("attacking facts: got %v, want %v", got, want)
		}
		drainEvents(entry.Events)
	})
}
