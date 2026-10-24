// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unicode/utf8"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Config supplies trusted host dependencies. Load must resolve an existing
// character owned by Identity, not accept character data from a participant.
// Catalog must be the catalog used by World. The host owns World.Run and its
// cancellation. Store implementations must honor their operation contexts.
// ResumeBudget defaults to 30 seconds (1 to 300 seconds); CloseBudget defaults
// to 100 milliseconds (also its maximum). Now defaults to time.Now and must
// be safe for concurrent calls. Hosts close their ports before stopping Run.
type Config struct {
	World                     *world.World
	Catalog                   *catalog.Catalog
	Load                      func(context.Context, bridge.Identity) (world.Character, error)
	ResumeBudget, CloseBudget time.Duration
	Now                       func() time.Time
}

// Client owns one captured incarnation's raw observation session. Its zero
// value is closed. Methods are safe for concurrent use; one Write and one
// Resume may be in progress. A single goroutine owns its mutable session state.
type Client struct {
	cfg                   Config
	owner                 world.Ref
	updates               chan bridge.Update
	requests              chan operation
	stop, done            chan struct{}
	cancel                context.CancelFunc
	closing               sync.Once
	writeBusy, resumeBusy atomic.Bool
	closeErr              error // published by closing done
}

var _ bridge.Port = (*Client)(nil)

type result struct {
	n        uint64
	recovery bridge.Recovery
	err      error
}

// Request contexts live only in request closures until the buffered reply.
// No request context becomes the lifetime of an admitted session.
type operation struct {
	call  func(context.Context, *session) result
	reply chan result
}

func portError(kind bridge.FailureKind) error { return &bridge.Error{Kind: kind} }

// OpenError reports an opening failure whose incarnation cleanup also failed.
// Owner is the exact Ref the host may retry with World.Leave. Err retains both
// the opening and cleanup errors; no client or background retry is started.
type OpenError struct {
	Owner world.Ref
	Err   error
}

// Error describes the opening and cleanup failures.
func (e *OpenError) Error() string { return fmt.Sprintf("worldbridge: open cleanup: %v", e.Err) }

// Unwrap exposes both failures to errors.Is and errors.As.
func (e *OpenError) Unwrap() error { return e.Err }

func cleanupOpen(cfg Config, owner world.Ref, err error) error {
	ctx, cancel := context.WithTimeout(context.Background(), cfg.CloseBudget)
	defer cancel()
	if cleanupErr := cfg.World.Leave(ctx, owner); cleanupErr != nil {
		return &OpenError{Owner: owner, Err: errors.Join(err, cleanupErr)}
	}
	return err
}

