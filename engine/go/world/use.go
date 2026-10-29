// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

func (w *World) useItem(s *state, p *player, req Request) {
	i := slices.IndexFunc(p.char.Items, func(it Item) bool { return it.ObjectID == req.Target.ID })
	if i < 0 || p.itemRefs[req.Target.ID] != req.Target {
		s.resolve(StageRefused, ReasonStale)
		return
	}
	if p.dead || p.teleporting {
		s.resolve(StageRefused, ReasonState)
		return
	}
	it := p.char.Items[i]
	tmpl, ok := w.cat.Item(it.TemplateID)
	if !ok {
		s.resolve(StageRefused, ReasonUnavailable)
		return
	}
	if tmpl.Kind == catalog.Weapon || tmpl.Kind == catalog.Armor {
		w.toggleEquipment(s, p, i, tmpl)
		return
	}
	if len(tmpl.Skills) != 1 {
		s.resolve(StageRefused, ReasonUnavailable)
		return
	}
	ref := tmpl.Skills[0]
	sk, ok := w.cat.Skill(ref.ID, ref.Level)
	if !ok || sk.Passive || sk.Type != "HEAL_STATIC" || sk.Target != "TARGET_SELF" || !sk.Item || !sk.Potion || sk.ItemConsumeID != it.TemplateID || sk.ItemConsume < 1 || int64(sk.ItemConsume) > it.Count || sk.MPConsume != 0 || sk.MPInitialConsume != 0 || sk.HPConsume != 0 || sk.Precondition || p.reuse[sk.ID].until.After(time.Now()) {
		s.resolve(StageRefused, ReasonState)
		return
	}
	life, fence := p.lifeVersion, p.fence
	p.disableSkill(sk.ID, time.Duration(sk.ReuseDelay)*time.Millisecond)
	p.send(SkillCast{CasterID: p.id, TargetID: p.id, SkillID: sk.ID, Level: sk.Level, HitTime: sk.HitTime, Reuse: sk.ReuseDelay, From: p.pos, TargetPos: p.pos})
	intent := s.cause().Intent
	apply := func(s *state) {
		if s.players[p.id] != p || p.dead || p.teleporting || p.lifeVersion != life || p.fence != fence || req.Deadline != 0 && s.now() >= req.Deadline || p.itemRefs[it.ObjectID] != req.Target {
			return
		}
		index := slices.IndexFunc(p.char.Items, func(row Item) bool { return row.ObjectID == it.ObjectID })
		if index < 0 || p.char.Items[index].Count < int64(sk.ItemConsume) {
			return
		}
		w.applyHealingItem(s, p, index, req.Target, sk, intent)
	}
	// Admission reserves reuse, not a second copy of the item. At launch, both
	// the exact carried row and the original life/fence must still authorize it.
	if sk.HitTime == 0 {
		apply(s)
		s.finish(StageDone)
	} else {
		s.afterRule(time.Duration(sk.HitTime)*time.Millisecond, RuleItemEffect, apply)
	}
}

