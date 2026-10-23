// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
	"slices"
	"testing"
	"testing/synctest"
)

func TestCommitPickupExactTransfer(t *testing.T) {
	for _, tc := range []struct {
		name  string
		held  int64
		count int64
		want  bool
	}{
		{"new stack", 0, 2, true},
		{"merge above float precision", 1 << 54, 3, true},
		{"overflow", math.MaxInt64, 1, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				c := syntheticCharacter(1)
				if tc.held != 0 {
					c.Items = append(c.Items, Item{ObjectID: 102, TemplateID: 9002, Count: tc.held, Slot: -1})
				}
				entry := enterTest(t, w, ctx, c)
				mob := objectOf(t, w, ctx, 9101)
				var ground Ref
				if err := w.do(ctx, func(s *state) {
					s.rule(RuleReward, func() { w.dropOnGround(s, s.npcs[mob], s.players[1], 9002, tc.count) })
					for _, g := range s.ground {
						ground = g.ref()
					}
				}); err != nil {
					t.Fatal(err)
				}
				log.reset()
				if err := w.doCommand(ctx, 1, VerbPickup, func(s *state) { w.doPickup(s, s.players[1], ground.ID) }); err != nil {
					t.Fatal(err)
				}
				var transfers []ResourceTransferred
				var removed bool
				for _, commit := range log.read() {
					for _, group := range commit.Groups {
						for _, effect := range group.Effects {
							switch e := effect.(type) {
							case ResourceTransferred:
								transfers = append(transfers, e)
							case ExistenceChanged:
								removed = e.Entity == ground && !e.After
							}
						}
					}
				}
				if !tc.want {
					if len(transfers) != 0 || removed {
						t.Fatalf("refused pickup effects: got %+v, removed %v", transfers, removed)
					}
					return
				}
				if len(transfers) != 1 {
					t.Fatalf("transfers: got %d, want 1", len(transfers))
				}
				e := transfers[0]
				if e.From.Entity != ground || e.To.Entity != entry.Self.Entity || e.Amount != IntegerAmount(tc.count) || e.ToBalance != (Balance{Before: IntegerAmount(tc.held), After: IntegerAmount(tc.held + tc.count)}) || e.FromBalance != (Balance{Before: IntegerAmount(tc.count)}) {
					t.Fatalf("transfer: got %+v, want exact %d + %d", e, tc.held, tc.count)
				}
				if e.To.Item.Gen == 0 || tc.held == 0 && e.To.Item != ground || tc.held != 0 && e.To.Item.ID != 102 {
					t.Fatalf("inventory incarnation: got %+v", e.To.Item)
				}
				if removed != (tc.held != 0) {
					t.Fatalf("merged incarnation removal: got %v, want %v", removed, tc.held != 0)
				}
			})
		})
	}
}

func TestCommitRefusalAndSnapshot(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		log.reset()
		if _, err := w.Snapshot(ctx, 1); err != nil {
			t.Fatal(err)
		}
		if len(log.read()) != 0 {
			t.Fatal("snapshot replayed a commit")
		}
		if err := w.MoveTo(ctx, 1, Point{X: 10000}); !errors.Is(err, ErrMove) {
			t.Fatalf("move: got %v, want ErrMove", err)
		}
		commits := log.read()
		if len(commits) != 1 || commits[0].Outcome.Stage != Refused || len(commits[0].Groups) != 0 || commits[0].Outcome.Intent.Owner != entry.Self.Entity {
			t.Fatalf("refusal: got %+v, want one owned refusal without effects", commits)
		}
	})
}

func TestCommitExhaustionPreventsMutation(t *testing.T) {
	for _, counter := range []string{"commit", "intent", "incarnation", "timer"} {
		t.Run(counter, func(t *testing.T) {
			w := New(nil, nil)
			s := w.s
			s.timers = nil
			mutated := false
			switch counter {
			case "commit":
				s.commitSeq = math.MaxUint64
				s.rule(RuleHost, func() { mutated = true })
			case "intent":
				s.commandSeq = math.MaxUint64
				s.command(Ref{ID: 1, Gen: 1}, VerbMove, func() { mutated = true })
			case "incarnation":
				s.generation = math.MaxUint64
				mutated = s.incarnation() != 0
			case "timer":
				s.seq = math.MaxUint64
				s.after(0, func(*state) { mutated = true })
				mutated = len(s.timers) != 0
			}
			if mutated || !errors.Is(s.err, ErrExhausted) {
				t.Fatalf("exhaustion: mutation %v, err %v", mutated, s.err)
			}
		})
	}
}

