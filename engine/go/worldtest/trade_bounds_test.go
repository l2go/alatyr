// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"testing"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestMerchantFinalInventoryRows(t *testing.T) {
	for _, tc := range []struct {
		name                  string
		sell                  bool
		item                  int32
		rows, finalRows       int
		coins, held, quantity int64
	}{
		{"new-stack-127-last-coin", false, 1060, 127, 127, 12, 0, 1},
		{"new-stack-128-last-coin", false, 1060, 128, 128, 12, 0, 1},
		{"new-stack-129-coin-remains", false, 1060, 128, 129, 13, 0, 1},
		{"existing-stack-128", false, 1060, 128, 128, 25, 1, 2},
		{"nonstack-127-last-coin", false, 4, 127, 127, 86, 0, 1},
		{"nonstack-128-last-coin", false, 4, 128, 128, 86, 0, 1},
		{"nonstack-two-128-last-coin", false, 4, 127, 128, 172, 0, 2},
		{"nonstack-two-129-last-coin", false, 4, 128, 129, 172, 0, 2},
		{"nonstack-128-coin-remains", false, 4, 127, 128, 87, 0, 1},
		{"nonstack-129-coin-remains", false, 4, 128, 129, 87, 0, 1},
		{"sell-stack-whole-127-new-coin", true, 1060, 127, 127, 0, 3, 3},
		{"sell-stack-whole-128-new-coin", true, 1060, 128, 128, 0, 3, 3},
		{"sell-stack-partial-128-new-coin", true, 1060, 127, 128, 0, 3, 2},
		{"sell-stack-partial-129-new-coin", true, 1060, 128, 129, 0, 3, 2},
		{"sell-stack-whole-129-to-128", true, 1060, 129, 128, 1, 3, 3},
		{"sell-nonstack-whole-127-new-coin", true, 4, 127, 127, 0, 1, 1},
		{"sell-nonstack-whole-128-new-coin", true, 4, 128, 128, 0, 1, 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.Y = 258243
				char.Items = nil
				add := func(template int32, count int64) {
					char.Items = append(char.Items, world.Item{ObjectID: int32(10 + len(char.Items)), TemplateID: template, Count: count, Slot: -1})
				}
				if tc.coins > 0 {
					add(57, tc.coins)
				}
				if tc.held > 0 {
					add(tc.item, tc.held)
				}
				// Authored, light enough even at 129 rows, with unique object IDs.
				// Keep a bought stack absent unless the case explicitly supplies it.
				filler := int32(4)
				if tc.item == 4 {
					filler = 1060
				}
				for len(char.Items) < tc.rows {
					add(filler, 1)
				}
				r := newRig(t, cat, char, allSources(), nil)
				req := merchantOffer(t, r, tc.sell, tc.item)
				req.Quantity = tc.quantity
				before := inventory(t, r)
				price := int64(12)
				if tc.item == 4 {
					price = 86
				}
				wantCoins, wantItems := tc.coins-price*tc.quantity, tc.held+tc.quantity
				if tc.sell {
					wantCoins, wantItems = tc.coins+price/2*tc.quantity, tc.held-tc.quantity
				}
				if wantCoins < 0 || len(before) != tc.rows {
					t.Fatalf("invalid boundary setup: coins=%d rows=%d", wantCoins, len(before))
				}
				c, err := r.w.Execute(t.Context(), req)
				after := inventory(t, r)
				if tc.finalRows > 128 {
					if err != nil || c.Outcome.Stage != world.StageRefused || c.Outcome.Reason != world.ReasonState || len(c.Groups) != 0 || !reflect.DeepEqual(before, after) {
						t.Fatalf("final rows %d must refuse atomically: %+v/%v", tc.finalRows, c, err)
					}
					return
				}
				if err != nil || c.Outcome.Stage != world.StageDone || len(after) != tc.finalRows || quantity(after, 57) != wantCoins || quantity(after, tc.item) != wantItems {
					t.Fatalf("trade: %+v/%v; rows/coins/items=%d/%d/%d, want %d/%d/%d", c, err, len(after), quantity(after, 57), quantity(after, tc.item), tc.finalRows, wantCoins, wantItems)
				}
				ids := make(map[int32]bool)
				for _, row := range after {
					if row.Item.Count <= 0 || row.Item.ObjectID <= 0 || ids[row.Item.ObjectID] || row.Entity.ID != row.Item.ObjectID || row.Entity.Gen == 0 {
						t.Fatalf("invalid committed inventory identity or count: %+v", row)
					}
					ids[row.Item.ObjectID] = true
				}
			})
		})
	}
}

func TestMerchantFinalLoadBoundary(t *testing.T) {
	// Human Fighter CON 39 multiplies the base 69000 by 1.40: 96600.
	for _, qty := range []int64{8011, 8012, 8013} {
		t.Run(strconv.FormatInt(qty, 10), func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.Y = 258243
				char.Items[0].Count = 12 * qty
				r := newRig(t, cat, char, allSources(), nil)
				req := merchantOffer(t, r, false, 1060)
				req.Quantity = qty
				before := inventory(t, r)
				c, err := r.w.Execute(t.Context(), req)
				if qty > 8012 {
					if err != nil || c.Outcome.Stage != world.StageRefused || len(c.Groups) != 0 || !reflect.DeepEqual(before, inventory(t, r)) {
						t.Fatalf("load above 96600 must refuse atomically: %+v/%v", c, err)
					}
				} else if err != nil || c.Outcome.Stage != world.StageDone || quantity(inventory(t, r), 1060) != 3+qty || quantity(inventory(t, r), 57) != 0 {
					t.Fatalf("load %d must fit: %+v/%v", 456+12*qty, c, err)
				}
			})
		})
	}
}

