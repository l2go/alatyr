// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"math"
	"net"
	"strings"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// The actor owns every probe. Encode and diagnostics detect output production
// even if run's bounded cleanup subsequently clears the connection queue.
type closeCodec struct {
	Codec
	closed     bool
	afterClose []wire.Record
}

func TestClosedScopeRejectsLateRuntimeWork(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		r := scheduleNewRig(t)
		r.game.services = []Step{{Value: "service"}}
		s := &actorState{game: r.game, errorState: &errorState{testState: &testState{}}}
		r.game.state = s
		codec := &closeCodec{Codec: r.game.gate.cfg.Codec}
		r.game.gate.cfg.Codec = codec
		w := &actorWorld{World: r.world, game: r.game, onClose: func() { codec.closed = true }}
		r.game.world = w
		r.game.fail(wire.CloseInternal)
		var summaries []Summary
		r.game.gate.cfg.Diagnostic = func(summary Summary) { summaries = append(summaries, summary) }
		r.conn.closing = false // Exercise session guards, not the connection guard.
		r.game.service(context.Background())
		r.game.expire()
		if r.game.observe(Update{}) {
			t.Error("closed scope accepted an observation")
		}
		r.game.commitReadyWitnesses()
		r.game.checkpointDone(r.conn)
		r.game.grant(r.conn)
		r.game.intent(r.conn, testIntent(2, 4, 1))
		r.game.process(input{conn: r.conn, records: []wire.Record{testIntent(2, 4, 1)}})
		if r.game.receive(r.conn, testIntent(2, 4, 1)) || r.game.checkpoint(r.conn, nil, false) {
			t.Error("closed scope accepted receive/checkpoint")
		}
		if r.game.fact(closeFact()) || r.game.result(record(wire.Result)) {
			t.Error("closed scope accepted Fact/Result")
		}
		r.game.broadcast(record(wire.Ping))
		r.game.send(r.conn, record(wire.Ping))
		r.game.drop(r.conn, wire.CloseFrameError)
		if err := r.game.write(context.Background(), Step{Value: "effect"}); !errors.Is(err, net.ErrClosed) {
			t.Errorf("late write error = %v, want net.ErrClosed", err)
		}
		// A closed actor must return even without any input, tick, or cancellation.
		r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) { return nil, func() {} }
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan struct{})
		go func() { defer close(done); r.game.run(ctx) }()
		synctest.Wait()
		select {
		case <-done:
		default:
			t.Error("closed actor waited for another event")
		}
		cancel()
		<-done
		if len(s.afterClose) != 0 || w.closes != 1 || w.writesAfterClose != 0 || len(r.world.writes) != 0 || len(codec.afterClose) != 0 || len(summaries) != 0 {
			t.Errorf("late work: State=%v closes=%d World.Write=%d/%v encoded=%d summaries=%d",
				s.afterClose, w.closes, w.writesAfterClose, r.world.writes, len(codec.afterClose), len(summaries))
		}
	})
}

func (c *closeCodec) Encode(rs []wire.Record) ([]byte, error) {
	if c.closed {
		c.afterClose = append(c.afterClose, rs...)
	}
	return c.Codec.Encode(rs)
}

type closeState struct {
	*actorState
	faultConn   *connection
	fault       string
	armed       bool
	snapshots   int
	observation Observation
	confirmed   bool
	reject      bool
}

func closeFact() wire.Record {
	return record(wire.Fact, wire.U(1, true, 7), wire.U(2, true, 1), wire.S(3, true, 0),
		wire.U(4, true, 1), wire.Data(5, true, "objects"), wire.U(6, true, 1))
}

func closeTestCodec(t *testing.T) Codec {
	t.Helper()
	bounds := wire.Limits{FrameBytes: 4096, StringBytes: 256, ListItems: 16,
		Depth: 4, Records: 16, Fields: 32, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128}
	registry := wire.Envelopes(wire.Schema{30: wire.Required(wire.UInt)}, bounds)
	registry[wire.Fact][32] = wire.Essential(wire.UInt)
	codec, err := wire.New(bounds, registry)
	if err != nil {
		t.Fatal(err)
	}
	return codec
}

