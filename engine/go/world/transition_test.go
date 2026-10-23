// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math/rand/v2"
	"reflect"
	"slices"
	"sync"
	"testing"
	"testing/synctest"
	"time"
)

type commitLog struct {
	mu      sync.Mutex
	entries []Commit
	writes  []Durability
}

func (log *commitLog) observe(entry Commit) {
	log.mu.Lock()
	defer log.mu.Unlock()
	log.entries = append(log.entries, entry)
}
func (log *commitLog) durable(write Durability) {
	log.mu.Lock()
	defer log.mu.Unlock()
	log.writes = append(log.writes, write)
}
func (log *commitLog) read() []Commit {
	log.mu.Lock()
	defer log.mu.Unlock()
	return slices.Clone(log.entries)
}
func (log *commitLog) reset() {
	log.mu.Lock()
	defer log.mu.Unlock()
	log.entries = nil
}

func startObservedWorld(t *testing.T, log *commitLog) (*World, *testStore, context.Context) {
	t.Helper()
	ctx, cancel := context.WithCancel(t.Context())
	store := &testStore{}
	w := New(syntheticCatalog(t), store, WithCommits(log.observe), WithDurability(log.durable), WithRandomSource(rand.NewChaCha8([32]byte{1})))
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	t.Cleanup(func() {
		cancel()
		if err := <-done; err != nil {
			t.Errorf("Run: got %v, want nil", err)
		}
	})
	return w, store, ctx
}

// advanceVirtual waits on a clock timer, not on a guessed scheduling delay.
// It is used only inside synctest; virtual time advances once goroutines block.
func advanceVirtual(d time.Duration) {
	timer := time.NewTimer(d)
	defer timer.Stop()
	<-timer.C
	synctest.Wait()
}

func TestCommitLearningCommitAndDurability(t *testing.T) {
	for _, fail := range []bool{false, true} {
		t.Run(map[bool]string{false: "saved", true: "failed write"}[fail], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, store, ctx := startObservedWorld(t, &log)
				store.failSkill = fail
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				teacher := objectOf(t, w, ctx, 9102)
				for range 2 {
					if err := w.Action(ctx, 1, teacher); err != nil {
						t.Fatal(err)
					}
				}
				if err := w.Bypass(ctx, 1, fmtChoice(teacher)); err != nil {
					t.Fatal(err)
				}
				drainEvents(entry.Events)
				log.reset()
				err := w.Learn(ctx, 1, 9202, 1)
				if (err != nil) != fail {
					t.Fatalf("Learn error: got %v, want failure %v", err, fail)
				}
				entries := log.read()
				if len(entries) != 1 {
					t.Fatalf("commit count: got %d, want 1", len(entries))
				}
				commit := entries[0]
				if commit.Outcome.Intent == (IntentID{}) || commit.Outcome.Stage != Done || commit.Outcome.Intent.Owner != entry.Self.Entity {
					t.Fatalf("commit result: got %+v, want done for %v", commit.Outcome, entry.Self.Entity)
				}
				var group *EffectGroup
				for i := range commit.Groups {
					if len(commit.Groups[i].Effects) == 2 {
						group = &commit.Groups[i]
					}
				}
				if group == nil {
					t.Fatal("missing indivisible learning group")
				}
				sp, ok := group.Effects[0].(ResourceTransferred)
				if !ok || sp.Resource.Kind != ResourceSP || sp.Amount != IntegerAmount(3) || sp.FromBalance != (Balance{Before: IntegerAmount(20), After: IntegerAmount(17)}) || sp.From.Entity != entry.Self.Entity {
					t.Fatalf("SP transfer: got %+v, want 20 → 17", group.Effects[0])
				}
				skill, ok := group.Effects[1].(CapabilityChanged)
				if !ok || skill.ID != 9202 || skill.Before != 0 || skill.After != 1 {
					t.Fatalf("skill: got %+v, want 9202 level 0 → 1", group.Effects[1])
				}
				if len(log.writes) != 1 || (log.writes[0].Err != nil) != fail || log.writes[0].Intent != commit.Outcome.Intent {
					t.Fatalf("durability: got %+v, want separate signal", log.writes)
				}
				if err := w.Leave(ctx, 1); err != nil {
					t.Fatal(err)
				}
				if store.saved.SP != 17 || !slices.Contains(store.saved.Skills, Skill{ID: 9202, Level: 1}) {
					t.Fatalf("saved state: got %+v, want committed skill and SP", store.saved)
				}
				if commit.Outcome.Stage != Done {
					t.Fatal("durability rewrote immutable commit")
				}
			})
		})
	}
}

