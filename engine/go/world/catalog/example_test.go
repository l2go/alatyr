// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog_test

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Instances of an item merge into one stack only when its consume type allows
// it; equipment type checks match the template's own kind.
func ExampleItemTemplate() {
	coin := catalog.ItemTemplate{ID: 57, Kind: catalog.EtcItem, ConsumeType: "asset"}
	coat := catalog.ItemTemplate{ID: 23, Kind: catalog.Armor, ArmorType: "light", BodyPartName: "chest"}
	fmt.Println(coin.Stackable(), coat.Stackable())
	fmt.Println(coat.IsKind("light"), coat.BodyPart() == 0x0400)
	// Output:
	// true false
	// true true
}

// A category built outside the catalog has no selection weights and selects
// nothing; use the categories that Catalog.DropCategories returns.
func ExampleDropCategory_DropOne() {
	var hand catalog.DropCategory
	hand.Drops = []*catalog.Drop{{ItemID: 57, Min: 1, Max: 1, Chance: catalog.MaxDropChance}}
	fmt.Println(hand.DropOne(func(n int) int { return 0 }) == nil)
	// Output: true
}