func (s *closeState) Snapshot(int64) (Snapshot, error) {
	s.called("Snapshot")
	if !s.armed {
		return Snapshot{Ready: true}, nil
	}
	s.snapshots++
	// Simulate the in-flight frame draining while a restart captures State.
	// The close path has room for its legitimate final Results and Bye.
	s.faultConn.mu.Lock()
	s.faultConn.active = 0
	s.faultConn.mu.Unlock()
	if s.fault == "snapshot" {
		return Snapshot{}, errors.New("adapter: snapshot unavailable")
	}
	return Snapshot{Ready: true, Facts: []wire.Record{closeFact()}}, nil
}

func (s *closeState) Proof(*Action, int64) (wire.Record, error) {
	s.called("Proof")
	return closeFact(), nil
}

func (s *closeState) Apply(Update, int64) (Observation, error) {
	s.called("Apply")
	return s.observation, nil
}

func (s *closeState) Witness(*Action, Update) (Witness, error) {
	s.called("Witness")
	return WitnessNone, nil
}

func (s *closeState) Confirmed(*Action) (bool, error) {
	s.called("Confirmed")
	return s.confirmed, nil
}

func (s *closeState) Validate(a *Action, _ int64) wire.Reason {
	s.called("Validate")
	if s.reject && a.Value == "reject" {
		return wire.ReasonInvalid
	}
	return wire.ReasonNone
}

type closeWriteWorld struct {
	*actorWorld
	failWrite bool
}

func (w *closeWriteWorld) Write(ctx context.Context, step Step) error {
	if w.failWrite {
		if w.game.closed {
			w.writesAfterClose++
		}
		return errors.New("world: write failed")
	}
	return w.actorWorld.Write(ctx, step)
}

