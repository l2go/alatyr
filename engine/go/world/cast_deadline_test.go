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
