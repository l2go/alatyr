// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"io"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// No socket, backend connection or Java process is involved.
type scheduleWorld struct {
	writes  []string
	updates chan Update
}

func (w *scheduleWorld) Updates() <-chan Update { return w.updates }
func (w *scheduleWorld) Close() error           { return nil }
func (w *scheduleWorld) Write(_ context.Context, step Step) error {
	if len(w.writes) < 1024 {
		w.writes = append(w.writes, step.Value.(string))
	}
	return nil
}

type scheduleTransport struct{}

func (scheduleTransport) Read(time.Time) ([]byte, error) { return nil, io.EOF }
func (scheduleTransport) Write([]byte, time.Time) error  { return nil }
func (scheduleTransport) Close() error                   { return nil }

// Only the projection methods relevant to this scheduling test are supplied.
// An unexpected use of an embedded State method fails instead of inventing backend
// facts or a successful teleport. There is no synthetic Complete(objects).
type scheduleState struct {
	State
	ready bool
	plans int
}

func (s *scheduleState) Snapshot(int64) (Snapshot, error) {
	if s.ready {
		return Snapshot{Ready: true}, nil
	}
	return Snapshot{Domains: []string{"objects"}}, nil
}
func (s *scheduleState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	s.plans++
	return &Action{Effective: 2, Local: true}, wire.ReasonNone
}
func (s *scheduleState) Validate(*Action, int64) wire.Reason { return wire.ReasonNone }
func (s *scheduleState) Apply(Update, int64) (Observation, error) {
	return Observation{}, nil
}
func (s *scheduleState) Invalidate() { s.ready = false }

type scheduleRig struct {
	game  *game
	conn  *connection
	world *scheduleWorld
	state *scheduleState
	now   time.Time
	input input
}

func scheduleNewRig(t *testing.T) *scheduleRig {
	t.Helper()
	cfg := testConfig(t)
	codec := cfg.Codec
	attempts, err := proto.New(cfg.Attempts)
	if err != nil {
		t.Fatal(err)
	}
	grant, err := attempts.Take(1)
	if err != nil {
		t.Fatal(err)
	}
	r := &scheduleRig{now: time.Unix(100, 0), world: &scheduleWorld{}, state: &scheduleState{ready: true}}
	g := &Gateway{
		stop: make(chan struct{}), start: r.now, games: map[string]*game{},
		cfg: Config{
			Codec: codec, Now: func() time.Time { return r.now },
			Limits: cfg.Limits, Teleports: cfg.Teleports, Attempts: cfg.Attempts,
			OutBytes: cfg.OutBytes, Write: 5 * time.Second,
			Checkpoint: 10 * time.Second, Hello: 5 * time.Second,
			Ping: 15 * time.Second, Pong: 30 * time.Second, TargetWait: 1500 * time.Millisecond,
			Ticks: cfg.Ticks,
		},
	}
	s := &game{
		gate: g, id: 1, world: r.world, state: r.state, attempts: attempts,
		input: make(chan input, cfg.Connections),
		done:  make(chan struct{}),
		conns: map[uint64]*connection{}, actions: map[uint64]*execution{},
		rate: bucket{tokens: 10, burst: 10, rate: 20},
	}
	c := &connection{
		game: s, id: 1, t: scheduleTransport{}, act: true, ready: true, hello: true,
		mode: wire.ModeAct, signal: make(chan struct{}, 1), done: make(chan struct{}),
		service: bucket{tokens: 4, burst: 4, rate: 2},
	}
	s.conns[c.id] = c
	g.games["offline"] = s
	r.game, r.conn = s, c

	intent := testIntent(1, 2, grant.Epoch)
	raw, err := codec.Encode([]wire.Record{intent})
	if err != nil {
		t.Fatal(err)
	}
	records, err := codec.Decode(raw)
	if err != nil {
		t.Fatal(err)
	}
	r.input = input{conn: c, records: records, bytes: len(raw)}
	s.process(r.input)
	s.service(context.Background()) // The original Aim completes locally, before the relocation.
	entry, ok := attempts.Lookup(1)
	if !ok || !entry.Terminal || !entry.Sent || r.state.plans != 1 || len(r.world.writes) != 0 {
		t.Fatal("seed must be a completed, valid local Aim", entry)
	}
	if !s.observe(Update{Relocate: true, Service: []Step{{Value: "ack"}, {Value: "probe"}}}) {
		t.Fatal("relocation was rejected")
	}
	r.drainOutput()
	return r
}