// Each row reaches the real actor through input, an update, or a tick. Sequence
// exhaustion happens in the live Fact or in a Result-triggered restart snapshot;
// snapshot errors always happen during that same bounded restart path.
func TestRunStopsAfterOutputClosesScope(t *testing.T) {
	for _, site := range []string{"checkpoint_witness", "full_window", "expired_results", "observed_results",
		"service_results", "service_checkwrite", "service_validate", "service_helper_timeout",
		"service_interrupt", "service_no_steps", "service_recheck", "service_backend_failure", "service_effect_sent",
		"observed_relocation", "observed_facts", "observed_readiness", "expired_connections",
		"input_batch", "control_transfer", "close_drain"} {
		for _, fault := range []string{"sequence", "snapshot"} {
			t.Run(site+"/"+fault, func(t *testing.T) {
				synctest.Test(t, func(t *testing.T) {
					r := scheduleNewRig(t)
					r.game.gate.cfg.Codec = closeTestCodec(t)
					if _, err := r.game.gate.cfg.Codec.Encode([]wire.Record{closeFact()}); err != nil {
						t.Fatalf("invalid probe fact: %v", err)
					}
					r.game.services = nil
					r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
					s := &closeState{faultConn: r.conn, fault: fault, actorState: &actorState{game: r.game,
						errorState: &errorState{testState: &testState{}}}}
					r.game.state = s
					_, control := r.game.attempts.Controller()
					add := func(n uint64, a *Action, sent bool) {
						t.Helper()
						s.action = a
						r.game.intent(r.conn, testIntent(n, 4, control))
						if r.game.actions[n] == nil {
							t.Fatalf("attempt %d was not prepared", n)
						}
						if sent {
							if err := r.game.attempts.Contact(n, r.game.gate.now()); err != nil {
								t.Fatal(err)
							}
							if _, err := r.game.attempts.Sent(n, false); err != nil {
								t.Fatal(err)
							}
						}
					}
					ticks := make(chan time.Time, 1)
					wantWrites := 0
					observer := func(id uint64) *connection {
						c := &connection{game: r.game, id: id, t: scheduleTransport{}, hello: true,
							mode: wire.ModeObserve, signal: make(chan struct{}, 1), done: make(chan struct{})}
						r.game.conns[id] = c
						return c
					}
					update := func(u Update) {
						r.world.updates = make(chan Update, 1)
						r.world.updates <- u
					}
					switch site {
					case "checkpoint_witness", "full_window":
						add(2, &Action{Effective: 4, Class: "take", Waiting: true,
							AfterCheckpoint: site == "checkpoint_witness", FullWindow: site == "full_window"}, true)
						r.game.actions[2].hasCandidate = true
						r.game.actions[2].candidate = r.game.gate.now()
						// A later queued action detects a service continuation.
						add(3, &Action{Effective: 4, Steps: []Step{{Value: "effect"}}}, false)
						if site == "checkpoint_witness" {
							r.conn.generation, r.conn.capturedReady = 1, true
							r.game.input <- input{conn: r.conn, checkpoint: 1}
						} else {
							r.now = r.now.Add(5 * time.Second)
							ticks <- r.now
						}
					case "expired_results", "observed_results", "observed_relocation", "close_drain":
						add(2, &Action{Effective: 4, Class: "take", Waiting: true}, true)
						add(3, &Action{Effective: 4, Class: "talk", Waiting: true}, true)
						if site == "expired_results" {
							r.now = r.now.Add(5 * time.Second)
							ticks <- r.now
						} else if site == "observed_results" {
							s.observation.Failures = []Failure{{N: 2, Reason: wire.ReasonInterrupted}, {N: 3, Reason: wire.ReasonInterrupted}}
							update(Update{})
						} else if site == "observed_relocation" {
							update(Update{Relocate: true})
						} else {
							s.observation.WorldClosed = true
							s.faultConn = observer(2)
							update(Update{})
						}
					case "observed_facts":
						s.observation.Facts = []wire.Record{closeFact(), closeFact()}
						update(Update{})
					case "observed_readiness":
						r.conn.ready = false
						observer(2)
						update(Update{})
					case "expired_connections":
						r.game.gate.cfg.Checkpoint = time.Minute
						r.now = r.now.Add(r.game.gate.cfg.Ping)
						observer(2)
						// The other connection must not be pinged after closure.
						r.game.conns[2].ready = true
						r.game.conns[2].lastPing = r.game.gate.now()
						ticks <- r.now
					case "input_batch":
						r.game.input <- input{conn: r.conn, records: []wire.Record{
							record(wire.Ping, wire.U(1, true, 1)), testIntent(2, 4, control)}}
					case "control_transfer":
						c := observer(2)
						c.mode, c.ready = wire.ModeAct, true
						c.service = bucket{tokens: 4, burst: 4, rate: 2}
						r.game.input <- input{conn: c, records: []wire.Record{record(wire.Control, wire.U(1, true, uint64(wire.ControlTake)))}}
					default:
						a := &Action{Effective: 4, Steps: []Step{{Value: "effect"}}}
						switch site {
						case "service_results", "service_interrupt":
							a.Local, a.Steps = true, nil
						case "service_validate":
							s.reject, a.Value = true, "reject"
						case "service_no_steps":
							a.Steps = nil
						case "service_recheck", "service_helper_timeout":
							a.Steps = []Step{{Value: "helper", Await: true}, {Value: "effect"}}
						case "service_effect_sent":
							wantWrites = 1
						}
						add(2, a, false)
						later := &Action{Effective: 4, Steps: []Step{{Value: "effect"}}}
						if site == "service_interrupt" {
							later.Steps[0].Await = true
						}
						add(3, later, false)
						switch site {
						case "service_no_steps":
							if err := r.game.attempts.Contact(2, r.game.gate.now()); err != nil {
								t.Fatal(err)
							}
						case "service_checkwrite":
							if _, err := r.game.attempts.Take(2); err != nil {
								t.Fatal(err)
							}
						case "service_helper_timeout":
							r.game.actions[2].waiting = true
							r.now = r.now.Add(r.game.gate.cfg.TargetWait)
						case "service_recheck":
							r.game.actions[2].index = 1
						}
						ticks <- r.now
					}
					r.drainOutput()
					s.armed = true
					if fault == "sequence" {
						r.game.seq = math.MaxUint64
					}
					if site != "observed_readiness" && (fault == "snapshot" || site == "expired_results" || site == "observed_results" ||
						site == "observed_relocation" || site == "expired_connections" || site == "input_batch" ||
						site == "control_transfer" || site == "close_drain" || strings.HasPrefix(site, "service_")) {
						if site == "checkpoint_witness" {
							// The controlling checkpoint finishes; an observer is
							// still transferring when the resulting live Fact fans out.
							s.faultConn = observer(2)
						}
						s.faultConn.transfer = true
						s.faultConn.active = r.game.gate.cfg.OutBytes
					}
					r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) { return ticks, func() {} }
					codec := &closeCodec{Codec: r.game.gate.cfg.Codec}
					r.game.gate.cfg.Codec = codec
					var afterClose []Summary
					var final []wire.Record
					var reason wire.CloseReason
					w := &actorWorld{World: r.world, game: r.game, onClose: func() {
						// Inspect before run's cleanup can erase an invalid suffix.
						r.conn.mu.Lock()
						for _, o := range r.conn.queue {
							rs, err := codec.Decode(o.raw)
							if err != nil {
								t.Error(err)
							}
							final = append(final, rs...)
						}
						r.conn.mu.Unlock()
						codec.closed = true
					}}
					r.game.world = &closeWriteWorld{actorWorld: w, failWrite: site == "service_backend_failure"}
					r.game.gate.cfg.Diagnostic = func(summary Summary) {
						if codec.closed {
							afterClose = append(afterClose, summary)
						}
						if summary.Record == wire.Bye {
							reason = wire.CloseReason(summary.Reason)
						}
					}
					ctx, cancel := context.WithCancel(context.Background())
					done := make(chan struct{})
					go func() { defer close(done); r.game.run(ctx) }()
					synctest.Wait()
					select {
					case <-done:
					default:
						t.Error("actor did not terminate without a new event")
					}
					cancel() // Cleanup only, not the cause of actor termination.
					<-done
					wantReason := wire.CloseInternal
					if site == "close_drain" {
						wantReason = wire.CloseWorldClosed
					}
					if !r.game.closed || reason != wantReason || w.closes != 1 {
						t.Errorf("closed=%v reason=%v closes=%d, want true/%v/1", r.game.closed, reason, w.closes, wantReason)
					}
					if len(final) == 0 || final[len(final)-1].Type != wire.Bye {
						t.Errorf("output at world close = %v, want final Bye", final)
					}
					if fault == "sequence" && (site == "checkpoint_witness" || site == "full_window" || site == "observed_facts") {
						for _, record := range final {
							if record.Type == wire.Fact || record.Type == wire.Result && uintField(record, 2) == uint64(wire.StageDone) {
								t.Errorf("exhausted sequence emitted a Fact or Done: %v", record)
							}
						}
					}
					if len(codec.afterClose) != 0 || len(afterClose) != 0 || len(s.afterClose) != 0 || w.writesAfterClose != 0 || len(r.world.writes) != wantWrites {
						t.Errorf("after close: encoded=%d summaries=%d State=%v World.Write=%d; total writes=%v",
							len(codec.afterClose), len(afterClose), s.afterClose, w.writesAfterClose, r.world.writes)
					}
					wantSnapshots := 1
					if site == "close_drain" || fault == "sequence" && (site == "checkpoint_witness" || site == "full_window" || site == "observed_facts") {
						wantSnapshots = 0
					}
					if site == "observed_readiness" && fault == "sequence" {
						wantSnapshots = 2 // Readiness followed by the checkpoint capture.
					}
					if s.snapshots != wantSnapshots {
						t.Errorf("fault snapshots=%d, want %d", s.snapshots, wantSnapshots)
					}
				})
			})
		}
	}
}
