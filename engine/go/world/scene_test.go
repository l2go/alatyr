// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"go/ast"
	"go/parser"
	"go/token"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"
	"testing/synctest"
	"time"
)

func TestCaptureReadsEverySourceWithoutEffects(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			w.addSkill(s, p, Skill{ID: 9201, Level: 1})
			p.disableSkill(9201, time.Second)
			w.talk(s, p, s.npcs[teacher.ID])
		}); err != nil {
			t.Fatal(err)
		}
		log.reset()
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		if !scene.End.Ready || scene.Begin.Sequence != scene.End.Sequence || scene.Begin.Time != scene.End.Time || len(scene.Coverage) != 7 || len(scene.Objects) != 2 || len(scene.Inventory) != 1 || len(scene.Skills) != 1 {
			t.Fatalf("capture: got %+v, want complete supported scene", scene)
		}
		for _, coverage := range scene.Coverage {
			if !coverage.Complete || coverage.Absent != (coverage.Domain == DomainTrade || coverage.Domain == DomainQuests) {
				t.Fatalf("coverage: got %+v", coverage)
			}
		}
		if scene.Inventory[0].Entity.Gen == 0 || !scene.Skills[0].Disabled || scene.Skills[0].ReuseUntil == 0 || scene.Dialogue.Speaker != teacher || scene.Dialogue.Page != "data/html/trainer/9102.htm" || len(scene.Dialogue.Affordances) != 1 {
			t.Fatalf("source detail: got %+v", scene)
		}
		original := cloneAffordances(scene.Dialogue.Affordances)
		advanceVirtual(500 * time.Millisecond)
		log.reset()
		fresh, subscription, err := w.Subscribe(ctx, entry.Self.Entity)
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(original, fresh.Dialogue.Affordances) || len(log.read()) != 0 {
			t.Fatal("capture/resubscription reissued choices or replayed effects")
		}
		if reason := <-entry.Subscription.End; reason != CloseReplaced {
			t.Fatalf("old stream: got %v, want replaced", reason)
		}
		if got := drainEvents(subscription.Events); len(got) != 0 {
			t.Fatalf("new stream replayed notifications: %+v", got)
		}
		fresh.Dialogue.Affordances[0].Parameters = []Parameter{{Name: "changed"}}
		fresh.Inventory[0].Item.Count = 77
		fresh.Self.Char.Items[0].Count = 88
		fresh.Objects[0].Entity.Gen = 0
		again := captureTest(t, w, ctx, entry.Self.Entity)
		if !reflect.DeepEqual(original, again.Dialogue.Affordances) || again.Inventory[0].Item.Count != 1 || again.Self.Char.Items[0].Count != 1 || again.Objects[0].Entity.Gen == 0 {
			t.Fatal("detached scene aliased mutable world data")
		}
	})
}

func TestCaptureReportsMissingSources(t *testing.T) {
	for _, domain := range []Domain{DomainObjects, DomainInventory, DomainSkills, DomainDialogue} {
		t.Run(string(domain), func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				if err := w.do(ctx, func(s *state) {
					p := s.players[1]
					switch domain {
					case DomainObjects:
						p.known[98765] = true
					case DomainInventory:
						delete(p.itemRefs, p.char.Items[0].ObjectID)
					case DomainSkills:
						p.memSkills = []Skill{{ID: 9999, Level: 1}}
					case DomainDialogue:
						p.dialogue = "missing/page"
					}
				}); err != nil {
					t.Fatal(err)
				}
				scene := captureTest(t, w, ctx, entry.Self.Entity)
				i := slices.IndexFunc(scene.Coverage, func(c Coverage) bool { return c.Domain == domain })
				if scene.End.Ready || i < 0 || scene.Coverage[i].Complete || scene.Coverage[i].Absent {
					t.Fatalf("missing %s: got %+v", domain, scene)
				}
				drainEvents(entry.Events)
			})
		})
	}
}

