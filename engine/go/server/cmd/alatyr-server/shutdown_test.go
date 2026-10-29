// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestHostShutdownWithActiveWebSocket(t *testing.T) {
	testHostShutdown(t, false)
}

func TestHostShutdownSavesWithinComposeGrace(t *testing.T) {
	testHostShutdown(t, true)
}

func testHostShutdown(t *testing.T, slow bool) {
	t.Helper()
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cfg := configTest()
	if slow {
		// This is the largest accepted value for one participant and a short DB
		// budget. The boundary test below rejects the next value and old limit 60.
		cfg.ShutdownSeconds = 15
	}
	cfg.HTTPAddress = testAddress(t)
	cfg.ProxyHosts = []string{cfg.HTTPAddress}
	cfg.ProxyOrigins = []string{"http://" + cfg.HTTPAddress}
	cfg.ProxyPeers = []string{"127.0.0.1"}
	var store Store = &hostMemoryStore{}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	var delayed *shutdownStore
	if slow {
		delayed = &shutdownStore{Store: store, finalDelay: 14 * time.Second}
		store = delayed
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
	// Model Docker's SIGKILL deadline from the instant SIGTERM is delivered.
	deadline.Reset(60 * time.Second)
	started := time.Now()
	cancel()
	select {
	case err := <-done:
		joined = true
		if slow {
			if !errors.Is(err, context.DeadlineExceeded) {
				t.Fatalf("retry error = %v, want deadline", err)
			}
			if !delayed.saved.Load() {
				t.Fatal("final persistence did not complete")
			}
			if delayed.calls.Load() != 3 {
				t.Fatalf("save calls = %d, want close, retry, final", delayed.calls.Load())
			}
			if time.Since(started) >= 60*time.Second {
				t.Fatal("final save exceeded Compose grace")
			}
		} else if err != nil {
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

// Fail the port's bounded departure and consume the whole retry budget. Only
// final persistence succeeds, after using nearly its full independent budget.
// These are deadline waits, not sleeps used to synchronize goroutines.
type shutdownStore struct {
	Store
	calls      atomic.Int32
	saved      atomic.Bool
	finalDelay time.Duration
}

func (s *shutdownStore) PutCharacter(ctx context.Context, c world.Character) error {
	if s.calls.Add(1) <= 2 {
		<-ctx.Done()
		return ctx.Err()
	}
	timer := time.NewTimer(s.finalDelay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		err := s.Store.PutCharacter(ctx, c)
		s.saved.Store(err == nil)
		return err
	}
}

func TestShutdownBudgetsFitComposeGrace(t *testing.T) {
	compose, err := os.ReadFile("../../../../../deploy/compose.yaml")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(compose), "stop_grace_period: 60s") {
		t.Fatal("shutdown bound must track the actual Compose stop grace")
	}
	for _, tc := range []struct {
		name                            string
		database, participants, maximum int
	}{
		{"short-database", 1, 1, 15}, {"long-database", 30, 1, 9}, {"full-registry", 1, 64, 13},
	} {
		t.Run(tc.name, func(t *testing.T) {
			cfg := configTest()
			cfg.DatabaseSeconds = tc.database
			cfg.Participants = make([]Participant, tc.participants)
			for i := range cfg.Participants {
				cfg.Participants[i] = Participant{ID: fmt.Sprint(i), Account: "example", Character: fmt.Sprint(i), TokenSHA256: fmt.Sprintf("%064x", i+1)}
			}
			cfg.ShutdownSeconds = tc.maximum
			if _, err := cfg.validate(); err != nil {
				t.Fatalf("safe maximum rejected: %v", err)
			}
			for _, seconds := range []int{tc.maximum + 1, 60} {
				cfg.ShutdownSeconds = seconds
				if _, err := cfg.validate(); err == nil {
					t.Fatalf("accepted %d-second phases beyond Compose grace", seconds)
				}
			}
		})
	}
}