// tradeCatalog changes only explicit fixture values; authored files stay intact.
func tradeCatalog(t *testing.T, old, replacement string) *catalog.Catalog {
	t.Helper()
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	file := filepath.Join(root, "catalog", "etc_item_template.csv")
	b, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Count(b, []byte(old)) != 1 {
		t.Fatal("trade fixture replacement must match exactly once")
	}
	if err := os.WriteFile(file, bytes.Replace(b, []byte(old), []byte(replacement), 1), 0600); err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	return cat
}

func TestMerchantPaymentFreesWeight(t *testing.T) {
	cat := tradeCatalog(t, "bronze,0,,,1,", "bronze,12,,,1,")
	synctest.Test(t, func(t *testing.T) {
		char := readyCharacter()
		char.Y = 258243
		char.Items = []world.Item{{ObjectID: 10, TemplateID: 57, Count: 12, Slot: -1}, {ObjectID: 11, TemplateID: 1060, Count: 8040, Slot: -1}}
		// Before: 96624, above capacity. Payment removes 144; goods add 12.
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, false, 1060)
		c, err := r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageDone || quantity(inventory(t, r), 57) != 0 || quantity(inventory(t, r), 1060) != 8041 {
			t.Fatalf("final load 96492 must fit after payment: %+v/%v", c, err)
		}
	})
}

func TestMerchantZeroPriceSaleRemovesRow(t *testing.T) {
	cat := tradeCatalog(t, "liquid,12,,,12,", "liquid,12,,,1,")
	synctest.Test(t, func(t *testing.T) {
		char := readyCharacter()
		char.Y = 258243
		char.Items = []world.Item{{ObjectID: 10, TemplateID: 1060, Count: 1, Slot: -1}}
		for i := range 127 {
			char.Items = append(char.Items, world.Item{ObjectID: int32(11 + i), TemplateID: 4, Count: 1, Slot: -1})
		}
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, true, 1060)
		c, err := r.w.Execute(t.Context(), req)
		rows := inventory(t, r)
		if err != nil || c.Outcome.Stage != world.StageDone || len(rows) != 127 || quantity(rows, 57) != 0 || quantity(rows, 1060) != 0 {
			t.Fatalf("zero-price sale must remove goods without adding currency: %+v/%v; rows=%+v", c, err, rows)
		}
	})
}

func TestMerchantCountAndQuantityBoundaries(t *testing.T) {
	// Zero weight isolates count arithmetic and the offer's request quantity cap.
	cat := tradeCatalog(t, "liquid,12,,,12,", "liquid,0,,,12,")
	for _, tc := range []struct {
		name                   string
		sell                   bool
		coins, items, quantity int64
		stage                  world.Stage
	}{
		{"stack-last-unit", false, 12, math.MaxInt64 - 1, 1, world.StageDone},
		{"stack-overflow", false, 12, math.MaxInt64, 1, world.StageRefused},
		{"coin-last-unit", true, math.MaxInt64 - 6, 1, 1, world.StageDone},
		{"coin-overflow", true, math.MaxInt64 - 5, 1, 1, world.StageRefused},
		{"quantity-below-cap", false, 12_000_000, 1, 999_999, world.StageDone},
		{"quantity-at-cap", false, 12_000_000, 1, 1_000_000, world.StageDone},
		{"quantity-above-cap", false, 12_000_012, 1, 1_000_001, world.StageRefused},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				char := readyCharacter()
				char.Y = 258243
				char.Items[0].Count, char.Items[2].Count = tc.coins, tc.items
				r := newRig(t, cat, char, allSources(), nil)
				req := merchantOffer(t, r, tc.sell, 1060)
				req.Quantity = tc.quantity
				before := inventory(t, r)
				c, err := r.w.Execute(t.Context(), req)
				after := inventory(t, r)
				if err != nil || c.Outcome.Stage != tc.stage {
					t.Fatalf("count boundary: %+v/%v, want %v", c, err, tc.stage)
				}
				if tc.stage == world.StageRefused {
					if len(c.Groups) != 0 || !reflect.DeepEqual(before, after) {
						t.Fatal("refused count boundary changed inventory")
					}
					return
				}
				wantCoins, wantItems := tc.coins-12*tc.quantity, tc.items+tc.quantity
				if tc.sell {
					wantCoins, wantItems = tc.coins+6*tc.quantity, tc.items-tc.quantity
				}
				if quantity(after, 57) != wantCoins || quantity(after, 1060) != wantItems {
					t.Fatalf("committed counts: %+v, want %d/%d", after, wantCoins, wantItems)
				}
			})
		})
	}
}
