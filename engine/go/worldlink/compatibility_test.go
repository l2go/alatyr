// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"bytes"
	"context"
	"encoding/hex"
	"encoding/json"
	"math/rand/v2"
	"os"
	"path/filepath"
	"slices"
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

func compatibilityTranscript(t *testing.T) [][]byte {
	t.Helper()
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	var out [][]byte
	synctest.Test(t, func(t *testing.T) {
		char := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104, Items: []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}}, Skills: []world.Skill{{ID: 3, Level: 1}}}
		w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1, 10}), world.WithRandomSource(rand.NewPCG(1, 2)))
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
		var source gateway.World
		var state gateway.State
		policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities { return meaning.Capabilities{Talk: o.Role == "tutor"} }}
		source, state, err = Open(t.Context(), Config{World: w, Catalog: cat, Meaning: cfg, Origin: origin, Policy: policy, Load: func(context.Context, Identity) (world.Character, error) { return char, nil }}, Identity{Account: char.Account, Character: char.Name})

		if err != nil {
			t.Fatal(err)
		}
		g := gatewaytest.New(t, cfg, func(context.Context, string) (gateway.World, gateway.State, error) { return source, state, nil }, origin)
		p := gatewaytest.Join(t, g, cfg, "one", wire.ModeAct, true)
		p.Send(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.U(3, true, p.Control), wire.At(11, true, -71047, 257883, -3104)}})
		p.Result(1, wire.StageSent)
		synctest.Wait()
		p.Drain()
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
		talk, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Talk, Target: target.ID}, meaning.CommandContext{N: 2, Control: p.Control, Target: target})
		if err != nil {
			t.Fatal(err)
		}
		p.Send(talk)
		p.Result(2, wire.StageDone)
		synctest.Wait()
		p.Drain()

		codec, err := meaning.NewCodec(cfg)
		if err != nil {
			t.Fatal(err)
		}
		for _, r := range p.Records {
			if r.Type == wire.Welcome {
				r.Fields = slices.Clone(r.Fields)
				for i := range r.Fields {
					if r.Fields[i].Number == 4 {
						r.Fields[i] = wire.U(4, true, 1)
					}
				}
			}
			b, err := codec.Encode([]wire.Record{r})
			if err != nil {
				t.Fatal(err)
			}
			out = append(out, b)
		}
	})
	return out
}

// TestGatewayBaselineTranscript preserves the initial checkpoint, movement and
// authored dialogue bytes captured before removal of the historical adapter.
func TestGatewayBaselineTranscript(t *testing.T) {
	b, err := os.ReadFile("testdata/gateway_baseline.json")
	if err != nil {
		t.Fatal(err)
	}
	var baseline []string
	if err := json.Unmarshal(b, &baseline); err != nil {
		t.Fatal(err)
	}
	got := compatibilityTranscript(t)
	if len(baseline) != len(got) {
		t.Fatalf("record count: baseline=%d direct=%d", len(baseline), len(got))
	}
	for i, want := range baseline {
		b, err := hex.DecodeString(want)
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(b, got[i]) {
			t.Fatalf("record %d differs from the historical transcript", i)
		}
	}
}