func TestCaptureDoesNotRenewExpiredAffordances(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		teacher := refTest(t, w, ctx, objectOf(t, w, ctx, 9102))
		executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbTalk, Target: teacher})
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		a := scene.Dialogue.Affordances[0]
		advanceVirtual(time.Minute)
		fresh, sub, err := w.Subscribe(ctx, entry.Self.Entity)
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(scene.Dialogue.Affordances, fresh.Dialogue.Affordances) || a.Expires > fresh.End.Time {
			t.Fatal("snapshot extended affordance lifetime")
		}
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbChoose, Target: teacher, AffordanceID: a.ID})
		if commit.Outcome.Stage != StageRefused || commit.Outcome.Reason != ReasonStale || len(commit.Groups) != 0 {
			t.Fatalf("expired choice: got %+v", commit)
		}
		drainEvents(sub.Events)
	})
}

func TestSubscriptionCloseReasonsAndRecovery(t *testing.T) {
	for _, tc := range []struct {
		name   string
		reason CloseReason
	}{
		{"overflow", CloseOverflow}, {"leave", CloseLeave}, {"stop", CloseStop},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				ctx, cancel := context.WithCancel(t.Context())
				defer cancel()
				w := New(syntheticCatalog(t), &testStore{})
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				drainEvents(entry.Events)
				switch tc.reason {
				case CloseOverflow:
					if err := w.do(ctx, func(s *state) {
						for range 65 {
							s.players[1].send(ActionIgnored{})
						}
					}); err != nil {
						t.Fatal(err)
					}
				case CloseLeave:
					if err := w.Leave(ctx, entry.Self.Entity); err != nil {
						t.Fatal(err)
					}
				case CloseStop:
					cancel()
				}
				if got, ok := <-entry.Subscription.End; !ok || got != tc.reason {
					t.Fatalf("close: got %v/%v, want %v", got, ok, tc.reason)
				}
				if _, ok := <-entry.Subscription.End; ok {
					t.Fatal("close reason repeated")
				}
				if tc.reason == CloseOverflow {
					scene, sub, err := w.Subscribe(ctx, entry.Self.Entity)
					if err != nil || !scene.End.Ready || scene.Self.Entity != entry.Self.Entity {
						t.Fatalf("recovery: got %+v, %v", scene, err)
					}
					executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbMove, Position: Point{X: 20}})
					if got := drainEvents(sub.Events); len(got) == 0 {
						t.Fatal("recovered stream lost new notifications")
					}
				} else {
					_, _, err := w.Subscribe(context.Background(), entry.Self.Entity)
					want := ErrNotPresent
					if tc.reason == CloseStop {
						want = ErrStopped
					}
					if !errors.Is(err, want) {
						t.Fatalf("impossible recovery: got %v, want %v", err, want)
					}
				}
				cancel()
				if err := <-done; err != nil {
					t.Fatal(err)
				}
			})
		})
	}
}

func TestTeleportFenceAndIncarnation(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		owner := entry.Self.Entity
		executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbTeleport, Position: Point{X: 7000}})
		scene := captureTest(t, w, ctx, owner)
		first := scene.End.Fence
		if scene.End.Ready || first == 0 || len(scene.Objects) != 0 || scene.Self.Entity != owner {
			t.Fatalf("pending relocation: %+v", scene)
		}
		for _, fence := range []Fence{0, first + 1} {
			commit := executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAppear, Fence: fence})
			if commit.Outcome.Stage != StageRefused || len(commit.Groups) != 0 {
				t.Fatalf("stale fence: %+v", commit)
			}
		}
		executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAppear, Fence: first})
		if !captureTest(t, w, ctx, owner).End.Ready {
			t.Fatal("appearance did not finish relocation")
		}
		executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbTeleport, Position: Point{X: 0}})
		second := captureTest(t, w, ctx, owner).End.Fence
		if second <= first {
			t.Fatal("relocation fence did not advance")
		}
		if old := executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAppear, Fence: first}); old.Outcome.Stage != StageRefused {
			t.Fatalf("old continuation: %+v", old)
		}
		executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAppear, Fence: second})
		if err := w.Leave(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		replacement := enterTest(t, w, ctx, syntheticCharacter(1))
		if replacement.Self.Entity.Gen <= owner.Gen {
			t.Fatal("reentry reused incarnation")
		}
		if old := executeTest(t, w, ctx, Request{Owner: owner, Verb: VerbAppear, Fence: second}); old.Outcome.Reason != ReasonStale {
			t.Fatalf("old incarnation: %+v", old)
		}
		drainEvents(replacement.Events)
	})
}

