// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"io"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type fencedState struct {
	*testState
	peer                    *connection
	snapshots, plans, stops int
}

func (s *fencedState) Snapshot(int64) (Snapshot, error) {
	s.snapshots++
	if peerClosed(s.peer) {
		return Snapshot{}, errors.New("gateway test: closed peer requested snapshot")
	}
	return Snapshot{Ready: true}, nil
}

func (s *fencedState) Plan(r wire.Record, now int64) (*Action, wire.Reason) {
	s.plans++
	return s.testState.Plan(r, now)
}

func (s *fencedState) Stop() *Step {
	s.stops++
	return nil
}

func peerClosed(c *connection) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.closed || c.closing
}

type byeErrorCodec struct{ Codec }

func (c byeErrorCodec) Encode(rs []wire.Record) ([]byte, error) {
	for _, r := range rs {
		if r.Type == wire.Bye {
			return nil, wire.ErrFrame
		}
	}
	return c.Codec.Encode(rs)
}

// startFencedRun leaves the live peer and the actual actor running between
// assertions. Cancellation only joins cleanup after the assertions have run.
func startFencedRun(t *testing.T, s *game) {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() { defer close(done); s.run(ctx) }()
	t.Cleanup(func() { cancel(); <-done })
	synctest.Wait()
}

func newFencedPeers(t *testing.T) (*scheduleRig, *connection, *fencedState) {
	t.Helper()
	r := scheduleNewRig(t)
	r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) { return nil, func() {} }
	r.game.services = nil
	r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
	live := &connection{game: r.game, id: 2, t: scheduleTransport{},
		hello: true, act: true, ready: true, mode: wire.ModeAct,
		signal: make(chan struct{}, 1), done: make(chan struct{}),
		service: bucket{tokens: 4, burst: 4, rate: 2}}
	r.game.conns[live.id] = live
	s := &fencedState{testState: &testState{}, peer: r.conn}
	r.game.state = s
	return r, live, s
}

func queuedRecords(t *testing.T, c *connection) []wire.Record {
	t.Helper()
	c.mu.Lock()
	defer c.mu.Unlock()
	var records []wire.Record
	for _, out := range c.queue {
		rs, err := c.game.gate.cfg.Codec.Decode(out.raw)
		if err != nil {
			t.Fatal(err)
		}
		records = append(records, rs...)
	}
	return records
}

func assertLiveControl(t *testing.T, r *scheduleRig, live *connection, epoch uint64) {
	t.Helper()
	owner, gotEpoch := r.game.attempts.Controller()
	if r.game.closed || peerClosed(live) || owner != live.id || gotEpoch != epoch {
		t.Fatalf("live scope/control: closed=%v peerClosed=%v owner=%d epoch=%d; want false/false/%d/%d",
			r.game.closed, peerClosed(live), owner, gotEpoch, live.id, epoch)
	}
	for _, record := range queuedRecords(t, live) {
		if record.Type == wire.Control && uintField(record, 1) == uint64(wire.ControlLost) {
			t.Fatal("closed peer revoked live control")
		}
	}
}

