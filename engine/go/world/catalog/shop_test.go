// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"os"
	"path/filepath"
	"testing"
)

func TestMerchantCatalogValidatesAuthoredTerms(t *testing.T) {
	for _, tc := range []struct {
		name, shop, rows string
		valid            bool
	}{
		{"authored", "731,30002\n", "731,0,1060,12,,\n", true},
		{"negative-price", "731,30002\n", "731,0,1060,-1,,\n", false},
		{"unknown-template", "731,30002\n", "731,0,999,12,,\n", false},
		{"unknown-shop", "731,30002\n", "732,0,1060,12,,\n", false},
		{"wrong-role", "731,30001\n", "731,0,1060,12,,\n", false},
		{"duplicate-shop", "731,30002\n731,30002\n", "731,0,1060,12,,\n", false},
		{"duplicate-position", "731,30002\n", "731,0,1060,12,,\n731,0,1061,12,,\n", false},
		{"duplicate-item", "731,30002\n", "731,0,1060,12,,\n731,1,1060,12,,\n", false},
		{"profitable-cycle", "731,30002\n", "731,0,1060,5,,\n", false},
		{"currency-for-sale", "731,30002\n", "731,0,57,12,,\n", false},
		{"finite-stock", "731,30002\n", "731,0,1060,12,3,60\n", false},
		{"restock-without-stock", "731,30002\n", "731,0,1060,12,,60\n", false},
		{"empty-shop", "731,30002\n", "", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			root := t.TempDir()
			if err := os.Mkdir(filepath.Join(root, "catalog"), 0700); err != nil {
				t.Fatal(err)
			}
			for name, body := range map[string]string{"merchant_shop.csv": "id,npc_template_id\n" + tc.shop, "merchant_buylist.csv": "merchant_shop_id,position,item_template_id,price,stock_count,restock_interval_s\n" + tc.rows} {
				if err := os.WriteFile(filepath.Join(root, "catalog", name), []byte(body), 0600); err != nil {
					t.Fatal(err)
				}
			}
			c := &Catalog{items: map[int32]ItemTemplate{57: {ID: 57, ConsumeType: "asset"}, 1060: {ID: 1060, Price: 12, Sellable: true}, 1061: {ID: 1061, Price: 12, Sellable: true}}, npcs: map[int32]NPCTemplate{30002: {ID: 30002, Role: Merchant}, 30001: {ID: 30001, Role: Trainer}}}
			err := c.loadShops(root)
			if !tc.valid {
				if err == nil {
					t.Fatal("invalid merchant terms accepted")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			got, ok := c.Shop(731)
			if !ok || got.NPC != 30002 || len(got.Offers) != 1 || got.Offers[0].Price != 12 || c.Currency() != 57 {
				t.Fatalf("wrong authored terms: %+v", got)
			}
			got.Offers[0].Price = 999
			again, _ := c.Shop(731)
			if again.Offers[0].Price != 12 {
				t.Fatal("catalog terms alias caller storage")
			}
		})
	}
}
