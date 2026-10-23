// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

type testCatalog struct {
	items  map[int32]catalog.ItemTemplate
	skills map[world.Skill]catalog.Skill
}

func (c testCatalog) Item(id int32) (catalog.ItemTemplate, bool) { v, ok := c.items[id]; return v, ok }
func (c testCatalog) Skill(id, level int32) (catalog.Skill, bool) {
	v, ok := c.skills[world.Skill{ID: id, Level: level}]
	return v, ok
}
func (c testCatalog) CanTeach(npc, class int32) bool { return npc == 100 && class == 7 }
func fixture() (translator, world.Scene) {
	c := testCatalog{
		items:  map[int32]catalog.ItemTemplate{201: {ID: 201, Kind: catalog.EtcItem, Name: "Stone", ConsumeType: "stackable"}, 202: {ID: 202, Kind: catalog.EtcItem, Name: "Token", ConsumeType: "asset"}},
		skills: map[world.Skill]catalog.Skill{{ID: 301, Level: 1}: {ID: 301, DisplayID: 301, Level: 1, Name: "Focus"}, {ID: 301, Level: 2}: {ID: 301, DisplayID: 301, Level: 2, Name: "Focus"}},
	}
	s := world.Scene{Self: world.Self{Entity: world.Ref{ID: 1, Gen: 1}, Char: world.Character{ObjectID: 1, ClassID: 7, Name: "Player", Level: 1, Exp: 1<<60 + 5, SP: 20, CurHP: 12.9, MaxHP: 20, CurMP: 4, MaxMP: 10}, RunSpeed: 100, WalkSpeed: 50, Running: true}, Begin: world.Marker{Sequence: 4, Time: world.Time(20 * time.Millisecond)}, End: world.Marker{Sequence: 4, Time: world.Time(20 * time.Millisecond), Ready: true}}
	n := world.NPCView{Entity: world.Ref{ID: 2, Gen: 2}, ObjectID: 2, Template: catalog.NPCTemplate{ID: 100, Role: catalog.Trainer, Name: "Teacher", SendsServerName: true, RunSpeed: 100, WalkSpeed: 50}}
	s.Objects = []world.Observation{{Entity: n.Entity, State: world.NPCSeen{NPC: n}}}
	for _, domain := range []world.Domain{world.DomainSelf, world.DomainObjects, world.DomainInventory, world.DomainSkills, world.DomainDialogue, world.DomainTrade, world.DomainQuests} {
		s.Coverage = append(s.Coverage, world.Coverage{Domain: domain, Complete: true, Absent: domain == world.DomainTrade || domain == world.DomainQuests})
	}
	return translator{catalog: c}, s
}
func choiceScene(s world.Scene) world.Scene {
	s.Dialogue = world.Dialogue{Speaker: s.Objects[0].Entity, Page: "lesson.htm", HTML: "<html><body><a action=\"bypass -h npc_2_SkillList\">Learn</a></body></html>", Affordances: []world.Affordance{{ID: 10, Verb: world.VerbChoose, Target: s.Objects[0].Entity, Parameters: []world.Parameter{{Name: "action", Value: world.LabelValue("npc_2_SkillList")}}, Issued: world.Time(2 * time.Millisecond), Expires: world.Time(time.Minute + 2*time.Millisecond)}}}
	return s
}
func lessonScene(s world.Scene) world.Scene {
	s = choiceScene(s)
	s.Dialogue.Affordances = []world.Affordance{{ID: 11, Verb: world.VerbLearn, Target: s.Objects[0].Entity, Issued: world.Time(3 * time.Millisecond), Expires: world.Time(time.Minute + 3*time.Millisecond), Parameters: []world.Parameter{{Name: "skill", Value: world.IntValue(301)}, {Name: "level", Value: world.IntValue(1)}}, Cost: []world.Cost{{Resource: world.Resource{Kind: world.ResourceSP}, Quantity: 10}}}}
	return s
}
func command(verb uint64) bridge.Command {
	c := bridge.Command{Key: 7, Verb: verb}
	switch verb {
	case 1:
		c.Payload = []wire.Field{wire.At(11, true, 20, 0, 0)}
	case 2, 3, 4, 5, 10:
		c.Payload = []wire.Field{wire.S(64, true, 2)}
	case 6:
		c.Payload = []wire.Field{wire.S(64, true, 2), wire.Data(68, true, "npc_2_SkillList")}
	case 7:
		c.Payload = []wire.Field{wire.S(14, true, 1), wire.S(16, true, 2), wire.S(66, true, 201)}
	case 8:
		c.Payload = []wire.Field{wire.S(14, true, 1), wire.S(16, true, 2), wire.S(64, true, 30), wire.S(66, true, 201)}
	case 12:
		c.Payload = []wire.Field{wire.S(64, true, 2), wire.Obj(18, true, []wire.Field{wire.S(1, true, 301), wire.S(2, true, 1)})}
	case 13:
		c.Payload = []wire.Field{wire.S(14, true, 0), wire.Obj(18, true, []wire.Field{wire.S(1, true, 301), wire.S(2, true, 1)})}
	}
	return c
}
func preparedTest(t *testing.T, verb uint64, s world.Scene) prepared {
	t.Helper()
	p, err := prepare(command(verb), s, s.End.Time)
	if err != nil {
		t.Fatal(err)
	}
	return p
}
func commit(p prepared, stage world.Stage) world.Commit {
	intent := world.Intent{ID: world.IntentID{Owner: p.request.Owner, N: 9}, Verb: p.request.Verb, Deadline: p.request.Deadline}
	return world.Commit{Sequence: 5, Time: world.Time(30 * time.Millisecond), Intent: intent, Outcome: world.Outcome{Intent: intent.ID, Stage: stage}}
}
func group(c world.Commit, es ...world.Effect) world.EffectGroup {
	return world.EffectGroup{Cause: world.Cause{Intent: c.Intent.ID}, Effects: es}
}
func learningGroup(c world.Commit, p prepared) world.EffectGroup {
	return group(c,
		world.ResourceTransferred{Resource: world.Resource{Kind: world.ResourceSP}, From: world.ResourceLocation{Entity: p.request.Owner, Place: world.PlaceCharacter}, To: world.ResourceLocation{Place: world.PlaceLearning}, Amount: world.IntegerAmount(p.cost), FromBalance: world.Balance{Before: world.IntegerAmount(20), After: world.IntegerAmount(20 - p.cost)}},
		world.CapabilityChanged{Entity: p.request.Owner, Capability: world.CapabilitySkill, ID: p.request.Skill.ID, Before: 0, After: 1},
	)
}
func receiptTest(t *testing.T, tr translator, p prepared, c world.Commit, err error) bridge.Receipt {
	t.Helper()
	r, failure := tr.receipt(p, c, err, 1, 1, time.Unix(1, 0))
	if failure != nil {
		t.Fatal(failure)
	}
	return r
}
