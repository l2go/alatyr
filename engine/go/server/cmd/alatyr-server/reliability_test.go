// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"errors"
	"fmt"
	"math/rand/v2"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
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
		var handoffs []backend.Handoff
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
		mc, origin := limits(1), time.Now()
		r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: hostBackendConfig(mc, origin)}
		r.meaningConfig.Handoff = func(h backend.Handoff) { auditMu.Lock(); handoffs = append(handoffs, h); auditMu.Unlock() }
		var mu sync.Mutex
		var scopes []*backend.World
		var resumed gateway.World
		var state gateway.State
		var scheduled *worldtest.SequencePort
		factory := func(ctx context.Context, id string) (gateway.World, gateway.State, error) {
			mu.Lock()
			defer mu.Unlock()
			if resumed != nil {
				scope, st := resumed, state
				resumed, state = nil, nil
				return scope, st, nil
			}
			scope, st, err := r.openPort(ctx, id, func(p bridge.Port) bridge.Port { scheduled = worldtest.NewSequencePort(p); return scheduled })
			if err == nil {
				scopes = append(scopes, scope.(*backend.World))
			}
			return scope, st, err
		}
		policy := gatewayPolicy(mc)
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
			World: w, Gateway: g, Config: mc, Peer: p, Owner: func() world.Ref { return current().port.Owner() },
			Resume: func() error {
				scope, st, err := current().session.Resume(t.Context())
				if err != nil {
					return err
				}
				mu.Lock()
				resumed, state = scope, st
				scopes = append(scopes, scope)
				mu.Unlock()
				return nil
			},
			Saved: func() world.Character {
				c, err := store.Character(t.Context(), "example", "Walker")
				if err != nil {
					t.Fatal(err)
				}
				return c
			},
			Close: closePorts, Stop: stop, Port: scheduled, Handoffs: func() []backend.Handoff {
				auditMu.Lock()
				defer auditMu.Unlock()
				return append([]backend.Handoff(nil), handoffs...)
			}, Commits: func() []world.Commit {
				auditMu.Lock()
				defer auditMu.Unlock()
				return append([]world.Commit(nil), commits...)
			}, Scopes: func() []*backend.World { mu.Lock(); defer mu.Unlock(); return append([]*backend.World(nil), scopes...) },
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