func TestAbsentDomainsHaveNoEventProducer(t *testing.T) {
	// Event is sealed by its private method. Inspect every production implementer:
	// adding a trade/quest event requires removing the absence declarations and
	// replacing this assertion with real capture tests.
	files, err := filepath.Glob("*.go")
	if err != nil {
		t.Fatal(err)
	}
	for _, path := range files {
		if strings.HasSuffix(path, "_test.go") {
			continue
		}
		file, err := parser.ParseFile(token.NewFileSet(), path, nil, 0)
		if err != nil {
			t.Fatal(err)
		}
		for _, decl := range file.Decls {
			fn, ok := decl.(*ast.FuncDecl)
			if !ok || fn.Recv == nil || fn.Name.Name != "event" {
				continue
			}
			name, ok := fn.Recv.List[0].Type.(*ast.Ident)
			if !ok {
				t.Fatalf("unrecognized event receiver in %s", path)
			}
			// An exhaustive allowlist is stronger than checking the new name:
			// any added event requires reviewing the absent capabilities.
			allowed := []string{"Moving", "Stopped", "LeftWorld", "Announcement", "NPCSeen", "PlayerSeen", "ObjectGone", "SelfChanged", "PositionObserved", "TargetChosen", "TargetDetails", "TargetCleared", "Vitals", "AttackStarted", "DamageNotice", "CombatStance", "GaitChanged", "PostureChanged", "Died", "Animation", "Following", "ZoneChanged", "Teleported", "Revived", "DialoguePage", "LearnList", "SkillsShown", "SkillCast", "SkillLaunched", "SkillCanceled", "SkillReuse", "Rewarded", "LevelChanged", "ItemDropped", "ItemPicked", "InventoryChanged", "ActionIgnored", "AffordancesChanged", "GameClock"}
			if !slices.Contains(allowed, name.Name) {
				t.Errorf("new event %s requires review of absent mechanics", name.Name)
			}
		}
	}
}

func TestAbsentMechanicsRefuseBeforeExecution(t *testing.T) {
	for _, verb := range []Verb{"buy", "sell", "quest"} {
		t.Run(string(verb), func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var log commitLog
				w, _, ctx := startObservedWorld(t, &log)
				entry := enterTest(t, w, ctx, syntheticCharacter(1))
				drainEvents(entry.Events)
				before := captureTest(t, w, ctx, entry.Self.Entity)
				commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: verb})
				after := captureTest(t, w, ctx, entry.Self.Entity)
				if commit.Outcome.Stage != StageRefused || commit.Outcome.Reason != ReasonUnavailable || len(commit.Groups) != 0 || len(drainEvents(entry.Events)) != 0 || !reflect.DeepEqual(before.Self, after.Self) || !reflect.DeepEqual(before.Coverage, after.Coverage) {
					t.Fatalf("absent mechanic %s: got %+v", verb, commit)
				}
			})
		})
	}
}

func TestExecuteEntryMovementExitSave(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, store, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		before, sub, err := w.Subscribe(ctx, entry.Self.Entity)
		if err != nil || !before.End.Ready {
			t.Fatalf("entry capture: %+v %v", before, err)
		}
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbMove, Position: Point{X: 20}})
		if commit.Outcome.Stage != StageUnknown {
			t.Fatalf("move handoff: %+v", commit)
		}
		advanceVirtual(time.Second)
		if after := captureTest(t, w, ctx, entry.Self.Entity); after.Self.Char.X != 20 {
			t.Fatalf("movement: got X %d, want 20", after.Self.Char.X)
		}
		drainEvents(sub.Events)
		if err := w.Leave(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		if store.saved.X != 20 || <-sub.End != CloseLeave {
			t.Fatalf("exit/save: %+v", store.saved)
		}
	})
}
