// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

// Store is the durable host's port. Callers own its lifetime through Run.
type Store interface {
	world.Store
	PutLearning(context.Context, world.Character) error
	Characters(context.Context, string) ([]world.Character, error)
	UsedIDs(context.Context) ([]int32, error)
	ReserveIDs(context.Context) (int32, int32, error)
	Ping(context.Context) error
}

type opened struct {
	port    *worldbridge.Client
	session *backend.Session
}

type runtime struct {
	opening       sync.WaitGroup
	openingCancel map[string]context.CancelFunc
	cfg           Config
	store         Store
	characters    *characterStore
	world         *world.World
	cat           *catalog.Catalog
	admissions    map[string]admission
	meaningConfig backend.Config
	gateway       *gateway.Gateway
	mu            sync.Mutex
	ports         map[string]opened
	failed        map[world.Ref]bool
	closing       bool
	ready         atomic.Bool
}

// Run owns the listeners, gateway, raw ports and World.Run. On every exit it
// closes admission, joins opening handlers, closes ports, retries fenced saves,
// and only then cancels the world. The caller closes Store after Run returns.
func Run(ctx context.Context, cfg Config, cat *catalog.Catalog, store Store) error {
	if cat == nil || store == nil {
		return errors.New("host: missing world dependencies")
	}
	admissions, err := cfg.validate()
	if err != nil {
		return err
	}
	startup, cancelStartup := context.WithTimeout(ctx, time.Duration(cfg.DatabaseSeconds)*time.Second)
	ids, err := store.UsedIDs(startup)
	if err != nil {
		cancelStartup()
		return err
	}
	first, last, err := store.ReserveIDs(startup)
	cancelStartup()
	if err != nil {
		return err
	}
	characters := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
	w := world.New(cat, characters, world.WithReservedIDs(ids), world.WithIDRange(first, last))
	life, cancel := context.WithCancel(context.Background())
	worldDone := make(chan error, 1)
	go func() { worldDone <- w.Run(life) }()
	defer cancel()
	r := &runtime{cfg: cfg, store: store, characters: characters, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool)}
	r.openingCancel = make(map[string]context.CancelFunc)
	origin := time.Now()
	mc := limits()
	r.meaningConfig = backend.Config{Meaning: mc, Origin: origin, Policy: meaning.Policy{Classify: classify}}
	codec, err := meaning.NewCodec(mc)
	if err != nil {
		cancel()
		<-worldDone
		return err
	}
	identities := make(map[string]gateway.Identity)
	for id := range admissions {
		identities[id] = gateway.Identity{Act: true}
	}
	r.gateway, err = gateway.New(gateway.Config{Codec: codec, Factory: r.open, Origin: origin, Identities: identities,
		Versions: []uint64{1}, Meanings: []string{meaning.Meaning}, World: cfg.WorldName, Limits: mc.LimitsFields(), Attempts: mc.Attempts,
		FrameBytes: mc.Wire.FrameBytes, OutBytes: 262144, Sessions: len(admissions), Connections: 4, Teleports: 8,
		IntentBurst: 16, IntentRate: 8, ServiceBurst: 8, ServiceRate: 4, Hello: 5 * time.Second, Write: time.Second, Checkpoint: 10 * time.Second,
		Ping: 15 * time.Second, Pong: 30 * time.Second, Batch: 10 * time.Millisecond, TargetWait: 1500 * time.Millisecond, Shutdown: time.Duration(cfg.ShutdownSeconds) * time.Second})
	if err != nil {
		cancel()
		<-worldDone
		return err
	}
	var listeners []net.Listener
	var server *http.Server
	var owners sync.WaitGroup
	errorsOut := make(chan error, 3)
	pending := make(map[net.Conn]bool)
	var pendingMu sync.Mutex
	stopping := false
	stop := func() error {
		r.ready.Store(false)
		r.mu.Lock()
		r.closing = true
		for _, cancel := range r.openingCancel {
			cancel()
		}
		r.mu.Unlock()
		var errs []error
		for _, l := range listeners {
			if err := l.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
				errs = append(errs, err)
			}
		}
		if server != nil {
			if err := server.Close(); err != nil {
				errs = append(errs, err)
			}
		}
		pendingMu.Lock()
		stopping = true
		for c := range pending {
			if err := c.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
				errs = append(errs, err)
			}
		}
		pendingMu.Unlock()
		// Closing Gateway cancels active factories and joins HTTP upgrades and transports.
		if err := r.gateway.Close(); err != nil {
			errs = append(errs, err)
		}
		owners.Wait()
		r.opening.Wait()
		r.mu.Lock()
		ports := r.ports
		failed := make(map[world.Ref]bool, len(r.failed))
		for owner := range r.failed {
			failed[owner] = true
		}
		r.mu.Unlock()
		for _, o := range ports {
			if err := o.session.Close(); err != nil {
				failed[o.port.Owner()] = true
			}
		}
		drain, cancelDrain := context.WithTimeout(context.Background(), time.Duration(cfg.ShutdownSeconds)*time.Second)
		defer cancelDrain()
		for owner := range failed {
			if err := w.Leave(drain, owner); err != nil {
				errs = append(errs, err)
			}
		}
		cancel()
		return errors.Join(errs...)
	}
	for _, address := range []string{cfg.HTTPAddress, cfg.StreamAddress} {
		if address == "" {
			continue
		}
		l, err := net.Listen("tcp", address)
		if err != nil {
			err = errors.Join(err, stop())
			return errors.Join(err, <-worldDone)
		}
		listeners = append(listeners, l)
		owners.Add(1)
		if address == cfg.HTTPAddress {
			server = &http.Server{Handler: r.handler(l.Addr().String()), ReadHeaderTimeout: 5 * time.Second, IdleTimeout: 30 * time.Second, MaxHeaderBytes: 8192}
			go func() {
				defer owners.Done()
				if err := server.Serve(&boundedListener{Listener: l, slots: make(chan struct{}, len(admissions)*4+64)}); err != nil && !errors.Is(err, http.ErrServerClosed) {
					errorsOut <- err
				}
			}()
		} else {
			go func() {
				defer owners.Done()
				slots := make(chan struct{}, 64)
				for {
					conn, err := l.Accept()
					if err != nil {
						if !errors.Is(err, net.ErrClosed) {
							errorsOut <- err
						}
						return
					}
					select {
					case slots <- struct{}{}:
					default:
						closeConnection(conn)
						continue
					}
					pendingMu.Lock()
					if stopping {
						pendingMu.Unlock()
						<-slots
						closeConnection(conn)
						return
					}
					pending[conn] = true
					pendingMu.Unlock()
					owners.Add(1)
					go func() {
						defer owners.Done()
						defer func() { <-slots; pendingMu.Lock(); delete(pending, conn); pendingMu.Unlock() }()
						if err := r.accept(ctx, conn); err != nil {
							closeConnection(conn)
						}
					}()
				}
			}()
		}
	}
	r.ready.Store(true)
	var runErr error
	worldEnded := false
	select {
	case <-ctx.Done():
	case runErr = <-errorsOut:
	case runErr = <-worldDone:
		worldEnded = true
	}
	runErr = errors.Join(runErr, stop())
	if !worldEnded {
		runErr = errors.Join(runErr, <-worldDone)
	}
	return runErr
}

