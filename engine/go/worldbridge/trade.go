// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func (t translator) trade(d world.Dialogue, list int32, sell bool, origin world.Time) ([]fact, error) {
	if !validRef(d.Speaker) || d.Page == "" || d.HTML == "" || len(d.Affordances) > maxRows || t.catalog == nil {
		return nil, errSource
	}
	kind := uint64(12)
	verb := world.VerbBuy
	if sell {
		kind = 13
		verb = world.VerbSell
	}
	rows := []wire.Value{}
	var issued world.Time
	seen := map[uint64]bool{}
	for _, a := range d.Affordances {
		offer, ok := a.Trade()
		if !ok || a.Verb != verb || a.ID == 0 || seen[a.ID] || a.Target != d.Speaker || offer.List != list || a.Issued < origin || a.Expires-a.Issued != world.Time(60_000_000_000) {
			return nil, errSource
		}
		seen[a.ID] = true
		if len(rows) == 0 || a.Issued < issued {
			issued = a.Issued
		}
		item, ok := t.catalog.Item(offer.Item)
		if !ok {
			return nil, errSource
		}
		name, err := text(6, item.Name, wire.ProvenanceAuthored)
		if err != nil {
			return nil, err
		}
		rows = append(rows, wire.Value{Fields: []wire.Field{wire.S(2, true, int64(offer.Object)), wire.S(3, true, int64(offer.Item)), wire.S(4, true, offer.Quantity), wire.S(5, true, offer.Price), name}})
	}
	fields := []wire.Field{wire.S(64, true, int64(d.Speaker.ID)), wire.S(22, true, int64(list)), wire.Items(24, true, rows), boolean(31, true)}
	if len(rows) == 0 {
		fields = append(fields, wire.S(74, true, 0))
	}
	if len(rows) > 0 {
		ms, err := elapsed(issued, origin)
		if err != nil {
			return nil, err
		}
		fields = append(fields, wire.S(74, true, ms))
	}
	return []fact{{kind, "trade", fields}}, nil
}
