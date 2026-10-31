// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// The world's character race IDs are mapped explicitly, never exposed as class
// identifiers. Unsupported races and absent class-kind metadata remain unknown.
func appearance(s world.Self) wire.Field {
	race := wire.RaceUnknown
	switch s.Char.Race {
	case 0:
		race = wire.RaceHuman
	case 1:
		race = wire.RaceElf
	case 2:
		race = wire.RaceDarkElf
	case 3:
		race = wire.RaceKruvan
	case 4:
		race = wire.RaceDwarf
	}
	sex := wire.SexMale
	if s.Char.Female {
		sex = wire.SexFemale
	}
	branch := wire.BranchUnknown
	if s.ClassKindKnown {
		branch = wire.BranchFighter
		if s.Mage {
			branch = wire.BranchMystic
		}
	}
	return wire.Obj(wire.AppearanceField, false, []wire.Field{wire.U(1, true, uint64(race)), wire.U(2, true, uint64(sex)), wire.U(3, true, uint64(branch))})
}
