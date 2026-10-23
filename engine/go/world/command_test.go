// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"
)

func captureTest(t *testing.T, w *World, ctx context.Context, owner Ref) Scene {
	t.Helper()
	scene, err := w.Capture(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	return scene
}

func refTest(t *testing.T, w *World, ctx context.Context, id int32) Ref {
	t.Helper()
	var ref Ref
	if err := w.do(ctx, func(s *state) { ref = s.ref(id) }); err != nil {
		t.Fatal(err)
	}
	return ref
}

func executeTest(t *testing.T, w *World, ctx context.Context, req Request) Commit {
	t.Helper()
	commit, err := w.Execute(ctx, req)
	if err != nil {
		t.Fatal(err)
	}
	return commit
}

func TestExecuteSelectOnlyAndSameCommit(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
		log.reset()
		req := Request{Owner: entry.Self.Entity, Verb: VerbSelect, Target: target}
		commit := executeTest(t, w, ctx, req)
		if got := log.read(); len(got) != 1 || !reflect.DeepEqual(got[0], commit) {
			t.Fatalf("observer: got %+v, want returned commitment %+v", got, commit)
		}
		if commit.Outcome.Stage != StageDone || len(commit.Groups) != 1 || len(commit.Groups[0].Effects) != 0 || len(commit.Groups[0].Facts) != 1 || commit.Groups[0].Facts[0].After != RefValue(target) {
			t.Fatalf("selection: got %+v, want one target fact and no effect", commit)
		}
		if same := executeTest(t, w, ctx, req); same.Outcome.Stage != StageUnknown || len(same.Groups) != 0 {
			t.Fatalf("same target: got %+v, want unknown without mutations", same)
		}
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			if p.attacking != 0 || p.casting || p.ai.intention != intentIdle || p.lastFolk != 0 {
				t.Errorf("select performed action: %+v", p.ai)
			}
		}); err != nil {
			t.Fatal(err)
		}
		req.Target = Ref{}
		if cleared := executeTest(t, w, ctx, req); cleared.Outcome.Stage != StageDone {
			t.Fatalf("clear: got %+v, want done", cleared)
		}
		drainEvents(entry.Events)
	})
}

func TestExecuteRefusalAndDeadline(t *testing.T) {
	for _, tc := range []struct {
		name     string
		verb     Verb
		deadline Time
		stale    bool
		stage    Stage
		reason   Reason
	}{
		{"expired", VerbMove, 1, false, StageExpired, ""},
		{"negative deadline", VerbMove, -1, false, StageRefused, ReasonInvalid},
		{"stale owner", VerbMove, 0, true, StageRefused, ReasonStale},
		{"unsupported", Verb("buy"), 0, false, StageRefused, ReasonUnavailable},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				advanceVirtual(time.Millisecond)
				owner := entry.Self.Entity
				if tc.stale {
					owner.Gen++
				}
				commit := executeTest(t, w, ctx, Request{Owner: owner, Verb: tc.verb, Deadline: tc.deadline, Position: Point{X: 100}})
				if commit.Outcome.Stage != tc.stage || commit.Outcome.Reason != tc.reason || len(commit.Groups) != 0 || commit.Intent.Deadline != tc.deadline {
					t.Fatalf("result: got %+v, want %v/%v without groups", commit, tc.stage, tc.reason)
				}
				drainEvents(entry.Events)
			})
		})
	}
}

