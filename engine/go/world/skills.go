// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"cmp"
	"slices"
	"strings"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func (p *player) skillLevel(id int32) int32 {
	for _, list := range [][]Skill{p.memSkills, p.char.Skills} {
		if i := slices.IndexFunc(list, func(s Skill) bool { return s.ID == id }); i >= 0 {
			return list[i].Level
		}
	}
	return -1
}

func (p *player) shownSkills() []Skill {
	out := slices.Clone(p.char.Skills)
	for _, m := range p.memSkills {
		if i := slices.IndexFunc(out, func(s Skill) bool { return s.ID == m.ID }); i >= 0 {
			out[i] = m
		} else {
			out = append(out, m)
		}
	}
	slices.SortFunc(out, func(a, b Skill) int { return cmp.Compare(a.ID, b.ID) })
	return out
}

func (w *World) addSkill(s *state, p *player, k Skill) {
	old := p.skillLevel(k.ID)
	w.putSkill(s, p, k)
	if old != k.Level {
		s.group([]Effect{CapabilityChanged{Entity: p.ref(), Capability: "skill", ID: k.ID, Before: max(old, 0), After: k.Level}})
	}
}
func (w *World) putSkill(s *state, p *player, k Skill) {
	old := p.skillLevel(k.ID)
	p.memSkills = slices.DeleteFunc(p.memSkills, func(o Skill) bool { return o.ID == k.ID })
	if i := slices.IndexFunc(p.char.Skills, func(o Skill) bool { return o.ID == k.ID }); i >= 0 {
		p.char.Skills[i] = k
	} else {
		p.char.Skills = append(p.char.Skills, k)
	}
	if old == k.Level {
		return
	}
	before, _ := w.cat.Skill(k.ID, old)
	after, _ := w.cat.Skill(k.ID, k.Level)
	w.refreshSkillFuncs(p)
	if len(before.Funcs) > 0 || len(after.Funcs) > 0 {
		w.markBroadcast(s, p, broadcastFullInfo)
	}
	w.markBroadcast(s, p, broadcastSkillList)
}

func (w *World) refreshSkillFuncs(p *player) {
	p.stats.SkillFuncs = w.skillFuncs(p.shownSkills(), p.stats.Worn)
}

func (w *World) skillFuncs(known []Skill, worn map[int32]catalog.ItemTemplate) []catalog.ItemFunc {
	var funcs []catalog.ItemFunc
	for _, k := range known {
		sk, ok := w.cat.Skill(k.ID, k.Level)
		if !ok || sk.Type == "NOTDONE" || len(sk.Funcs) == 0 {
			continue
		}
		if !sk.Offensive() && !weaponAllowed(sk, worn) {
			continue
		}
		funcs = append(funcs, sk.Funcs...)
	}
	return funcs
}

func weaponAllowed(sk catalog.Skill, worn map[int32]catalog.ItemTemplate) bool {
	if len(sk.WeaponsAllowed) == 0 {
		return true
	}
	for _, slot := range []int32{catalog.SlotRHand, catalog.SlotLHand} {
		if t, ok := worn[slot]; ok && t.Kind == catalog.Weapon && slices.Contains(sk.WeaponsAllowed, strings.ToLower(t.WeaponType)) {
			return true
		}
	}
	return false
}
func (w *World) removeSkill(s *state, p *player, id int32) bool {
	old := p.skillLevel(id)
	had := old >= 1
	p.char.Skills = slices.DeleteFunc(p.char.Skills, func(k Skill) bool { return k.ID == id })
	p.memSkills = slices.DeleteFunc(p.memSkills, func(k Skill) bool { return k.ID == id })
	if had {
		s.group([]Effect{CapabilityChanged{Entity: p.ref(), Capability: "skill", ID: id, Before: old}})
		w.refreshSkillFuncs(p)
		w.markBroadcast(s, p, broadcastFullInfo|broadcastSkillList)
	}
	return had
}
