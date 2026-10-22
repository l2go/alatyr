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
