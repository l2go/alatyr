// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"testing"
	"testing/synctest"
	"time"
)

func enterCastDeadlineTest(t *testing.T, w *World, ctx context.Context) (Entry, Ref) {
	t.Helper()
	c := syntheticCharacter(1)
	c.X = 280
	c.Skills = []Skill{{ID: 9201, Level: 1}}
	entry := enterTest(t, w, ctx, c)
	target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
	// Keep the target alive through the permitted cast and subsequent swings.
	if err := w.do(ctx, func(s *state) {
		n := s.npcs[target.ID]
		n.tmpl.MaxHP, n.hp = 10000, 10000
	}); err != nil {
		t.Fatal(err)
	}
	executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbSelect, Target: target})
	drainEvents(entry.Events)
	return entry, target
}

func TestExecuteCastContinuationHonorsDeadline(t *testing.T) {
	for _, tc := range []struct {
		name     string
		cool     time.Duration
		deadline time.Duration
		attack   bool
	}{
		{"swing crosses deadline", 0, 650 * time.Millisecond, true},
		{"finalizer after deadline", 400 * time.Millisecond, 800 * time.Millisecond, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorldWithCatalog(t, &log, syntheticCatalogWithCoolTime(t, tc.cool))
				entry, target := enterCastDeadlineTest(t, w, ctx)
				deadline := captureTest(t, w, ctx, entry.Self.Entity).End.Time + Time(tc.deadline)
				cast := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: Skill{ID: 9201, Level: 1}, Deadline: deadline})
				if cast.Outcome.Stage != StageUnknown {
					t.Fatalf("cast: got %+v, want unknown", cast.Outcome)
				}
				log.reset()
				drainEvents(entry.Events)
				advanceVirtual(600 * time.Millisecond)
				launched := drainEvents(entry.Events)
				if !containsEvent(launched, func(e SkillLaunched) bool { return e.CasterID == entry.Self.Entity.ID }) {
					t.Fatalf("permitted launch: got %+v, want SkillLaunched", launched)
				}
				if got := containsEvent(launched, func(e AttackStarted) bool { return e.AttackerID == entry.Self.Entity.ID }); got != tc.attack {
					t.Errorf("automatic swing: got %v, want %v", got, tc.attack)
				}
				if tc.attack {
					if err := w.do(ctx, func(s *state) {
						if got := s.players[1].ai; got.deadline != deadline || got.refTarget != target {
							t.Errorf("continuation: got deadline %v target %v, want %v %v", got.deadline, got.refTarget, deadline, target)
						}
					}); err != nil {
						t.Fatal(err)
					}
				}
				permittedHarm := false
				for _, commit := range log.read() {
					for _, group := range commit.Groups {
						for _, effect := range group.Effects {
							if harm, ok := effect.(HarmApplied); ok && harm.Source == entry.Self.Entity && harm.Target == target && commit.Time < deadline {
								permittedHarm = true
							}
						}
					}
				}
				if !permittedHarm {
					t.Fatal("permitted cast harm missing")
				}
				before := captureTest(t, w, ctx, entry.Self.Entity)
				advanceVirtual(tc.deadline - 600*time.Millisecond)
				late := drainEvents(entry.Events)
				advanceVirtual(1000*time.Millisecond - tc.deadline)
				if after := captureTest(t, w, ctx, entry.Self.Entity); after.Moving || after.CombatUntil != before.CombatUntil {
					t.Errorf("finalizer cleanup: got moving %v stance %v, want stopped stance %v", after.Moving, after.CombatUntil, before.CombatUntil)
				}
				late = append(late, drainEvents(entry.Events)...)
				advanceVirtual(1500 * time.Millisecond)
				late = append(late, drainEvents(entry.Events)...)
				for _, event := range late {
					if e, ok := event.(AttackStarted); ok && e.AttackerID == entry.Self.Entity.ID {
						t.Errorf("late automatic attack: %+v", e)
					}
					if e, ok := event.(Moving); ok && e.ObjectID == entry.Self.Entity.ID {
						t.Errorf("late automatic movement: %+v", e)
					}
					if e, ok := event.(Following); ok && e.ObjectID == entry.Self.Entity.ID {
						t.Errorf("late automatic follow: %+v", e)
					}
				}
				for _, commit := range log.read() {
					if commit.Outcome.Intent == cast.Intent.ID {
						t.Errorf("deferred work refixed cast outcome: %+v", commit.Outcome)
					}
					for _, group := range commit.Groups {
						for _, effect := range group.Effects {
							if harm, ok := effect.(HarmApplied); ok && harm.Source == entry.Self.Entity && commit.Time >= deadline {
								t.Errorf("late player harm: %+v at %v", harm, commit.Time)
							}
						}
					}
				}
			})
		})
	}
}

