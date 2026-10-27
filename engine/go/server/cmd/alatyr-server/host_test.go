// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func configTest() Config {
	digest := sha256.Sum256([]byte(strings.Repeat("a", 64)))
	return Config{HTTPAddress: "127.0.0.1:0", WorldPath: "world", WorldName: "authored", DatabaseSeconds: 1, ShutdownSeconds: 1,
		Participants: []Participant{{ID: "one", Account: "example", Character: "Walker", TokenSHA256: hex.EncodeToString(digest[:])}}}
}
func TestAdmissionConfiguration(t *testing.T) {
	for _, kind := range []string{"wildcard", "hostname", "empty", "duplicates", "shared-token", "bad-digest", "missing-proxy-origin", "unbounded-shutdown", "duplicate-listener"} {
		t.Run(kind, func(t *testing.T) {
			c := configTest()
			switch kind {
			case "wildcard":
				c.HTTPAddress = "0.0.0.0:8080"
			case "hostname":
				c.HTTPAddress = "localhost:8080"
			case "empty":
				c.Participants = nil
			case "duplicates":
				c.Participants = append(c.Participants, c.Participants[0])
			case "shared-token":
				p := c.Participants[0]
				p.ID = "two"
				p.Character = "Other"
				c.Participants = append(c.Participants, p)
			case "bad-digest":
				c.Participants[0].TokenSHA256 = "invalid"
			case "missing-proxy-origin":
				c.ProxyHosts = []string{"example.test"}
			case "unbounded-shutdown":
				c.ShutdownSeconds = 61
			case "duplicate-listener":
				c.StreamAddress = c.HTTPAddress
			}
			if _, err := c.validate(); err == nil {
				t.Fatal("unsafe configuration accepted")
			}
		})
	}
}

func TestBearerAuthenticationAndRedactedRefusals(t *testing.T) {
	cfg := configTest()
	admissions, err := cfg.validate()
	if err != nil {
		t.Fatal(err)
	}
	r := &runtime{cfg: cfg, admissions: admissions}
	for _, token := range []string{"", strings.Repeat("b", 64), strings.Repeat("a", 63), strings.Repeat("a", 65)} {
		if got := r.authenticate(token); got != "" {
			t.Fatalf("invalid token admitted as %q", got)
		}
	}
	if got := r.authenticate(strings.Repeat("a", 64)); got != "one" {
		t.Fatalf("valid token got %q", got)
	}
	for _, path := range []string{"/participant", "/readyz", "/healthz", "/unknown"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "http://127.0.0.1"+path, nil)
		req.Header.Set("X-Alatyr-Identity", "one") // An unauthenticated claimed identity grants nothing.
		r.handler("127.0.0.1:8080").ServeHTTP(w, req)
		want := map[string]int{"/participant": 401, "/readyz": 503, "/healthz": 200, "/unknown": 404}[path]
		if w.Code != want {
			t.Fatalf("%s got %d, want %d", path, w.Code, want)
		}
		if strings.Contains(w.Body.String(), "example") || strings.Contains(w.Body.String(), "Walker") {
			t.Fatal("refusal disclosed a binding")
		}
	}
}

type canceledStore struct{ memstore.Store }

func (*canceledStore) UsedIDs(context.Context) ([]int32, error) { return nil, context.Canceled }
func (*canceledStore) ReserveIDs(context.Context) (int32, int32, error) {
	panic("called after startup failed")
}
func (*canceledStore) Ping(context.Context) error { return context.Canceled }

func TestStartupFailureDoesNotStartWorld(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	if err := Run(t.Context(), configTest(), cat, &canceledStore{}); err == nil {
		t.Fatal("startup error lost")
	}
}

func (*canceledStore) PutLearning(context.Context, world.Character) error { return context.Canceled }

type hostMemoryStore struct{ memstore.Store }

func (*hostMemoryStore) UsedIDs(context.Context) ([]int32, error) { return []int32{1, 2}, nil }
func (*hostMemoryStore) ReserveIDs(context.Context) (int32, int32, error) {
	return 1 << 28, 1 << 29, nil
}
func (*hostMemoryStore) Ping(context.Context) error { return nil }
func (s *hostMemoryStore) PutLearning(ctx context.Context, c world.Character) error {
	return s.PutCharacter(ctx, c)
}

