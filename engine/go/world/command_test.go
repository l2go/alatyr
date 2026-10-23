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
		drainEvents(entry.Events)
		log.reset()
		same := executeTest(t, w, ctx, req)
		if same.Outcome.Stage != StageDone || len(same.Groups) != 0 {
			t.Fatalf("same target: got %+v, want done without groups", same)
		}
		if got := log.read(); len(got) != 1 || !reflect.DeepEqual(got[0], same) {
			t.Fatalf("same-target observer: got %+v, want returned commitment %+v", got, same)
		}
		if events := drainEvents(entry.Events); len(events) != 0 {
			t.Fatalf("same-target notifications: got %+v, want none", events)
		}
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			if p.attacking != 0 || p.casting || p.ai.intention != intentIdle || p.lastFolk != 0 || p.lastTarget != 0 {
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

func TestExecuteSelectRefusalReasons(t *testing.T) {
	for _, tc := range []struct {
		name                     string
		dead, teleporting, stale bool
		reason                   Reason
	}{
		{"dead owner", true, false, false, ReasonState},
		{"teleporting owner", false, true, false, ReasonState},
		{"stale target", false, false, true, ReasonStale},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				target := refTest(t, w, ctx, objectOf(t, w, ctx, 9101))
				if tc.stale {
					target.Gen++
				}
				if err := w.do(ctx, func(s *state) {
					p := s.players[1]
					p.dead, p.teleporting = tc.dead, tc.teleporting
				}); err != nil {
					t.Fatal(err)
				}
				drainEvents(entry.Events)
				log.reset()
				commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbSelect, Target: target})
				if commit.Outcome.Stage != StageRefused || commit.Outcome.Reason != tc.reason || len(commit.Groups) != 0 {
					t.Fatalf("select: got %+v, want refused/%s without groups", commit, tc.reason)
				}
				if events := drainEvents(entry.Events); len(events) != 0 {
					t.Fatalf("notifications: got %+v, want none", events)
				}
			})
		})
	}
}

func TestExecuteChooseCommitsIssuedList(t *testing.T) {
	for _, tc := range []struct {
		name   string
		skills []Skill
		count  int
	}{
		{"available lessons", nil, 2},
		{"empty list", []Skill{{ID: 9201, Level: 1}, {ID: 9202, Level: 1}}, 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				c := syntheticCharacter(1)
				c.Skills = tc.skills
				entry := enterTest(t, w, ctx, c)
				teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
				executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbTalk, Target: teacher})
				scene := captureTest(t, w, ctx, entry.Self.Entity)
				if len(scene.Dialogue.Affordances) != 1 {
					t.Fatalf("choices: got %+v, want one", scene.Dialogue.Affordances)
				}
				req := Request{Owner: entry.Self.Entity, Verb: VerbChoose, Target: teacher, AffordanceID: scene.Dialogue.Affordances[0].ID}
				drainEvents(entry.Events)
				log.reset()
				commit := executeTest(t, w, ctx, req)
				if commit.Outcome.Stage != StageDone || commit.Outcome.Reason != "" {
					t.Fatalf("choose: got %+v, want done", commit)
				}
				if got := log.read(); len(got) != 1 || !reflect.DeepEqual(got[0], commit) {
					t.Fatalf("choose observer: got %+v, want returned commitment %+v", got, commit)
				}
				for _, group := range commit.Groups {
					if group.Cause.Intent != commit.Intent.ID || len(group.Effects) != 0 {
						t.Fatalf("choose group: got %+v, want command facts without effects", group)
					}
				}
				events := drainEvents(entry.Events)
				if !containsEvent(events, func(e LearnList) bool { return e.NPCID == teacher.ID && len(e.Skills) == tc.count }) {
					t.Fatalf("lesson list: got %+v, want %d lessons", events, tc.count)
				}
				if !containsEvent(events, func(e AffordancesChanged) bool { return len(e.Affordances) == tc.count }) {
					t.Fatalf("issued affordances: got %+v, want %d", events, tc.count)
				}
				if captured := captureTest(t, w, ctx, entry.Self.Entity); len(captured.Dialogue.Affordances) != tc.count {
					t.Fatalf("captured lessons: got %+v, want %d", captured.Dialogue.Affordances, tc.count)
				}
				if replay := executeTest(t, w, ctx, req); replay.Outcome.Stage != StageRefused || replay.Outcome.Reason != ReasonStale || len(replay.Groups) != 0 {
					t.Fatalf("replaced choice: got %+v, want stale refusal without groups", replay)
				}
			})
		})
	}
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

