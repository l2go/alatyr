// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func merchantOffer(t *testing.T, r *rig, sell bool, item int32) world.Request {
	t.Helper()
	owner := r.port.Port.(*worldbridge.Client).Owner()
	target := r.actor(30002)
	c, err := r.w.Execute(t.Context(), world.Request{Owner: owner, Verb: world.VerbTalk, Target: target})
	if err != nil || c.Outcome.Stage != world.StageDone {
		t.Fatalf("merchant talk: %+v/%v", c, err)
	}
	scene, err := r.w.Capture(t.Context(), owner)
	if err != nil {
		t.Fatal(err)
	}
	index := 0
	if sell {
		index = 1
	}
	if len(scene.Dialogue.Affordances) <= index {
		t.Fatal("merchant choices missing")
	}
	c, err = r.w.Execute(t.Context(), world.Request{Owner: owner, Verb: world.VerbChoose, Target: target, AffordanceID: scene.Dialogue.Affordances[index].ID})
	if err != nil || c.Outcome.Stage != world.StageDone {
		t.Fatalf("merchant choice: %+v/%v", c, err)
	}
	scene, err = r.w.Capture(t.Context(), owner)
	if err != nil {
		t.Fatal(err)
	}
	for _, a := range scene.Dialogue.Affordances {
		if v, ok := a.Trade(); ok && v.Item == item {
			return world.Request{Owner: owner, Verb: a.Verb, Target: target, AffordanceID: a.ID, Quantity: 1}
		}
	}
	t.Fatalf("item %d absent in sale=%t offers", item, sell)
	return world.Request{}
}
func inventory(t *testing.T, r *rig) []world.Carried {
	t.Helper()
	s, err := r.w.Capture(t.Context(), r.port.Port.(*worldbridge.Client).Owner())
	if err != nil {
		t.Fatal(err)
	}
	return s.Inventory
}
func quantity(rows []world.Carried, id int32) int64 {
	var n int64
	for _, row := range rows {
		if row.Item.TemplateID == id {
			n += row.Item.Count
		}
	}
	return n
}

func TestMerchantAtomicBuySellAndPersistence(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, false, 1060)
		req.Quantity = 2
		c, err := r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageDone {
			t.Fatalf("buy: %+v/%v", c, err)
		}
		groups := 0
		for _, g := range c.Groups {
			if len(g.Effects) > 0 {
				groups++
				if len(g.Effects) != 2 {
					t.Fatal("currency/item were not one two-sided group")
				}
			}
		}
		if groups != 1 {
			t.Fatalf("atomic groups: got %d, want 1", groups)
		}
		rows := inventory(t, r)
		if quantity(rows, 57) != 976 || quantity(rows, 1060) != 5 {
			t.Fatalf("buy balances: %+v", rows)
		}
		before := rows
		c, err = r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageRefused || c.Outcome.Reason != world.ReasonStale || !reflect.DeepEqual(before, inventory(t, r)) {
			t.Fatal("committed offer replay mutated inventory")
		}
		sell := merchantOffer(t, r, true, 1060)
		sell.Quantity = 2
		c, err = r.w.Execute(t.Context(), sell)
		if err != nil || c.Outcome.Stage != world.StageDone {
			t.Fatalf("sell: %+v/%v", c, err)
		}
		rows = inventory(t, r)
		if quantity(rows, 57) != 988 || quantity(rows, 1060) != 3 {
			t.Fatalf("sell balances: %+v", rows)
		}
		if err := r.session.Close(); err != nil {
			t.Fatal(err)
		}
		chars, err := r.store.Characters(t.Context(), char.Account)
		if err != nil {
			t.Fatal(err)
		}
		if len(chars) != 1 {
			t.Fatal("saved character absent")
		}
		coin := int64(0)
		for _, it := range chars[0].Items {
			if it.TemplateID == 57 {
				coin = it.Count
			}
		}
		if coin != 988 {
			t.Fatalf("saved coin balance: got %d, want 988", coin)
		}
	})
}