// The first three terminal paths crossed with take, and Bye crossed with
// resync, restart_ping and checkpoint_done, are the six minimal reproductions.
// The remaining cases cover every immediate/drained finish and late input kind.
func TestRunFencesClosedConnectionInput(t *testing.T) {
	for _, terminal := range []string{"bye", "slow_consumer", "transport_error", "drain_bye", "bye_encode_error", "bye_overflow"} {
		for _, source := range []string{"take", "resync", "restart_ping", "checkpoint_done", "intent", "hello", "pong", "batch"} {
			t.Run(terminal+"/"+source, func(t *testing.T) {
				synctest.Test(t, func(t *testing.T) {
					r, live, state := newFencedPeers(t)
					_, initialEpoch := r.game.attempts.Controller()
					r.conn.generation, r.conn.capturedReady = 1, false
					if source == "restart_ping" {
						r.conn.transfer = true
					}
					var terminalInput input
					terminalInput.conn = r.conn
					switch terminal {
					case "bye":
						terminalInput.records = []wire.Record{bye(wire.CloseNormal)}
					case "slow_consumer":
						r.conn.active = r.game.gate.cfg.OutBytes
						r.conn.restarted = true // Overflow must close, not restart this transfer.
						terminalInput.records = []wire.Record{record(wire.Ping, wire.U(1, true, 1))}
					case "transport_error":
						r.conn.close() // The writer may close before its error is processed.
						terminalInput.err = io.EOF
					case "drain_bye", "bye_encode_error", "bye_overflow":
						terminalInput.err = wire.ErrFrame
						if terminal == "bye_encode_error" {
							r.game.gate.cfg.Codec = byeErrorCodec{r.game.gate.cfg.Codec}
						} else if terminal == "bye_overflow" {
							r.conn.active = r.game.gate.cfg.OutBytes
						}
					}
					take := record(wire.Control, wire.U(1, true, uint64(wire.ControlTake)))
					late := input{conn: r.conn}
					switch source {
					case "take":
						late.records = []wire.Record{take}
					case "resync":
						late.records = []wire.Record{record(wire.Control, wire.U(1, true, uint64(wire.ControlResync)), wire.U(2, true, r.game.attempts.Projection()))}
					case "restart_ping":
						late.records = []wire.Record{record(wire.Ping, wire.U(1, true, 2))}
					case "checkpoint_done":
						late.checkpoint = r.conn.generation
					case "intent":
						late.records = []wire.Record{testIntent(2, 2, initialEpoch+2)}
					case "hello":
						late.records = []wire.Record{record(wire.Hello)}
					case "pong":
						r.conn.pendingPing, r.conn.nonce = true, 7
						late.records = []wire.Record{record(wire.Pong, wire.U(1, true, 7))}
					case "batch":
						// A synchronous output close must also fence the rest of
						// this very batch before trace, not only the next input.
						terminalInput.records = append(terminalInput.records, take, testIntent(2, 2, initialEpoch), record(wire.Ping, wire.U(1, true, 3)))
						late.records = []wire.Record{take, testIntent(2, 2, initialEpoch+2)}
					}
					var lateTrace []Summary
					r.game.gate.cfg.Debug = true
					r.game.gate.cfg.Diagnostic = func(summary Summary) {
						if summary.Connection == r.conn.id && summary.Record != wire.Bye && peerClosed(r.conn) {
							lateTrace = append(lateTrace, summary)
						}
					}
					// All three inputs exist before run starts, like events
					// already posted by transport loops before the first close.
					r.game.input <- terminalInput
					r.game.input <- input{conn: live, records: []wire.Record{take}}
					r.game.input <- late
					startFencedRun(t, r.game)
					assertLiveControl(t, r, live, initialEpoch+2)
					if !peerClosed(r.conn) || state.snapshots != 0 || state.plans != 0 || len(lateTrace) != 0 || r.game.attempts.NextN() != 2 || len(r.world.writes) != 0 {
						t.Fatalf("late work: peerClosed=%v Snapshot=%d Plan=%d traces=%v NextN=%d writes=%v; want true/0/0/none/2/none",
							peerClosed(r.conn), state.snapshots, state.plans, lateTrace, r.game.attempts.NextN(), r.world.writes)
					}
					if source == "pong" && !r.conn.pendingPing {
						t.Fatal("closed peer consumed a late Pong")
					}
					// The second peer can still respond and admit one local
					// action. Ignoring the entire actor input is not a fix.
					r.game.input <- input{conn: live, records: []wire.Record{
						record(wire.Ping, wire.U(1, true, 9)), testIntent(2, 2, initialEpoch+2)}}
					synctest.Wait()
					assertLiveControl(t, r, live, initialEpoch+2)
					if state.plans != 1 || r.game.attempts.NextN() != 3 || len(r.world.writes) != 0 {
						t.Fatalf("live admission: Plan=%d NextN=%d writes=%v; want 1/3/none", state.plans, r.game.attempts.NextN(), r.world.writes)
					}
					pong, sent := false, false
					for _, out := range queuedRecords(t, live) {
						pong = pong || out.Type == wire.Pong && uintField(out, 1) == 9
						sent = sent || out.Type == wire.Result && uintField(out, 1) == 2 && uintField(out, 2) == uint64(wire.StageSent)
					}
					if !pong || !sent {
						t.Fatalf("live output Pong=%v Sent=%v; want both", pong, sent)
					}
				})
			})
		}
	}
}

func TestRunPreservesFirstClosedTransportCleanup(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		r, live, state := newFencedPeers(t)
		_, initialEpoch := r.game.attempts.Controller()
		r.conn.generation, r.conn.capturedReady = 1, false
		r.conn.close()
		// Physical closure rejects frames and completions, but must not consume
		// the actor's still-pending obligation to process its first error.
		r.game.input <- input{conn: r.conn, records: []wire.Record{
			record(wire.Control, wire.U(1, true, uint64(wire.ControlTake))), testIntent(2, 2, initialEpoch)}}
		r.game.input <- input{conn: r.conn, checkpoint: 1}
		startFencedRun(t, r.game)
		owner, epoch := r.game.attempts.Controller()
		if owner != r.conn.id || epoch != initialEpoch || state.snapshots != 0 || state.plans != 0 || state.stops != 0 {
			t.Fatalf("pre-error input changed owner/epoch/State: %d/%d/%+v", owner, epoch, state)
		}
		r.game.input <- input{conn: r.conn, err: io.EOF}
		synctest.Wait()
		owner, epoch = r.game.attempts.Controller()
		if owner != 0 || epoch != initialEpoch+1 || state.stops != 1 || r.game.stop || r.game.closed {
			t.Fatalf("first error cleanup: owner=%d epoch=%d Stop=%d pendingStop=%v closed=%v", owner, epoch, state.stops, r.game.stop, r.game.closed)
		}
		r.game.input <- input{conn: live, records: []wire.Record{record(wire.Control, wire.U(1, true, uint64(wire.ControlTake)))}}
		r.game.input <- input{conn: r.conn, err: io.EOF}
		r.game.input <- input{conn: r.conn, err: wire.ErrFrame}
		r.game.input <- input{conn: r.conn, checkpoint: 1}
		synctest.Wait()
		assertLiveControl(t, r, live, initialEpoch+2)
		if state.stops != 1 || state.snapshots != 0 || state.plans != 0 || r.game.attempts.NextN() != 2 || len(r.world.writes) != 0 {
			t.Fatalf("duplicate terminal work: Stop=%d Snapshot=%d Plan=%d NextN=%d writes=%v", state.stops, state.snapshots, state.plans, r.game.attempts.NextN(), r.world.writes)
		}
	})
}

