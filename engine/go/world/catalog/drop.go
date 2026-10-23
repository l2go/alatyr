// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"cmp"
	"slices"
)

// MaxDropChance is 100% on the authored chance scale, in millionths. Load
// rejects a drop above it and a category whose drop chances sum above it.
const MaxDropChance = 1000000

// Drop is one possible item of a category: between Min and Max of ItemID with
// Chance in millionths.
type Drop struct {
	ItemID   int32
	Min, Max int64
	Chance   int32
}

// DropCategory groups the drops of one NPC that share Type. Chance is the sum of
// its drops' authored chances; Drops are ordered by descending chance. Whether a
// category is rolled at all is decided by the caller from Chance.
type DropCategory struct {
	Type     int32
	Drops    []*Drop
	Chance   int32
	balanced int32
}

// DropOne selects one drop with probability proportional to its chance and
// returns a pointer into Drops. intN must return a uniform value in [0, n).
// The selection weights are computed when the catalog is loaded, so use
// categories from Catalog.DropCategories without changing their drops: a zero
// or hand-built DropCategory selects nothing, and an edited one keeps its old
// weights.
func (c *DropCategory) DropOne(intN func(n int) int) *Drop {
	if c.balanced <= 0 {
		return nil
	}
	point := int32(intN(int(c.balanced)))
	var sum int32
	for _, d := range c.Drops {
		sum += min(d.Chance, MaxDropChance)
		if sum > point {
			return d
		}
	}
	return nil
}

// DropCategories returns copies of NPC template npc's drop categories.
func (c *Catalog) DropCategories(npc int32) []*DropCategory {
	var out []*DropCategory
	for _, cat := range c.drops[npc] {
		cp := *cat
		cp.Drops = nil
		for _, d := range cat.Drops {
			v := *d
			cp.Drops = append(cp.Drops, &v)
		}
		out = append(out, &cp)
	}
	return out
}

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

// Stackable reports whether instances of the item merge into one stack.
func (t ItemTemplate) Stackable() bool {
	return t.ConsumeType == "asset" || t.ConsumeType == "stackable"
}