func TestExecuteQueuedCastExpiresWithoutResource(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		c := syntheticCharacter(1)
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
		if err := w.do(ctx, func(s *state) { s.players[1].attackEnd = time.Now().Add(time.Second) }); err != nil {
			t.Fatal(err)
		}
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: c.Skills[0], Deadline: scene.End.Time + Time(100*time.Millisecond)})
		if commit.Outcome.Stage != StageUnknown {
			t.Fatalf("handoff: got %v, want unknown", commit.Outcome)
		}
		log.reset()
		advanceVirtual(200 * time.Millisecond)
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			p.attackEnd = time.Time{}
			s.rule(RuleAttackReady, func() { w.executeNextIntention(s, p) })
			if p.casting || p.mp != 100 || p.ai.next != nil {
				t.Errorf("expired execution: casting %v MP %g next %+v", p.casting, p.mp, p.ai.next)
			}
		}); err != nil {
			t.Fatal(err)
		}
		for _, c := range log.read() {
			if c.Outcome.Intent == commit.Intent.ID {
				t.Fatal("delayed execution refixed outcome")
			}
			for _, g := range c.Groups {
				if len(g.Effects) != 0 {
					t.Fatalf("expired cast applied effects: %+v", g)
				}
			}
		}
		drainEvents(entry.Events)
	})
}

func TestExecuteCastKeepsCapturedTargetAndLevel(t *testing.T) {
	for _, tc := range []struct {
		name                      string
		staleTarget, changedLevel bool
	}{
		{"changed selection", false, false}, {"stale incarnation", true, false}, {"changed skill level", false, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				c := syntheticCharacter(1)
				c.X = 280
				c.Skills = []Skill{{ID: 9201, Level: 1}}
				entry := enterTest(t, w, ctx, c)
				target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
				teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
				if err := w.do(ctx, func(s *state) { s.players[1].attackEnd = time.Now().Add(time.Second) }); err != nil {
					t.Fatal(err)
				}
				commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: c.Skills[0]})
				if commit.Outcome.Stage != StageUnknown {
					t.Fatalf("cast: got %+v, want unknown", commit)
				}
				executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbSelect, Target: teacher})
				drainEvents(entry.Events)
				if err := w.do(ctx, func(s *state) {
					p := s.players[1]
					if tc.staleTarget {
						s.npcs[target.ID].generation++
					}
					if tc.changedLevel {
						p.char.Skills[0].Level = 2
					}
					p.attackEnd = time.Time{}
					s.rule(RuleAttackReady, func() { w.executeNextIntention(s, p) })
				}); err != nil {
					t.Fatal(err)
				}
				events := drainEvents(entry.Events)
				started := containsEvent(events, func(e SkillCast) bool { return e.TargetID == target.ID && e.Level == 1 })
				if want := !tc.staleTarget && !tc.changedLevel; started != want {
					t.Fatalf("captured cast: got started %v, want %v; %+v", started, want, events)
				}
			})
		})
	}
}

func TestExecuteLearnUsesAffordanceAndSeparateDurability(t *testing.T) {
	for _, failed := range []bool{false, true} {
		t.Run(map[bool]string{false: "saved", true: "write failed"}[failed], func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, store, ctx := startObservedWorld(t, &log)
				store.failSkill = failed
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
				executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbTalk, Target: teacher})
				scene := captureTest(t, w, ctx, entry.Self.Entity)
				choice := scene.Dialogue.Affordances[0]
				executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbChoose, Target: teacher, AffordanceID: choice.ID})
				scene = captureTest(t, w, ctx, entry.Self.Entity)
				var lesson Affordance
				for _, a := range scene.Dialogue.Affordances {
					if affordanceSkill(a, Skill{ID: 9202, Level: 1}) {
						lesson = a
					}
				}
				if lesson.ID == 0 {
					t.Fatal("lesson not issued")
				}
				log.reset()
				commit, err := w.Execute(ctx, Request{Owner: entry.Self.Entity, Verb: VerbLearn, Target: teacher, AffordanceID: lesson.ID, Skill: Skill{ID: 9202, Level: 1}})
				if (err != nil) != failed || commit.Outcome.Stage != StageDone {
					t.Fatalf("learn: got %+v, %v; want memory done, failure %v", commit, err, failed)
				}
				group := slices.IndexFunc(commit.Groups, func(g EffectGroup) bool { return len(g.Effects) == 2 })
				if group < 0 {
					t.Fatal("learning group missing")
				}
				sp := commit.Groups[group].Effects[0].(ResourceTransferred)
				if sp.Amount != IntegerAmount(lesson.Cost[0].Quantity) || len(log.writes) != 1 || log.writes[0].Intent != commit.Intent.ID || (log.writes[0].Err != nil) != failed {
					t.Fatalf("cost/durability: got %+v %+v", sp, log.writes)
				}
				if err := w.Leave(ctx, 1); err != nil {
					t.Fatal(err)
				}
				if store.saved.SP != 17 || !slices.Contains(store.saved.Skills, Skill{ID: 9202, Level: 1}) {
					t.Fatalf("save: got %+v", store.saved)
				}
			})
		})
	}
}

