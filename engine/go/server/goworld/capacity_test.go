// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package goworld

import (
	"context"
	"fmt"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

// This finite crowd checks the supported profile, not a hardware maximum.
func TestLimitsBoundDirectCrowd(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		cfg := Limits(ParticipantLimit)
		chars := map[string]world.Character{}
		var ids []int32
		for i := range ParticipantLimit {
			name := fmt.Sprintf("Walker%d", i)
			id := int32(i + 1)
			ids = append(ids, id)
			chars[name] = world.Character{ObjectID: id, Account: "crowd", Name: name, Level: 2, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
		}
		w := world.New(cat, memstore.New(), world.WithReservedIDs(ids), world.WithInterest(cfg.Objects-1))
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		t.Cleanup(func() {
			cancel()
			if err := <-done; err != nil {
				t.Error(err)
			}
		})
		var sessions []*worldlink.Session
		var states []gateway.State
		for i := range ParticipantLimit {
			name := fmt.Sprintf("Walker%d", i)
			c := BackendConfig(cfg, time.Now())
			s, state, err := Open(t.Context(), w, cat, worldlink.Identity{Account: "crowd", Character: name}, func(_ context.Context, id worldlink.Identity) (world.Character, error) {
				return chars[id.Character], nil
			}, c)
			if err != nil {
				t.Fatal(err)
			}
			sessions = append(sessions, s)
			states = append(states, state)
		}
		t.Cleanup(func() {
			for _, s := range sessions {
				if err := s.Close(); err != nil {
					t.Error(err)
				}
			}
		})
		codec, err := meaning.NewCodec(cfg)
		if err != nil {
			t.Fatal(err)
		}
		maxFacts, maxBytes, maxFrame := 0, 0, 0
		for i, state := range states {
			snapshot, err := state.Snapshot(0)
			if err != nil || !snapshot.Ready {
				t.Fatalf("capture %d: ready=%v/%v", i, snapshot.Ready, err)
			}
			bytes := 0
			for _, fact := range snapshot.Facts {
				raw, err := codec.Encode([]wire.Record{fact})
				if err != nil {
					t.Fatal(err)
				}
				bytes += len(raw)
				maxFrame = max(maxFrame, len(raw))
			}
			maxFacts, maxBytes = max(maxFacts, len(snapshot.Facts)), max(maxBytes, bytes)
			if bytes >= int(cfg.Advertised[29]) {
				t.Fatalf("checkpoint %d bytes=%d exceeds profile", i, bytes)
			}
		}
		t.Logf("participants=%d objects limit=%d max checkpoint facts=%d fact bytes=%d largest fact=%d source slots=%d", len(sessions), cfg.Objects, maxFacts, maxBytes, maxFrame, sessions[0].Capacity())
	})
}