func TestExecuteExpiredCastKeepsIndependentQueuedAttack(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorldWithCatalog(t, &log, syntheticCatalogWithCoolTime(t, 400*time.Millisecond))
		entry, target := enterCastDeadlineTest(t, w, ctx)
		now := captureTest(t, w, ctx, entry.Self.Entity).End.Time
		cast := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: Skill{ID: 9201, Level: 1}, Deadline: now + Time(800*time.Millisecond)})
		advanceVirtual(100 * time.Millisecond)
		deadline := now + Time(2*time.Second)
		queued := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbAttack, Target: target, Deadline: deadline})
		if cast.Outcome.Stage != StageUnknown || queued.Outcome.Stage != StageUnknown {
			t.Fatalf("handoffs: got %+v %+v, want unknown", cast.Outcome, queued.Outcome)
		}
		log.reset()
		drainEvents(entry.Events)
		advanceVirtual(900 * time.Millisecond)
		events := drainEvents(entry.Events)
		if !containsEvent(events, func(e AttackStarted) bool { return e.AttackerID == entry.Self.Entity.ID && e.TargetID == target.ID }) {
			t.Fatalf("independent queued attack: got %+v, want AttackStarted", events)
		}
		if err := w.do(ctx, func(s *state) {
			got := s.players[1].ai
			if got.next != nil || got.cause != (Cause{Intent: queued.Intent.ID}) || got.refTarget != target || got.deadline != deadline {
				t.Errorf("queued ownership: got %+v, want intent %v target %v deadline %v", got, queued.Intent.ID, target, deadline)
			}
		}); err != nil {
			t.Fatal(err)
		}
		advanceVirtual(time.Second)
		drainEvents(entry.Events)
		advanceVirtual(time.Second)
		if events := drainEvents(entry.Events); containsEvent(events, func(e AttackStarted) bool { return e.AttackerID == entry.Self.Entity.ID }) {
			t.Errorf("attack after independent deadline: %+v", events)
		}
		for _, commit := range log.read() {
			if commit.Outcome.Intent == cast.Intent.ID || commit.Outcome.Intent == queued.Intent.ID {
				t.Errorf("deferred work refixed outcome: %+v", commit.Outcome)
			}
			for _, group := range commit.Groups {
				for _, effect := range group.Effects {
					if harm, ok := effect.(HarmApplied); ok && harm.Source == entry.Self.Entity && commit.Time >= deadline {
						t.Errorf("harm after independent deadline: %+v at %v", harm, commit.Time)
					}
				}
			}
		}
	})
}

func TestLegacyCastFinalizerWithInvalidTarget(t *testing.T) {
	for _, replaced := range []bool{false, true} {
		t.Run(map[bool]string{false: "missing", true: "replaced"}[replaced], func(t *testing.T) {
			for _, queued := range []bool{false, true} {
				t.Run(map[bool]string{false: "idle", true: "queued move"}[queued], func(t *testing.T) {
					synctest.Test(t, func(t *testing.T) {
						var log commitLog
						w, _, ctx := startObservedWorldWithCatalog(t, &log, syntheticCatalogWithCoolTime(t, 400*time.Millisecond))
						entry, target := enterCastDeadlineTest(t, w, ctx)
						if err := w.do(ctx, func(s *state) { w.clientStartAutoAttack(s, s.players[1]) }); err != nil {
							t.Fatal(err)
						}
						if err := w.MagicSkillUse(ctx, 1, 9201, false, false); err != nil {
							t.Fatal(err)
						}
						advanceVirtual(600 * time.Millisecond)
						if events := drainEvents(entry.Events); !containsEvent(events, func(e SkillLaunched) bool { return e.CasterID == 1 && len(e.Targets) == 1 && e.Targets[0] == target.ID }) {
							t.Fatalf("legacy launch: got %+v, want captured target", events)
						}
						before := captureTest(t, w, ctx, entry.Self.Entity)
						advanceVirtual(100 * time.Millisecond)
						var move Commit
						if queued {
							move = executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbMove, Position: Point{X: 1000}, Deadline: before.End.Time + Time(2*time.Second)})
							if move.Outcome.Stage != StageUnknown {
								t.Fatalf("queued move: got %+v, want unknown", move.Outcome)
							}
						}
						if err := w.do(ctx, func(s *state) {
							if replaced {
								s.npcs[target.ID].generation++
							} else {
								delete(s.npcs, target.ID)
							}
							if p := s.players[1]; !p.casting || p.ai.deadline != 0 {
								t.Errorf("legacy cast: got casting %v deadline %v, want true and no deadline", p.casting, p.ai.deadline)
							}
						}); err != nil {
							t.Fatal(err)
						}
						log.reset()
						drainEvents(entry.Events)
						advanceVirtual(300 * time.Millisecond)
						after := captureTest(t, w, ctx, entry.Self.Entity)
						if after.CombatUntil != before.CombatUntil || after.Moving != queued {
							t.Errorf("legacy cleanup: got stance %v moving %v, want %v %v", after.CombatUntil, after.Moving, before.CombatUntil, queued)
						}
						if err := w.do(ctx, func(s *state) {
							p := s.players[1]
							want := intentIdle
							if queued {
								want = intentMoveTo
								if p.ai.cause != (Cause{Intent: move.Intent.ID}) || p.ai.deadline != move.Intent.Deadline {
									t.Errorf("queued ownership: got %+v, want intent %v deadline %v", p.ai, move.Intent.ID, move.Intent.Deadline)
								}
							}
							if p.casting || p.ai.intention != want || p.ai.next != nil || p.attacking != 0 {
								t.Errorf("finalizer: got casting %v AI %+v attacking %v, want intention %v and no pending cast or attack", p.casting, p.ai, p.attacking, want)
							}
						}); err != nil {
							t.Fatal(err)
						}
						events := drainEvents(entry.Events)
						if containsEvent(events, func(e AttackStarted) bool { return e.AttackerID == 1 }) || containsEvent(events, func(e CombatStance) bool { return e.ObjectID == 1 && e.On }) {
							t.Errorf("invalid legacy target started combat: %+v", events)
						}
						if queued && !containsEvent(events, func(e Moving) bool { return e.ObjectID == 1 && e.To == (Point{X: 1000}) }) {
							t.Errorf("queued movement not delivered: %+v", events)
						}
						for _, commit := range log.read() {
							for _, group := range commit.Groups {
								for _, effect := range group.Effects {
									if harm, ok := effect.(HarmApplied); ok && harm.Source == entry.Self.Entity {
										t.Errorf("invalid legacy target applied harm: %+v", harm)
									}
								}
							}
						}
					})
				})
			}
		})
	}
}