func TestCommitHitIsAppliedHarm(t *testing.T) {
	for _, tc := range []struct {
		name      string
		attacking int32
		dead      bool
		want      bool
	}{
		{"applied", 1, false, true}, {"canceled swing", 0, false, false}, {"dead target", 1, true, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				mob := objectOf(t, w, ctx, 9101)
				drainEvents(entry.Events)
				log.reset()
				if err := w.do(ctx, func(s *state) {
					p, n := s.players[1], s.npcs[mob]
					p.attacking, n.dead = tc.attacking, tc.dead
					s.rule(RuleAttackHit, func() { w.onHit(s, p, n, 5, false, false) })
				}); err != nil {
					t.Fatal(err)
				}
				found := false
				for _, entry := range log.read() {
					for _, group := range entry.Groups {
						for _, effect := range group.Effects {
							if harm, ok := effect.(HarmApplied); ok {
								found = true
								if harm.Before-harm.After != 5 || group.Cause.Rule != RuleAttackHit || harm.Target.Gen == 0 {
									t.Fatalf("harm: got %+v cause %+v", harm, group.Cause)
								}
							}
						}
					}
				}
				if found != tc.want {
					t.Fatalf("applied harm: got %v, want %v", found, tc.want)
				}
			})
		})
	}
}

func TestCommitQueuedIntentionKeepsCause(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		if err := w.do(ctx, func(s *state) { s.players[1].attackEnd = time.Now().Add(time.Second) }); err != nil {
			t.Fatal(err)
		}
		log.reset()
		if err := w.MoveTo(ctx, 1, Point{X: 20}); err != nil {
			t.Fatal(err)
		}
		queued := log.read()
		if len(queued) != 1 || queued[0].Outcome.Intent == (IntentID{}) {
			t.Fatalf("queue result: got %+v", queued)
		}
		command := queued[0].Outcome.Intent
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			p.attackEnd = time.Time{}
			s.rule(RuleAttackReady, func() { w.executeNextIntention(s, p) })
		}); err != nil {
			t.Fatal(err)
		}
		found := false
		for _, commit := range log.read()[1:] {
			for _, group := range commit.Groups {
				for _, change := range group.Facts {
					if change.Property == PropertyMovement && change.After == IntValue(1) {
						found = true
						if group.Cause.Intent != command || group.Cause.Rule != "" {
							t.Fatalf("queued cause: got %+v, want %+v", group.Cause, command)
						}
					}
				}
			}
		}
		if !found {
			t.Fatal("queued movement did not commit")
		}
		drainEvents(entry.Events)
	})
}

func TestCommitRegenerationRewardAndRespawn(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		c := syntheticCharacter(1)
		c.CurHP = 100
		entry := enterTest(t, w, ctx, c)
		mob := objectOf(t, w, ctx, 9101)
		log.reset()
		advanceVirtual(4 * time.Second)
		if err := w.do(ctx, func(s *state) {
			s.rule(RuleAttackHit, func() { w.reduceHP(s, s.npcs[mob], 1000, s.players[1]) })
		}); err != nil {
			t.Fatal(err)
		}
		advanceVirtual(20 * time.Second)
		seen := map[Rule]bool{}
		var previous Time
		var sequence uint64
		for _, commit := range log.read() {
			if commit.Time < previous || commit.Sequence <= sequence {
				t.Fatalf("nonmonotonic commit: %+v", commit)
			}
			previous, sequence = commit.Time, commit.Sequence
			for _, group := range commit.Groups {
				seen[group.Cause.Rule] = true
			}
		}
		for _, rule := range []Rule{RuleRegeneration, RuleReward, RuleDecay, RuleRespawn} {
			if !seen[rule] {
				t.Errorf("rule %q: not observed, want commit", rule)
			}
		}
		if replacement := objectOf(t, w, ctx, 9101); replacement == mob {
			t.Fatalf("respawn ID: got %d, want new incarnation", replacement)
		}
		drainEvents(entry.Events)
	})
}

func TestCommitIncarnationAndDetachedValues(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		if err := w.Leave(ctx, 1); err != nil {
			t.Fatal(err)
		}
		second := enterTest(t, w, ctx, syntheticCharacter(1))
		if first.Self.Entity.ID != second.Self.Entity.ID || first.Self.Entity.Gen >= second.Self.Entity.Gen {
			t.Fatalf("incarnations: got %v then %v", first.Self.Entity, second.Self.Entity)
		}
		entries := log.read()
		original := entries[len(entries)-1]
		cloned := cloneCommit(original)
		cloned.Groups[0].Cause.Rule = "changed"
		if original.Groups[0].Cause.Rule == "changed" {
			t.Fatal("group storage is shared")
		}
		cloned.Outcome.Stage = Refused
		if original.Outcome.Stage != Done {
			t.Fatal("result storage is shared")
		}
	})
}

func TestCommitRandomSourceAndZeroValues(t *testing.T) {
	cat := syntheticCatalog(t)
	w1 := New(cat, &testStore{}, WithRandomSource(rand.NewChaCha8([32]byte{9})))
	w2 := New(cat, &testStore{}, WithRandomSource(rand.NewChaCha8([32]byte{9})))
	for range 20 {
		if a, b := w1.s.rng.Uint64(), w2.s.rng.Uint64(); a != b {
			t.Fatalf("random draw: got %d, want %d", a, b)
		}
	}
	var zero World
	if err := zero.Run(t.Context()); !errors.Is(err, ErrUninitialized) {
		t.Fatalf("zero Run: got %v, want ErrUninitialized", err)
	}
	var outcome Stage
	if outcome != Unknown {
		t.Fatalf("zero outcome: got %v, want unknown", outcome)
	}
	if !reflect.DeepEqual(cloneCommit(Commit{}), Commit{}) {
		t.Fatal("zero transition changed during clone")
	}
}
