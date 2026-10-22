// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

type ItemFunc struct {
	Stat        string
	Op          string
	Order       int
	Value       float64
	Conditional bool
	Cond        *Cond
}

func (c *Catalog) ItemFuncs(id int32) []ItemFunc { return c.itemFuncs[id] }
