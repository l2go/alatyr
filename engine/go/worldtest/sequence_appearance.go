// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// Read actual world views and public fields independently of the producer's
// translator, DTO encoder and capability classifier. No new opcode is needed.
func checkPlayerAppearances(records []wire.Record, scene world.Scene) error {
	players := map[int32]world.Self{scene.Self.Entity.ID: scene.Self}
	for _, o := range scene.Objects {
		if p, ok := o.State.(world.PlayerSeen); ok {
			players[p.Player.Entity.ID] = p.Player
		}
	}
	for _, r := range records {
		if r.Type != wire.Fact {
			continue
		}
		kind := gatewaytest.U(r, 1)
		role, _ := wire.Find(r.Fields, 11)
		ref, _ := wire.Find(r.Fields, 10)
		id, _ := wire.Find(ref.Fields, 1)
		a, present := wire.Find(r.Fields, wire.AppearanceField)
		player := kind == kindAppeared && string(role.Data) == "player"
		if player && !present {
			return fmt.Errorf("player %d has no appearance", id.S)
		}
		if !present {
			continue
		}
		if !player && (kind != kindVitals || int32(id.S) != scene.Self.Entity.ID) {
			return fmt.Errorf("appearance on a non-player fact")
		}
		if len(a.Fields) != 3 {
			return fmt.Errorf("appearance has incomplete or extra metadata")
		}
		for _, n := range []uint32{1, 2, 3} {
			if _, ok := wire.Find(a.Fields, n); !ok {
				return fmt.Errorf("appearance is missing member %d", n)
			}
		}
		p, known := players[int32(id.S)]
		if !known {
			continue
		} // A departed player's historical appearance was checked while visible.
		race := wire.RaceUnknown
		switch p.Char.Race {
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
		if p.Char.Female {
			sex = wire.SexFemale
		}
		branch := wire.BranchUnknown
		if p.ClassKindKnown {
			branch = wire.BranchFighter
			if p.Mage {
				branch = wire.BranchMystic
			}
		}
		for i, want := range []uint64{uint64(race), uint64(sex), uint64(branch)} {
			v, _ := wire.Find(a.Fields, uint32(i+1))
			if v.U != want {
				return fmt.Errorf("player %d appearance member %d=%d, world=%d", id.S, i+1, v.U, want)
			}
		}
	}
	return nil
}
