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

func TestDirectDialogue(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104, Items: []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}}, Skills: []world.Skill{{ID: 3, Level: 1}}}
		w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1, 10}))
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		t.Cleanup(func() {
			cancel()
			if err := <-done; err != nil {
				t.Error(err)
			}
		})
		cfg := gatewaytest.Config()
		origin := time.Now()
		session, state, err := Open(t.Context(), Config{World: w, Catalog: cat, Meaning: cfg, Origin: origin, Load: func(context.Context, Identity) (world.Character, error) { return c, nil }, Policy: meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities { return meaning.Capabilities{Talk: o.Role == "tutor"} }}}, Identity{Account: c.Account, Character: c.Name})
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			if err := session.Close(); err != nil {
				t.Error(err)
			}
		})
		g := gatewaytest.New(t, cfg, func(context.Context, string) (gateway.World, gateway.State, error) { return session, state, nil }, origin)
		p := gatewaytest.Join(t, g, cfg, "one", wire.ModeAct, true)
		var target meaning.Ref
		for _, r := range p.Records {
			if r.Type == wire.Fact {
				ev, err := meaning.EventValue(r)
				if err != nil {
					t.Fatal(err)
				}
				if ev.Event.Role == "tutor" {
					target = ev.Context.Ref
				}
			}
		}
		if target.Gen == 0 {
			t.Fatal("tutor absent")
		}
		req, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Talk, Target: target.ID}, meaning.CommandContext{N: 1, Control: p.Control, Target: target})
		if err != nil {
			t.Fatal(err)
		}
		p.Send(req)
		p.Result(1, wire.StageDone)
		// A zero queue budget allows immediate work at the same clock instant.
		// It must not become an ambiguous backend error before source admission.
		immediate, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Talk, Target: target.ID}, meaning.CommandContext{N: 2, Control: p.Control, Target: target})
		if err != nil {
			t.Fatal(err)
		}
		immediate.Fields = append(immediate.Fields, wire.U(4, true, 0))
		p.Send(immediate)
		p.Result(2, wire.StageDone)

		synctest.Wait()
		p.Drain()
		for _, r := range p.Records {
			if r.Type == wire.Bye {
				t.Fatalf("unexpected bye %+v", r)
			}
		}
	})
}
