// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package stat

import (
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"math"
	"testing"
)

func TestOrderedFunctionsAndBase(t *testing.T) {
	p := Player{Level: 11, Template: catalog.PlayerTemplate{STR: 35}, Funcs: []catalog.ItemFunc{
		{Stat: "pAtk", Op: "set", Order: 8, Value: 10},
		{Stat: "pAtk", Op: "add", Order: 0x40, Value: 4},
	}, SkillFuncs: []catalog.ItemFunc{{Stat: "pAtk", Op: "basemul", Order: 0x40, Value: 0.5}}}
	want := 10*strBonus[35]*p.levelMod() + 4 + 5
	if got := p.calc(PAtk, 99); math.Abs(got-want) > 1e-10 {
		t.Fatalf("got %v want %v", got, want)
	}
}
func TestAbsentClassMetadataIsExplicit(t *testing.T) {
	p := Player{Level: 1, Template: catalog.PlayerTemplate{PDef: 72}, Worn: map[int32]catalog.ItemTemplate{catalog.SlotChest: {Kind: catalog.Armor, BodyPartName: "chest"}}}
	if p.PDefKnown() {
		t.Fatal("invented class metadata")
	}
	mage := false
	p.Mage = &mage
	if !p.PDefKnown() {
		t.Fatal("explicit metadata unknown")
	}
	if got := p.PDef(); got != 36 {
		t.Fatal(got)
	}
}
func TestArmorConditions(t *testing.T) {
	p := Player{Worn: map[int32]catalog.ItemTemplate{catalog.SlotChest: {Kind: catalog.Armor, BodyPartName: "chest", ArmorType: "light"}}}
	c := catalog.Cond{Op: "using", Kinds: []string{"light"}}
	if p.holds(c) {
		t.Fatal("light chest without legs")
	}
	p.Worn[catalog.SlotLegs] = catalog.ItemTemplate{Kind: catalog.Armor, ArmorType: "light"}
	if !p.holds(c) {
		t.Fatal("matching light armor")
	}
}
