// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"errors"
	"fmt"
	"math/rand/v2"
	"slices"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/server/goworld"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldlink"
	"github.com/l2go/alatyr/engine/go/worldtest"
)

func hostSequence(t *testing.T, cat *catalog.Catalog, data []byte) []byte {
	t.Helper()
	var transcript []byte
	synctest.Test(t, func(t *testing.T) {
		cfg := configTest()
		admissions, err := cfg.validate()
		if err != nil {
			t.Fatal(err)
		}
		store := &hostMemoryStore{}
		if err := store.PutCharacter(t.Context(), worldtest.SequenceCharacter(data)); err != nil {
			t.Fatal(err)
		}
		chars := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
		var auditMu sync.Mutex
		var commits []world.Commit
		var handoffs []worldlink.Handoff
		w := world.New(cat, chars, world.WithReservedIDs([]int32{1, 10, 11, 12}), world.WithIDRange(1<<28, 1<<29), world.WithRandomSource(rand.NewPCG(1, 2)), world.WithCommits(func(c world.Commit) { auditMu.Lock(); commits = append(commits, c); auditMu.Unlock() }))
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		stop := sync.OnceFunc(func() {
			cancel()
			if err := <-done; err != nil {
				t.Error(err)
			}
		})
		t.Cleanup(stop)
		mc, origin := goworld.Limits(1), time.Now()
		r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: goworld.BackendConfig(mc, origin)}
		r.meaningConfig.Handoff = func(h worldlink.Handoff) { auditMu.Lock(); handoffs = append(handoffs, h); auditMu.Unlock() }
		var scheduled *worldtest.SequenceReceiver
		factory := func(ctx context.Context, id string) (gateway.World, gateway.State, error) {
			w, state, err := r.open(ctx, id)
			if err != nil {
				return nil, nil, err
			}
			scheduled = worldtest.NewSequenceReceiver(w)
			return scheduled, state, nil
		}

		policy := goworld.GatewayPolicy(mc)
		policy.Origin = origin
		policy.Factory = factory
		policy.Identities = map[string]gateway.Identity{"one": {Act: true}}
		policy.World = "test-world"
		policy.Codec, err = meaning.NewCodec(mc)
		if err != nil {
			t.Fatal(err)
		}
		g, err := gateway.New(policy)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			if err := g.Close(); err != nil && !errors.Is(err, world.ErrStopped) {
				t.Error(err)
			}
		})
		current := func() opened { r.mu.Lock(); defer r.mu.Unlock(); return r.ports["one"] }
		closePorts := func() error { return current().session.Close() }
		t.Cleanup(func() {
			if err := closePorts(); err != nil && !errors.Is(err, world.ErrStopped) {
				t.Error(err)
			}
		})
		p := gatewaytest.Join(t, g, mc, "one", wire.ModeAct, true)
		transcript = worldtest.ExerciseSequence(t, data, worldtest.SequenceHost{
			Flush: scheduled.Flush, World: w, Gateway: g, Config: mc, Peer: p, Owner: func() world.Ref { return current().session.Owner() },
			Saved: func() world.Character {
				c, err := store.Character(t.Context(), "example", "Walker")
				if err != nil {
					t.Fatal(err)
				}
				return c
			},
			Close: closePorts, Stop: stop, Handoffs: func() []worldlink.Handoff {
				auditMu.Lock()
				defer auditMu.Unlock()
				return slices.Clone(handoffs)
			}, Commits: func() []world.Commit {
				auditMu.Lock()
				defer auditMu.Unlock()
				return append([]world.Commit(nil), commits...)
			},
		})
	})
	return transcript
}

func TestHostGatewayReliability(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	for i, seed := range worldtest.SequenceSeeds() {
		t.Run(fmt.Sprint(i), func(t *testing.T) {
			t.Parallel()
			worldtest.CompareSequence(t, hostSequence(t, cat, seed), hostSequence(t, cat, seed))
		})
	}
}
