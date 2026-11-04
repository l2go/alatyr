// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"context"
	"errors"
	"fmt"
	"math"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Identity is a trusted binding to an existing character, never wire input.
type Identity struct {
	// Account is the canonical stored account name.
	Account string
	// Character is the canonical stored character name.
	Character string
}

// Config supplies the world, its catalog, loader and shared gateway clock.
// Meaning is the single source of bounds. Load must return the bound character.
type Config struct {
	// World owns simulation and visibility.
	World *world.World
	// Catalog supplies authored presentation and skill metadata.
	Catalog *catalog.Catalog
	// Load returns a detached character matching the trusted identity.
	Load func(context.Context, Identity) (world.Character, error)
	// Meaning supplies protocol reference and publication bounds.
	Meaning meaning.Config
	// Policy grants capabilities for trusted source roles; Steps must be nil.
	Policy meaning.Policy
	// Origin is the gateway clock origin shared by this session.
	Origin time.Time
	// Now uses the same clock as the gateway; nil selects time.Now.
	Now func() time.Time
	// CloseBudget bounds the final departure attempt; zero selects 100 ms.
	CloseBudget time.Duration
	// Handoff receives numeric command correlation after Execute returns.
	// It must not block or retain mutable source data.
	Handoff func(Handoff)
}

// Handoff correlates a participant attempt with its source-assigned intent.
type Handoff struct {
	// N is the participant attempt, or zero for system service.
	N uint64
	// Intent is the source-assigned command identity.
	Intent world.IntentID
}

// OpenError retains an incarnation whose opening cleanup could not be saved.
// The host retries Leave with Owner; no background retry is started.
type OpenError struct {
	// Owner is the incarnation retained for host-owned cleanup retries.
	Owner world.Ref
	// Err joins the opening failure and its unsuccessful cleanup.
	Err error
}

// Error describes the joined opening and cleanup failures.
func (e *OpenError) Error() string { return fmt.Sprintf("worldlink: open cleanup: %v", e.Err) }

// Unwrap exposes the joined failures to errors.Is and errors.As.
func (e *OpenError) Unwrap() error { return e.Err }

// Session owns one incarnation's gateway adapter. It starts no goroutines.
// The gateway serializes Write, Receive and all State calls; Close may run
// concurrently and releases the incarnation exactly once.
type Session struct {
	cfg          Config
	owner        world.Ref
	class        int32
	mailbox      *world.Mailbox
	tr           translator
	state        *state
	once         sync.Once
	stop         chan struct{}
	closeErr     error
	errMu        sync.Mutex
	writeErr     error
	activeCancel context.CancelFunc // guarded by errMu; never retains a Context field
	pending      map[world.IntentID]*plan
}

// publicationsPerObject covers departure, appearance, target, movement and
// resource refreshes in a relocation burst. Overflow remains recoverable.
const publicationsPerObject = 8

type plan struct {
	owner    *state
	record   wire.Record
	plan     meaning.Plan
	n        uint64
	contact  int64
	request  world.Request
	deadline int64
	caused   bool
}
type operation struct {
	plan    *plan
	command meaning.Command
}

// Publication is one typed source delivery to the gateway state.
// It is local trusted composition data, never participant input.
type Publication struct {
	// Event is an immutable source observation.
	Event world.Event
	// Commit carries a source-authored outcome.
	Commit world.Commit
	// Resync asks for an authoritative checkpoint.
	Resync bool
	// Err ends the source scope.
	Err error
}
type state struct {
	session *Session
	cursor  *meaning.Cursor
}