func TestExecuteTargetRefusalReasons(t *testing.T) {
	for _, verb := range []Verb{VerbAttack, VerbTalk, VerbCast} {
		t.Run(string(verb), func(t *testing.T) {
			for _, tc := range []struct {
				name    string
				stale   bool
				missing bool
				dead    bool
				reason  Reason
			}{
				{"stale incarnation", true, false, false, ReasonStale},
				{"missing target", false, true, false, ReasonStale},
				{"dead owner", false, false, true, ReasonState},
				{"stale before state", true, false, true, ReasonStale},
			} {
				t.Run(tc.name, func(t *testing.T) {
					synctest.Test(t, func(t *testing.T) {
						var log commitLog
						w, _, ctx := startObservedWorld(t, &log)
						c := syntheticCharacter(1)
						c.Skills = []Skill{{ID: 9201, Level: 1}}
						entry := enterTest(t, w, ctx, c)
						templateID := int32(9101)
						if verb == VerbTalk {
							templateID = 9102
						}
						target := refTest(t, w, ctx, objectOf(t, w, ctx, templateID))
						if tc.stale {
							target.Gen++
						}
						if tc.missing {
							target.ID = 98765
						}
						if tc.dead {
							if err := w.do(ctx, func(s *state) { s.players[1].dead = true }); err != nil {
								t.Fatal(err)
							}
						}
						drainEvents(entry.Events)
						log.reset()
						commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: verb, Target: target, Skill: c.Skills[0]})
						if commit.Outcome.Stage != StageRefused || commit.Outcome.Reason != tc.reason || len(commit.Groups) != 0 {
							t.Fatalf("refusal: got %+v, want refused/%s without groups", commit, tc.reason)
						}
						if events := drainEvents(entry.Events); len(events) != 0 {
							t.Fatalf("refusal notifications: got %+v, want none", events)
						}
						if got := log.read(); len(got) != 1 || !reflect.DeepEqual(got[0], commit) {
							t.Fatalf("observer: got %+v, want returned refusal %+v", got, commit)
						}
					})
				})
			}
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
		observer := enterTest(t, w, ctx, syntheticCharacter(2))
		if scene := captureTest(t, w, ctx, observer.Self.Entity); !slices.ContainsFunc(scene.Objects, func(o Observation) bool { return o.Entity == entry.Self.Entity }) {
			t.Fatal("observer does not know moving player")
		}
		drainEvents(entry.Events)
		drainEvents(observer.Events)
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		move := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbMove, Position: Point{X: 1000}, Deadline: scene.End.Time + Time(150*time.Millisecond)})
		if move.Outcome.Stage != StageUnknown {
			t.Fatalf("move: got %+v, want unknown", move.Outcome)
		}
		for name, out := range map[string]<-chan Event{"self": entry.Events, "observer": observer.Events} {
			if events := drainEvents(out); !containsEvent(events, func(e Moving) bool { return e.ObjectID == entry.Self.Entity.ID && e.To == (Point{X: 1000}) }) {
				t.Fatalf("%s movement start: got %+v, want Moving", name, events)
			}
		}
		advanceVirtual(100 * time.Millisecond)
		at := captureTest(t, w, ctx, entry.Self.Entity)
		pos := Point{at.Self.Char.X, at.Self.Char.Y, at.Self.Char.Z}
		if pos.X <= 0 || !at.Moving {
			t.Fatal("movement did not start before expiry")
		}
		drainEvents(entry.Events)
		drainEvents(observer.Events)
		log.reset()
		advanceVirtual(100 * time.Millisecond)
		want := Stopped{ObjectID: entry.Self.Entity.ID, Pos: pos, Heading: at.Self.Char.Heading}
		for name, out := range map[string]<-chan Event{"self": entry.Events, "observer": observer.Events} {
			var stops []Stopped
			for _, event := range drainEvents(out) {
				if e, ok := event.(Stopped); ok && e.ObjectID == entry.Self.Entity.ID {
					stops = append(stops, e)
				}
			}
			if !slices.Equal(stops, []Stopped{want}) {
				t.Errorf("%s deadline stop: got %+v, want exactly %+v", name, stops, want)
			}
		}
		advanceVirtual(400 * time.Millisecond)
		after := captureTest(t, w, ctx, entry.Self.Entity)
		if got := (Point{after.Self.Char.X, after.Self.Char.Y, after.Self.Char.Z}); got != pos || after.Moving || after.Self.Char.Heading != want.Heading {
			t.Fatalf("captured stop: got position %v heading %v moving %v, want %v %v false", got, after.Self.Char.Heading, after.Moving, pos, want.Heading)
		}
		seen := captureTest(t, w, ctx, observer.Self.Entity)
		i := slices.IndexFunc(seen.Objects, func(o Observation) bool { return o.Entity == entry.Self.Entity })
		if i < 0 || seen.Objects[i].Moving {
			t.Fatalf("observer capture: got %+v, want stopped player", seen.Objects)
		}
		view, ok := seen.Objects[i].State.(PlayerSeen)
		if !ok || (Point{view.Player.Char.X, view.Player.Char.Y, view.Player.Char.Z}) != pos || view.Player.Char.Heading != want.Heading {
			t.Fatalf("observer position: got %+v, want position %v heading %v", seen.Objects[i], pos, want.Heading)
		}
		for name, out := range map[string]<-chan Event{"self": entry.Events, "observer": observer.Events} {
			for _, event := range drainEvents(out) {
				if e, ok := event.(Stopped); ok && e.ObjectID == entry.Self.Entity.ID {
					t.Errorf("%s repeated deadline stop: %+v", name, e)
				}
				if e, ok := event.(Moving); ok && e.ObjectID == entry.Self.Entity.ID {
					t.Errorf("%s late movement: %+v", name, e)
				}
				if e, ok := event.(AttackStarted); ok && e.AttackerID == entry.Self.Entity.ID {
					t.Errorf("%s late attack: %+v", name, e)
				}
			}
		}
		for _, commit := range log.read() {
			if commit.Outcome.Intent == move.Intent.ID {
				t.Errorf("deadline cleanup refixed move outcome: %+v", commit.Outcome)
			}
			for _, group := range commit.Groups {
				for _, fact := range group.Facts {
					if fact.Subject == entry.Self.Entity && fact.Property == PropertyPosition {
						t.Errorf("position integrated after deadline: %+v", fact)
					}
				}
				for _, effect := range group.Effects {
					if harm, ok := effect.(HarmApplied); ok && harm.Source == entry.Self.Entity {
						t.Errorf("late player harm: %+v", harm)
					}
				}
			}
		}
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