func TestValueClosedKinds(t *testing.T) {
	r := Ref{ID: 1, Gen: 2}
	values := []Value{IntValue(0), RefValue(r), LabelValue("active"), PointValue(Point{X: 3}), TimeValue(4)}
	for i, v := range values {
		_, integer := v.Integer()
		_, ref := v.Ref()
		_, label := v.Label()
		_, point := v.Point()
		_, clock := v.Time()
		got := []bool{integer, ref, label, point, clock}
		if slices.Index(got, true) != i || slices.Contains(got[i+1:], true) {
			t.Fatalf("kind %d: got %v", i, got)
		}
	}
	if (Value{}) != IntValue(0) || (Intent{}).Deadline != 0 || (Commit{}).Outcome.Intent != (IntentID{}) {
		t.Fatal("zero values are not useful")
	}
}

func TestCommitTerminalOutcomeIsFixedOnce(t *testing.T) {
	var commits []Commit
	w := New(nil, nil, WithCommits(func(c Commit) { commits = append(commits, c) }))
	w.s.command(Ref{ID: 1, Gen: 1}, VerbLearn, func() {
		w.s.finish(Done)
		w.s.finish(Refused)
	})
	if len(commits) != 1 || commits[0].Outcome.Stage != Done {
		t.Fatalf("outcome: got %+v, want one done outcome", commits)
	}
}

func TestCommitRunExhaustionReturnsError(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := New(nil, nil)
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		if err := w.do(ctx, func(s *state) { s.commitSeq = math.MaxUint64 }); err != nil {
			t.Fatal(err)
		}
		called := false
		if err := w.do(ctx, func(*state) { called = true }); !errors.Is(err, ErrExhausted) {
			t.Fatalf("command: got %v, want ErrExhausted", err)
		}
		if called {
			t.Fatal("exhausted command executed")
		}
		if err := <-done; !errors.Is(err, ErrExhausted) {
			t.Fatalf("Run: got %v, want ErrExhausted", err)
		}
	})
}

func TestCommitCleanupAndAmbientOutcomes(t *testing.T) {
	for _, action := range []string{"teleport", "leave", "death"} {
		t.Run(action, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				c := syntheticCharacter(1)
				c.X = 50
				entry := enterTest(t, w, ctx, c)
				teacher := objectOf(t, w, ctx, 9102)
				for range 2 {
					if err := w.Action(ctx, 1, teacher); err != nil {
						t.Fatal(err)
					}
				}
				if err := w.Bypass(ctx, 1, fmtChoice(teacher)); err != nil {
					t.Fatal(err)
				}
				if err := w.do(ctx, func(s *state) {
					p := s.players[1]
					p.attacking = 1
					p.casting = true
					p.move = &moveData{dest: Point{X: 100}}
				}); err != nil {
					t.Fatal(err)
				}
				log.reset()
				switch action {
				case "teleport":
					if err := w.Teleport(ctx, 1, Point{X: 100}, false); err != nil {
						t.Fatal(err)
					}
				case "leave":
					if err := w.Leave(ctx, 1); err != nil {
						t.Fatal(err)
					}
				case "death":
					if err := w.do(ctx, func(s *state) { s.rule(RuleAttackHit, func() { w.reduceHP(s, s.players[1], 1000, s.npcs[teacher]) }) }); err != nil {
						t.Fatal(err)
					}
				}
				found := map[Property]bool{}
				for _, c := range log.read() {
					if action == "death" && c.Outcome.Intent != (IntentID{}) {
						t.Fatalf("ambient outcome: %+v", c.Outcome)
					}
					for _, group := range c.Groups {
						if group.Cause == (Cause{}) {
							t.Fatal("missing mutation cause")
						}
						for _, f := range group.Facts {
							if f.Subject == entry.Self.Entity && f.Before == IntValue(1) && f.After == IntValue(0) {
								found[f.Property] = true
							}
						}
					}
				}
				properties := []Property{PropertyAttacking, PropertyCasting, PropertyMovement}
				if action != "death" {
					properties = append(properties, PropertyLearning)
				}
				for _, p := range properties {
					if !found[p] {
						t.Errorf("cleanup %s: missing 1 -> 0", p)
					}
				}
			})
		})
	}
}
