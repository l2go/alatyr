// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// All State callbacks are instrumented, including those not used by service.
// Only the actor accesses these counters; assertions follow its termination.
type actorState struct {
	*errorState
	game       *game
	afterClose []string
}

func (s *actorState) called(method string) {
	if s.game.closed {
		s.afterClose = append(s.afterClose, method)
	}
}
func (s *actorState) Apply(update Update, now int64) (Observation, error) {
	s.called("Apply")
	return s.errorState.Apply(update, now)
}
func (s *actorState) Snapshot(now int64) (Snapshot, error) {
	s.called("Snapshot")
	return s.errorState.Snapshot(now)
}
func (s *actorState) Plan(intent wire.Record, now int64) (*Action, wire.Reason) {
	s.called("Plan")
	return s.errorState.Plan(intent, now)
}
func (s *actorState) Validate(action *Action, now int64) wire.Reason {
	s.called("Validate")
	return s.errorState.Validate(action, now)
}
func (s *actorState) Start(action *Action) error {
	s.called("Start")
	return s.errorState.Start(action)
}
func (s *actorState) Confirmed(action *Action) (bool, error) {
	s.called("Confirmed")
	return s.errorState.Confirmed(action)
}
func (s *actorState) Witness(action *Action, update Update) (Witness, error) {
	s.called("Witness")
	return s.errorState.Witness(action, update)
}
func (s *actorState) CandidateValid(action *Action) (bool, error) {
	s.called("CandidateValid")
	return s.errorState.CandidateValid(action)
}
func (s *actorState) Proof(action *Action, now int64) (wire.Record, error) {
	s.called("Proof")
	return s.errorState.Proof(action, now)
}
func (s *actorState) Invalidate() {
	s.called("Invalidate")
	s.errorState.Invalidate()
}
func (s *actorState) Stop() *Step {
	s.called("Stop")
	return s.errorState.Stop()
}

type actorWorld struct {
	World
	game                     *game
	closes, writesAfterClose int
	onClose                  func()
}

func (w *actorWorld) Close() error {
	w.closes++
	if w.onClose != nil {
		w.onClose()
	}
	return w.World.Close()
}
func (w *actorWorld) Write(ctx context.Context, step Step) error {
	if w.game.closed {
		w.writesAfterClose++
	}
	return w.World.Write(ctx, step)
}

func TestRunStopsAfterServiceStateError(t *testing.T) {
	testRunServiceStateError(t, false)
}

func TestRunDoesNotCallStateAfterServiceStateError(t *testing.T) {
	testRunServiceStateError(t, true)
}

func TestRunStopsAfterObservationAndCheckpointStateErrors(t *testing.T) {
	for _, method := range []string{"Witness", "CandidateValid"} {
		t.Run(method, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				r := scheduleNewRig(t)
				r.game.services = nil
				r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
				s := &actorState{game: r.game, errorState: &errorState{testState: &testState{}, method: method,
					action: &Action{Effective: 4, Class: "take", Waiting: true, AfterCheckpoint: method == "CandidateValid"}}}
				r.game.state = s
				_, control := r.game.attempts.Controller()
				r.game.intent(r.conn, testIntent(2, 4, control))
				if err := r.game.attempts.Contact(2, r.game.gate.now()); err != nil {
					t.Fatal(err)
				}
				if _, err := r.game.attempts.Sent(2, false); err != nil {
					t.Fatal(err)
				}
				ex := r.game.actions[2]
				ex.hasCandidate, ex.candidate = true, r.game.gate.now()
				w := &actorWorld{World: r.world, game: r.game}
				r.game.world = w
				if method == "Witness" {
					r.world.updates = make(chan Update, 1)
					r.world.updates <- Update{Value: struct{}{}}
				} else {
					r.conn.generation, r.conn.capturedReady = 1, true
					r.game.input <- input{conn: r.conn, checkpoint: 1}
				}
				r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) { return nil, func() {} }
				ctx, cancel := context.WithCancel(context.Background())
				runDone := make(chan struct{})
				go func() { defer close(runDone); r.game.run(ctx) }()
				synctest.Wait()
				select {
				case <-runDone:
				default:
					t.Error("actor remained alive after State error")
				}
				cancel()
				<-runDone
				// A late drain callback must be harmless after internal closure.
				r.conn.ready = true
				r.game.commitReadyWitnesses()
				if !r.game.closed || w.closes != 1 || s.calls != 1 || len(s.afterClose) != 0 || w.writesAfterClose != 0 || len(r.world.writes) != 0 {
					t.Fatalf("closed=%v closes=%d errors=%d calls after close=%v writes=%d/%d", r.game.closed, w.closes, s.calls, s.afterClose, len(r.world.writes), w.writesAfterClose)
				}
			})
		})
	}
}