func normalizeConfig(cfg Config, id bridge.Identity) (Config, error) {
	if cfg.World == nil || cfg.Catalog == nil || cfg.Load == nil {
		return cfg, portError(bridge.ConfigError)
	}
	for _, name := range []string{id.Account, id.Character} {
		if name == "" || len(name) > 128 || !utf8.ValidString(name) || strings.ContainsRune(name, 0) {
			return cfg, portError(bridge.ConfigError)
		}
	}
	if cfg.ResumeBudget == 0 {
		cfg.ResumeBudget = 30 * time.Second
	}
	if cfg.CloseBudget == 0 {
		cfg.CloseBudget = 100 * time.Millisecond
	}
	if cfg.ResumeBudget < time.Second || cfg.ResumeBudget > 300*time.Second || cfg.CloseBudget <= 0 || cfg.CloseBudget > 100*time.Millisecond {
		return cfg, portError(bridge.ConfigError)
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	return cfg, nil
}

// Open enters a trusted existing character and returns its complete, ready
// checkpoint. ctx bounds admission only. A failed opening removes only the
// entered incarnation. If cleanup fails, an OpenError retains its exact Owner
// for a host-owned World.Leave retry alongside both errors.
func Open(ctx context.Context, cfg Config, id bridge.Identity) (*Client, bridge.Snapshot, error) {
	cfg, err := normalizeConfig(cfg, id)
	if err != nil {
		return nil, bridge.Snapshot{}, err
	}
	ctx, cancelOpening := context.WithTimeout(ctx, 10*time.Second)
	defer cancelOpening()
	// Validate the world's lifecycle without relying on its private fields.
	// A missing zero Ref is expected from a constructed, running world.
	if _, err := cfg.World.Capture(ctx, world.Ref{}); !errors.Is(err, world.ErrNotPresent) {
		return nil, bridge.Snapshot{}, fmt.Errorf("worldbridge: world unavailable: %w", err)
	}
	if err := ctx.Err(); err != nil {
		return nil, bridge.Snapshot{}, err
	}
	char, err := cfg.Load(ctx, id)
	if err != nil {
		return nil, bridge.Snapshot{}, fmt.Errorf("worldbridge: load character: %w", err)
	}
	if char.ObjectID <= 0 || char.Account != id.Account || char.Name != id.Character {
		return nil, bridge.Snapshot{}, portError(bridge.Authentication)
	}
	if err := ctx.Err(); err != nil {
		return nil, bridge.Snapshot{}, err
	}
	entry, err := cfg.World.Enter(ctx, char.Clone())
	if err != nil {
		return nil, bridge.Snapshot{}, fmt.Errorf("worldbridge: enter: %w", err)
	}
	fail := func(err error) (*Client, bridge.Snapshot, error) {
		return nil, bridge.Snapshot{}, cleanupOpen(cfg, entry.Self.Entity, err)
	}
	scene, sub, err := cfg.World.Subscribe(ctx, entry.Self.Entity)
	if err != nil {
		return fail(fmt.Errorf("worldbridge: subscribe: %w", err))
	}
	tr := translator{catalog: cfg.Catalog}
	snap, next, err := tr.checkpoint(scene, 1, 1, scene.Begin.Time)
	if err != nil {
		return fail(err)
	}
	if !snap.Ready {
		return fail(errSource)
	}
	if err := ctx.Err(); err != nil {
		return fail(err)
	}
	lifetime, cancel := context.WithCancel(context.Background())
	c := &Client{cfg: cfg, owner: entry.Self.Entity, updates: make(chan bridge.Update, 1), requests: make(chan operation), stop: make(chan struct{}), done: make(chan struct{}), cancel: cancel}
	s := &session{client: c, tr: tr, scene: scene, sub: sub, origin: scene.Begin.Time, epoch: 1, nextFact: next, nextAttempt: 1, sourceSeq: scene.End.Seq, keys: make(map[uint64]bool)}
	go s.run(lifetime)
	return c, snap, nil
}

// Owner returns the captured incarnation. After a failed Close the host may
// retry World.Leave with this Ref; Close itself never retries or changes result.
func (c *Client) Owner() world.Ref { return c.owner }

// Updates returns ordered raw batches and lifecycle changes. Closed is last;
// an unread update may be discarded in its favor. The zero value is inactive.
func (c *Client) Updates() <-chan bridge.Update { return c.updates }

func (c *Client) submit(ctx context.Context, call func(context.Context, *session) result) result {
	if c.done == nil {
		return result{err: portError(bridge.StateError)}
	}
	if err := ctx.Err(); err != nil {
		return result{err: err}
	}
	op := operation{call: call, reply: make(chan result, 1)}
	select {
	case <-c.stop:
		return result{err: portError(bridge.StateError)}
	default:
	}
	select {
	case c.requests <- op:
	case <-ctx.Done():
		return result{err: ctx.Err()}
	case <-c.done:
		return result{err: portError(bridge.StateError)}
	case <-c.stop:
		return result{err: portError(bridge.StateError)}
	}
	// After admission wait for the owning operation. This retains its number
	// even if cancellation races a command that has already changed the world.
	select {
	case r := <-op.reply:
		return r
	case <-c.done:
		select {
		case r := <-op.reply:
			return r
		default:
			return result{err: portError(bridge.StateError)}
		}
	}
}

// Write registers Key and an attempt before execution. Keys remain unique
// across Resume. This session admits at most 4096 keys and retains the last 64
// receipts. Errors after admission do not prove that no mutation occurred.
// QueueTTLMS and the caller deadline bound the original attempt, not recovery.
func (c *Client) Write(ctx context.Context, command bridge.Command) (uint64, error) {
	if !c.writeBusy.CompareAndSwap(false, true) {
		return 0, portError(bridge.StateError)
	}
	defer c.writeBusy.Store(false)
	command, err := normalize(command)
	if err != nil {
		return 0, err
	}
	if c.cfg.Now == nil {
		return 0, portError(bridge.StateError)
	}
	at := c.cfg.Now()
	ttl := uint64(5000)
	if command.QueueTTLMS != nil {
		ttl = *command.QueueTTLMS
	}
	deadline := at.Add(time.Duration(ttl) * time.Millisecond)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		deadline = d
	}
	r := c.submit(ctx, func(lifetime context.Context, s *session) result {
		request, cancel := context.WithDeadline(ctx, deadline)
		defer cancel()
		stop := context.AfterFunc(lifetime, cancel)
		defer stop()
		return s.write(request, lifetime, command, deadline)
	})
	return r.n, r.err
}

// Resume restores this same live incarnation after observation loss. It
// returns a fresh epoch and copies of retained receipts marked Replay, without
// reexecuting commands or renewing their original authority or deadlines.
func (c *Client) Resume(ctx context.Context) (bridge.Recovery, error) {
	if !c.resumeBusy.CompareAndSwap(false, true) {
		return bridge.Recovery{}, portError(bridge.StateError)
	}
	defer c.resumeBusy.Store(false)
	r := c.submit(ctx, func(lifetime context.Context, s *session) result {
		request, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		stop := context.AfterFunc(lifetime, cancel)
		defer stop()
		return s.resume(request)
	})
	return r.recovery, r.err
}

// Close cancels active operations, waits for the owner and returns its stable
// departure/save result. It does not stop World.Run. A failed save remains the
// host's responsibility using Owner; no background retry is started.
func (c *Client) Close() error {
	if c.done == nil {
		return nil
	}
	c.closing.Do(func() { close(c.stop); c.cancel() })
	<-c.done
	return c.closeErr
}
