// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestBridgeTradePacketsCaptureCompleteOffersWithoutOptionalFlag(t *testing.T) {
	for _, tc := range []struct {
		name       string
		kind, verb uint64
		list       int32
	}{{"buy", 12, 7, 731}, {"sell", 13, 8, 0}} {
		for _, flag := range []int{-1, 0, 1} {
			name := tc.name + map[int]string{-1: "/ordinary", 0: "/explicit-incomplete", 1: "/explicit-complete"}[flag]
			t.Run(name, func(t *testing.T) {
				// The exact list shape emitted by Java BuyList/SellList, including birth.
				fields := []wire.Field{wire.U(1, true, tc.kind), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "trade"), wire.U(6, true, 1), wire.S(22, true, int64(tc.list)), wire.S(23, true, 1000), wire.S(74, true, 0), wire.Items(24, true, []wire.Value{{Fields: []wire.Field{wire.S(2, true, 100), wire.S(3, true, 1060), wire.S(4, true, 3), wire.S(5, true, 12)}}})}
				if flag >= 0 {
					fields = append(fields, wire.U(31, true, uint64(flag)))
				}
				e, err := event(wire.Record{Type: wire.Fact, Fields: fields})
				if err != nil {
					t.Fatal(err)
				}
				if e.Complete != (flag != 0) {
					t.Fatalf("complete=%t for flag %d", e.Complete, flag)
				}
				p, err := meaning.NewProposals(gatewaytest.Config())
				if err != nil {
					t.Fatal(err)
				}
				ctx, err := p.Capture(e, meaning.Ref{}, e.Complete, 0)
				if err != nil {
					t.Fatal(err)
				}
				rec := wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, tc.verb), wire.S(14, true, int64(tc.list)), wire.U(15, true, ctx.Offers[0]), wire.S(16, true, 1), wire.S(17, true, 12)}}
				plan, reason := meaning.PlanIntent(rec, meaning.Environment{Alive: true, Proposals: p})
				if flag == 0 {
					if reason != wire.ReasonStaleOffer {
						t.Fatalf("incomplete list gained authority: %v", reason)
					}
					return
				}
				if reason != wire.ReasonNone || plan.Command.Item != 1060 || plan.Command.Count != 1 {
					t.Fatalf("ordinary complete list refused: %+v/%v", plan, reason)
				}
			})
		}
	}
}

func TestBridgeTradeCompletenessDoesNotPromoteInventoryOrMissingRows(t *testing.T) {
	for _, kind := range []uint64{12, 14} {
		domain := "trade"
		if kind == 14 {
			domain = "inventory"
		}
		fields := []wire.Field{wire.U(1, true, kind), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, domain), wire.U(6, true, 1)}
		if kind == 14 {
			fields = append(fields, wire.Items(24, true, []wire.Value{}))
		}
		e, err := event(wire.Record{Type: wire.Fact, Fields: fields})
		if err == nil && e.Complete {
			t.Fatalf("kind %d gained unsupported completeness", kind)
		}
	}
}
