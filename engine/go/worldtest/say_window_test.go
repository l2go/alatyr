// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestG01SayWindowWitness(t *testing.T) {
	for _, tc := range []struct {
		name   string
		npc    int32
		choice int
		kind   uint64
	}{
		{"shop", 30002, 0, 12}, {"offer", 30002, 1, 13}, {"practice", 30001, 0, 22},
	} {
		t.Run(tc.name, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				if tc.npc == 30002 {
					char.Y = 258243
				}
				r := newRig(t, cat, char, allSources(), nil)
				npc := r.actor(tc.npc)
				r.p.Send(r.intent(1, 5, r.ref(npc.ID, 10)))
				r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				r.choose(2, tc.choice)
				r.expect(2, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				fact := r.p.Fact(tc.kind)
				if gatewaytest.U(fact, 32) != 2 {
					t.Fatal("window missing choice cause")
				}
				window, result := -1, -1
				for i, rec := range r.p.Records {
					if rec.Type == wire.Fact && gatewaytest.U(rec, 32) == 2 {
						window = i
					}
					if rec.Type == wire.Result && gatewaytest.U(rec, 1) == 2 && gatewaytest.U(rec, 2) == uint64(wire.StageDone) {
						result = i
					}
					if rec.Type == wire.Fact {
						if _, ok := wire.Find(rec.Fields, 75); ok {
							t.Fatal("raw key leaked")
						}
					}
				}
				if window < 0 || result <= window {
					t.Fatalf("window/result order=%d/%d", window, result)
				}
			})
		})
	}
}

func TestSayWindowRejectsUnattributedEvidence(t *testing.T) {
	for _, loss := range []string{"wrong-npc", "wrong-choice-key", "snapshot", "late", "receipt-only"} {
		t.Run(loss, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				var other int32
				filter := func(u bridge.Update) bridge.Update {
					for i, rec := range u.Batch.Records {
						if rec.Type != wire.Fact || gatewaytest.U(rec, 1) != 22 {
							continue
						}
						switch loss {
						case "late":
							time.Sleep(6 * time.Second)
						case "snapshot":
							u.Batch.Snapshot = true
						}
						for j, f := range rec.Fields {
							if loss == "wrong-npc" && f.Number == 64 {
								rec.Fields[j] = wire.S(64, true, int64(other))
							}
							if loss == "wrong-choice-key" && f.Number == 75 {
								rec.Fields[j] = wire.U(75, true, 99999)
							}
						}
						if loss == "receipt-only" {
							u.Batch.Records = nil
							u.Batch.ReceivedAt = nil
							return u
						}
						u.Batch.Records[i] = rec
					}
					return u
				}
				r := newRig(t, cat, readyCharacter(), allSources(), filter)
				other = r.actor(30002).ID
				npc := r.actor(30001)
				r.p.Send(r.intent(1, 5, r.ref(npc.ID, 10)))
				r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				r.choose(2, 0)
				r.p.Result(2, wire.StageSent)
				r.advance(6 * time.Second)
				r.expect(2, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
			})
		})
	}
}
