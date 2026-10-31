// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestPlayerAppearanceOnFullSourceDescriptions(t *testing.T) {
	tr, s := fixture()
	s.Self.Char.Race = 3
	s.Self.Char.Female = true
	s.Self.ClassKindKnown = false
	s.Self.Mage = true
	for _, e := range []world.Event{world.SelfChanged{Self: s.Self}, world.PlayerSeen{Player: s.Self}} {
		facts, err := tr.events(e, s, 0)
		if err != nil {
			t.Fatal(err)
		}
		for _, f := range facts {
			a, present := wire.Find(f.fields, wire.AppearanceField)
			if !present || len(a.Fields) != 3 {
				t.Fatalf("kind %d lost appearance or exposed extra data", f.kind)
			}
			for i, want := range []uint64{uint64(wire.RaceKruvan), uint64(wire.SexFemale), uint64(wire.BranchUnknown)} {
				v, ok := wire.Find(a.Fields, uint32(i+1))
				if !ok || v.U != want {
					t.Fatalf("kind %d member %d: %+v want %d", f.kind, i+1, v, want)
				}
			}
		}
	}
	n := s.Objects[0].State.(world.NPCSeen).NPC
	f, err := tr.npc(n, s.Self.Char.ClassID)
	if err != nil {
		t.Fatal(err)
	}
	if _, yes := wire.Find(f.fields, wire.AppearanceField); yes {
		t.Fatal("NPC got player appearance")
	}
	s.Self.Char.Race = -1
	facts, err := tr.self(s.Self)
	if err != nil {
		t.Fatal(err)
	}
	a, _ := wire.Find(facts[0].fields, wire.AppearanceField)
	race, _ := wire.Find(a.Fields, 1)
	if race.U != uint64(wire.RaceUnknown) {
		t.Fatal("unsupported race guessed")
	}
}
