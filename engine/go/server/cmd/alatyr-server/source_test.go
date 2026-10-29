// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"math/rand/v2"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Exercise the host's production config and admission factory, never a test
// Source callback. The worldtest suite separately proves nil authority fails.
func TestHostExclusiveSources(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	for _, family := range []string{"dialogue", "pickup", "learning", "return"} {
		t.Run(family, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				cfg := configTest()
				admissions, err := cfg.validate()
				if err != nil {
					t.Fatal(err)
				}
				store := &hostMemoryStore{}
				c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, Exp: 68, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104, Items: []world.Item{{ObjectID: 2, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}}}
				if family == "return" {
					c.CurHP = 0
				}
				if family == "pickup" {
					c.X, c.Y = -70357, 256863
				}
				if err := store.PutCharacter(t.Context(), c); err != nil {
					t.Fatal(err)
				}
				chars := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
				w := world.New(cat, chars, world.WithReservedIDs([]int32{1, 2}), world.WithIDRange(1<<28, 1<<29), world.WithRandomSource(rand.NewPCG(1, 2)))
				ctx, cancel := context.WithCancel(context.Background())
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				t.Cleanup(func() {
					cancel()
					if err := <-done; err != nil {
						t.Error(err)
					}
				})
				mc, origin := limits(1), time.Now()
				r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: hostBackendConfig(mc, origin)}
				g := gatewaytest.New(t, mc, r.open, origin)
				p := gatewaytest.Join(t, g, mc, "one", wire.ModeAct, true)
				sync := func() { synctest.Wait(); p.Drain(); synctest.Wait(); p.Drain() }
				sync()
				intent := func(n, verb uint64, fields ...wire.Field) wire.Record {
					return wire.Record{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, p.Control)}, fields...)}
				}
				ref := func(id int32, field uint32) wire.Field {
					t.Helper()
					sync()
					for i := len(p.Records) - 1; i >= 0; i-- {
						rec := p.Records[i]
						if rec.Type != wire.Fact {
							continue
						}
						f, ok := wire.Find(rec.Fields, 10)
						if !ok {
							continue
						}
						v, _ := wire.Find(f.Fields, 1)
						if v.S == int64(id) {
							return wire.Obj(field, true, f.Fields)
						}
					}
					t.Fatalf("missing observed reference %d", id)
					return wire.Field{}
				}
				actor := func(template int32) world.Ref {
					scene, err := w.Capture(t.Context(), r.ports["one"].port.Owner())
					if err != nil {
						t.Fatal(err)
					}
					for _, o := range scene.Objects {
						if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == template {
							return o.Entity
						}
					}
					t.Fatalf("missing actor %d", template)
					return world.Ref{}
				}
				expect := func(n uint64) { t.Helper(); p.Result(n, wire.StageDone) }
				switch family {
				case "dialogue":
					p.Send(intent(1, 11, ref(actor(30001).ID, 10)))
					expect(1)
				case "learning":
					p.Send(intent(1, 5, ref(actor(30001).ID, 10)))
					expect(1)
					spoke, err := meaning.EventValue(p.Fact(11))
					if err != nil {
						t.Fatal(err)
					}
					p.Send(intent(2, 6, wire.U(12, true, spoke.Context.Dialog), wire.U(13, true, spoke.Context.Options[0].ID)))
					practice, err := meaning.EventValue(p.Fact(22))
					if err != nil {
						t.Fatal(err)
					}
					index := -1
					for i, row := range practice.Event.Goods {
						if row.ID == 257 {
							index = i
						}
					}
					if index < 0 {
						t.Fatal("missing authored lesson")
					}
					p.Send(intent(3, 13, wire.S(14, true, int64(practice.Event.List)), wire.U(15, true, practice.Context.Offers[index])))
					p.Result(3, wire.StageSent)
					sync()
					time.Sleep(4990 * time.Millisecond)
					sync()
					for _, rec := range p.Records {
						if rec.Type == wire.Result && gatewaytest.U(rec, 1) == 3 && gatewaytest.U(rec, 2) != uint64(wire.StageSent) {
							t.Fatal("learning completed before evidence window")
						}
					}
					time.Sleep(10 * time.Millisecond)
					sync()
					expect(3)
					saved, err := store.Character(t.Context(), c.Account, c.Name)
					if err != nil {
						t.Fatal(err)
					}
					if saved.SP >= c.SP {
						t.Fatal("learning did not persist its SP debit")
					}
				case "return":
					p.Send(intent(1, 9))
					expect(1)
					if p.Epoch != 2 || !p.Ready {
						t.Fatalf("return epoch/ready=%d/%t", p.Epoch, p.Ready)
					}
				case "pickup":
					owner := r.ports["one"].port.Owner()
					if err := w.Harm(t.Context(), owner, actor(18003), 1000000); err != nil {
						t.Fatal(err)
					}
					sync()
					var drop meaning.Event
					for _, rec := range p.Records {
						if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 8 {
							d, err := meaning.EventValue(rec)
							if err != nil {
								t.Fatal(err)
							}
							drop = d.Event
							break
						}
					}
					if drop.ID == 0 {
						t.Fatal("owned hunt drop absent")
					}
					if err := w.MoveTo(t.Context(), 1, world.Point{X: drop.X, Y: drop.Y, Z: drop.Z}); err != nil {
						t.Fatal(err)
					}
					for range 20 {
						time.Sleep(100 * time.Millisecond)
						sync()
					}
					p.Send(intent(1, 4, ref(drop.ID, 20)))
					expect(1)
				}
			})
		})
	}
}
