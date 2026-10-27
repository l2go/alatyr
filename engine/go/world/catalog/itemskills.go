// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"errors"
	"strconv"
	"strings"
)

// SkillRef identifies an exact level of an authored item effect.
type SkillRef struct{ ID, Level int32 }

func itemSkills(r *row) []SkillRef {
	if _, ok := r.cols["item_skills"]; !ok {
		return nil
	}
	raw := strings.TrimSpace(r.str("item_skills"))
	if raw == "" {
		return nil
	}
	var out []SkillRef
	seen := make(map[SkillRef]bool)
	for part := range strings.SplitSeq(raw, ";") {
		id, level, ok := strings.Cut(strings.TrimSpace(part), "-")
		i, e1 := strconv.ParseInt(id, 10, 32)
		l, e2 := strconv.ParseInt(level, 10, 32)
		ref := SkillRef{int32(i), int32(l)}
		if !ok || e1 != nil || e2 != nil || i <= 0 || l <= 0 || seen[ref] {
			r.err = errors.New("invalid item skill reference")
			return nil
		}
		seen[ref] = true
		out = append(out, ref)
	}
	return out
}
