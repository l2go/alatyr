// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"context"
	"encoding/csv"
	"fmt"
	"math"
	"math/rand/v2"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

type testStore struct {
	saved     Character
	skills    []Skill
	failSkill bool
}

func (store *testStore) PutCharacter(_ context.Context, c Character) error {
	store.saved = c.Clone()
	return nil
}
func (store *testStore) PutSkill(_ context.Context, _ int32, k Skill) error {
	if store.failSkill {
		return fmt.Errorf("synthetic write failure")
	}
	store.skills = append(store.skills, k)
	return nil
}
func repositoryWorld() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(file), "../../../world")
}

// Headers are the authored schema; all rows, XML and pages below are synthetic.
func syntheticCatalog(t *testing.T) *catalog.Catalog {
	t.Helper()
	root := t.TempDir()
	for _, dir := range []string{"catalog", "data/stats/weapon", "data/stats/armor", "data/stats/etcitem", "data/stats/skills", "data/zone", "data/html/trainer"} {
		if err := os.MkdirAll(filepath.Join(root, dir), 0700); err != nil {
			t.Fatal(err)
		}
	}
	data := map[string][]map[string]string{
		"player_template":   {{"player_class_id": "0", "class_name": "Synthetic", "base_strength": "40", "base_constitution": "40", "base_dexterity": "30", "base_intelligence": "20", "base_wit": "20", "base_mental": "20", "base_physical_attack": "20", "base_physical_defense": "20", "base_magic_attack": "10", "base_magic_defense": "20", "base_attack_speed": "400", "base_casting_speed": "400", "base_run_speed": "100", "base_accuracy": "50", "base_critical_rate": "0", "male_collision_radius": "5", "female_collision_radius": "5", "male_collision_height": "10", "female_collision_height": "10"}},
		"level_up_gain":     {{"player_class_id": "0", "base_hp_max": "200", "hp_per_level": "10", "base_mp_max": "100", "mp_per_level": "5", "base_cp_max": "50", "cp_per_level": "5", "class_base_level": "1"}},
		"weapon_template":   {{"id": "9001", "name": "Synthetic blade", "body_part": "rhand", "weight": "1", "weapon_type": "sword", "physical_damage": "20", "attack_speed": "400", "consume_type": "normal"}},
		"armor_template":    {},
		"etc_item_template": {{"id": "9002", "name": "Synthetic token", "weight": "1", "item_type": "none", "consume_type": "asset"}},
		"npc_template": {
			{"id": "9101", "name": "Synthetic creature", "instance_type": "L2Monster", "collision_radius": "5", "collision_height": "10", "level": "1", "max_hp": "70", "max_mp": "30", "hp_regen": "1", "mp_regen": "1", "p_atk": "2", "p_def": "20", "p_atk_speed": "400", "dexterity": "20", "attack_range": "30", "walk_speed": "30", "run_speed": "60", "reward_exp": "10", "reward_sp": "2"},
			{"id": "9102", "name": "Synthetic teacher", "instance_type": "L2Trainer", "collision_radius": "5", "collision_height": "10", "level": "1", "max_hp": "100", "max_mp": "30", "p_atk": "1", "p_def": "20", "p_atk_speed": "400", "dexterity": "20", "attack_range": "30", "walk_speed": "30", "run_speed": "60"},
		},
		"spawn": {
			{"spawn_group": "WORLD", "id": "1", "npc_template_id": "9101", "npc_count": "1", "x": "300", "y": "0", "z": "0", "heading": "0", "respawn_delay_s": "10", "period_of_day": "ALWAYS"},
			{"spawn_group": "WORLD", "id": "2", "npc_template_id": "9102", "npc_count": "1", "x": "100", "y": "0", "z": "0", "heading": "0", "respawn_delay_s": "10", "period_of_day": "ALWAYS"},
		},
		"skill_trainer_class": {{"npc_template_id": "9102", "player_class_id": "0"}},
		"skill_tree": {
			{"player_class_id": "0", "skill_id": "9201", "skill_level": "1", "skill_name": "Synthetic strike", "sp": "5", "min_level": "1"},
			{"player_class_id": "0", "skill_id": "9202", "skill_level": "1", "skill_name": "Synthetic practice", "sp": "3", "min_level": "1"},
			{"player_class_id": "0", "skill_id": "9202", "skill_level": "2", "skill_name": "Synthetic practice", "sp": "4", "min_level": "2"},
		},
		"drop": {{"npc_template_id": "9101", "item_template_id": "9002", "category": "0", "min_count": "2", "max_count": "2", "chance": "1000000"}},
	}
	for name, rows := range data {
		f, err := os.Open(filepath.Join(repositoryWorld(), "catalog", name+".csv"))
		if err != nil {
			t.Fatal(err)
		}
		head, err := csv.NewReader(f).Read()
		f.Close()
		if err != nil {
			t.Fatal(err)
		}
		var buf bytes.Buffer
		cw := csv.NewWriter(&buf)
		cw.Write(head)
		for _, row := range rows {
			values := make([]string, len(head))
			for i, h := range head {
				values[i] = row[h]
			}
			cw.Write(values)
		}
		cw.Flush()
		if err := os.WriteFile(filepath.Join(root, "catalog", name+".csv"), buf.Bytes(), 0600); err != nil {
			t.Fatal(err)
		}
	}
	for name, body := range map[string]string{
		"data/stats/weapon/synthetic.xml": `<list><item id="9001"><for><set order="0x08" stat="pAtk" val="20"/></for></item></list>`,
		"data/stats/skills/synthetic.xml": `<list>
   <skill id="9201" name="Synthetic strike" levels="1">
    <set name="operateType" val="OP_ACTIVE"/><set name="skillType" val="PDAM"/><set name="target" val="TARGET_ONE"/>
    <set name="power" val="20"/><set name="mpConsume" val="4"/><set name="mpInitialConsume" val="2"/>
    <set name="castRange" val="60"/><set name="effectRange" val="120"/><set name="hitTime" val="600"/>
    <set name="reuseDelay" val="5000"/><set name="staticHitTime" val="true"/><set name="staticReuse" val="true"/><set name="overHit" val="true"/>
   </skill>
   <skill id="9202" name="Synthetic practice" levels="2"><table name="#gain">4 6</table><set name="operateType" val="OP_PASSIVE"/><set name="skillType" val="BUFF"/><set name="target" val="TARGET_SELF"/><for><add order="0x40" stat="pAtk" val="#gain"/></for></skill>
  </list>`,
		"data/zone/synthetic.xml":    `<list><zone type="Town"><shape type="Rect"><point x="-1000" y="-1000"/><point x="-900" y="-900"/></shape></zone></list>`,
		"data/html/trainer/9102.htm": `<html><body>Synthetic teacher<button action="bypass -h npc_%objectId%_SkillList"></body></html>`,
	} {
		if err := os.WriteFile(filepath.Join(root, name), []byte(body), 0600); err != nil {
			t.Fatal(err)
		}
	}
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	return cat
}
func syntheticCharacter(id int32) Character {
	return Character{ObjectID: id, Account: "synthetic", Name: fmt.Sprintf("Player%d", id), ClassID: 0, Level: 1, SP: 20, CurHP: 200, CurMP: 100, CurCP: 50, Items: []Item{{ObjectID: id + 100, TemplateID: 9001, Count: 1, Slot: catalog.SlotRHand}}}
}
func drainEvents(out <-chan Event) []Event {
	var got []Event
	for {
		select {
		case e, ok := <-out:
			if !ok {
				return got
			}
			got = append(got, e)
		default:
			return got
		}
	}
}
func containsEvent[T Event](events []Event, match func(T) bool) bool {
	for _, e := range events {
		if value, ok := e.(T); ok && match(value) {
			return true
		}
	}
	return false
}
func startTestWorld(t *testing.T, cat *catalog.Catalog, options ...Option) (*World, *testStore, context.Context) {
	t.Helper()
	store := &testStore{}
	ctx, cancel := context.WithCancel(t.Context())
	t.Cleanup(cancel)
	w := New(cat, store, options...)
	w.s.rng = rand.New(rand.NewPCG(1, 2))
	go w.Run(ctx)
	return w, store, ctx
}
func enterTest(t *testing.T, w *World, ctx context.Context, c Character) Entry {
	t.Helper()
	entry, err := w.Enter(ctx, c)
	if err != nil {
		t.Fatal(err)
	}
	return entry
}
func objectOf(t *testing.T, w *World, ctx context.Context, template int32) int32 {
	t.Helper()
	var id int32
	w.do(ctx, func(s *state) {
		for _, n := range s.npcs {
			if n.tmpl.ID == template {
				id = n.id
				break
			}
		}
	})
	if id == 0 {
		t.Fatal("missing NPC")
	}
	return id
}

