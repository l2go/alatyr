// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"io"
	"net"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type trackedWorld struct {
	updates chan Update
	closes  atomic.Int32
	err     error
}

func (w *trackedWorld) Updates() <-chan Update          { return w.updates }
func (*trackedWorld) Write(context.Context, Step) error { return nil }
func (w *trackedWorld) Close() error                    { w.closes.Add(1); return w.err }

func newTestGateway(t *testing.T, cfg Config) *Gateway {
	t.Helper()
	g, err := New(cfg)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := g.Close(); err != nil {
			t.Error(err)
		}
	})
	return g
}

func bindPipe(t *testing.T, g *Gateway, ctx context.Context, identity string) net.Conn {
	t.Helper()
	a, b := net.Pipe()
	if err := g.Bind(ctx, identity, &Stream{a, 4096}); err != nil {
		t.Fatal(errors.Join(err, a.Close(), b.Close()))
	}
	t.Cleanup(func() {
		if err := b.Close(); err != nil {
			t.Error(err)
		}
	})
	return b
}

func TestBindReservesIdentityWithoutHoldingGlobalLock(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cfg := testConfig(t)
		started, release := make(chan struct{}), make(chan struct{})
		var calls atomic.Int32
		cfg.Factory = func(ctx context.Context, id string) (World, State, error) {
			if id == "one" {
				calls.Add(1)
				close(started)
				select {
				case <-release:
				case <-ctx.Done():
					return nil, nil, ctx.Err()
				}
			}
			return &trackedWorld{}, &testState{}, nil
		}
		g := newTestGateway(t, cfg)
		a, b := net.Pipe()
		t.Cleanup(func() {
			if err := b.Close(); err != nil {
				t.Error(err)
			}
		})
		result := make(chan error, 1)
		go func() { result <- g.Bind(t.Context(), "one", &Stream{a, 4096}) }()
		<-started
		if err := g.Bind(t.Context(), "one", scheduleTransport{}); !errors.Is(err, ErrFull) {
			t.Fatalf("same identity admission = %v, want ErrFull", err)
		}
		bindPipe(t, g, t.Context(), "two")
		if calls.Load() != 1 {
			t.Fatalf("opens = %d, want 1", calls.Load())
		}
		close(release)
		if err := <-result; err != nil {
			t.Fatal(err)
		}
	})
}

func TestAdmissionContextDoesNotOwnAcceptedSession(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w := &trackedWorld{}
		cfg := testConfig(t)
		cfg.Factory = func(context.Context, string) (World, State, error) { return w, &testState{}, nil }
		g := newTestGateway(t, cfg)
		ctx, cancel := context.WithCancel(t.Context())
		bindPipe(t, g, ctx, "one")
		cancel()
		synctest.Wait()
		if _, err := g.Status(t.Context(), "one"); err != nil {
			t.Fatal(err)
		}
		if w.closes.Load() != 0 {
			t.Fatal("request cancellation closed accepted world")
		}
		if err := g.Close(); err != nil {
			t.Fatal(err)
		}
		if w.closes.Load() != 1 {
			t.Fatalf("closes = %d, want 1", w.closes.Load())
		}
	})
}

func TestCloseCancelsOpeningAndJoinsOwners(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		started := make(chan struct{})
		cfg := testConfig(t)
		cfg.Factory = func(ctx context.Context, _ string) (World, State, error) {
			close(started)
			<-ctx.Done()
			return nil, nil, ctx.Err()
		}
		g := newTestGateway(t, cfg)
		result := make(chan error, 1)
		go func() { result <- g.Bind(t.Context(), "one", scheduleTransport{}) }()
		<-started
		if err := g.Close(); err != nil {
			t.Fatal(err)
		}
		if err := <-result; !errors.Is(err, context.Canceled) {
			t.Fatalf("Bind = %v, want canceled", err)
		}
		if err := g.Bind(t.Context(), "two", scheduleTransport{}); !errors.Is(err, ErrAuth) {
			t.Fatalf("Bind after Close = %v, want ErrAuth", err)
		}
	})
}

func TestCloseDeadlineAndWorldError(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		started, release := make(chan struct{}), make(chan struct{})
		want := errors.New("synthetic close failure")
		w := &trackedWorld{err: want}
		cfg := testConfig(t)
		cfg.Shutdown = time.Millisecond
		cfg.Factory = func(context.Context, string) (World, State, error) {
			close(started)
			<-release // Deliberately violates Factory cancellation to test the bound.
			return w, &testState{}, nil
		}
		g, err := New(cfg)
		if err != nil {
			t.Fatal(err)
		}
		result := make(chan error, 1)
		go func() { result <- g.Bind(t.Context(), "one", scheduleTransport{}) }()
		<-started
		if err := g.Close(); !errors.Is(err, context.DeadlineExceeded) {
			t.Fatalf("Close = %v, want deadline", err)
		}
		close(release)
		<-result
		synctest.Wait()
		if err := g.Close(); !errors.Is(err, want) {
			t.Fatalf("Close = %v, want %v", err, want)
		}
		if w.closes.Load() != 1 {
			t.Fatalf("closes = %d, want 1", w.closes.Load())
		}
	})
}

