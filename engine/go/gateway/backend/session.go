// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"context"
	"errors"
	"math"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Config supplies explicit projection limits, clocks, and audited producers.
type Config struct {
	// Meaning bounds the actor-owned dictionary and its retained projection.
	// Pending/Results cannot exceed 8/64, QueueMS/EvidenceMS 5000, or ProposalMS 60000.
	Meaning meaning.Config
	// Policy classifies targets before admission; Steps is not accepted here.
	Policy meaning.Policy
	// Source grants only producer exclusivity audited by the host. Nil grants none.
	Source func(meaning.Event) meaning.Source
	// Origin must equal gateway.Config.Origin and remains fixed across Resume.
	Origin time.Time
	// Now uses the same clock as the gateway. Nil selects time.Now.
	Now func() time.Time
	// ResumeBudget bounds detached retention, at most 30 seconds; zero selects 30s.
	ResumeBudget time.Duration
	// Handoff optionally observes bounded numeric correlation after Port.Write.
	// It must not block. Nil disables it; payloads and error strings are excluded.
	Handoff func(Handoff)
}

// Handoff is local numeric correlation, not a receipt or participant outcome.
type Handoff struct {
	// N is the participant attempt; zero is a system step.
	N uint64
	// Index is the captured step index.
	Index int
	// Role distinguishes helper, effect, and system writes.
	Role gateway.StepRole
	// Key is the session-owned command key.
	Key uint64
	// RawN is the attempt returned by Port.Write; zero means no allocated attempt.
	RawN uint64
}

// Session owns a raw port and its monotonically increasing command keys.
// A detached scope must end before explicit Resume can construct its successor.
// Close is idempotent. The host owns Session.Close even after its scope detaches.
type Session struct {
	mu                           sync.Mutex
	port                         bridge.Port
	cfg                          Config
	projection                   *meaning.State
	world                        *World
	key                          uint64
	detached, recovering, closed bool
	expires                      time.Time
	timer                        *time.Timer
	once                         sync.Once
	err                          error
}