// Simulates an immediately reading participant. Output backpressure cannot be
// the cause of the failure, and no unbounded test buffer accumulates results.
func (r *scheduleRig) drainOutput() {
	c := r.conn
	c.mu.Lock()
	c.queue = nil
	c.bytes = 0
	c.snapshot = nil
	c.cursor = 0
	c.mu.Unlock()
}

func TestServiceProgressUnderCachedIntentInput(t *testing.T) {
	r := scheduleNewRig(t)
	const turns = 256
	for i := 0; i < turns; i++ {
		r.now = r.now.Add(time.Millisecond)
		// A legal worst-case run-loop schedule: an ordinary world update is
		// observed, then another already-arrived duplicate is ready at service.
		// A cached duplicate is protocol-valid and does not spend a new Intent
		// token, allocate another n, or plan another world command.
		if !r.game.observe(Update{}) {
			t.Fatal("ordinary update closed the game")
		}
		r.input.received = r.game.gate.now()
		if len(r.game.input) < cap(r.game.input) {
			r.game.input <- r.input
		}
		r.game.service(context.Background())
		if i == 1 && (len(r.world.writes) != 2 || r.world.writes[0] != "ack" || r.world.writes[1] != "probe") {
			t.Fatal("pending service must finish in its first two turns", r.world.writes)
		}
		r.drainOutput()
		if r.game.closed || r.conn.closing || r.state.plans != 1 || r.game.attempts.NextN() != 2 {
			t.Fatal("input ceased to be an accepted replay", i)
		}
	}
	if len(r.world.writes) != 2 || len(r.game.services) != 0 {
		t.Fatal("pending service did not finish exactly once", r.world.writes)
	}
}

func TestParticipantProgressUnderContinuousService(t *testing.T) {
	r := scheduleNewRig(t)
	ctx, cancel := context.WithCancel(context.Background())
	r.game.gate.cfg.Batch = time.Millisecond
	r.world.updates = make(chan Update, 1)
	processed := make(chan struct{}, 64)
	r.game.gate.cfg.Diagnostic = func(s Summary) {
		if s.Record == wire.Intent {
			select {
			case processed <- struct{}{}:
			default:
			}
		}
	}
	producerDone := make(chan struct{})
	go func() {
		defer close(producerDone)
		for {
			select {
			case r.world.updates <- Update{Service: []Step{{Value: "ack"}}}:
			case <-ctx.Done():
				return
			}
		}
	}()
	runDone, writerDone := make(chan struct{}), make(chan struct{})
	go func() { defer close(runDone); r.game.run(ctx) }()
	go func() { defer close(writerDone); r.conn.writeLoop() }()
	t.Cleanup(func() {
		cancel()
		<-runDone
		r.conn.close()
		<-writerDone
		<-producerDone
	})
	deadline := time.NewTimer(2 * time.Second)
	defer deadline.Stop()
	for i := 0; i < 64; i++ {
		select {
		case r.game.input <- r.input:
		case <-deadline.C:
			t.Fatal("participant input was not admitted during continuous service")
		}
		select {
		case <-processed:
		case <-deadline.C:
			t.Fatal("participant input starved during continuous service")
		}
	}
}

func TestServiceProgressWhenInputIsEmpty(t *testing.T) {
	r := scheduleNewRig(t)
	r.game.service(context.Background())
	r.game.service(context.Background())
	if len(r.world.writes) != 2 || r.world.writes[0] != "ack" || r.world.writes[1] != "probe" || len(r.game.services) != 0 {
		t.Fatal("control: service should progress without participant input", r.world.writes)
	}
}
