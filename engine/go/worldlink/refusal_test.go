// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"context"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

type beforeWriteWorld struct {
	*Session
	before func(context.Context) error
}

func (w *beforeWriteWorld) Write(ctx context.Context, step gateway.Step) error {
	if step.N != 0 {
		if err := w.before(ctx); err != nil {
			return err
		}
	}
	return w.Session.Write(ctx, step)
}

func TestObserverReservationRefusalIsImmediate(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"full", "closed", "reserved"} {
		t.Run(name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				char := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, CurHP: 100, X: -71057, Y: 257883, Z: -3104}
				w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1}))
				ctx, cancel := context.WithCancel(context.Background())
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				t.Cleanup(func() {
					cancel()
					if err := <-done; err != nil {
						t.Error(err)
					}
				})
				cfg, origin := gatewaytest.Config(), time.Now()
				cfg.Attempts.Pending = 1
				var intent world.IntentID
				source, state, err := Open(t.Context(), Config{World: w, Catalog: cat, Meaning: cfg, Origin: origin,
					Load: func(context.Context, Identity) (world.Character, error) { return char, nil },
					Policy: meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
						return meaning.Capabilities{Talk: o.Role == "tutor"}
					}},
					Handoff: func(h Handoff) { intent = h.Intent },
				}, Identity{Account: char.Account, Character: char.Name})
				if err != nil {
					t.Fatal(err)
				}
				port := &beforeWriteWorld{Session: source, before: func(ctx context.Context) error {
					// Run after planning and validation, immediately before Write:
					// the gateway cannot consume and free this reservation first.
					if name == "closed" {
						source.mailbox.Stop()
						return nil
					}
					if name == "reserved" {
						// A refusal after a successful watch must also leave no
						// pending binding or duplicate terminal outcome behind.
						return w.Harm(ctx, world.Ref{}, source.Owner(), 1e6)
					}
					_, err := w.Execute(ctx, world.Request{Owner: source.Owner(), Observer: source.mailbox, Verb: world.VerbAppear})
					return err
				}}
				g := gatewaytest.New(t, cfg, func(context.Context, string) (gateway.World, gateway.State, error) { return port, state, nil }, origin)
				p := gatewaytest.Join(t, g, cfg, "one", wire.ModeAct, true)
				var target meaning.Ref
				for _, r := range p.Records {
					if r.Type != wire.Fact {
						continue
					}
					e, err := meaning.EventValue(r)
					if err != nil {
						t.Fatal(err)
					}
					if e.Event.Role == "tutor" {
						target = e.Context.Ref
					}
				}
				if target.Gen == 0 {
					t.Fatal("tutor absent")
				}
				record, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Talk, Target: target.ID}, meaning.CommandContext{N: 1, Control: p.Control, Target: target})
				if err != nil {
					t.Fatal(err)
				}
				p.Send(record)
				synctest.Wait()
				p.Drain()
				refused := 0
				for _, r := range p.Records {
					if r.Type != wire.Result || gatewaytest.U(r, 1) != 1 {
						continue
					}
					switch stage := wire.Stage(gatewaytest.U(r, 2)); stage {
					case wire.StageRefused:
						refused++
						if gatewaytest.U(r, 3) != uint64(wire.ReasonWorld) || gatewaytest.U(r, 7) != uint64(wire.DeliveryNone) {
							t.Errorf("refusal=%+v, want world reason and no delivery", r)
						}
					default:
						t.Errorf("unexpected stage %v for synchronously refused talk", stage)
					}
				}
				if refused != 1 || len(source.pending) != 0 || intent.N == 0 {
					t.Errorf("refusals=%d pending=%d intent=%+v, want one immediate refusal and no pending attempt", refused, len(source.pending), intent)
				}
				if elapsed := time.Since(origin); elapsed >= time.Duration(cfg.Attempts.EvidenceMS)*time.Millisecond {
					t.Errorf("refusal waited for evidence expiry: %v", elapsed)
				}
			})
		})
	}
}
