// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestSequenceAppearanceOracleRejectsMutations(t *testing.T) {
	scene := world.Scene{Self: world.Self{Entity: world.Ref{ID: 1, Gen: 1}, Char: world.Character{ObjectID: 1, Race: 0, Female: true}, ClassKindKnown: false, Mage: true}}
	for _, name := range []string{"valid-unknown", "missing", "race", "sex", "guessed-branch", "missing-member", "class-id", "npc"} {
		t.Run(name, func(t *testing.T) {
			fs := []wire.Field{wire.U(1, true, uint64(wire.RaceHuman)), wire.U(2, true, uint64(wire.SexFemale)), wire.U(3, true, uint64(wire.BranchUnknown))}
			switch name {
			case "race":
				fs[0] = wire.U(1, true, uint64(wire.RaceElf))
			case "sex":
				fs[1] = wire.U(2, true, uint64(wire.SexMale))
			case "guessed-branch":
				fs[2] = wire.U(3, true, uint64(wire.BranchMystic))
			case "missing-member":
				fs = fs[:2]
			case "class-id":
				fs = append(fs, wire.U(4, false, 123))
			}
			role := "player"
			if name == "npc" {
				role = "creature"
			}
			r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, kindAppeared), wire.Obj(10, true, []wire.Field{wire.S(1, true, 1), wire.U(2, true, 1)}), wire.Data(11, true, role)}}
			if name != "missing" {
				r.Fields = append(r.Fields, wire.Obj(wire.AppearanceField, false, fs))
			}
			err := checkPlayerAppearances([]wire.Record{r}, scene)
			if (err == nil) != (name == "valid-unknown") {
				t.Fatalf("%s: %v", name, err)
			}
		})
	}
}