func TestCombatDeathDecayRespawn(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, store, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		mob := objectOf(t, w, ctx, 9101)
		if err := w.AttackRequest(ctx, c.ObjectID, mob); err != nil {
			t.Fatal(err)
		}
		got := drainEvents(entry.Events)
		died := false
		for range 300 {
			time.Sleep(100 * time.Millisecond)
			got = append(got, drainEvents(entry.Events)...)
			if containsEvent(got, func(e Died) bool { return e.ObjectID == mob }) {
				died = true
				break
			}
		}
		if !died || !containsEvent(got, func(e Following) bool { return e.ObjectID == 1 }) || !containsEvent(got, func(e AttackDone) bool { return e.AttackerID == 1 && e.Damage > 0 }) {
			t.Fatal("fight did not walk, attack and kill")
		}
		if !containsEvent(got, func(e Rewarded) bool { return e.Exp == 10 && e.SP == 2 }) {
			t.Fatal("damage-share reward")
		}
		if w.Logout(ctx, 1) == nil {
			t.Fatal("combat allowed voluntary logout")
		}
		var drop ItemDropped
		for _, e := range got {
			if v, ok := e.(ItemDropped); ok {
				drop = v
			}
		}
		if drop.Count != 2 || drop.TemplateID != 9002 {
			t.Fatal("synthetic drop")
		}
		if err := w.Pickup(ctx, 1, drop.ObjectID); err != nil {
			t.Fatal(err)
		}
		for range 20 {
			time.Sleep(100 * time.Millisecond)
			drainEvents(entry.Events)
		}
		self, _ := w.Snapshot(ctx, 1)
		if !slices.ContainsFunc(self.Char.Items, func(it Item) bool { return it.TemplateID == 9002 && it.Count == 2 }) {
			t.Fatal("pickup did not mutate inventory")
		}
		got = nil
		for range 200 {
			time.Sleep(100 * time.Millisecond)
			got = append(got, drainEvents(entry.Events)...)
		}
		if !containsEvent(got, func(e ObjectGone) bool { return e.ObjectID == mob }) || !containsEvent(got, func(e NPCSeen) bool { return e.NPC.Template.ID == 9101 && e.NPC.ObjectID != mob && !e.NPC.Dead }) {
			t.Fatal("corpse decay or fresh respawn")
		}
		if err := w.Leave(ctx, 1); err != nil {
			t.Fatal(err)
		}
		if store.saved.Exp != 10 || store.saved.SP != 22 {
			t.Fatal("reward not saved")
		}
	})
}
func TestPlayersMeetForgetAndAppear(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		a := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(a.Events)
		c := syntheticCharacter(2)
		c.X = 100
		b := enterTest(t, w, ctx, c)
		if !containsEvent(drainEvents(a.Events), func(e PlayerSeen) bool { return e.Player.Char.ObjectID == 2 }) || !containsEvent(drainEvents(b.Events), func(e PlayerSeen) bool { return e.Player.Char.ObjectID == 1 }) {
			t.Fatal("players do not meet")
		}
		if err := w.Teleport(ctx, 2, Point{X: 7000}, false); err != nil {
			t.Fatal(err)
		}
		if !containsEvent(drainEvents(a.Events), func(e ObjectGone) bool { return e.ObjectID == 2 }) {
			t.Fatal("teleport did not remove knowledge")
		}
		w.Appear(ctx, 2)
		drainEvents(b.Events)
		w.Teleport(ctx, 2, Point{X: 100}, false)
		drainEvents(b.Events)
		if containsEvent(drainEvents(a.Events), func(e PlayerSeen) bool { return e.Player.Char.ObjectID == 2 }) {
			t.Fatal("teleporting player visible")
		}
		w.Appear(ctx, 2)
		if !containsEvent(drainEvents(a.Events), func(e PlayerSeen) bool { return e.Player.Char.ObjectID == 2 }) {
			t.Fatal("appearance not visible")
		}
		w.MoveTo(ctx, 2, Point{X: 5000})
		var got []Event
		for range 600 {
			time.Sleep(100 * time.Millisecond)
			got = append(got, drainEvents(a.Events)...)
			drainEvents(b.Events)
		}
		if !containsEvent(got, func(e ObjectGone) bool { return e.ObjectID == 2 }) {
			t.Fatal("walking player never forgotten")
		}
	})
}
func TestTrainerLearningAndPassiveStats(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, store, ctx := startTestWorld(t, cat)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		teacher := objectOf(t, w, ctx, 9102)
		w.Action(ctx, 1, teacher)
		w.Action(ctx, 1, teacher)
		page := drainEvents(entry.Events)
		if !containsEvent(page, func(e NPCPage) bool { return strings.Contains(e.HTML, fmt.Sprintf("npc_%d_SkillList", teacher)) }) {
			t.Fatal("authored trainer page not loaded")
		}
		choice := fmt.Sprintf("npc_%d_SkillList", teacher)
		if w.Bypass(ctx, 1, choice+"_extra") == nil {
			t.Fatal("unoffered choice accepted")
		}
		if err := w.Bypass(ctx, 1, choice); err != nil {
			t.Fatal(err)
		}
		if !containsEvent(drainEvents(entry.Events), func(e LearnList) bool { return len(e.Skills) == 2 }) {
			t.Fatal("skill tree offer")
		}
		before, _ := w.Snapshot(ctx, 1)
		if err := w.Learn(ctx, 1, 9202, 1); err != nil {
			t.Fatal(err)
		}
		after, _ := w.Snapshot(ctx, 1)
		if after.PAtk <= before.PAtk || after.Char.SP != 17 || len(store.skills) != 1 {
			t.Fatal("learning did not persist, charge or refresh passive")
		}
		if w.Learn(ctx, 1, 9202, 2) == nil {
			t.Fatal("level requirement ignored")
		}
		if w.Learn(ctx, 1, 9202, 1) == nil {
			t.Fatal("duplicate learning charged")
		}
		if err := w.Leave(ctx, 1); err != nil {
			t.Fatal(err)
		}
		if store.saved.SP != 17 || !slices.Contains(store.saved.Skills, Skill{ID: 9202, Level: 1}) {
			t.Fatal("learning snapshot")
		}
	})
}
func TestSkillCastReuseAndProtectedMovement(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.X = 250
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		mob := objectOf(t, w, ctx, 9101)
		w.Action(ctx, 1, mob)
		drainEvents(entry.Events)
		w.MagicSkillUse(ctx, 1, 9201, false, true)
		if !containsEvent(drainEvents(entry.Events), func(e SkillCast) bool { return e.HitTime == 600 }) {
			t.Fatal("cast not started")
		}
		w.MoveTo(ctx, 1, Point{X: 150})
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.X != 250 {
			t.Fatal("protected cast interrupted by movement")
		}
		time.Sleep(650 * time.Millisecond)
		got := drainEvents(entry.Events)
		if !containsEvent(got, func(e DamageNotice) bool { return e.TargetID == mob && e.Damage > 0 }) || !containsEvent(got, func(e Moving) bool { return e.ObjectID == 1 }) {
			t.Fatal("cast damage or queued movement missing")
		}
		self, _ = w.Snapshot(ctx, 1)
		if self.Char.CurMP != 94 {
			t.Fatalf("MP charge: %v", self.Char.CurMP)
		}
		w.MagicSkillUse(ctx, 1, 9201, false, true)
		if !containsEvent(drainEvents(entry.Events), func(e SkillReuse) bool { return e.RemainingMilliseconds > 0 }) {
			t.Fatal("reuse not enforced")
		}
	})
}
func TestStaleAttackAndCastCannotHitAfterLeave(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.X = 250
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		mob := objectOf(t, w, ctx, 9101)
		w.Action(ctx, 1, mob)
		w.MagicSkillUse(ctx, 1, 9201, false, true)
		w.Leave(ctx, 1)
		enterTest(t, w, ctx, c)
		time.Sleep(700 * time.Millisecond)
		w.do(ctx, func(s *state) {
			if s.npcs[mob].hp != 70 {
				t.Fatal("old cast damaged new presence")
			}
		})
		w.AttackRequest(ctx, 1, mob)
		w.Leave(ctx, 1)
		enterTest(t, w, ctx, c)
		time.Sleep(700 * time.Millisecond)
		w.do(ctx, func(s *state) {
			if s.npcs[mob].hp != 70 {
				t.Fatal("old swing damaged new presence")
			}
		})
	})
}
func TestRegenDeathAndExplicitRevive(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.CurHP = 40
		c.CurMP = 10
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		time.Sleep(4 * time.Second)
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.CurHP <= 40 || self.Char.CurMP <= 10 {
			t.Fatal("regeneration missing")
		}
		mob := objectOf(t, w, ctx, 9101)
		w.do(ctx, func(s *state) { w.reduceHP(s, s.players[1], 10000, s.npcs[mob]) })
		self, _ = w.Snapshot(ctx, 1)
		if !self.Dead || self.Char.CurHP != 0 || len(self.Char.Skills) != 0 {
			t.Fatal("death state or invented skill")
		}
		time.Sleep(4 * time.Second)
		self, _ = w.Snapshot(ctx, 1)
		if self.Char.CurHP != 0 {
			t.Fatal("dead player regenerated")
		}
		if err := w.Teleport(ctx, 1, Point{X: 50}, true); err != nil {
			t.Fatal(err)
		}
		w.Appear(ctx, 1)
		self, _ = w.Snapshot(ctx, 1)
		if self.Dead || self.Teleporting || self.Char.CurHP != self.Char.MaxHP*0.7 || self.Char.X != 50 {
			t.Fatal("explicit revive")
		}
	})
}
func TestItemJournalPickupStackAndOverflow(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		var journal bytes.Buffer
		w, _, ctx := startTestWorld(t, cat, WithItemLog(itemlog.New(&journal, time.Now)))
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		mob := objectOf(t, w, ctx, 9101)
		for range 2 {
			var item int32
			w.do(ctx, func(s *state) {
				w.dropOnGround(s, s.npcs[mob], s.players[1], 9002, 2)
				for id := range s.ground {
					item = id
				}
				s.players[1].pos = s.ground[item].pos
			})
			w.Pickup(ctx, 1, item)
			time.Sleep(100 * time.Millisecond)
			drainEvents(entry.Events)
		}
		self, _ := w.Snapshot(ctx, 1)
		token := slices.IndexFunc(self.Char.Items, func(it Item) bool { return it.TemplateID == 9002 })
		if token < 0 || self.Char.Items[token].Count != 4 {
			t.Fatal("stack missing")
		}
		text := journal.String()
		if strings.Count(text, "[CREATE:Loot") != 2 || strings.Count(text, "[CHANGE:Pickup") != 2 || strings.Count(text, "[DELETE:Pickup") != 1 {
			t.Fatalf("journal lifecycle: %s", text)
		}
		w.do(ctx, func(s *state) {
			p := s.players[1]
			p.char.Items[token].Count = math.MaxInt64
			before := journal.Len()
			if w.addItem(s, p, Item{ObjectID: 999, TemplateID: 9002, Count: 1, Slot: -1}, "Pickup", itemlog.Null, true) == nil || p.char.Items[token].Count != math.MaxInt64 || journal.Len() != before {
				t.Fatal("overflow mutated inventory or journal")
			}
		})
	})
}
func TestExplicitExperienceThresholds(t *testing.T) {
	cat := syntheticCatalog(t)
	option, err := WithExperienceLevels([]int64{0, 10, 30})
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat, option)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		w.do(ctx, func(s *state) { w.addExpAndSP(s, s.players[1], 10, 2, false) })
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.Level != 2 || self.Char.CurHP != self.Char.MaxHP || self.Char.CurMP != self.Char.MaxMP || len(self.Char.Skills) != 0 {
			t.Fatal("explicit progression or unsolicited skill")
		}
	})
}

