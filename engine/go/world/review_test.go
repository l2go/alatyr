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

func TestAmountClosedKinds(t *testing.T) {
	for _, tc := range []struct {
		name       string
		amount     Amount
		integer    int64
		continuous float64
		isInteger  bool
	}{
		{"zero", Amount{}, 0, 0, true},
		{"exact integer", IntegerAmount(1<<54 + 3), 1<<54 + 3, 0, true},
		{"continuous zero", ContinuousAmount(0), 0, 0, false},
		{"fraction", ContinuousAmount(0.5), 0, 0.5, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			integer, integerOK := tc.amount.Integer()
			continuous, continuousOK := tc.amount.Continuous()
			if integerOK != tc.isInteger || continuousOK == tc.isInteger || integer != tc.integer || continuous != tc.continuous {
				t.Fatalf("amount: got (%d, %v), (%g, %v), want %+v", integer, integerOK, continuous, continuousOK, tc)
			}
		})
	}
}

func TestCommitContinuousResourceBalances(t *testing.T) {
	for _, tc := range []struct {
		name          string
		before, after float64
	}{
		{"gain", 10, 10.5}, {"spend", 10.5, 10},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var log commitLog
			w := New(nil, nil, WithCommits(log.observe))
			entity := Ref{ID: 1, Gen: 1}
			w.s.rule(RuleRegeneration, func() { w.s.resourceChange(entity, ResourceHP, tc.before, tc.after) })
			commits := log.read()
			if len(commits) != 1 || len(commits[0].Groups) != 1 || len(commits[0].Groups[0].Effects) != 1 {
				t.Fatalf("resource commits: got %+v, want one transfer", commits)
			}
			e := commits[0].Groups[0].Effects[0].(ResourceTransferred)
			balance, worldBalance := e.ToBalance, e.FromBalance
			if tc.after < tc.before {
				balance, worldBalance = e.FromBalance, e.ToBalance
			}
			if e.Amount != ContinuousAmount(0.5) || balance != (Balance{Before: ContinuousAmount(tc.before), After: ContinuousAmount(tc.after)}) || worldBalance != (Balance{Before: ContinuousAmount(0), After: ContinuousAmount(0)}) {
				t.Fatalf("continuous transfer: got %+v", e)
			}
		})
	}
}

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