func (r *runtime) open(ctx context.Context, id string) (gateway.World, gateway.State, error) {
	a, ok := r.admissions[id]
	if !ok {
		return nil, nil, gateway.ErrAuth
	}
	// A port removed by Gateway can still own a detached scope or failed save.
	// Retire and join it before a new incarnation for this identity is loaded.
	request, cancel := context.WithTimeout(ctx, time.Duration(r.cfg.DatabaseSeconds)*time.Second)
	ctx = request
	r.mu.Lock()
	if r.closing {
		r.mu.Unlock()
		cancel()
		return nil, nil, gateway.ErrAuth
	}
	r.opening.Add(1)
	r.openingCancel[id] = cancel
	defer func() { cancel(); r.mu.Lock(); delete(r.openingCancel, id); r.mu.Unlock(); r.opening.Done() }()
	previous, exists := r.ports[id]
	delete(r.ports, id)
	r.mu.Unlock()
	if exists {
		if err := previous.session.Close(); err != nil {
			if err := r.world.Leave(ctx, previous.port.Owner()); err != nil {
				r.mu.Lock()
				r.ports[id] = previous
				r.mu.Unlock()
				return nil, nil, err
			}
		}
	}
	p, snapshot, err := worldbridge.Open(ctx, worldbridge.Config{World: r.world, Catalog: r.cat, Load: func(ctx context.Context, id bridge.Identity) (world.Character, error) {
		chars, err := r.store.Characters(ctx, id.Account)
		if err != nil {
			return world.Character{}, err
		}
		for _, c := range chars {
			if c.Account == id.Account && c.Name == id.Character {
				r.mu.Lock()
				var retry []world.Ref
				for owner := range r.failed {
					if owner.ID == c.ObjectID {
						retry = append(retry, owner)
					}
				}
				r.mu.Unlock()
				for _, owner := range retry {
					if err := r.world.Leave(ctx, owner); err != nil {
						return world.Character{}, err
					}
					r.mu.Lock()
					delete(r.failed, owner)
					r.mu.Unlock()
				}
				if len(retry) > 0 {
					fresh, err := r.store.Characters(ctx, id.Account)
					if err != nil {
						return world.Character{}, err
					}
					for _, saved := range fresh {
						if saved.ObjectID == c.ObjectID && saved.Account == id.Account && saved.Name == id.Character {
							c = saved
							break
						}
					}
				}
				if c.ClassID != c.BaseClass {
					return world.Character{}, errors.New("host: active subclasses are unsupported")
				}
				return r.characters.load(c), nil
			}
		}
		return world.Character{}, gateway.ErrAuth
	}}, a.identity)
	if err != nil {
		var opening *worldbridge.OpenError
		if errors.As(err, &opening) {
			r.mu.Lock()
			r.failed[opening.Owner] = true
			r.mu.Unlock()
		}
		return nil, nil, err
	}
	s, w, state, err := backend.New(p, snapshot, r.meaningConfig)
	if err != nil {
		closeErr := p.Close()
		if closeErr != nil {
			r.mu.Lock()
			r.failed[p.Owner()] = true
			r.mu.Unlock()
		}
		return nil, nil, errors.Join(err, closeErr)
	}
	r.mu.Lock()
	r.ports[id] = opened{p, s}
	r.mu.Unlock()
	return w, state, nil
}

