// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

// ItemFunc is one stat function of an item or skill, applied to Stat in
// ascending Order. Op is set, add, sub, mul or div, which combine Value with the
// current stat; basemul, which adds Value times the stat as it stood before the
// first order-0x30 function; or enchant, which is accepted but has no effect
// yet. Conditional marks a function whose order, value or condition is
// unsupported or invalid; it is never applied. Cond, if set, must hold.
type ItemFunc struct {
	Stat        string
	Op          string
	Order       int
	Value       float64
	Conditional bool
	Cond        *Cond
}

// ItemFuncs returns a copy of item id's stat functions.
func (c *Catalog) ItemFuncs(id int32) []ItemFunc { return cloneFuncs(c.itemFuncs[id]) }

func cloneFuncs(in []ItemFunc) []ItemFunc {
	if in == nil {
		return nil
	}
	out := make([]ItemFunc, len(in))
	copy(out, in)
	for i := range out {
		if in[i].Cond != nil {
			v := cloneCond(*in[i].Cond)
			out[i].Cond = &v
		}
	}
	return out
}
func cloneCond(c Cond) Cond {
	c.Kinds = append([]string(nil), c.Kinds...)
	args := make([]Cond, len(c.Args))
	for i, a := range c.Args {
		args[i] = cloneCond(a)
	}
	c.Args = args
	return c
}