func TestMerchantRefusalsAreAtomic(t *testing.T) {
	for _, name := range []string{"no-money", "quantity-zero", "quantity-negative", "quantity-cap", "load", "stack-overflow", "currency-overflow", "sell-too-many", "foreign-owner", "foreign-target", "expired", "replaced-offer", "inventory-cap"} {
		t.Run(name, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.Y = 258243
				sell := name == "currency-overflow" || name == "sell-too-many"
				if name == "load" {
					char.Items[0].Count = 2_000_000
				}
				if name == "no-money" {
					char.Items[0].Count = 1
				}
				if name == "currency-overflow" {
					char.Items[0].Count = math.MaxInt64
				}
				if name == "stack-overflow" {
					char.Items[2].Count = math.MaxInt64
				}
				r := newRig(t, cat, char, allSources(), nil)
				item := int32(1060)
				if name == "inventory-cap" {
					item = 4
				}
				req := merchantOffer(t, r, sell, item)
				switch name {
				case "inventory-cap":
					req.Quantity = 128
				case "quantity-zero":
					req.Quantity = 0
				case "quantity-negative":
					req.Quantity = -1
				case "quantity-cap":
					req.Quantity = 1_000_001
				case "load":
					req.Quantity = 100000
				case "sell-too-many":
					req.Quantity = 4
				case "foreign-owner":
					req.Owner.Gen++
				case "foreign-target":
					req.Target.Gen++
				case "expired":
					for range 601 {
						r.advance(100 * time.Millisecond)
					}
				case "replaced-offer":
					merchantOffer(t, r, false, 1060)
				}
				before := inventory(t, r)
				c, err := r.w.Execute(t.Context(), req)
				if err != nil || c.Outcome.Stage != world.StageRefused || len(c.Groups) != 0 || !reflect.DeepEqual(before, inventory(t, r)) {
					t.Fatalf("%s: got %+v/%v; balances must be unchanged", name, c, err)
				}
			})
		})
	}
}

func TestMerchantNonstackableAndSaleEligibility(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243
		// Bound Wattlekeeper gear and equipped items cannot become merchant sale rows.
		char.Items = append(char.Items, world.Item{ObjectID: 13, TemplateID: 1, Count: 1, Slot: -1})
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, false, 4)
		req.Quantity = 2
		c, err := r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageDone {
			t.Fatalf("nonstackable buy: %+v/%v", c, err)
		}
		rows := inventory(t, r)
		if quantity(rows, 4) != 3 || quantity(rows, 57) != 828 {
			t.Fatalf("nonstack balances: %+v", rows)
		}
		ids := map[int32]bool{}
		for _, row := range rows {
			if ids[row.Item.ObjectID] {
				t.Fatal("inventory identity reused")
			}
			ids[row.Item.ObjectID] = true
			if row.Item.TemplateID == 4 && row.Item.Count != 1 {
				t.Fatal("nonstackable quantity merged")
			}
		}
		merchantOffer(t, r, true, 1060)
		scene, err := r.w.Capture(t.Context(), req.Owner)
		if err != nil {
			t.Fatal(err)
		}
		for _, a := range scene.Dialogue.Affordances {
			v, ok := a.Trade()
			if !ok {
				t.Fatal("untyped trade row")
			}
			if v.Item == 1 || v.Item == 57 || v.Object == 11 {
				t.Fatalf("ineligible sale row: %+v", v)
			}
		}
	})
}

func TestMerchantConcurrentOfferExecutesOnce(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, false, 1060)
		type answer struct {
			c   world.Commit
			err error
		}
		replies := make(chan answer, 2)
		start := make(chan struct{})
		for range 2 {
			go func() { <-start; c, err := r.w.Execute(t.Context(), req); replies <- answer{c, err} }()
		}
		close(start)
		done, refused := 0, 0
		for range 2 {
			reply := <-replies
			if reply.err != nil {
				t.Fatal(reply.err)
			}
			switch reply.c.Outcome.Stage {
			case world.StageDone:
				done++
			case world.StageRefused:
				refused++
			}
		}
		if done != 1 || refused != 1 {
			t.Fatalf("concurrent offer: done/refused=%d/%d, want 1/1", done, refused)
		}
		rows := inventory(t, r)
		if quantity(rows, 57) != 988 || quantity(rows, 1060) != 4 {
			t.Fatalf("offer executed more than once: %+v", rows)
		}
	})
}

func TestMerchantPriceMultiplicationOverflowIsAtomic(t *testing.T) {
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	file := filepath.Join(root, "catalog", "merchant_buylist.csv")
	b, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	b = bytes.Replace(b, []byte("731,0,1060,12,,"), []byte("731,0,1060,9223372036854775807,,"), 1)
	if err := os.WriteFile(file, b, 0600); err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		char := readyCharacter()
		char.Y = 258243
		char.Items[0].Count = math.MaxInt64
		r := newRig(t, cat, char, allSources(), nil)
		req := merchantOffer(t, r, false, 1060)
		req.Quantity = 2
		before := inventory(t, r)
		c, err := r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageRefused || c.Outcome.Reason != world.ReasonInvalid || len(c.Groups) != 0 || !reflect.DeepEqual(before, inventory(t, r)) {
			t.Fatalf("price multiplication overflow: %+v/%v", c, err)
		}
	})
}