// Open enters the bound character and attaches its one direct observer.
// The opening context does not own the admitted session's lifetime.
func Open(ctx context.Context, cfg Config, id Identity) (*Session, gateway.State, error) {
	if cfg.World == nil || cfg.Catalog == nil || cfg.Load == nil || cfg.Origin.IsZero() || cfg.Meaning.Objects < 2 || cfg.Meaning.Attempts.Pending < 1 || cfg.Meaning.Objects > (math.MaxInt-cfg.Meaning.Attempts.Pending)/publicationsPerObject || cfg.Meaning.Advertised[20] == 0 || cfg.Meaning.Advertised[20] > uint64((1<<63-1)/int64(time.Millisecond)) || cfg.Policy.Steps != nil {
		return nil, nil, wire.ErrConfig
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.CloseBudget == 0 {
		cfg.CloseBudget = 100 * time.Millisecond
	}
	if cfg.CloseBudget < 0 || cfg.CloseBudget > 100*time.Millisecond || cfg.Now().Before(cfg.Origin) {
		return nil, nil, wire.ErrConfig
	}
	cursor, err := meaning.NewCursor(cfg.Meaning)
	if err != nil {
		return nil, nil, err
	}
	c, err := cfg.Load(ctx, id)
	if err != nil {
		return nil, nil, err
	}
	if c.ObjectID <= 0 || c.Account != id.Account || c.Name != id.Character {
		return nil, nil, gateway.ErrAuth
	}
	entry, err := cfg.World.Enter(ctx, c.Clone())
	if err != nil {
		return nil, nil, err
	}
	fail := func(err error) (*Session, gateway.State, error) {
		cleanup, cancel := context.WithTimeout(context.Background(), cfg.CloseBudget)
		defer cancel()
		if cleanupErr := cfg.World.Leave(cleanup, entry.Self.Entity); cleanupErr != nil {
			err = &OpenError{Owner: entry.Self.Entity, Err: errors.Join(err, cleanupErr)}
		}
		return nil, nil, err
	}
	// A relocation can forget and reveal the full visibility set in one turn.
	// Eight publications per visible object cover that burst, then resynchronize.
	scene, box, err := cfg.World.Observe(ctx, entry.Self.Entity, cfg.Meaning.Objects*publicationsPerObject, cfg.Meaning.Attempts.Pending)
	if err != nil {
		return fail(err)
	}
	s := &Session{cfg: cfg, owner: entry.Self.Entity, class: c.ClassID, mailbox: box, stop: make(chan struct{}), pending: map[world.IntentID]*plan{}}
	s.tr = translator{cat: cfg.Catalog, origin: scene.End.Time, clockOrigin: cfg.World.ClockOrigin(), gatewayOrigin: cfg.Origin, textBytes: cfg.Meaning.Wire.StringBytes, listItems: cfg.Meaning.Wire.ListItems}
	s.state = &state{session: s, cursor: cursor}
	view, err := s.tr.view(scene)
	if err != nil || !view.Ready {
		return fail(errors.Join(err, errSource))
	}
	if _, err = cursor.Capture(view, cfg.Now().Sub(cfg.Origin).Milliseconds()); err != nil {
		return fail(err)
	}
	return s, s.state, nil
}

// Owner returns the exact world incarnation for host-owned cleanup retries.
func (s *Session) Owner() world.Ref { return s.owner }

// Updates is unused by direct receivers. The gateway consumes Notify/Receive.
func (s *Session) Updates() <-chan gateway.Update { return nil }

// Notify wakes the gateway when the source mailbox can be read.
func (s *Session) Notify() <-chan struct{} { return s.mailbox.Notify() }

// Capacity is the source mailbox's state/event and exact-outcome budget.
func (s *Session) Capacity() int {
	return s.cfg.Meaning.Objects*publicationsPerObject + s.cfg.Meaning.Attempts.Pending
}

// Receive removes one typed publication without another queue or goroutine.
func (s *Session) Receive() (gateway.Update, bool) {
	p, ok := s.mailbox.Next()
	if !ok {
		if s.mailbox.Drained() {
			return gateway.Update{Value: Publication{Err: world.ErrStopped}}, true
		}
		return gateway.Update{}, false
	}
	u := gateway.Update{Value: Publication{Event: p.Event, Commit: p.Commit, Resync: p.Resync}, Resync: p.Resync}
	if v, ok := p.Event.(world.Teleported); ok && v.Entity == s.owner {
		u.Relocate = true
	}
	if p.Event != nil {
		ms := s.tr.gatewayMS(p.Event.Time())
		u.ReceivedMS = &ms
	}
	return u, true
}

// Close departs the incarnation and returns its stable save result.
func (s *Session) Close() error {
	s.once.Do(func() {
		close(s.stop)
		s.errMu.Lock()
		cancelActive := s.activeCancel
		s.errMu.Unlock()
		if cancelActive != nil {
			cancelActive()
		}
		s.mailbox.Stop()
		ctx, cancel := context.WithTimeout(context.Background(), s.cfg.CloseBudget)
		defer cancel()
		err := s.cfg.World.LeaveObserver(ctx, s.owner, s.mailbox)
		s.errMu.Lock()
		s.closeErr = errors.Join(err, s.writeErr)
		s.errMu.Unlock()
	})
	return s.closeErr
}

// operation owns cancellation only for its stack lifetime. Gateway calls are
// serialized; Close may cancel the current call without another goroutine.
func (s *Session) operation(ctx context.Context) (context.Context, func(), error) {
	request, cancel := context.WithCancel(ctx)
	s.errMu.Lock()
	select {
	case <-s.stop:
		s.errMu.Unlock()
		cancel()
		return nil, nil, context.Canceled
	default:
	}
	s.activeCancel = cancel
	s.errMu.Unlock()
	return request, func() { s.errMu.Lock(); s.activeCancel = nil; s.errMu.Unlock(); cancel() }, nil
}

// capture takes a current scene, optionally fencing the observer's queued state.
// A fenced capture during teleport also executes VerbAppear with that fence
// to complete the handshake, then captures the resulting scene; it is not a
// pure read of the world.
func (s *Session) capture(fence bool) (world.Scene, error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(s.cfg.Meaning.Advertised[20])*time.Millisecond)
	defer cancel()
	ctx, end, err := s.operation(ctx)
	if err != nil {
		return world.Scene{}, err
	}
	defer end()
	var scene world.Scene
	if fence {
		scene, err = s.cfg.World.CaptureObserver(ctx, s.owner, s.mailbox)
	} else {
		scene, err = s.cfg.World.Capture(ctx, s.owner)
	}
	if err != nil {
		return scene, err
	}
	if fence && scene.Self.Teleporting {
		gap := scene.End.Discontinuous
		commit, err := s.cfg.World.Execute(ctx, world.Request{Owner: s.owner, Verb: world.VerbAppear, Fence: scene.End.Fence})
		if err != nil {
			return world.Scene{}, err
		}
		if commit.Outcome.Stage != world.StageDone {
			return world.Scene{}, errSource
		}
		current, err := s.cfg.World.Capture(ctx, s.owner)
		current.Begin.Discontinuous, current.End.Discontinuous = gap, gap
		return current, err
	}
	return scene, nil
}

