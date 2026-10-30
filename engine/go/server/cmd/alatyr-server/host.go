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
	Character(context.Context, string, string) (world.Character, error)
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
// then joins Run and persists its detached final checkpoints, including on a
// fatal world exit. The caller closes Store after Run returns.
func Run(ctx context.Context, cfg Config, cat *catalog.Catalog, store Store) error {
	return serve(ctx, cfg, cat, store)
}

func serve(ctx context.Context, cfg Config, cat *catalog.Catalog, store Store, options ...world.Option) error {
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
	options = append([]world.Option{world.WithReservedIDs(ids), world.WithIDRange(first, last)}, options...)
	w := world.New(cat, characters, options...)
	life, cancel := context.WithCancel(context.Background())
	worldDone := make(chan error, 1)
	go func() { worldDone <- w.Run(life) }()
	defer cancel()
	r := &runtime{cfg: cfg, store: store, characters: characters, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool)}
	r.openingCancel = make(map[string]context.CancelFunc)
	origin := time.Now()
	mc := limits(len(admissions))
	r.meaningConfig = hostBackendConfig(mc, origin)
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
	policy := gatewayPolicy(mc)
	policy.Codec, policy.Factory, policy.Origin, policy.Identities = codec, r.open, origin, identities
	policy.World = cfg.WorldName
	policy.Shutdown = time.Duration(cfg.ShutdownSeconds) * time.Second
	r.gateway, err = gateway.New(policy)
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
		// Let net/http own its listener close before closing the raw handles.
		// Close can still observe a listener already closed by its Serve owner.
		if server != nil {
			if err := server.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
				errs = append(errs, err)
			}
		}
		for _, l := range listeners {
			if err := l.Close(); err != nil && !errors.Is(err, net.ErrClosed) {
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
			if err := w.Leave(drain, owner); err != nil && !errors.Is(err, world.ErrStopped) {
				errs = append(errs, err)
			}
		}
		cancel()
		return errors.Join(errs...)
	}
	// Joining all admitted ports before final persistence also joins in-flight
	// learning/departure I/O. An older write cannot overwrite this final checkpoint.
	finish := func(runErr error, worldEnded bool) error {
		runErr = errors.Join(runErr, stop())
		if !worldEnded {
			runErr = errors.Join(runErr, <-worldDone)
		}
		finalCtx, cancelFinal := context.WithTimeout(context.Background(), time.Duration(cfg.ShutdownSeconds)*time.Second)
		defer cancelFinal()
		checkpoints, err := w.Final(finalCtx)
		runErr = errors.Join(runErr, err)
		for _, checkpoint := range checkpoints {
			runErr = errors.Join(runErr, characters.PutCharacter(finalCtx, checkpoint.Character))
		}
		return runErr
	}
	for _, address := range []string{cfg.HTTPAddress, cfg.StreamAddress} {
		if address == "" {
			continue
		}
		l, err := net.Listen("tcp", address)
		if err != nil {
			return finish(err, false)
		}
		listeners = append(listeners, l)
		owners.Add(1)
		if address == cfg.HTTPAddress {
			server = &http.Server{Handler: r.handler(l.Addr().String()), ReadHeaderTimeout: policy.Hello, IdleTimeout: policy.Pong, MaxHeaderBytes: policy.FrameBytes}
			go func() {
				defer owners.Done()
				if err := server.Serve(&boundedListener{Listener: l, slots: make(chan struct{}, policy.Sessions*policy.Connections+64)}); err != nil && !errors.Is(err, http.ErrServerClosed) {
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
	return finish(runErr, worldEnded)
}

// hostBackendConfig binds evidence authority to this host's Go world only.
func hostBackendConfig(mc meaning.Config, origin time.Time) backend.Config {
	return backend.Config{Meaning: mc, Origin: origin, Policy: meaning.Policy{Classify: classify}, Source: goWorldSource}
}

// goWorldSource audits the five exclusive evidence families. Each admitted
// identity has one backend scope and one fenced worldbridge port. World.Run is
// the sole writer of that incarnation; worldbridge translates its ordered events
// without merging another producer. Dialogue comes only from that player's
// accepted interaction; choice windows additionally bind their original intent
// and captured NPC incarnation to the raw effect key; pickup from its atomic
// owned transfer; learning from its
// serialized, durably committed skill/SP transaction; return from its fenced
// revival and relocation. Item use carries the exact world intent through a
// bounded raw key binding; only its live slot/count change may witness completion.
// Trades and delayed observations cannot acquire that binding. Retired ports
// are joined before replacement admission.
// Participants cannot inject these events or this policy. Additional event
// producers or parallel mutation paths require a new exclusivity audit here.
func goWorldSource(meaning.Event) meaning.Source {
	return meaning.Source{Dialogue: true, Pickup: true, Learning: true, Return: true, ItemUse: true}
}

func (r *runtime) open(ctx context.Context, id string) (gateway.World, gateway.State, error) {
	return r.openPort(ctx, id, nil)
}

// openPort allows a controlled observation schedule in integration tests. A nil
// decorator uses the production port directly; it never changes Source policy.
func (r *runtime) openPort(ctx context.Context, id string, decorate func(bridge.Port) bridge.Port) (gateway.World, gateway.State, error) {
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
	// Resolve both names with PostgreSQL citext semantics, then pass the stored
	// canonical spelling to the byte-exact bridge identity fence.
	c, err := r.store.Character(ctx, a.identity.Account, a.identity.Character)
	if err != nil {
		return nil, nil, err
	}
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
			return nil, nil, err
		}
		r.mu.Lock()
		delete(r.failed, owner)
		r.mu.Unlock()
	}
	if len(retry) > 0 {
		c, err = r.store.Character(ctx, a.identity.Account, a.identity.Character)
		if err != nil {
			return nil, nil, err
		}
	}
	if c.ClassID != c.BaseClass {
		return nil, nil, errors.New("host: active subclasses are unsupported")
	}
	identity := bridge.Identity{Account: c.Account, Character: c.Name}
	p, snapshot, err := worldbridge.Open(ctx, worldbridge.Config{World: r.world, Catalog: r.cat, Load: func(context.Context, bridge.Identity) (world.Character, error) {
		return r.characters.load(c), nil
	}}, identity)
	if err != nil {
		var opening *worldbridge.OpenError
		if errors.As(err, &opening) {
			r.mu.Lock()
			r.failed[opening.Owner] = true
			r.mu.Unlock()
		}
		return nil, nil, err
	}
	var port bridge.Port = p
	if decorate != nil {
		port = decorate(p)
	}
	s, w, state, err := backend.New(port, snapshot, r.meaningConfig)
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
	if err := c.SetReadDeadline(time.Now().Add(gatewayPolicy(r.meaningConfig.Meaning).Hello)); err != nil {
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
	return r.gateway.Bind(ctx, id, &gateway.Stream{Conn: c, Limit: r.meaningConfig.Meaning.Wire.FrameBytes})
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
			h.Config.ProxyPeers = r.cfg.ProxyPeers
			if len(h.Config.ProxyPeers) == 0 {
				peer, _, _ := net.SplitHostPort(address)
				h.Config.ProxyPeers = []string{peer}
			}
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
