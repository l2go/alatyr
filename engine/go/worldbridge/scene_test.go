// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"math"
	"reflect"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestCheckpointUsesCapturedSourcesAndKnownEmpty(t *testing.T) {
	tr, s := fixture()
	s = lessonScene(s)
	s.Inventory = []world.Carried{{Entity: world.Ref{ID: 20, Gen: 20}, Item: world.Item{ObjectID: 20, TemplateID: 201, Count: 3, Slot: -1}}, {Entity: world.Ref{ID: 21, Gen: 21}, Item: world.Item{ObjectID: 21, TemplateID: 202, Count: 13, Slot: -1}}}
	cp, next, err := tr.checkpoint(s, 1, 1, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !cp.Ready || len(cp.Incomplete) != 0 || cp.FenceSeq != next-1 || cp.EncodedBytes <= 0 {
		t.Fatalf("got %+v, want complete ready checkpoint", cp)
	}
	seen := map[uint64]bool{}
	for _, r := range cp.Facts {
		k := profile.U(r, 1)
		seen[k] = true
		if profile.Has(r, 10) || profile.Has(r, 16) || profile.Has(r, 32) {
			t.Fatal("participant reference or cause escaped into raw fact")
		}
		if k == 14 {
			v, _ := wire.Find(r.Fields, 24)
			if len(v.Items) != 1 || profile.S(r, 23) != 13 {
				t.Fatalf("got %+v, want one item and separate authored coin", r)
			}
		}
		if k == 16 {
			stats, _ := wire.Find(r.Fields, 25)
			xp, _ := wire.Find(stats.Fields, 6)
			hp, _ := wire.Find(stats.Fields, 1)
			if xp.S != 1<<60+5 || hp.S != 12 {
				t.Fatal("exact XP or checked display conversion lost")
			}
		}
		if k == 22 {
			lessons, _ := wire.Find(r.Fields, 34)
			maxLevel, _ := wire.Find(lessons.Items[0].Fields, 4)
			if maxLevel.S != 2 || profile.S(r, 74) != 3 {
				t.Fatal("maximum or proposal origin was invented")
			}
		}
	}
	for _, k := range []uint64{1, 14, 16, 22, 23, 24} {
		if !seen[k] {
			t.Fatalf("missing source kind %d", k)
		}
	}
	fresh := s
	fresh.Begin.Time += world.Time(time.Second)
	fresh.End.Time = fresh.Begin.Time
	later, _, err := tr.checkpoint(fresh, 2, next, 0)
	if err != nil {
		t.Fatal(err)
	}
	for _, r := range later.Facts {
		if profile.U(r, 1) == 22 && profile.S(r, 74) != 3 {
			t.Fatal("checkpoint renewed proposal")
		}
	}
	cp.Facts[0].Fields[0].Value.U = 99
	again, _, err := tr.checkpoint(s, 1, 1, 0)
	if err != nil || profile.U(again.Facts[0], 1) != 1 {
		t.Fatal("checkpoint output aliases source")
	}
}

func TestCheckpointDoesNotReleaseTeleportFence(t *testing.T) {
	tr, s := fixture()
	s.Self.Teleporting = true
	cp, _, err := tr.checkpoint(s, 1, 1, 0)
	if err != nil {
		t.Fatal(err)
	}
	if cp.Ready {
		t.Fatal("got ready checkpoint, want held teleport fence")
	}
}

func TestCheckpointRejectsIncompleteOrUnrepresentableDomains(t *testing.T) {
	for _, tc := range []struct {
		name, domain string
		mutate       func(*world.Scene)
	}{
		{"unknown inventory", "inventory", func(s *world.Scene) {
			s.Coverage = slices.DeleteFunc(s.Coverage, func(c world.Coverage) bool { return c.Domain == world.DomainInventory })
		}},
		{"trade capability not absent", "trade", func(s *world.Scene) { s.Coverage[5].Absent = false }},
		{"quest capability not absent", "quests", func(s *world.Scene) { s.Coverage[6].Absent = false }},
		{"invalid HP", "self", func(s *world.Scene) { s.Self.Char.CurHP = math.NaN() }},
		{"unknown item", "inventory", func(s *world.Scene) {
			s.Inventory = []world.Carried{{Entity: world.Ref{ID: 20, Gen: 20}, Item: world.Item{ObjectID: 20, TemplateID: 999, Count: 1}}}
		}},
		{"multiple asset rows", "inventory", func(s *world.Scene) {
			s.Inventory = []world.Carried{{Entity: world.Ref{ID: 20, Gen: 20}, Item: world.Item{ObjectID: 20, TemplateID: 202, Count: 1}}, {Entity: world.Ref{ID: 21, Gen: 21}, Item: world.Item{ObjectID: 21, TemplateID: 202, Count: 1}}}
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			tc.mutate(&s)
			cp, _, err := tr.checkpoint(s, 1, 1, 0)
			if err != nil {
				t.Fatal(err)
			}
			if cp.Ready || !slices.Contains(cp.Incomplete, tc.domain) {
				t.Fatalf("got %+v, want incomplete %s", cp, tc.domain)
			}
		})
	}
}
func TestCheckpointBoundsFenceAndExpiredProposals(t *testing.T) {
	tr, s := fixture()
	for _, tc := range []struct {
		name   string
		mutate func(*world.Scene)
		want   error
	}{
		{"split fence", func(s *world.Scene) { s.End.Fence = 1 }, errSource},
		{"split sequence", func(s *world.Scene) { s.End.Sequence++ }, errSource},
		{"object bound", func(s *world.Scene) { s.Objects = make([]world.Observation, maxRows+1) }, errCapacity},
	} {
		t.Run(tc.name, func(t *testing.T) {
			copyScene := s
			tc.mutate(&copyScene)
			_, next, err := tr.checkpoint(copyScene, 1, 1, 0)
			if !errors.Is(err, tc.want) || next != 1 {
				t.Fatalf("got %v/next %d, want %v/next 1", err, next, tc.want)
			}
		})
	}
	s = choiceScene(s)
	s.Begin.Time = world.Time(2 * time.Minute)
	s.End.Time = s.Begin.Time
	p, err := prepare(command(6), s, s.End.Time)
	if err != nil || p.refusal != world.ReasonStale {
		t.Fatalf("got %+v/%v, want stale expired choice", p, err)
	}
	if s.Dialogue.Affordances[0].Expires != world.Time(time.Minute+2*time.Millisecond) {
		t.Fatal("authority expiry mutated")
	}
}
func TestCheckpointCapacityIsAtomic(t *testing.T) {
	tr, s := fixture()
	cat := tr.catalog.(testCatalog)
	for i := int32(0); i < 400; i++ {
		s.Inventory = append(s.Inventory, world.Carried{Entity: world.Ref{ID: 20 + i, Gen: uint64(20 + i)}, Item: world.Item{ObjectID: 20 + i, TemplateID: 201, Count: 1, Slot: -1}})
	}
	template := cat.items[201]
	template.Name = strings.Repeat("x", maxPayloadBytes)
	cat.items[201] = template
	cp, next, err := tr.checkpoint(s, 1, 1, 0)
	if err == nil || next != 1 || cp.Ready || len(cp.Facts) != 0 {
		t.Fatalf("got %+v/%d/%v, want atomic capacity failure", cp, next, err)
	}
}
func TestNotificationMappingKeepsFactsSeparateFromEffects(t *testing.T) {
	tr, s := fixture()
	for _, tc := range []struct {
		name  string
		event world.Event
		kind  uint64
	}{
		{"moving", world.Moving{ObjectID: 1, To: world.Point{X: 5}}, 2},
		{"stopped", world.Stopped{ObjectID: 1, Pos: world.Point{X: 5}}, 3},
		{"attack calculated", world.AttackStarted{AttackerID: 1, TargetID: 2, Damage: 4}, 5},
		{"picked before inventory", world.ItemPicked{PlayerID: 1, ObjectID: 3}, 9},
		{"inventory total", world.InventoryChanged{Changes: []world.ItemChange{{Kind: "added", Item: world.Item{ObjectID: 20, TemplateID: 201, Count: 3, Slot: -1}}}}, 15},
		{"reward delta", world.Rewarded{ObjectID: 1, XP: 20, SP: 2}, 17},
		{"ignored is not refusal", world.ActionIgnored{}, 0},
		{"details are not selection", world.TargetDetails{TargetID: 2}, 0},
		{"damage notice is not harm", world.DamageNotice{AttackerID: 1, TargetID: 2, Damage: 4}, 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			fs, err := tr.events(tc.event, s, 0)
			if err != nil {
				t.Fatal(err)
			}
			if tc.kind == 0 {
				if len(fs) != 0 {
					t.Fatalf("got %+v, want no facts", fs)
				}
				return
			}
			rs, _, err := factRecords(fs, 1, 1, s.End.Time, 0)
			if err != nil {
				t.Fatal(err)
			}
			if len(rs) != 1 || profile.U(rs[0], 1) != tc.kind {
				t.Fatalf("got %+v, want raw fact kind %d", rs, tc.kind)
			}
			for _, r := range rs {
				if r.Type != wire.Fact || profile.Has(r, 32) {
					t.Fatal("notification fabricated receipt or cause")
				}
			}
		})
	}
}
func TestAffordanceChangesDoNotRepeatSpeech(t *testing.T) {
	tr, s := fixture()
	s = choiceScene(s)
	for _, scene := range []world.Scene{s, lessonScene(s)} {
		fs, err := tr.events(world.AffordancesChanged{Affordances: scene.Dialogue.Affordances}, scene, 0)
		if err != nil {
			t.Fatal(err)
		}
		for _, f := range fs {
			if f.kind == 11 {
				t.Fatal("affordance change fabricated another utterance")
			}
		}
	}
}
func TestSourceInputsRemainUnchanged(t *testing.T) {
	tr, s := fixture()
	s = lessonScene(s)
	before := s
	before.Dialogue.Affordances = slices.Clone(s.Dialogue.Affordances)
	before.Dialogue.Affordances[0].Params = slices.Clone(s.Dialogue.Affordances[0].Params)
	before.Dialogue.Affordances[0].Cost = slices.Clone(s.Dialogue.Affordances[0].Cost)
	_, _, err := tr.checkpoint(s, 1, 1, 0)
	if err != nil {
		t.Fatal(err)
	}
	_, err = prepare(command(13), s, s.End.Time)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(s, before) {
		t.Fatal("translation changed source authority")
	}
}

