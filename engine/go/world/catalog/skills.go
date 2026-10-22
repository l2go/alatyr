// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"cmp"
	"slices"
)

type SkillLearn struct{ SkillID, Level, MinLevel, SP int32 }

func (c *Catalog) sortSkillTree() {
	for _, rows := range c.skillTree {
		slices.SortStableFunc(rows, func(a, b SkillLearn) int {
			return cmp.Or(cmp.Compare(a.SkillID, b.SkillID), cmp.Compare(a.Level, b.Level))
		})
	}
}

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

func (c *Catalog) MinLevelForNewSkill(class, level int32) int32 {
	least := int32(0)
	for _, s := range c.tree(class) {
		if s.MinLevel > level && s.SP != 0 && (least == 0 || s.MinLevel < least) {
			least = s.MinLevel
		}
	}
	return least
}
