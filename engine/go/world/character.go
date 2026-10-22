// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import "slices"

type Character struct {
	ObjectID              int32
	Account               string
	Name                  string
	ClassID               int32
	BaseClass             int32
	Race                  int32
	Female                bool
	HairStyle             int32
	HairColor             int32
	Face                  int32
	Level                 int32
	Exp                   int64
	SP                    int32
	MaxHP, MaxCP, MaxMP   float64
	CurHP, CurMP, CurCP   float64
	X, Y, Z               int32
	Heading               int32
	Vitality              int32
	RecommendationsLeft   int32
	LastAccess            int64
	Items                 []Item
	Skills                []Skill
	Shortcuts             []Shortcut
	QuestVars             []QuestVar
	NameColor, TitleColor int32
	DeathPenalty          int32
}

type Skill struct {
	ID, Level int32
}

type ShortcutType int32

type Shortcut struct {
	Slot, Page int32
	Type       ShortcutType
	ID, Level  int32
}

type QuestVar struct {
	Quest, Name, Value string
}

func (c *Character) RegisterShortcut(s Shortcut) {
	if i := slices.IndexFunc(c.Shortcuts, func(o Shortcut) bool { return o.Page == s.Page && o.Slot == s.Slot }); i >= 0 {
		c.Shortcuts[i] = s
		return
	}
	c.Shortcuts = append(c.Shortcuts, s)
}

type Item struct {
	ObjectID   int32
	TemplateID int32
	Count      int64
	Slot       int32
}

func (it Item) Equipped() bool { return it.Slot >= 0 }

func (c Character) Clone() Character {
	c.Items = slices.Clone(c.Items)
	c.Skills = slices.Clone(c.Skills)
	c.Shortcuts = slices.Clone(c.Shortcuts)
	c.QuestVars = slices.Clone(c.QuestVars)
	return c
}
