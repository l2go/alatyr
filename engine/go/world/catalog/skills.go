// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"cmp"
	"slices"
)

// SkillLearn is one row of a class skill tree: skill level Level of SkillID
// for SP, from character level MinLevel.
type SkillLearn struct{ SkillID, Level, MinLevel, SP int32 }

func (c *Catalog) sortSkillTree() {
	for _, rows := range c.skillTree {
		slices.SortStableFunc(rows, func(a, b SkillLearn) int {
			return cmp.Or(cmp.Compare(a.SkillID, b.SkillID), cmp.Compare(a.Level, b.Level))
		})
	}
}

// AvailableSkills returns the tree rows a character of class and level may learn
// next: level 1 of an unknown skill or the level after the known one. known
// returns the current level of a skill, or less than 1 if it is unknown.
func (c *Catalog) AvailableSkills(class, level int32, known func(id int32) int32) []SkillLearn {
	var out []SkillLearn
	for _, s := range c.tree(class) {
		if s.MinLevel > level {
			continue
		}
		if k := known(s.SkillID); k >= 1 {
			if k == s.Level-1 {
				out = append(out, s)
			}
		} else if s.Level == 1 {
			out = append(out, s)
		}
	}
	return out
}

// MinLevelForNewSkill returns the lowest character level above level at which
// class's tree offers a skill with a nonzero cost, or 0 if there is none.
func (c *Catalog) MinLevelForNewSkill(class, level int32) int32 {
	least := int32(0)
	for _, s := range c.tree(class) {
		if s.MinLevel > level && s.SP != 0 && (least == 0 || s.MinLevel < least) {
			least = s.MinLevel
		}
	}
	return least
}