func (s *state) Snapshot(now int64) (gateway.Snapshot, error) {
	scene, err := s.session.capture(true)
	if err != nil {
		return gateway.Snapshot{}, err
	}
	view, err := s.session.tr.view(scene)
	if err != nil {
		return gateway.Snapshot{}, err
	}
	if scene.End.Discontinuous {
		s.cursor.Reset()
	}
	// Capturing takes a loop turn; the caller's timestamp may precede the fence.
	now = max(now, s.session.cfg.Now().Sub(s.session.cfg.Origin).Milliseconds())
	facts, err := s.cursor.Capture(view, now)
	return gateway.Snapshot{Facts: facts, Ready: view.Ready, Domains: view.Domains, Shared: true}, err
}

func (s *state) Apply(u gateway.Update, now int64) (gateway.Observation, error) {
	p, ok := u.Value.(Publication)
	if !ok {
		return gateway.Observation{}, meaning.ErrValue
	}
	if p.Err != nil {
		return gateway.Observation{WorldClosed: true}, nil
	}
	if p.Resync {
		s.cursor.Reset()
		return gateway.Observation{}, nil
	}
	if p.Commit.Outcome.Intent.N != 0 {
		binding, ok := s.session.pending[p.Commit.Outcome.Intent]
		if !ok {
			return gateway.Observation{}, nil
		}
		resolution := gateway.Resolution{N: binding.n, ObservedMS: max(binding.contact, s.session.tr.gatewayMS(p.Commit.Time))}
		switch p.Commit.Outcome.Stage {
		case world.StageDone:
			resolution.Stage = wire.StageDone
		case world.StageRefused:
			resolution.Stage = wire.StageRefused
			resolution.Reason = refusal(p.Commit.Outcome.Reason)
		case world.StageExpired:
			resolution.Stage = wire.StageRefused
			resolution.Reason = wire.ReasonTimeout
		case world.StageCanceled, world.StageYielded:
			resolution.Stage = wire.StageUnknown
			resolution.Reason = wire.ReasonInterrupted
		default:
			return gateway.Observation{}, nil
		}
		delete(s.session.pending, p.Commit.Outcome.Intent)
		return gateway.Observation{Resolutions: []gateway.Resolution{resolution}}, nil
	}
	if p.Event == nil {
		return gateway.Observation{}, nil
	}
	inputs, source, err := s.session.tr.events(p.Event, s.session.class, s.session.owner)
	if err != nil {
		return gateway.Observation{}, err
	}
	out := gateway.Observation{}
	binding := s.session.pending[p.Event.Intent()]
	for _, in := range inputs {
		r, err := s.cursor.Record(in, source, now)
		if err != nil {
			return gateway.Observation{}, err
		}
		if binding != nil && !binding.caused && completing(binding.plan.Command.Kind, in.Event.Kind) {
			r.Fields = append(r.Fields, wire.U(32, true, binding.n))
			binding.caused = true
		}
		out.Facts = append(out.Facts, r)
	}
	return out, nil
}
func completing(command, event string) bool {
	switch command {
	case meaning.Talk, meaning.Say:
		return event == meaning.Spoke || event == meaning.Shop || event == meaning.Offer || event == meaning.Practice
	case meaning.Take:
		return event == meaning.Took
	case meaning.Buy, meaning.Sell, meaning.UseItem:
		return event == meaning.Inventory
	case meaning.Learn:
		return event == meaning.Known
	case meaning.Rise:
		return event == meaning.Revived
	}
	return false
}
func refusal(reason world.Reason) wire.Reason {
	if reason == world.ReasonInvalid {
		return wire.ReasonInvalid
	}
	return wire.ReasonWorld
}