func TestOpenAuthoredCharacterPreservesHiddenTemplates(t *testing.T) {
	cfg := configTest()
	admissions, err := cfg.validate()
	if err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	store := &hostMemoryStore{}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104, Items: []world.Item{{ObjectID: 2, TemplateID: 999999, Count: 7, Slot: -1}}, Skills: []world.Skill{{ID: 999999, Level: 1}}}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	chars := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
	w := world.New(cat, chars, world.WithReservedIDs([]int32{1, 2}))
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	}()
	r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: backend.Config{Meaning: limits(), Origin: time.Now()}}
	port, state, err := r.open(t.Context(), "one")
	if err != nil {
		t.Fatal(err)
	}
	if snap, err := state.Snapshot(0); err != nil || !snap.Ready {
		t.Fatalf("snapshot: %+v/%v", snap, err)
	}
	if err := port.Close(); err != nil {
		t.Fatal(err)
	}
	saved, err := store.Characters(t.Context(), "example")
	if err != nil || len(saved) != 1 || len(saved[0].Items) != 1 || saved[0].Items[0].TemplateID != 999999 || len(saved[0].Skills) != 1 || saved[0].Skills[0].ID != 999999 {
		t.Fatalf("hidden item lost: %+v/%v", saved, err)
	}
}

type faultStore struct {
	hostMemoryStore
	fail atomic.Bool
}

func (s *faultStore) PutCharacter(ctx context.Context, c world.Character) error {
	if s.fail.Load() {
		return errors.New("injected departure failure")
	}
	return s.Store.PutCharacter(ctx, c)
}

func TestFailedDepartureRetriesOriginalRefBeforeReload(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cfg := configTest()
	admissions, err := cfg.validate()
	if err != nil {
		t.Fatal(err)
	}
	store := &faultStore{}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	chars := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
	w := world.New(cat, chars, world.WithReservedIDs([]int32{1}))
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	}()
	r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: backend.Config{Meaning: limits(), Origin: time.Now()}}
	port, _, err := r.open(ctx, "one")
	if err != nil {
		t.Fatal(err)
	}
	old := r.ports["one"].port.Owner()
	if err := w.Teleport(ctx, 1, world.Point{X: -71027, Y: c.Y, Z: c.Z}, false); err != nil {
		t.Fatal(err)
	}
	store.fail.Store(true)
	if err := port.Close(); err == nil {
		t.Fatal("departure failure lost")
	}
	store.fail.Store(false)
	// Exercise the opening-cleanup retry path too. It must reload after its save,
	// rather than enter the stale character read before the retry.
	delete(r.ports, "one")
	r.failed[old] = true
	next, _, err := r.open(ctx, "one")
	if err != nil {
		t.Fatal(err)
	}
	owner := r.ports["one"].port.Owner()
	if owner == old {
		t.Fatal("replacement reused incarnation")
	}
	scene, err := w.Capture(ctx, owner)
	if err != nil || scene.Self.Char.X != -71027 {
		t.Fatalf("retry loaded stale state: %+v/%v", scene.Self.Char, err)
	}
	if err := w.Leave(ctx, old); err == nil {
		t.Fatal("old Ref could depart replacement")
	}
	if err := next.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestGoWorldTargetCapabilities(t *testing.T) {
	for _, tc := range []struct {
		role string
		want meaning.Capabilities
	}{
		{"tutor", meaning.Capabilities{Talk: true, Cast: true}},
		{"provisioner", meaning.Capabilities{Talk: true, Cast: true}},
		{"folk", meaning.Capabilities{Talk: true, Cast: true}},
		{"creature", meaning.Capabilities{Strike: true, Cast: true}},
		{meaning.RoleDrop, meaning.Capabilities{Pickup: true}},
		{meaning.RolePlayer, meaning.Capabilities{}},
		{meaning.RoleUnknown, meaning.Capabilities{}},
	} {
		if got := classify(meaning.Observed{Role: tc.role}); got != tc.want {
			t.Fatalf("%q got %+v, want %+v", tc.role, got, tc.want)
		}
	}
}