func TestExecuteCancellationBeforeAdmission(t *testing.T) {
	w := New(nil, nil)
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	commit, err := w.Execute(ctx, Request{})
	if !errors.Is(err, context.Canceled) || !reflect.DeepEqual(commit, Commit{}) {
		t.Fatalf("admission: got %+v, %v", commit, err)
	}
}

func TestExecuteCastDeadlinePreventsLaunchEffects(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		c := syntheticCharacter(1)
		c.X = 280
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbCast, Target: target, Skill: c.Skills[0], Deadline: scene.End.Time + Time(100*time.Millisecond)})
		if commit.Outcome.Stage != StageUnknown {
			t.Fatalf("cast start: got %+v", commit)
		}
		initial := captureTest(t, w, ctx, entry.Self.Entity).Self.Char.CurMP
		if initial != 98 {
			t.Fatalf("initial MP: got %g, want 98", initial)
		}
		log.reset()
		advanceVirtual(700 * time.Millisecond)
		for _, commit := range log.read() {
			for _, group := range commit.Groups {
				for _, effect := range group.Effects {
					if _, ok := effect.(HarmApplied); ok {
						t.Fatalf("expired launch applied harm: %+v", effect)
					}
					if e, ok := effect.(ResourceTransferred); ok && e.Resource.Kind == ResourceMP && e.From.Entity == entry.Self.Entity {
						t.Fatalf("expired launch spent MP: %+v", e)
					}
				}
			}
		}
		if after := captureTest(t, w, ctx, entry.Self.Entity); after.Self.Char.CurMP != initial {
			t.Fatalf("MP after expiry: got %g, want %g", after.Self.Char.CurMP, initial)
		}
		drainEvents(entry.Events)
	})
}

func TestExecuteMovementDeadlineStopsAtBoundary(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbMove, Position: Point{X: 1000}, Deadline: scene.End.Time + Time(150*time.Millisecond)})
		advanceVirtual(100 * time.Millisecond)
		at := captureTest(t, w, ctx, entry.Self.Entity).Self.Char.X
		if at <= 0 {
			t.Fatal("movement did not start before expiry")
		}
		advanceVirtual(500 * time.Millisecond)
		if after := captureTest(t, w, ctx, entry.Self.Entity); after.Self.Char.X != at {
			t.Fatalf("movement after expiry: got %d, want %d", after.Self.Char.X, at)
		}
		drainEvents(entry.Events)
	})
}

func TestExecuteAdmissionWaitHonorsWorldDeadline(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w := New(syntheticCatalog(t), &testStore{})
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		result := make(chan Commit, 1)
		failed := make(chan error, 1)
		go func() {
			commit, err := w.Execute(ctx, Request{Owner: Ref{ID: 1, Gen: 1}, Verb: VerbMove, Deadline: Time(100 * time.Millisecond), Position: Point{X: 100}})
			result <- commit
			failed <- err
		}()
		synctest.Wait()
		advanceVirtual(200 * time.Millisecond)
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		commit := <-result
		if err := <-failed; err != nil {
			t.Fatal(err)
		}
		if commit.Outcome.Stage != StageExpired || len(commit.Groups) != 0 {
			t.Fatalf("admission delay: got %+v, want expired without effects", commit)
		}
		cancel()
		if err := <-done; err != nil {
			t.Fatal(err)
		}
	})
}