func (s *state) environment(now int64) (meaning.Environment, world.Scene, error) {
	scene, err := s.session.capture(false)
	if err != nil {
		return meaning.Environment{}, world.Scene{}, err
	}
	view, err := s.session.tr.view(scene)
	if err != nil {
		return meaning.Environment{}, world.Scene{}, err
	}
	return s.cursor.Environment(view, now, s.session.cfg.Policy.Classify), scene, nil
}
func (s *state) Plan(r wire.Record, now int64) (*gateway.Action, wire.Reason) {
	env, scene, err := s.environment(now)
	if err != nil {
		return nil, wire.ReasonBackendError
	}
	captured, reason := meaning.PlanIntent(r, env)
	if reason != wire.ReasonNone {
		return nil, reason
	}
	n, _ := wire.Find(r.Fields, 1)
	command := captured.Command
	if command.Kind == meaning.Cast {
		command.Count = int64(captured.SkillLevel)
	}
	var req world.Request
	if command.Kind != meaning.Aim && command.Kind != meaning.Focus {
		req, err = request(command, scene)
		if err != nil {
			return nil, wire.ReasonInvalid
		}
	}
	p := &plan{owner: s, record: r, plan: captured, n: n.U, request: req}
	a := &gateway.Action{Effective: captured.Effect.Effective, Class: captured.Effect.Class, Waiting: captured.Effect.Waiting, Value: p, Local: captured.Command.Kind == meaning.Aim || captured.Command.Kind == meaning.Focus}
	a.SurviveRelocation = captured.Command.Kind == meaning.Rise
	if !a.Local {
		a.Steps = []gateway.Step{{Value: operation{plan: p, command: captured.Command}}}
	}
	return a, wire.ReasonNone
}
func (s *state) plan(a *gateway.Action) (*plan, error) {
	if a == nil {
		return nil, meaning.ErrValue
	}
	p, ok := a.Value.(*plan)
	if !ok || p == nil || p.owner != s {
		return nil, meaning.ErrValue
	}
	return p, nil
}
func (s *state) Validate(a *gateway.Action, now int64) wire.Reason {
	p, err := s.plan(a)
	if err != nil {
		return wire.ReasonInvalid
	}
	env, _, err := s.environment(now)
	if err != nil {
		return wire.ReasonBackendError
	}
	again, reason := meaning.PlanIntent(p.record, env)
	if reason != wire.ReasonNone {
		return reason
	}
	if again.Effect.Effective != p.plan.Effect.Effective {
		return wire.ReasonInvalid
	}
	return wire.ReasonNone
}
func (s *state) Start(a *gateway.Action) error { _, err := s.plan(a); return err }
func (s *state) Confirmed(a *gateway.Action) (bool, error) {
	_, err := s.plan(a)
	return err == nil, err
}
func (s *state) Witness(a *gateway.Action, u gateway.Update) (gateway.Witness, error) {
	_, err := s.plan(a)
	return gateway.WitnessNone, err
}
func (s *state) CandidateValid(a *gateway.Action) (bool, error) {
	_, err := s.plan(a)
	return false, err
}
func (s *state) Proof(a *gateway.Action, now int64) (wire.Record, error) {
	return wire.Record{}, meaning.ErrValue
}
func (s *state) Invalidate() {
	s.cursor.Reset()
	// Relocation retires every contacted attempt except the return that causes
	// it. Release those exact-outcome reservations before accepting new work.
	for id, binding := range s.session.pending {
		if binding.plan.Command.Kind != meaning.Rise {
			delete(s.session.pending, id)
			s.session.mailbox.Forget(id)
		}
	}
}
func (s *state) Stop() *gateway.Step {
	scene, err := s.session.capture(false)
	if err != nil || scene.Self.Dead {
		return nil
	}
	return &gateway.Step{Value: operation{command: meaning.Command{Kind: meaning.Walk, X: scene.Self.Char.X, Y: scene.Self.Char.Y, Z: scene.Self.Char.Z}}}
}

var _ gateway.World = (*Session)(nil)
var _ gateway.Receiver = (*Session)(nil)
var _ gateway.State = (*state)(nil)