func TestFailedUpgradeReleasesOnlyUnusedWorld(t *testing.T) {
	for _, existing := range []bool{false, true} {
		name := "new"
		if existing {
			name = "existing"
		}
		t.Run(name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				w := &trackedWorld{}
				cfg := testConfig(t)
				cfg.Factory = func(context.Context, string) (World, State, error) { return w, &testState{}, nil }
				g := newTestGateway(t, cfg)
				if existing {
					bindPipe(t, g, t.Context(), "one")
				}
				h := HTTP{Gateway: g, Config: HTTPConfig{LocalIdentity: "one", LocalAddress: "127.0.0.1:8080"}}
				r := httptest.NewRequest("GET", "http://127.0.0.1:8080/wire", nil)
				r.Header.Set("Origin", "http://127.0.0.1:8080")
				response := httptest.NewRecorder()
				h.ServeHTTP(response, r)
				if response.Code != 400 {
					t.Fatalf("status = %d, want 400", response.Code)
				}
				synctest.Wait()
				want := int32(1)
				if existing {
					want = 0
				}
				if w.closes.Load() != want {
					t.Fatalf("closes = %d, want %d", w.closes.Load(), want)
				}
			})
		})
	}
}

func TestHTTPRejectsRemoteProxyIdentityBeforeOpening(t *testing.T) {
	cfg := testConfig(t)
	cfg.Factory = func(context.Context, string) (World, State, error) {
		t.Error("untrusted request opened a world")
		return nil, nil, io.EOF
	}
	g := newTestGateway(t, cfg)
	h := HTTP{Gateway: g, Config: HTTPConfig{Proxy: true, Origins: []string{"https://play.example"},
		Hosts: []string{"play.example"}, IdentityHeader: "X-Identity", SchemeHeader: "X-Forwarded-Proto"}}
	r := httptest.NewRequest("GET", "https://play.example/wire", nil)
	r.RemoteAddr = "192.0.2.1:1234"
	r.Header.Set("Origin", "https://play.example")
	r.Header.Set("X-Forwarded-Proto", "https")
	r.Header.Set("X-Identity", "one")
	response := httptest.NewRecorder()
	h.ServeHTTP(response, r)
	if response.Code != 401 {
		t.Fatalf("status = %d, want 401", response.Code)
	}
}

func TestCanceledFactoryReleasesReservationForRetry(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cfg := testConfig(t)
		started := make(chan struct{})
		var opens atomic.Int32
		cfg.Factory = func(ctx context.Context, _ string) (World, State, error) {
			if opens.Add(1) == 1 {
				close(started)
				<-ctx.Done()
				return nil, nil, ctx.Err()
			}
			return &trackedWorld{}, &testState{}, nil
		}
		g := newTestGateway(t, cfg)
		ctx, cancel := context.WithCancel(t.Context())
		result := make(chan error, 1)
		go func() { result <- g.Bind(ctx, "one", scheduleTransport{}) }()
		<-started
		cancel()
		if err := <-result; !errors.Is(err, context.Canceled) {
			t.Fatalf("Bind = %v, want canceled", err)
		}
		bindPipe(t, g, t.Context(), "one")
		if opens.Load() != 2 {
			t.Fatalf("opens = %d, want 2", opens.Load())
		}
	})
}

func TestCloseJoinsBlockedTransportWriter(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cfg := testConfig(t)
		g := newTestGateway(t, cfg)
		a, b := net.Pipe()
		t.Cleanup(func() {
			if err := b.Close(); err != nil {
				t.Error(err)
			}
		})
		blocked := make(chan struct{})
		transport := &blockedWriter{Stream: Stream{a, cfg.FrameBytes}, started: blocked}
		if err := g.Bind(t.Context(), "one", transport); err != nil {
			t.Fatal(err)
		}
		g.mu.Lock()
		s := g.games["one"]
		c := s.conns[1]
		g.mu.Unlock()
		if !c.enqueue(record(wire.Ping, wire.U(1, true, 1))) {
			t.Fatal("enqueue failed")
		}
		<-blocked
		if err := g.Close(); err != nil {
			t.Fatal(err)
		}
		select {
		case <-c.writerDone:
		default:
			t.Fatal("writer was not joined")
		}
		select {
		case <-s.done:
		default:
			t.Fatal("session was not joined")
		}
	})
}

type blockedWriter struct {
	Stream
	started chan struct{}
}

func (w *blockedWriter) Write(b []byte, deadline time.Time) error {
	close(w.started)
	return w.Stream.Write(b, deadline)
}
