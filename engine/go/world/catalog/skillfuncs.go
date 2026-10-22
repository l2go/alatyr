// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"strconv"
	"strings"
)

type Cond struct {
	Op   string
	Mask int32
	Args []Cond
}

func skillFuncs(n xmlNode, value func(string) (string, error)) []ItemFunc {
	var out []ItemFunc
	for _, ch := range n.Children {
		if ch.XMLName.Local != "for" {
			continue
		}
		for _, f := range ch.Children {
			op := strings.ToLower(f.XMLName.Local)
			switch op {
			case "set", "add", "sub", "mul", "div", "basemul", "enchant":
			default:
				continue
			}
			out = append(out, skillFunc(op, f, value))
		}
	}
	return out
}

func skillFunc(op string, f xmlNode, value func(string) (string, error)) ItemFunc {
	stat, _ := f.attr("stat")
	fn := ItemFunc{Stat: stat, Op: op}
	orderText, _ := f.attr("order")
	order, err := strconv.ParseInt(strings.TrimPrefix(strings.TrimSpace(orderText), "0x"), 16, 32)
	valText, _ := f.attr("val")
	v, verr := value(valText)
	val, ferr := strconv.ParseFloat(v, 64)
	if err != nil || verr != nil || ferr != nil {
		fn.Conditional = true
		return fn
	}
	fn.Order, fn.Value = int(order), val
	var conds []Cond
	for _, c := range f.Children {
		cond, ok := parseCond(c)
		if !ok {
			fn.Conditional = true
			return fn
		}
		conds = append(conds, cond)
	}
	switch len(conds) {
	case 0:
	case 1:
		fn.Cond = &conds[0]
	default:
		fn.Cond = &Cond{Op: "and", Args: conds}
	}
	return fn
}

func parseCond(n xmlNode) (Cond, bool) {
	switch op := n.XMLName.Local; op {
	case "and", "or", "not":
		c := Cond{Op: op}
		for _, ch := range n.Children {
			a, ok := parseCond(ch)
			if !ok {
				return Cond{}, false
			}
			c.Args = append(c.Args, a)
		}
		if len(c.Args) == 0 || op == "not" && len(c.Args) != 1 {
			return Cond{}, false
		}
		return c, true
	case "using":
		if len(n.Attrs) != 1 || n.Attrs[0].Name.Local != "kind" {
			return Cond{}, false
		}
		or := Cond{Op: "or"}
		for alt := range strings.SplitSeq(n.Attrs[0].Value, "|") {
			mask := int32(0)
			for kind := range strings.SplitSeq(alt, ",") {
				mask |= kindMask(strings.TrimSpace(kind))
			}
			or.Args = append(or.Args, Cond{Op: "using", Mask: mask})
		}
		if len(or.Args) == 1 {
			return or.Args[0], true
		}
		return or, true
	}
	return Cond{}, false
}