func TestDetachedEventsBetweenObservers(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		a := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(a.Events)
		b := enterTest(t, w, ctx, syntheticCharacter(2))
		drainEvents(a.Events)
		drainEvents(b.Events)
		c := enterTest(t, w, ctx, syntheticCharacter(3))
		drainEvents(a.Events)
		drainEvents(b.Events)
		drainEvents(c.Events)
		w.do(ctx, func(s *state) { w.fullInfo(s, s.players[1]) })
		first := drainEvents(b.Events)
		second := drainEvents(c.Events)
		for _, e := range first {
			if v, ok := e.(PlayerSeen); ok {
				v.Player.Char.Items[0].Count = 999
			}
		}
		for _, e := range second {
			if v, ok := e.(PlayerSeen); ok && v.Player.Char.Items[0].Count != 1 {
				t.Fatal("observer events share mutable state")
			}
		}
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.Items[0].Count != 1 {
			t.Fatal("event changed live state")
		}
	})
}

type syntheticGround struct{}

func (syntheticGround) Height(_, _, _ int32) (int32, bool)                { return 7, true }
func (syntheticGround) Walk(_, _, _, x, y, z int32) (int32, int32, int32) { return min(x, 50), y, z }
func TestSyntheticGroundAdapter(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat, WithGround(syntheticGround{}))
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		w.MoveTo(ctx, 1, Point{X: 100})
		for range 20 {
			time.Sleep(100 * time.Millisecond)
			drainEvents(entry.Events)
		}
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.X != 50 || self.Char.Z != 7 {
			t.Fatal("geometry adapter did not bound walk and height")
		}
	})
}

