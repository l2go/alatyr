// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"cmp"
	"slices"
)
const MaxDropChance = 1000000
type Drop struct {
	ItemID   int32
	Min, Max int64
	Chance   int32
}
type DropCategory struct {
	Type  int32
	Drops []*Drop
	Chance int32
	balanced int32
}
func (c *DropCategory) DropOne(intN func(n int) int) *Drop {
	point := int32(intN(int(c.balanced)))
	var sum int32
	for _, d := range c.Drops {
		sum += min(d.Chance, MaxDropChance)
		if sum >= point {
			return d
		}
	}
	return nil
}
func (c *Catalog) DropCategories(npc int32) []*DropCategory { return c.drops[npc] }

type dropRow struct {
	npc, category int32
	drop          Drop
}
func (c *Catalog) buildDrops(rows []dropRow) {
	slices.SortStableFunc(rows, func(a, b dropRow) int {
		return cmp.Or(cmp.Compare(a.npc, b.npc), cmp.Compare(b.drop.Chance, a.drop.Chance))
	})
	for _, r := range rows {
		var cat *DropCategory
		for _, k := range c.drops[r.npc] {
			if k.Type == r.category {
				cat = k
			}
		}
		if cat == nil {
			cat = &DropCategory{Type: r.category}
			c.drops[r.npc] = append(c.drops[r.npc], cat)
		}
		d := r.drop
		cat.Drops = append(cat.Drops, &d)
		cat.Chance += d.Chance
		cat.balanced += min(d.Chance, MaxDropChance)
	}
}
func (t ItemTemplate) Stackable() bool {
	return t.ConsumeType == "asset" || t.ConsumeType == "stackable"
}