func TestEverySupportedNotificationHasAnExplicitMapping(t *testing.T) {
	tr, s := fixture()
	s.Skills = []world.KnownSkill{{Skill: world.Skill{ID: 301, Level: 1}}}
	s = choiceScene(s)
	npc := s.Objects[0].State.(world.NPCSeen)
	npc.NPC.Running = true
	s.Objects[0].State = npc
	for _, tc := range []struct {
		name  string
		event world.Event
		kinds []uint64
	}{
		{"NPC", npc, []uint64{1}},
		{"player", world.PlayerSeen{Player: s.Self}, []uint64{1}},
		{"self", world.SelfChanged{Self: s.Self}, []uint64{1, 16}},
		{"position", world.PositionObserved{ObjectID: 1}, []uint64{4}},
		{"target chosen", world.TargetChosen{ObjectID: 1, TargetID: 2}, []uint64{24}},
		{"target clear", world.TargetCleared{ObjectID: 1}, []uint64{24}},
		{"vitals", world.Vitals{ObjectID: 1, HP: 12, MP: 4, MaxHP: 20, MaxMP: 10}, []uint64{16}},
		{"following", world.Following{ObjectID: 1, TargetID: 2}, []uint64{2}},
		{"teleport", world.Teleported{ObjectID: 1, Entity: s.Self.Entity, Fence: 3}, []uint64{4}},
		{"died", world.Died{ObjectID: 2}, []uint64{6}},
		{"revived", world.Revived{ObjectID: 2}, []uint64{7}},
		{"dialogue", world.DialoguePage{NPCID: 2, HTML: s.Dialogue.HTML}, []uint64{11}},
		{"skills", world.SkillsShown{Skills: []world.Skill{{ID: 301, Level: 1}}}, []uint64{23}},
		{"ground", world.ItemDropped{ObjectID: 3, TemplateID: 201, Count: 1, DropperID: 2}, []uint64{8}},
		{"gone", world.ObjectGone{ObjectID: 2}, []uint64{10}},
		{"notice", world.Announcement{Text: "Hello"}, []uint64{18}},
		{"level", world.LevelChanged{ObjectID: 1, Before: 1, After: 2}, []uint64{16}},
		{"own gait", world.GaitChanged{ObjectID: 1, Running: true}, []uint64{1}},
		{"NPC gait", world.GaitChanged{ObjectID: 2, Running: true}, []uint64{1}},
		{"stance", world.CombatStance{}, nil},
		{"posture", world.PostureChanged{}, nil},
		{"animation", world.Animation{}, nil},
		{"zone", world.ZoneChanged{}, nil},
		{"cast", world.SkillCast{}, nil},
		{"launch", world.SkillLaunched{}, nil},
		{"canceled", world.SkillCanceled{}, nil},
		{"reuse", world.SkillReuse{}, nil},
		{"clock", world.GameClock{}, nil},
		{"left", world.LeftWorld{}, nil},
	} {
		t.Run(tc.name, func(t *testing.T) {
			fs, err := tr.events(tc.event, s, 0)
			if err != nil {
				t.Fatal(err)
			}
			var kinds []uint64
			for _, f := range fs {
				kinds = append(kinds, f.kind)
			}
			if !slices.Equal(kinds, tc.kinds) {
				t.Fatalf("got %v, want %v", kinds, tc.kinds)
			}
			if len(fs) == 0 {
				return
			}
			rs, _, err := factRecords(fs, 1, 1, s.End.Time, 0)
			if err != nil {
				t.Fatal(err)
			}
			if tc.name == "player" && profile.Has(rs[0], 19) {
				t.Fatal("foreign appearance claimed ownership")
			}
			if tc.name == "teleport" && profile.U(rs[0], 66) != 3 {
				t.Fatal("teleport fence replaced with entity generation")
			}
		})
	}
}

