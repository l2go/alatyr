// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

// AppearanceField is the optional alatyr/1 Fact object describing a player.
// It carries race (1), sex (2) and base branch (3), never a numeric class ID.
const AppearanceField uint32 = 35

// Race is a player's authored people, independent of capabilities.
type Race uint64

// Player races. Unknown is explicit, not an absent appearance report.
const (
	RaceUnknown Race = iota
	RaceHuman
	RaceElf
	RaceDarkElf
	RaceKruvan
	RaceDwarf
)

// Sex is a player's reported sex, independent of capabilities.
type Sex uint64

// Player sexes. Unknown is explicit, not an absent appearance report.
const (
	SexUnknown Sex = iota
	SexMale
	SexFemale
)

// Branch is the base visual branch, never a class or permission identifier.
type Branch uint64

// Base branches. Missing class-kind metadata must remain BranchUnknown.
const (
	BranchUnknown Branch = iota
	BranchFighter
	BranchMystic
)

// AppearanceSpec returns an independent optional object specification. All three
// members are required when present and accept only their closed vocabularies.
// The outer field is nonessential so older codecs can skip the whole report.
func AppearanceSpec() Spec {
	race, sex, branch := Required(UInt), Required(UInt), Required(UInt)
	race.Enum = []uint64{uint64(RaceUnknown), uint64(RaceHuman), uint64(RaceElf), uint64(RaceDarkElf), uint64(RaceKruvan), uint64(RaceDwarf)}
	sex.Enum = []uint64{uint64(SexUnknown), uint64(SexMale), uint64(SexFemale)}
	branch.Enum = []uint64{uint64(BranchUnknown), uint64(BranchFighter), uint64(BranchMystic)}
	return Spec{Shape: Object, Object: Schema{1: race, 2: sex, 3: branch}}
}
