// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/xml"
	"math"
	"path/filepath"
	"runtime"
	"testing"
)

func TestAuthoredGameplayCatalog(t *testing.T) {
	_, file, _, _ := runtime.Caller(0)
	// Read the repository's CC-BY-SA-4.0 world, without fixture copies.
	c, err := Load(filepath.Join(filepath.Dir(file), "../../../../world"))
	if err != nil {
		t.Fatal(err)
	}
	if len(c.npcs) != 5 || len(c.Spawns("WORLD")) != 74 {
		t.Fatal("authored templates and spawns")
	}
	if !c.CanTeach(30001, 0) || c.CanTeach(30001, 10) {
		t.Fatal("invented teacher ancestry")
	}
	list := c.AvailableSkills(0, 2, func(int32) int32 { return -1 })
	if len(list) != 2 || list[0].SkillID != 3 || list[1].SkillID != 257 {
		t.Fatal("authored direct skill tree")
	}
	strike, ok := c.Skill(3, 2)
	if !ok || strike.Power != 46 || strike.MPConsume != 6 || strike.HitTime != 650 {
		t.Fatal("authored active skill")
	}
	if _, ok := c.Skill(3, 999); ok {
		t.Fatal("invented fallback level")
	}
	practice, ok := c.Skill(257, 1)
	if !ok || len(practice.Funcs) != 1 {
		t.Fatal("passive functions")
	}
	practice.Funcs[0].Value = 999
	if c.PassiveFuncs(257, 1)[0].Value != 4 {
		t.Fatal("mutable passive catalog")
	}
	drops := c.DropCategories(18003)
	if len(drops) != 3 {
		t.Fatal("authored drop categories")
	}
	drops[0].Drops[0].Chance = 0
	if c.DropCategories(18003)[0].Drops[0].Chance == 0 {
		t.Fatal("mutable drop catalog")
	}
	if !c.ZoneAt(-71057, 257883, -3104).Peace {
		t.Fatal("authored peaceful zone")
	}
	if _, ok := c.HTML("data/html/trainer/30001.htm"); !ok {
		t.Fatal("authored trainer page")
	}
	restart, ok := c.RestartAt(-71057, 257883, -3104)
	if !ok || restart.ID != 731 || len(restart.Normal) != 1 || len(restart.Chaotic) != 1 {
		t.Fatal("authored explicit restart")
	}
	restart.Normal[0].X = 0
	original, _ := c.RestartAt(-71057, 257883, -3104)
	if original.Normal[0].X == 0 {
		t.Fatal("mutable restart points")
	}
	if _, ok := c.RestartAt(math.MinInt32, math.MinInt32, 0); ok {
		t.Fatal("invented restart fallback")
	}
}
func TestSkillXMLRejectsInvalidAndCopiesFunctions(t *testing.T) {
	for _, attrs := range []string{`id="0" levels="1"`, `id="9001" levels="0"`, `id="9001" levels="1001"`} {
		var node xmlNode
		xml.Unmarshal([]byte("<skill "+attrs+"/>"), &node)
		if _, _, err := parseSkill(node); err == nil {
			t.Fatal("accepted invalid skill shape")
		}
	}
	for _, value := range []string{"NaN", "1e50", "-1"} {
		_, err := skillOf(9001, 1, map[string]string{"operateType": "OP_ACTIVE", "skillType": "PDAM", "target": "TARGET_ONE", "power": value})
		if err == nil {
			t.Fatal("accepted invalid skill power")
		}
	}
	if _, err := skillOf(9001, 1, map[string]string{"operateType": "OP_ACTIVE", "skillType": "PDAM", "target": "TARGET_ONE", "weaponsAllowed": "2"}); err == nil {
		t.Fatal("accepted numeric weapon table")
	}
	c := &Catalog{skills: map[int32]skillLevels{9001: {levels: map[int32]Skill{1: {WeaponsAllowed: []string{"synthetic"}, Funcs: []ItemFunc{{Cond: &Cond{Op: "using", Kinds: []string{"synthetic"}}}}}}}}}
	copy, _ := c.Skill(9001, 1)
	copy.WeaponsAllowed[0] = "changed"
	copy.Funcs[0].Cond.Kinds[0] = "changed"
	original, _ := c.Skill(9001, 1)
	if original.WeaponsAllowed[0] != "synthetic" || original.Funcs[0].Cond.Kinds[0] != "synthetic" {
		t.Fatal("mutable skill accessor")
	}
}
func TestDropSelectionBoundary(t *testing.T) {
	first := &Drop{ItemID: 9001, Chance: 1}
	last := &Drop{ItemID: 9002, Chance: 1}
	category := DropCategory{Drops: []*Drop{first, last}, balanced: 2}
	if category.DropOne(func(int) int { return 0 }) != first || category.DropOne(func(int) int { return 1 }) != last {
		t.Fatal("off-by-one category selection")
	}
}
func TestZoneGeometryExtremeCoordinates(t *testing.T) {
	shape := zoneShape{kind: "Cylinder", x: []int32{math.MaxInt32}, y: []int32{0}, radiusSq: 100}
	if shape.contains(math.MinInt32, 0, 0) || !shape.contains(math.MaxInt32-5, 0, 0) {
		t.Fatal("overflow in cylinder distance")
	}
	shape = zoneShape{kind: "Poly", x: []int32{math.MinInt32, math.MaxInt32, math.MaxInt32, math.MinInt32}, y: []int32{math.MinInt32, math.MinInt32, math.MaxInt32, math.MaxInt32}}
	if !shape.contains(0, 0, 0) {
		t.Fatal("overflow in polygon intersection")
	}
}
