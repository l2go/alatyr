// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math/rand/v2"
	"reflect"
	"strings"
	"testing"
	"testing/synctest"
	"time"
)

type fixedRandomSource uint64

func (r fixedRandomSource) Uint64() uint64 { return uint64(r) }

func TestCommitDialogueUsesPageIdentity(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		teacher := objectOf(t, w, ctx, 9102)
		log.reset()
		if err := w.doCommand(ctx, 1, VerbAction, func(s *state) { w.talk(s, s.players[1], s.npcs[teacher]) }); err != nil {
			t.Fatal(err)
		}
		var page string
		var speech string
		for _, commit := range log.read() {
			for _, group := range commit.Groups {
				for _, fact := range group.Facts {
					if fact.Property == PropertyDialogue {
						page, _ = fact.After.Label()
					}
				}
				for _, effect := range group.Effects {
					if e, ok := effect.(SpeechCompleted); ok {
						speech = e.Text
					}
				}
			}
		}
		if page != "data/html/trainer/9102.htm" || !strings.Contains(speech, "<") {
			t.Fatalf("dialogue: got page %q, speech %q, want catalog path and HTML speech", page, speech)
		}
		if err := w.do(ctx, func(s *state) {
			if got := s.players[1].dialogue; got != page {
				t.Fatalf("cached page: got %q, want %q", got, page)
			}
		}); err != nil {
			t.Fatal(err)
		}
		drainEvents(entry.Events)
	})
}

func TestCommitAnimationIsOnlyNotification(t *testing.T) {
	for _, force := range []bool{false, true} {
		t.Run(map[bool]string{false: "ambient", true: "forced"}[force], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				teacher := objectOf(t, w, ctx, 9102)
				drainEvents(entry.Events)
				log.reset()
				if err := w.do(ctx, func(s *state) {
					n := s.npcs[teacher]
					n.lastAnim = time.Now().Add(-time.Hour)
					s.rule(RuleAI, func() {
						if !w.tryAnimation(s, n, force, false) {
							t.Fatal("expected animation to be shown")
						}
					})
				}); err != nil {
					t.Fatal(err)
				}
				if got := log.read(); len(got) != 0 {
					t.Fatalf("animation commits: got %+v, want no state change", got)
				}
				var found bool
				for _, event := range drainEvents(entry.Events) {
					if e, ok := event.(Animation); ok && e.ObjectID == teacher && e.Forced == force {
						found = true
					}
				}
				if !found {
					t.Fatal("missing animation notification")
				}
			})
		})
	}
}

func TestCommitDeferredRefusalHasNoOutcome(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w := New(nil, nil, WithCommits(log.observe))
		s := w.s
		owner := Ref{ID: 1, Gen: 1}
		var original IntentID
		s.command(owner, VerbMove, func() {
			original = s.cause().Intent
			s.finish(StageDone)
		})
		log.reset()
		s.afterRule(time.Second, RuleMovement, func(s *state) {
			s.source(Cause{Intent: original}, func() {
				// A delayed refusal path must not fix the prior command again.
				s.change(owner, PropertyMovement, boolValue(true), boolValue(false))
				s.finish(StageRefused)
			})
		})
		advanceVirtual(time.Second)
		s.runDue()
		commits := log.read()
		if len(commits) != 1 || commits[0].Outcome != (Outcome{}) || commits[0].Intent != (Intent{}) {
			t.Fatalf("deferred refusal: got %+v, want observations without an outcome", commits)
		}
		if got := commits[0].Groups[0].Cause.Intent; got != original {
			t.Fatalf("deferred cause: got %+v, want %+v", got, original)
		}
	})
}

func TestCommitEnterIntentIdentityIsImmutable(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		log.reset()
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		var found bool
		for _, commit := range log.read() {
			if commit.Intent.Verb != VerbEnter {
				continue
			}
			found = true
			id := commit.Intent.ID
			if id.N == 0 || id.Owner != (Ref{}) || commit.Outcome.Intent != id {
				t.Fatalf("enter identity: got %+v, outcome %+v, want unchanged unowned ID", id, commit.Outcome)
			}
			for _, group := range commit.Groups {
				if group.Cause.Intent != (IntentID{}) && group.Cause.Intent != id {
					t.Fatalf("enter cause: got %+v, want %+v", group.Cause.Intent, id)
				}
			}
		}
		if !found {
			t.Fatal("missing entry commitment")
		}
		drainEvents(entry.Events)
	})
}

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