func TestDialogueNotificationRequiresActualPage(t *testing.T) {
	tr, s := fixture()
	for _, tc := range []struct {
		name  string
		page  world.Dialogue
		event world.DialoguePage
	}{
		{"missing page", world.Dialogue{}, world.DialoguePage{}},
		{"different page", choiceScene(s).Dialogue, world.DialoguePage{NPCID: 2, HTML: "<p>Other</p>"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			scene := s
			scene.Dialogue = tc.page
			fs, err := tr.events(tc.event, scene, 0)
			if !errors.Is(err, errSource) || len(fs) != 0 {
				t.Fatalf("got %+v/%v, want incomplete source without facts", fs, err)
			}
		})
	}
}

func TestLessonsAndEmptyListRequireActualSource(t *testing.T) {
	tr, s := fixture()
	s = lessonScene(s)
	for _, tc := range []struct {
		name      string
		count     int
		wantError bool
	}{
		{"issued lesson", 1, false}, {"issued empty list", 0, false}, {"mismatched source", 2, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			scene := s
			event := world.LearnList{NPCID: 2}
			for range tc.count {
				event.Skills = append(event.Skills, catalog.SkillLearn{SkillID: 301, Level: 1, SP: 10})
			}
			if tc.count == 0 {
				scene.Dialogue.Affordances = nil
			}
			fs, err := tr.events(event, scene, 0)
			if tc.wantError {
				if !errors.Is(err, errSource) {
					t.Fatalf("got %v, want missing source", err)
				}
				return
			}
			if err != nil || len(fs) != 1 || fs[0].kind != 22 {
				t.Fatalf("got %+v/%v, want one real practice fact", fs, err)
			}
		})
	}
}

func TestUnknownDisplaySkillIsIncomplete(t *testing.T) {
	tr, s := fixture()
	cat := tr.catalog.(testCatalog)
	sk := cat.skills[world.Skill{ID: 301, Level: 1}]
	sk.DisplayID = 999
	cat.skills[world.Skill{ID: 301, Level: 1}] = sk
	s.Skills = []world.KnownSkill{{Skill: world.Skill{ID: 301, Level: 1}}}
	cp, _, err := tr.checkpoint(s, 1, 1, 0)
	if err != nil || cp.Ready || !slices.Contains(cp.Incomplete, "skills") {
		t.Fatalf("got %+v/%v, want incomplete display mapping", cp, err)
	}
}