func testRunServiceStateError(t *testing.T, tickAfterClose bool) {
	t.Helper()
	for _, method := range []string{"Start", "Confirmed"} {
		t.Run(method, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				r := scheduleNewRig(t)
				r.game.services = nil
				r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
				s := &actorState{game: r.game, errorState: &errorState{
					testState: &testState{}, method: method,
					action: &Action{Effective: 4, Class: "take", Waiting: true,
						Steps: []Step{{Value: "effect"}}},
				}}
				if method == "Confirmed" {
					s.action.Steps = []Step{{Value: "helper", Await: true}, {Value: "effect"}}
				}
				r.game.state = s
				_, control := r.game.attempts.Controller()
				r.game.intent(r.conn, testIntent(2, 4, control))
				if r.game.actions[2] == nil {
					t.Fatal("missing queued action")
				}
				if tickAfterClose {
					// An incomplete observer makes the next checkpoint tick call
					// Snapshot if the actor incorrectly accepts it after closure.
					observer := &connection{game: r.game, id: 2, t: scheduleTransport{},
						hello: true, mode: wire.ModeObserve, transfer: true,
						incompleteAt: r.game.gate.now(),
						signal:       make(chan struct{}, 1), done: make(chan struct{})}
					r.game.conns[observer.id] = observer
				}
				ticks := make(chan time.Time, 1)
				ticks <- r.now
				stopped := false
				r.game.gate.cfg.Ticks = func(time.Duration) (<-chan time.Time, func()) {
					return ticks, func() { stopped = true }
				}
				closed := make(chan struct{})
				w := &actorWorld{World: r.world, game: r.game, onClose: func() {
					if tickAfterClose {
						r.now = r.now.Add(r.game.gate.cfg.Checkpoint)
						ticks <- r.now
					}
					close(closed)
				}}
				r.game.world = w
				var byes []Summary
				r.game.gate.cfg.Diagnostic = func(summary Summary) {
					if summary.Record == wire.Bye {
						byes = append(byes, summary)
					}
				}
				ctx, cancel := context.WithCancel(context.Background())
				runDone := make(chan struct{})
				go func() { defer close(runDone); r.game.run(ctx) }()
				<-closed
				synctest.Wait()
				select {
				case <-runDone:
				default:
					t.Error("actor did not exit after service State error")
				}
				// Cancellation is cleanup only: the assertion above requires
				// termination without any further event or context cancellation.
				cancel()
				<-runDone
				if !stopped || !r.game.closed || !r.conn.closing || w.closes != 1 || s.calls != 1 {
					t.Errorf("stopped=%v closed=%v closing=%v closes=%d errors=%d, want true/true/true/1/1",
						stopped, r.game.closed, r.conn.closing, w.closes, s.calls)
				}
				if len(s.afterClose) != 0 {
					t.Errorf("State calls after closure = %v, want none", s.afterClose)
				}
				if len(r.world.writes) != 0 || w.writesAfterClose != 0 {
					t.Errorf("World writes=%d after closure=%d, want 0/0", len(r.world.writes), w.writesAfterClose)
				}
				if tickAfterClose && len(ticks) != 1 {
					t.Errorf("pending checkpoint ticks = %d, want 1 unprocessed", len(ticks))
				}
				wantByes := 2 // Session and acting connection.
				if tickAfterClose {
					wantByes++ // Observing connection.
				}
				if len(byes) != wantByes {
					t.Errorf("Bye summaries = %d, want %d", len(byes), wantByes)
				}
				for _, bye := range byes {
					if bye.Reason != uint64(wire.CloseInternal) {
						t.Errorf("Bye reason = %d, want internal", bye.Reason)
					}
				}
			})
		})
	}
}