func TestProtectedSwingAndPositionObservation(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.X = 250
		entry := enterTest(t, w, ctx, c)
		drainEvents(entry.Events)
		w.ValidatePosition(ctx, 1, Point{X: 260, Y: 0, Z: 5})
		self, _ := w.Snapshot(ctx, 1)
		if self.Char.X != 250 || self.Char.Z != 5 {
			t.Fatal("horizontal authority lost")
		}
		w.ValidatePosition(ctx, 1, Point{X: 9000, Z: 50})
		self, _ = w.Snapshot(ctx, 1)
		if self.Char.Z != 5 {
			t.Fatal("far observation accepted")
		}
		mob := objectOf(t, w, ctx, 9101)
		w.AttackRequest(ctx, 1, mob)
		drainEvents(entry.Events)
		w.MoveTo(ctx, 1, Point{X: 100, Y: 0, Z: 5})
		self, _ = w.Snapshot(ctx, 1)
		if self.Char.X != 250 {
			t.Fatal("swing did not protect movement")
		}
		time.Sleep(1400 * time.Millisecond)
		if !containsEvent(drainEvents(entry.Events), func(e Moving) bool { return e.To.X == 100 }) {
			t.Fatal("saved swing intention not executed")
		}
	})
}

func TestTemporarySkillReplacementAndRemoval(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		w.do(ctx, func(s *state) {
			p := s.players[1]
			base := p.stats.PAtk()
			p.memSkills = append(p.memSkills, Skill{ID: 9202, Level: 1})
			w.refreshSkillFuncs(p)
			if p.stats.PAtk() <= base {
				t.Fatal("temporary passive missing")
			}
			w.addSkill(s, p, Skill{ID: 9202, Level: 2})
			if len(p.memSkills) != 0 || p.skillLevel(9202) != 2 || len(p.shownSkills()) != 1 {
				t.Fatal("replacement duplicated temporary skill")
			}
			if !w.removeSkill(s, p, 9202) || p.skillLevel(9202) > 0 || p.stats.PAtk() != base {
				t.Fatal("removal left passive functions")
			}
		})
	})
}
