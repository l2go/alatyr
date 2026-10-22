// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"slices"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Negotiate selects the highest supported version inside a valid peer range.
// Accepted versions and dictionaries are configuration, not package defaults.
func Negotiate(hello wire.Record, versions []uint64, meanings []string) (uint64, string, error) {
	if hello.Type != wire.Hello {
		return 0, "", wire.ErrFrame
	}
	lo, hasLo := wire.Find(hello.Fields, 1)
	hi, hasHi := wire.Find(hello.Fields, 2)
	meaning, hasMeaning := wire.Find(hello.Fields, 3)
	if !hasLo || !hasHi || !hasMeaning || lo.U > hi.U {
		return 0, "", wire.ErrFrame
	}
	name := string(meaning.Data)
	if !slices.Contains(meanings, name) {
		return 0, "", wire.ErrUnsupported
	}
	var selected uint64
	for _, v := range versions {
		if v != 0 && v >= lo.U && v <= hi.U && v > selected {
			selected = v
		}
	}
	if selected == 0 {
		return 0, "", wire.ErrUnsupported
	}
	return selected, name, nil
}
