// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
)

func (c *Character) QuestVar(quest, name string) (string, bool) {
	i := slices.IndexFunc(c.QuestVars, func(v QuestVar) bool { return v.Quest == quest && v.Name == name })
	if i < 0 {
		return "", false
	}
	return c.QuestVars[i].Value, true
}

func (c *Character) SetQuestVar(quest, name, value string) {
	if i := slices.IndexFunc(c.QuestVars, func(v QuestVar) bool { return v.Quest == quest && v.Name == name }); i >= 0 {
		c.QuestVars[i].Value = value
		return
	}
	c.QuestVars = append(c.QuestVars, QuestVar{Quest: quest, Name: name, Value: value})
}

func (p *player) count(templateID int32) int64 {
	var n int64
	for _, it := range p.char.Items {
		if it.TemplateID == templateID {
			n += it.Count
		}
	}
	return n
}