type transportCloseCodec struct {
	Codec
	peer    *connection
	trigger uint64
	phase   wire.CheckpointPhase
	fired   bool
}

func (c *transportCloseCodec) Encode(rs []wire.Record) ([]byte, error) {
	for _, r := range rs {
		if !c.fired && r.Type == c.trigger && (c.phase == 0 || uintField(r, 1) == uint64(c.phase)) {
			c.fired = true
			c.peer.close()
		}
	}
	return c.Codec.Encode(rs)
}

// Physical Close can race a previously admitted capture or Bye Encode. The
// publication boundary must not refill buffers already cleared by Close, and
// abandoning a single peer's capture must not terminate the still-live scope.
func TestRunRejectsPublicationAfterTransportClose(t *testing.T) {
	for _, site := range []string{"checkpoint", "observe_end", "finish"} {
		t.Run(site, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				r, live, state := newFencedPeers(t)
				_, initialEpoch := r.game.attempts.Controller()
				codec := &transportCloseCodec{Codec: r.game.gate.cfg.Codec, peer: r.conn, trigger: wire.Bye}
				r.game.gate.cfg.Codec = codec
				switch site {
				case "checkpoint":
					codec.trigger, codec.phase = wire.Checkpoint, wire.CheckpointBegin
					r.game.input <- input{conn: r.conn, records: []wire.Record{record(wire.Control,
						wire.U(1, true, uint64(wire.ControlResync)), wire.U(2, true, r.game.attempts.Projection()))}}
				case "observe_end":
					codec.trigger, codec.phase = wire.Checkpoint, wire.CheckpointEnd
					r.conn.ready, r.conn.awaitComplete = false, true
					r.world.updates = make(chan Update, 1)
					r.world.updates <- Update{}
				case "finish":
					r.game.input <- input{conn: r.conn, err: wire.ErrFrame}
				}
				startFencedRun(t, r.game)
				if !codec.fired || r.game.closed || !peerClosed(r.conn) || peerClosed(live) {
					t.Fatalf("publication close fired=%v scopeClosed=%v oldClosed=%v liveClosed=%v", codec.fired, r.game.closed, peerClosed(r.conn), peerClosed(live))
				}
				r.conn.mu.Lock()
				buffers := len(r.conn.queue) + len(r.conn.snapshot) + r.conn.bytes
				r.conn.mu.Unlock()
				if buffers != 0 {
					t.Fatalf("publication refilled closed buffers: %d", buffers)
				}
				// Preserve the first error's cleanup after the cancelled capture.
				r.game.input <- input{conn: r.conn, err: io.EOF}
				synctest.Wait()
				r.game.input <- input{conn: live, records: []wire.Record{record(wire.Control, wire.U(1, true, uint64(wire.ControlTake)))}}
				synctest.Wait()
				assertLiveControl(t, r, live, initialEpoch+2)
				wantSnapshots := 1
				if site == "finish" {
					wantSnapshots = 0
				}
				if state.snapshots != wantSnapshots || state.stops != 1 || len(r.world.writes) != 0 {
					t.Fatalf("capture/cleanup: Snapshot=%d Stop=%d writes=%v; want %d/1/none", state.snapshots, state.stops, r.world.writes, wantSnapshots)
				}
			})
		})
	}
}

func TestRunSkipsClosedConnectionOnWorldAndTimerEvents(t *testing.T) {
	for _, source := range []string{"observe", "expire"} {
		t.Run(source, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				r, live, state := newFencedPeers(t)
				_, initialEpoch := r.game.attempts.Controller()
				r.conn.ready, r.conn.awaitComplete = false, true
				r.conn.generation = 1
				ticks := make(chan time.Time, 1)
				r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) { return ticks, func() {} }
				r.world.updates = make(chan Update, 1)
				r.game.input <- input{conn: r.conn, err: wire.ErrFrame}
				r.game.input <- input{conn: live, records: []wire.Record{record(wire.Control, wire.U(1, true, uint64(wire.ControlTake)))}}
				startFencedRun(t, r.game)
				assertLiveControl(t, r, live, initialEpoch+2)
				r.now = r.now.Add(11 * time.Second)
				if source == "observe" {
					r.world.updates <- Update{}
				} else {
					ticks <- r.now
				}
				synctest.Wait()
				assertLiveControl(t, r, live, initialEpoch+2)
				if state.snapshots != 0 || state.plans != 0 || len(r.world.writes) != 0 {
					t.Fatalf("closed drain on %s: Snapshot=%d Plan=%d writes=%v", source, state.snapshots, state.plans, r.world.writes)
				}
			})
		})
	}
}