func (r *runtime) authenticate(token string) string {
	if len(token) != 64 {
		return ""
	}
	digest := sha256.Sum256([]byte(token))
	id := ""
	for candidate, a := range r.admissions {
		if subtle.ConstantTimeCompare(a.digest[:], digest[:]) == 1 {
			id = candidate
		}
	}
	return id
}

func (r *runtime) accept(ctx context.Context, c net.Conn) error {
	if err := c.SetReadDeadline(time.Now().Add(5 * time.Second)); err != nil {
		return err
	}
	var token [65]byte
	if _, err := io.ReadFull(c, token[:]); err != nil {
		return err
	}
	if token[64] != '\n' {
		return gateway.ErrAuth
	}
	id := r.authenticate(string(token[:64]))
	if id == "" {
		return gateway.ErrAuth
	}
	return r.gateway.Bind(ctx, id, &gateway.Stream{Conn: c, Limit: 8192})
}

func (r *runtime) handler(address string) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		if req.Method == "GET" && req.URL.Path == "/healthz" {
			w.WriteHeader(http.StatusOK)
			return
		}
		if req.Method == "GET" && req.URL.Path == "/readyz" {
			ctx, cancel := context.WithTimeout(req.Context(), time.Duration(r.cfg.DatabaseSeconds)*time.Second)
			defer cancel()
			if !r.ready.Load() || r.store.Ping(ctx) != nil {
				http.Error(w, "unavailable", http.StatusServiceUnavailable)
				return
			}
			if _, err := r.world.Capture(ctx, world.Ref{}); !errors.Is(err, world.ErrNotPresent) {
				http.Error(w, "unavailable", http.StatusServiceUnavailable)
				return
			}
			w.WriteHeader(http.StatusOK)
			return
		}
		if req.URL.Path != "/participant" {
			http.NotFound(w, req)
			return
		}
		values := req.Header.Values("Authorization")
		if len(values) != 1 || !strings.HasPrefix(values[0], "Bearer ") {
			http.Error(w, "participant unavailable", http.StatusUnauthorized)
			return
		}
		id := r.authenticate(strings.TrimPrefix(values[0], "Bearer "))
		if id == "" {
			http.Error(w, "participant unavailable", http.StatusUnauthorized)
			return
		}
		h := gateway.HTTP{Gateway: r.gateway, Config: gateway.HTTPConfig{LocalAddress: address, LocalIdentity: id}}
		if len(r.cfg.ProxyHosts) > 0 {
			h.Config.Proxy = true
			h.Config.Hosts = r.cfg.ProxyHosts
			h.Config.Origins = r.cfg.ProxyOrigins
			h.Config.IdentityHeader = "X-Alatyr-Identity"
			h.Config.SchemeHeader = "X-Forwarded-Proto"
			req = req.Clone(req.Context())
			req.Header.Set(h.Config.IdentityHeader, id)
		}
		h.ServeHTTP(w, req)
	})
}

func closeConnection(conn net.Conn) {
	if err := conn.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
		slog.Warn("participant connection close failed")
	}
}
