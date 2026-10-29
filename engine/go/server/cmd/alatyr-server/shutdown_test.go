// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestHostShutdownWithActiveWebSocket(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cfg := configTest()
	cfg.HTTPAddress = testAddress(t)
	cfg.ProxyHosts = []string{cfg.HTTPAddress}
	cfg.ProxyOrigins = []string{"http://" + cfg.HTTPAddress}
	cfg.ProxyPeers = []string{"127.0.0.1"}
	store := &hostMemoryStore{}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- Run(ctx, cfg, cat, store) }()
	joined := false
	t.Cleanup(func() {
		cancel()
		if !joined {
			if err := <-done; err != nil {
				t.Error(err)
			}
		}
	})
	client := &http.Client{Timeout: time.Second}
	defer client.CloseIdleConnections()
	tick := time.NewTicker(10 * time.Millisecond)
	defer tick.Stop()
	deadline := time.NewTimer(5 * time.Second)
	defer deadline.Stop()
	for {
		response, err := client.Get("http://" + cfg.HTTPAddress + "/readyz")
		if err == nil {
			response.Body.Close()
			if response.StatusCode == http.StatusOK {
				break
			}
		}
		select {
		case err := <-done:
			joined = true
			t.Fatalf("startup: %v", err)
		case <-deadline.C:
			t.Fatal("host never ready")
		case <-tick.C:
		}
	}
	socket, err := openImageSocket("http://"+cfg.HTTPAddress, strings.Repeat("a", 64), cfg)
	if err != nil {
		t.Fatal(err)
	}
	defer socket.Close()
	codec, err := meaning.NewCodec(limits(1))
	if err != nil {
		t.Fatal(err)
	}
	sendImage(t, socket, codec, wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, meaning.WireVersion), wire.U(2, true, meaning.WireVersion), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}})
	ready := false
	for !ready {
		for _, r := range readImage(t, socket, codec) {
			if r.Type == wire.Checkpoint && testUint(r, 1) == uint64(wire.CheckpointEnd) && testUint(r, 3) == 1 {
				ready = true
			}
		}
	}
	// Keep the upgraded page open while the host closes listeners and persists.
	cancel()
	select {
	case err := <-done:
		joined = true
		if err != nil {
			t.Fatalf("normal shutdown returned %v, want nil (process exit 0)", err)
		}
	case <-deadline.C:
		t.Fatal("shutdown did not join")
	}
	saved, err := store.Character(t.Context(), c.Account, c.Name)
	if err != nil || saved.X != c.X || saved.SP != c.SP {
		t.Fatalf("shutdown checkpoint: %+v/%v", saved, err)
	}
}