func (w *World) applyHealingItem(s *state, p *player, index int, ref Ref, sk catalog.Skill, intent IntentID) {
	it := p.char.Items[index]
	before := it.Count
	it.Count -= int64(sk.ItemConsume)
	kind, op := "modified", itemlog.Change
	if it.Count == 0 {
		kind, op = "removed", itemlog.Delete
		p.char.Items = slices.Delete(p.char.Items, index, index+1)
		delete(p.itemRefs, it.ObjectID)
	} else {
		p.char.Items[index] = it
	}
	effects := []Effect{ResourceTransferred{Resource: Resource{Kind: ResourceItem, TemplateID: it.TemplateID}, From: ResourceLocation{Entity: p.ref(), Item: ref, Place: PlaceInventory}, To: ResourceLocation{Place: PlaceConsumption}, Amount: IntegerAmount(int64(sk.ItemConsume)), FromBalance: Balance{Before: IntegerAmount(before), After: IntegerAmount(it.Count)}}}
	if it.Count == 0 {
		effects = append(effects, ExistenceChanged{Entity: ref, Before: true})
	}
	oldHP := p.hp
	nextHP := min(float64(p.maxHP()), oldHP+sk.Power)
	if nextHP != oldHP {
		effects = append(effects, ResourceTransferred{Resource: Resource{Kind: ResourceHP}, From: ResourceLocation{Place: PlaceWorld}, To: ResourceLocation{Entity: p.ref(), Place: PlaceCharacter}, Amount: ContinuousAmount(nextHP - oldHP), FromBalance: Balance{Before: ContinuousAmount(0), After: ContinuousAmount(0)}, ToBalance: Balance{Before: ContinuousAmount(oldHP), After: ContinuousAmount(nextHP)}})
	}
	p.send(SkillLaunched{CasterID: p.id, SkillID: sk.ID, Level: sk.Level, Targets: []int32{p.id}})
	w.assignHP(s, p, nextHP)
	s.group(effects)
	p.send(InventoryChanged{UseIntent: intent, Changes: []ItemChange{{Kind: kind, Item: it}}, Load: w.load(p)})
	w.logItem(op, "ItemUse", it.ObjectID, it.TemplateID, it.Count, p, sk.Name)
}

func equipmentSlot(t catalog.ItemTemplate) (int32, bool) {
	switch t.BodyPartName {
	case "head":
		return catalog.SlotHead, true
	case "rhand", "lrhand":
		return catalog.SlotRHand, true
	case "lhand":
		return catalog.SlotLHand, true
	case "gloves":
		return catalog.SlotGloves, true
	case "chest", "fullarmor":
		return catalog.SlotChest, true
	case "legs":
		return catalog.SlotLegs, true
	case "feet":
		return catalog.SlotFeet, true
	}
	return 0, false
}

func (w *World) toggleEquipment(s *state, p *player, index int, tmpl catalog.ItemTemplate) {
	slot, ok := equipmentSlot(tmpl)
	if !ok || p.char.Items[index].Count != 1 || p.casting || p.attackingNow(time.Now()) {
		s.resolve(StageRefused, ReasonState)
		return
	}
	items := slices.Clone(p.char.Items)
	if items[index].Equipped() {
		items[index].Slot = -1
	} else {
		for i := range items {
			held, _ := w.cat.Item(items[i].TemplateID)
			conflict := items[i].Slot == slot || tmpl.BodyPartName == "lrhand" && items[i].Slot == catalog.SlotLHand || slot == catalog.SlotLHand && held.BodyPartName == "lrhand" && items[i].Equipped() || tmpl.BodyPartName == "fullarmor" && items[i].Slot == catalog.SlotLegs || slot == catalog.SlotLegs && held.BodyPartName == "fullarmor" && items[i].Equipped()
			if conflict {
				items[i].Slot = -1
			}
		}
		items[index].Slot = slot
	}
	next := p.char
	next.Items = items
	stats, err := w.statsOf(next)
	if err != nil {
		s.resolve(StageRefused, ReasonState)
		return
	}
	var changes []ItemChange
	var facts []Fact
	for i, it := range items {
		if before := p.char.Items[i].Slot; it.Slot != before {
			changes = append(changes, ItemChange{Kind: "modified", Item: it})
			facts = append(facts, Fact{Subject: p.itemRefs[it.ObjectID], Property: PropertyEquipmentSlot, Before: IntValue(int64(before)), After: IntValue(int64(it.Slot))})
		}
	}
	p.char.Items, p.stats = items, stats
	w.refreshSkillFuncs(p)
	s.group(nil, facts...)
	w.setHP(s, p, p.hp)
	w.setMP(s, p, p.mp)
	w.setCP(s, p, p.cp)
	w.markBroadcast(s, p, broadcastFullInfo)
	p.send(InventoryChanged{UseIntent: s.cause().Intent, Changes: changes, Load: w.load(p)})
	s.finish(StageDone)
}
