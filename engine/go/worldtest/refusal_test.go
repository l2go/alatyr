// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"reflect"
	"sync/atomic"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestG11WorldItemRefusalPreservesTradeClass(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.CurHP = 20
		r := newRig(t, cat, char, allSources(), nil)
		use := func(n uint64) { r.p.Send(r.intent(n, verbUseItem, wire.S(21, true, 12))) }
		use(1)
		r.p.Result(1, wire.StageSent)
		r.advance(500 * time.Millisecond)
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		before := inventory(t, r)
		use(2)
		r.expect(2, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone, 0)
		if !reflect.DeepEqual(before, inventory(t, r)) || quantity(before, 1060) != 2 {
			t.Fatal("cooldown refusal consumed another item")
		}
		if commands := r.port.commands(); len(commands) != 2 || commands[1].Verb != verbUseItem {
			t.Fatal("refusal did not reach the world")
		}
		r.advance(7 * time.Second)
		use(3)
		r.p.Result(3, wire.StageSent)
		r.advance(500 * time.Millisecond)
		r.expect(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		if quantity(inventory(t, r), 1060) != 1 {
			t.Fatal("fresh use did not consume exactly once")
		}
	})
}

func TestWorldBuyRefusalPreservesTradeClass(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243
		r := newRig(t, cat, char, allSources(), nil)
		target := r.actor(30002)
		r.p.Send(r.intent(1, verbTalk, r.ref(target.ID, 10)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.choose(2, 0)
		r.expect(2, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		d, err := meaning.EventValue(r.p.Fact(kindShop))
		if err != nil {
			t.Fatal(err)
		}
		index := -1
		for i, g := range d.Event.Goods {
			if g.ID == 1060 {
				index = i
			}
		}
		if index < 0 {
			t.Fatal("shop fixture lacks healing item")
		}
		before := inventory(t, r)
		r.p.Send(r.intent(3, verbBuy, wire.S(14, true, int64(d.Event.List)), wire.U(15, true, d.Context.Offers[index]), wire.S(16, true, 100), wire.S(17, true, d.Event.Goods[index].Price)))
		r.expect(3, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone, 0)
		if commands := r.port.commands(); len(commands) != 3 || commands[2].Verb != verbBuy {
			t.Fatal("purchase was not refused after handoff")
		}
		if !reflect.DeepEqual(before, inventory(t, r)) {
			t.Fatal("refused purchase changed inventory or wallet")
		}
		// Equipment shares trade with purchases, and has a live causal witness.
		r.p.Send(r.intent(4, verbUseItem, wire.S(21, true, 11)))
		r.expect(4, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	})
}

func TestUnknownAfterContactStillTaintsTrade(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		var lost atomic.Bool
		lost.Store(true)
		filter := func(u bridge.Update) bridge.Update {
			if lost.Load() && u.Kind == bridge.LiveBatch {
				u.Batch.Records = nil
				u.Batch.ReceivedAt = nil
				u.Batch.Receipts = nil
			}
			return u
		}
		r := newRig(t, cat, readyCharacter(), allSources(), filter)
		r.p.Send(r.intent(1, verbUseItem, wire.S(21, true, 11)))
		r.p.Result(1, wire.StageSent)
		r.advance(5 * time.Second)
		r.expect(1, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
		lost.Store(false)
		r.p.Send(r.intent(2, verbUseItem, wire.S(21, true, 11)))
		r.p.Result(2, wire.StageSent)
		r.sync()
		for _, r := range r.p.Records {
			if r.Type == wire.Result && gatewaytest.U(r, 1) == 2 && gatewaytest.U(r, 2) == uint64(wire.StageDone) {
				t.Fatal("true Unknown lost its class taint")
			}
		}
		r.advance(5 * time.Second)
		r.expect(2, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
		if len(r.port.commands()) != 2 {
			t.Fatal("second action did not test live world evidence")
		}
	})
}
