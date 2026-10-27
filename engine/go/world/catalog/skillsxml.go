// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"errors"
	"fmt"
	"maps"
	"strings"
)

// SkillState classifies a skill's effect on its target.
type SkillState int8

// Skill states.
const (
	SkillOffensive SkillState = -1
	SkillNeutral   SkillState = 0
	SkillPositive  SkillState = 1
)

// Skill is one level of an authored skill from the stat XML.
type Skill struct {
	ID, Level                             int32
	DisplayID                             int32
	Name                                  string
	Passive                               bool
	Operate                               string
	Type, Target                          string
	SendToClient                          bool
	State                                 SkillState
	Magic                                 bool
	Power                                 float64
	MPConsume, MPInitialConsume           int32
	HPConsume                             int32
	HitTime, CoolTime, ReuseDelay         int32
	CastRange                             int32
	EffectRange                           int32
	WeaponsAllowed                        []string
	OverHit                               bool
	SSBoost                               float64
	BaseCritRate                          int32
	Lethal1, Lethal2                      int32
	StaticHitTime, StaticReuse            bool
	IgnoreShld, IgnoreResists             bool
	CanBeReflected                        bool
	NeededCharges, ItemConsume, SoulCount int32
	ItemConsumeID                         int32
	Item, Potion                          bool
	Precondition                          bool
	Funcs                                 []ItemFunc
}

// Toggle reports whether the skill switches on and off.
func (s Skill) Toggle() bool { return s.Operate == "OP_TOGGLE" }

// Offensive reports whether the skill is offensive.
func (s Skill) Offensive() bool { return s.State == SkillOffensive }

// NotDone reports whether the skill is unimplemented or has no target.
func (s Skill) NotDone() bool { return s.Type == "NOTDONE" || s.Target == "TARGET_NONE" }

type skillLevels struct {
	levels   map[int32]Skill
	maxLevel int32
}

func parseSkill(n xmlNode) (int32, skillLevels, error) {
	idText, _ := n.attr("id")
	id, err := javaDecode(strings.TrimSpace(idText))
	if err != nil {
		return 0, skillLevels{}, err
	}
	levelsText, ok := n.attr("levels")
	if !ok {
		return 0, skillLevels{}, errors.New("no levels")
	}
	levels, err := javaDecode(strings.TrimSpace(levelsText))
	if err != nil {
		return 0, skillLevels{}, err
	}
	if id <= 0 || levels < 1 || levels > 1000 {
		return 0, skillLevels{}, errors.New("invalid skill id or levels")
	}
	tables := map[string][]string{}
	for _, ch := range n.Children {
		if ch.XMLName.Local == "table" {
			name, _ := ch.attr("name")
			if !strings.HasPrefix(name, "#") {
				return 0, skillLevels{}, fmt.Errorf("table %q", name)
			}
			if _, exists := tables[name]; exists {
				return 0, skillLevels{}, errors.New("duplicate skill table")
			}
			tables[name] = strings.Fields(ch.Text)
		}
	}
	value := func(v string, i int) (string, error) {
		v = strings.TrimSpace(v)
		if !strings.HasPrefix(v, "#") {
			return v, nil
		}
		t, ok := tables[v]
		if !ok || i < 0 || i >= len(t) {
			return "", fmt.Errorf("table %s has no element %d", v, i)
		}
		return t[i], nil
	}
	setsAt := func(prefix string, i int, into map[string]string) error {
		for _, ch := range n.Children {
			if ch.XMLName.Local != prefix {
				continue
			}
			name, _ := ch.attr("name")
			val, _ := ch.attr("val")
			v, err := value(val, i)
			if err != nil {
				return err
			}
			into[strings.TrimSpace(name)] = v
		}
		return nil
	}
	name, _ := n.attr("name")
	precondition := false
	for _, ch := range n.Children {
		if ch.XMLName.Local == "cond" {
			precondition = true
		}
	}
	out := skillLevels{levels: map[int32]Skill{}, maxLevel: levels}
	var last map[string]string
	for l := int32(1); l <= levels; l++ {
		set := map[string]string{}
		if err := setsAt("set", int(l-1), set); err != nil {
			return 0, skillLevels{}, err
		}
		s, err := skillOf(id, l, set)
		if err != nil {
			return 0, skillLevels{}, err
		}
		s.Name, s.Precondition = name, precondition
		s.Funcs = skillFuncs(n, func(v string) (string, error) { return value(v, int(l-1)) })
		out.levels[l] = s
		last = set
	}
	for k := 1; k <= 8; k++ {
		countText, ok := n.attr(fmt.Sprintf("enchantLevels%d", k))
		if !ok {
			continue
		}
		count, err := javaDecode(strings.TrimSpace(countText))
		if err != nil {
			return 0, skillLevels{}, err
		}
		if count < 1 || count > 99 {
			return 0, skillLevels{}, errors.New("invalid enchant levels")
		}
		for i := range count {
			set := make(map[string]string, len(last))
			maps.Copy(set, last)
			if err := setsAt(fmt.Sprintf("enchant%d", k), int(i), set); err != nil {
				return 0, skillLevels{}, err
			}
			level := int32(k)*100 + i + 1
			s, err := skillOf(id, level, set)
			if err != nil {
				return 0, skillLevels{}, err
			}
			s.Name, s.Precondition = name, precondition
			s.Funcs = out.levels[levels].Funcs
			out.levels[level] = s
		}
	}
	return id, out, nil
}
