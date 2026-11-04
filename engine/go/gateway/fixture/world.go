// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package fixture supplies a bounded typed world for gateway contract tests.
package fixture

import (
	"context"
	"slices"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Config supplies explicit meaning limits, initial facts and a scripted writer.
type Config struct {
	// Meaning is the gateway's shared dictionary configuration.
	Meaning meaning.Config
	// Policy classifies trusted fixture targets.
	Policy meaning.Policy
	// Initial contains owned current facts, followed by explicit domain coverage.
	Initial []meaning.Input
	// Origin is the gateway clock origin.
	Origin time.Time
	// Now is the shared test clock; nil selects time.Now.
	Now func() time.Time
	// OnWrite scripts facts and commitments after a checked command is captured.
	OnWrite func(context.Context, gateway.Step, meaning.Command) error
}

type delivery struct {
	input      meaning.Input
	resolution *gateway.Resolution
}

// World owns typed inputs and a bounded write log. It starts no goroutines.
// Producers stop before Close; Close unblocks a producer waiting in Emit.
type World struct {
	cfg     Config
	state   *state
	updates chan gateway.Update
	done    chan struct{}
	once    sync.Once
	emitMu  sync.Mutex
	mu      sync.Mutex
	writes  []Write
}

// Write is one observed gateway contact, including system service at N zero.
type Write struct {
	// N is the participant attempt, or zero for system service.
	N uint64
	// Command is the captured typed operation.
	Command meaning.Command
}

type state struct{ *meaning.State }

func (s *state) Apply(u gateway.Update, now int64) (gateway.Observation, error) {
	d, ok := u.Value.(delivery)
	if !ok {
		return gateway.Observation{}, meaning.ErrValue
	}
	if d.resolution != nil {
		return gateway.Observation{Resolutions: []gateway.Resolution{*d.resolution}}, nil
	}
	return s.State.Apply(gateway.Update{Value: d.input}, now)
}
func (s *state) Witness(a *gateway.Action, u gateway.Update) (gateway.Witness, error) {
	if _, err := s.State.Describe(a); err != nil {
		return gateway.WitnessNone, err
	}
	return gateway.WitnessNone, nil
}

// New creates a manually scripted direct world with the advertised bounds.
func New(cfg Config) (*World, error) {
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.Origin.IsZero() {
		return nil, wire.ErrConfig
	}
	projection, err := meaning.NewState(cfg.Meaning, cfg.Policy)
	if err != nil {
		return nil, err
	}
	now := cfg.Now().Sub(cfg.Origin).Milliseconds()
	for _, in := range cfg.Initial {
		in.ReceivedAt = now
		if _, err := projection.Apply(gateway.Update{Value: in}, now); err != nil {
			return nil, err
		}
	}
	return &World{cfg: cfg, state: &state{projection}, updates: make(chan gateway.Update, cfg.Meaning.Attempts.Results), done: make(chan struct{})}, nil
}

// Projection returns the actor-owned fixture state.
func (w *World) Projection() gateway.State { return w.state }

// Updates yields manually supplied typed source values.
func (w *World) Updates() <-chan gateway.Update { return w.updates }

// Emit supplies one source fact without a protocol serialization round trip.
func (w *World) Emit(ctx context.Context, in meaning.Input) error {
	in.ReceivedAt = w.cfg.Now().Sub(w.cfg.Origin).Milliseconds()
	return w.emit(ctx, delivery{input: in})
}

// Resolve supplies an exact source outcome for a contacted participant attempt.
func (w *World) Resolve(ctx context.Context, n uint64, stage wire.Stage, reason wire.Reason) error {
	r := gateway.Resolution{N: n, Stage: stage, Reason: reason, ObservedMS: w.cfg.Now().Sub(w.cfg.Origin).Milliseconds()}
	return w.emit(ctx, delivery{resolution: &r})
}
func (w *World) emit(ctx context.Context, d delivery) error {
	w.emitMu.Lock()
	defer w.emitMu.Unlock()
	select {
	case <-w.done:
		return gateway.ErrAuth
	default:
	}
	select {
	case w.updates <- gateway.Update{Value: d}:
		return nil
	case <-w.done:
		return gateway.ErrAuth
	case <-ctx.Done():
		return ctx.Err()
	}
}

// Write validates the adapter value, records one contact and invokes the script.
func (w *World) Write(ctx context.Context, step gateway.Step) error {
	op, err := meaning.OperationValue(step)
	if err != nil {
		return err
	}
	w.mu.Lock()
	if len(w.writes) >= w.cfg.Meaning.Attempts.Results {
		w.mu.Unlock()
		return meaning.ErrLimit
	}
	w.writes = append(w.writes, Write{N: step.N, Command: op.Command})
	w.mu.Unlock()
	if w.cfg.OnWrite != nil {
		return w.cfg.OnWrite(ctx, step, op.Command)
	}
	return nil
}

// Writes returns an independent snapshot of the bounded contact log.
func (w *World) Writes() []Write { w.mu.Lock(); defer w.mu.Unlock(); return slices.Clone(w.writes) }

// Close ends the source and releases a blocked producer exactly once.
func (w *World) Close() error {
	w.once.Do(func() { close(w.done); w.emitMu.Lock(); close(w.updates); w.emitMu.Unlock() })
	return nil
}
