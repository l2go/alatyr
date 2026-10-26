// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strconv"
)

// Shop is an authored merchant's ordered, unlimited-stock offer list.
// Finite stock is rejected at load time until a restocking policy is provided.
type Shop struct {
	ID, NPC int32
	Offers  []Offer
}

// Offer is one authored sale row. Price is per item without residence tax.
type Offer struct {
	Position, Item int32
	Price          int64
}

// Shop returns a detached offer list belonging to id.
func (c *Catalog) Shop(id int32) (Shop, bool) {
	v, ok := c.shops[id]
	v.Offers = slices.Clone(v.Offers)
	return v, ok
}

// Currency returns the unique authored asset template, or zero when absent.
func (c *Catalog) Currency() int32 { return c.currency }

func optionalPrice(r *row) int64 {
	if _, ok := r.cols["price"]; !ok {
		return 0
	}
	if r.str("price") == "" {
		return 0
	}
	return r.int64("price")
}
func optionalSellable(r *row) bool {
	if _, ok := r.cols["is_sellable"]; !ok {
		return false
	}
	return r.bool("is_sellable")
}

func (c *Catalog) loadShops(root string) error {
	c.shops = make(map[int32]Shop)
	for _, item := range c.items {
		if item.ConsumeType == "asset" {
			if c.currency != 0 {
				return errors.New("catalog: multiple currency templates")
			}
			c.currency = item.ID
		}
	}
	for _, table := range []struct {
		name string
		read func(row) error
	}{
		{"merchant_shop.csv", func(r row) error {
			id, npc := r.int("id"), r.int("npc_template_id")
			n, ok := c.npcs[npc]
			if r.err != nil {
				return r.err
			}
			if id <= 0 || !ok || n.Role != Merchant {
				return errors.New("invalid shop merchant")
			}
			if _, ok := c.shops[id]; ok {
				return errors.New("duplicate shop")
			}
			c.shops[id] = Shop{ID: id, NPC: npc}
			return nil
		}},
		{"merchant_buylist.csv", func(r row) error {
			id := r.int("merchant_shop_id")
			offer := Offer{Position: r.int("position"), Item: r.int("item_template_id"), Price: r.int64("price")}
			shop, ok := c.shops[id]
			item, known := c.items[offer.Item]
			stock, interval := r.str("stock_count"), r.str("restock_interval_s")
			if r.err != nil {
				return r.err
			}
			if stock != "" || interval != "" {
				return errors.New("finite merchant stock requires a restocking policy")
			}
			if !ok || !known || offer.Position < 0 || offer.Price <= 0 || item.ConsumeType == "asset" || c.currency == 0 {
				return errors.New("invalid merchant offer")
			}
			// Selling back to a merchant must never create currency.
			if item.Sellable && item.Price/2 > offer.Price {
				return errors.New("profitable merchant cycle")
			}
			for _, old := range shop.Offers {
				if old.Position == offer.Position || old.Item == offer.Item {
					return errors.New("duplicate merchant row")
				}
			}
			if len(shop.Offers) >= 128 {
				return errors.New("too many merchant rows")
			}
			shop.Offers = append(shop.Offers, offer)
			c.shops[id] = shop
			return nil
		}},
	} {
		path := filepath.Join(root, "catalog", table.name)
		if err := readCSV(path, table.read); err != nil {
			if errors.Is(err, os.ErrNotExist) && len(c.shops) == 0 {
				continue
			}
			return fmt.Errorf("catalog: %s: %w", table.name, err)
		}
	}
	for id, shop := range c.shops {
		if len(shop.Offers) == 0 {
			return errors.New("catalog: empty merchant shop " + strconv.Itoa(int(id)))
		}
		slices.SortFunc(shop.Offers, func(a, b Offer) int {
			if a.Position < b.Position {
				return -1
			}
			if a.Position > b.Position {
				return 1
			}
			return 0
		})
		c.shops[id] = shop
	}
	return nil
}