// New takes ownership of port on success and returns its first participant scope.
// A nonready snapshot or invalid configuration is an error, never fabricated readiness.
func New(port bridge.Port, snapshot bridge.Snapshot, cfg Config) (*Session, *World, gateway.State, error) {
	if port == nil || !snapshot.Ready || cfg.Origin.IsZero() || cfg.Policy.Steps != nil ||
		cfg.Meaning.ProposalMS > 60000 || cfg.Meaning.Attempts.Pending > 8 ||
		cfg.Meaning.Attempts.Results > 64 || cfg.Meaning.Attempts.EvidenceMS > 5000 ||
		cfg.Meaning.Attempts.QueueMS > 5000 {
		return nil, nil, nil, wire.ErrConfig
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.ResumeBudget == 0 {
		cfg.ResumeBudget = 30 * time.Second
	}
	if cfg.ResumeBudget < 0 || cfg.ResumeBudget > 30*time.Second || cfg.Origin.After(cfg.Now()) {
		return nil, nil, nil, wire.ErrConfig
	}
	projection, err := meaning.NewState(cfg.Meaning, cfg.Policy)
	if err != nil {
		return nil, nil, nil, err
	}
	s := &Session{port: port, cfg: cfg, projection: projection}
	w, state, err := s.scope(snapshot)
	if err != nil {
		return nil, nil, nil, err
	}
	s.world = w
	go w.pump()
	return s, w, state, nil
}

// Resume restores the retained raw session for a new participant scope.
// It never reexecutes a command or applies Recovery.Receipts as live evidence.
// A failed recovery closes the retained port instead of reopening a new session.
func (s *Session) Resume(ctx context.Context) (*World, gateway.State, error) {
	s.mu.Lock()
	if s.closed || !s.detached || s.recovering || !s.cfg.Now().Before(s.expires) {
		s.mu.Unlock()
		return nil, nil, &bridge.Error{Kind: bridge.StateError}
	}
	s.recovering = true
	budget := s.expires.Sub(s.cfg.Now())
	s.mu.Unlock()
	ctx, cancel := context.WithTimeout(ctx, budget)
	defer cancel()
	recovery, err := s.port.Resume(ctx)
	if err != nil {
		return nil, nil, errors.Join(err, s.Close())
	}
	s.mu.Lock()
	if s.closed || !s.cfg.Now().Before(s.expires) {
		s.mu.Unlock()
		return nil, nil, errors.Join(&bridge.Error{Kind: bridge.StateError}, s.Close())
	}
	if s.timer != nil {
		s.timer.Stop()
	}
	// Reset projection lifetimes, but retain its monotonic Ref/proposal counters.
	s.projection.Invalidate()
	w, state, err := s.scope(recovery.Snapshot)
	if err != nil {
		s.mu.Unlock()
		return nil, nil, errors.Join(err, s.Close())
	}
	s.world, s.detached, s.recovering = w, false, false
	s.mu.Unlock()
	go w.pump()
	return w, state, nil
}

// Close stops the current scope and closes the raw session exactly once.
// Its returned error retains uncertainty about remote closure.
func (s *Session) Close() error {
	s.once.Do(func() {
		s.mu.Lock()
		s.closed = true
		w := s.world
		if s.timer != nil {
			s.timer.Stop()
		}
		s.mu.Unlock()
		if w != nil {
			w.stopPump()
		}
		s.err = s.port.Close()
	})
	return s.err
}

func (s *Session) nextKey() (uint64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || s.detached || s.key == math.MaxUint64 {
		return 0, &bridge.Error{Kind: bridge.StateError}
	}
	s.key++
	return s.key, nil
}

type packet struct {
	record   wire.Record
	at       time.Time
	receipts []bridge.Receipt
	snapshot bool
	kind     bridge.UpdateKind
}

type pending struct {
	plan              *plan
	role              gateway.StepRole
	n, verb           uint64
	contact, deadline int64
}

// World is one participant scope over a retained Session. Only the runtime
// calls Write and its State methods; the pump only transfers raw observations.
type World struct {
	session    *Session
	state      *state
	updates    chan gateway.Update
	stop, done chan struct{}
	once       sync.Once
	pending    map[uint64]pending
	returnPlan *plan
}

// Updates yields bounded typed observations in raw receive order.
func (w *World) Updates() <-chan gateway.Update { return w.updates }

// Close closes both this scope and its raw session. It is idempotent.
func (w *World) Close() error { return w.session.Close() }

func (w *World) stopPump() { w.once.Do(func() { close(w.stop) }); <-w.done }

// Detach stops the scope without closing the raw port. Recovery is explicit,
// bounded by the original detach deadline, and belongs to the Session owner.
func (w *World) Detach() error {
	w.stopPump()
	s := w.session
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || s.world != w || s.detached {
		return nil
	}
	s.detached = true
	s.expires = s.cfg.Now().Add(s.cfg.ResumeBudget)
	s.timer = time.AfterFunc(s.cfg.ResumeBudget, func() { s.Close() })
	return nil
}

func (w *World) pump() {
	defer close(w.done)
	defer close(w.updates)
	// The pump owns only this receive fence. It never reads mutable State.
	self, epoch, seq, g := w.state.self, w.state.epoch, w.state.seq, w.state.g
	send := func(p packet) bool {
		u := gateway.Update{Value: p}
		r := p.record
		if r.Type == wire.Fact && profile.U(r, 4) == epoch && profile.U(r, 2) > seq {
			seq = profile.U(r, 2)
			if profile.Has(r, 67) && profile.U(r, 67) == 1 && profile.U(r, 1) == 4 && profile.S(r, 64) == int64(self) && profile.U(r, 66) > g {
				g = profile.U(r, 66)
				u.Relocate = true
			}
		}
		if !p.at.IsZero() {
			ms := p.at.Sub(w.session.cfg.Origin).Milliseconds()
			u.ReceivedMS = &ms
		}
		select {
		case w.updates <- u:
			return true
		case <-w.stop:
			return false
		}
	}
	for {
		select {
		case <-w.stop:
			return
		case u, ok := <-w.session.port.Updates():
			if !ok {
				send(packet{kind: bridge.Closed})
				return
			}
			if u.Kind != bridge.LiveBatch {
				send(packet{kind: u.Kind})
				return
			}
			b := u.Batch
			if len(b.Records) != len(b.ReceivedAt) || len(b.Records) > 1024 || len(b.Receipts) > 72 {
				send(packet{kind: bridge.Closed})
				return
			}
			if len(b.Records) == 0 {
				if !send(packet{kind: bridge.LiveBatch, receipts: b.Receipts}) {
					return
				}
			}
			for i, r := range b.Records {
				p := packet{kind: bridge.LiveBatch, record: r, at: b.ReceivedAt[i], snapshot: b.Snapshot}
				if i == 0 {
					p.receipts = b.Receipts
				}
				if !send(p) {
					return
				}
			}
		}
	}
}

// Write registers a key and its participant/role binding before handing off to
// Port.Write, so even an immediate receipt cannot race the correspondence.
// A foreign value returns an error without formatting or logging it.
func (w *World) Write(ctx context.Context, step gateway.Step) error {
	op, ok := step.Value.(operation)
	if !ok || op.plan == nil || op.plan.owner != w.state {
		return meaning.ErrValue
	}
	if step.N != op.plan.n || step.Index != op.index || (step.Role == gateway.StepHelper) != op.value.Selection || step.Role > gateway.StepEffect || (step.N == 0) != (step.Role == gateway.StepSystem) {
		return meaning.ErrValue
	}
	now := w.session.cfg.Now().Sub(w.session.cfg.Origin).Milliseconds()
	for key, p := range w.pending {
		if now >= p.deadline {
			delete(w.pending, key)
		}
	}
	if len(w.pending) >= 8 {
		return &bridge.Error{Kind: bridge.Capacity}
	}
	key, err := w.session.nextKey()
	if err != nil {
		return err
	}
	c, err := command(op.value, key, step.QueueTTLMS)
	if err != nil {
		return err
	}
	deadline := step.DeadlineMS
	if step.N == 0 {
		deadline = now + 5000
	}
	w.pending[key] = pending{plan: op.plan, role: step.Role, verb: c.Verb, contact: step.ContactMS, deadline: deadline}
	op.plan.contact, op.plan.deadline = step.ContactMS, step.DeadlineMS
	if step.Role == gateway.StepHelper {
		op.plan.helperSent = true
	}
	if step.Role == gateway.StepEffect && c.Verb == 9 {
		w.returnPlan = op.plan
	}
	if step.Role == gateway.StepEffect && c.Verb == 14 {
		op.plan.itemKey = key
	}
	n, err := w.session.port.Write(ctx, c)
	if fn := w.session.cfg.Handoff; fn != nil {
		fn(Handoff{N: step.N, Index: step.Index, Role: step.Role, Key: key, RawN: n})
	}
	if n == 0 {
		delete(w.pending, key)
	} else {
		p := w.pending[key]
		p.n = n
		w.pending[key] = p
	}
	return err
}

var _ gateway.World = (*World)(nil)
var _ gateway.Detacher = (*World)(nil)
