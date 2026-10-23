// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type errorState struct {
	*testState
	method               string
	action               *Action
	calls, plans, proofs int
}

func (s *errorState) failure(method string) error {
	if s.method == method {
		s.calls++
		return errors.New("adapter: private value must not escape")
	}
	return nil
}
func (s *errorState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	s.plans++
	return s.action, wire.ReasonNone
}
func (s *errorState) Start(*Action) error             { return s.failure("Start") }
func (s *errorState) Confirmed(*Action) (bool, error) { return true, s.failure("Confirmed") }
func (s *errorState) Witness(*Action, Update) (Witness, error) {
	return WitnessMatch, s.failure("Witness")
}
func (s *errorState) CandidateValid(*Action) (bool, error) { return true, s.failure("CandidateValid") }
func (s *errorState) Apply(Update, int64) (Observation, error) {
	return Observation{Facts: []wire.Record{record(wire.Fact)}}, nil
}
func (s *errorState) Proof(*Action, int64) (wire.Record, error) {
	s.proofs++
	return wire.Record{}, nil
}

type errorWorld struct {
	World
	closes int
}

func (w *errorWorld) Close() error { w.closes++; return w.World.Close() }

func TestStateErrorsCloseParticipantWithoutLeakingValues(t *testing.T) {
	for _, tc := range []struct{ name, method string }{
		{"start", "Start"},
		{"waiting_helper", "Confirmed"},
		{"unwritten_helper", "Confirmed"},
		{"effect_after_helper", "Confirmed"},
		{"live_witness", "Witness"},
		{"expired_candidate", "CandidateValid"},
		{"checkpoint_candidate", "CandidateValid"},
		{"intent_after_expired_candidate", "CandidateValid"},
		{"observe_after_expired_candidate", "CandidateValid"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := scheduleNewRig(t)
			r.game.services = nil
			r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
			s := &errorState{testState: &testState{}, method: tc.method,
				action: &Action{Effective: 4, Class: "take", Waiting: true, Steps: []Step{{Value: "effect"}}, Value: struct{}{}}}
			r.game.state = s
			w := &errorWorld{World: r.world}
			r.game.world = w
			_, control := r.game.attempts.Controller()
			r.game.intent(r.conn, testIntent(2, 4, control))
			ex := r.game.actions[2]
			if ex == nil {
				t.Fatal("missing queued action")
			}
			if tc.method == "Confirmed" {
				ex.action.Steps = []Step{{Value: "helper", Await: true}, {Value: "effect"}}
				if tc.name == "waiting_helper" {
					ex.waiting = true
				}
				if tc.name == "effect_after_helper" {
					ex.index = 1
				}
			}
			if tc.method == "Witness" || tc.method == "CandidateValid" {
				if err := r.game.attempts.Contact(2, r.game.gate.now()); err != nil {
					t.Fatal(err)
				}
				if _, err := r.game.attempts.Sent(2, false); err != nil {
					t.Fatal(err)
				}
				ex.hasCandidate = true
				ex.candidate = r.game.gate.now()
				if tc.name == "checkpoint_candidate" {
					ex.action.AfterCheckpoint = true
				} else if tc.method == "CandidateValid" {
					ex.action.FullWindow = true
					r.now = r.now.Add(5000 * time.Millisecond)
				}
			}
			var summaries []Summary
			r.game.gate.cfg.Diagnostic = func(s Summary) { summaries = append(summaries, s) }
			switch tc.name {
			case "live_witness", "observe_after_expired_candidate":
				if r.game.observe(Update{Value: struct{}{}}) {
					t.Fatal("observation continued after error")
				}
			case "expired_candidate":
				r.game.expire()
			case "intent_after_expired_candidate":
				r.game.intent(r.conn, testIntent(3, 4, control))
			case "checkpoint_candidate":
				r.game.commitReadyWitnesses()
			default:
				r.game.service(context.Background())
			}
			if !r.game.closed || !r.conn.closing || w.closes != 1 || s.calls != 1 {
				t.Fatalf("closed=%v closing=%v closes=%d calls=%d", r.game.closed, r.conn.closing, w.closes, s.calls)
			}
			if len(r.world.writes) != 0 || s.proofs != 0 || s.plans != 1 {
				t.Fatalf("continued after error: writes=%d proofs=%d plans=%d", len(r.world.writes), s.proofs, s.plans)
			}
			byes := 0
			for _, summary := range summaries {
				if summary.Record == wire.Bye {
					byes++
					if summary.Reason != uint64(wire.CloseInternal) {
						t.Fatalf("Bye reason = %d, want internal", summary.Reason)
					}
				} else if summary.Record != wire.Result {
					t.Fatalf("unexpected diagnostic: %#v", summary)
				}
			}
			if byes != 2 {
				t.Fatalf("internal Bye summaries = %d, want session and connection", byes)
			}
			// The output and diagnostics have no field capable of carrying an adapter value.
			r.conn.mu.Lock()
			queue := append([]outgoing(nil), r.conn.queue...)
			r.conn.mu.Unlock()
			last, err := r.game.gate.cfg.Codec.Decode(queue[len(queue)-1].raw)
			if err != nil || len(last) != 1 || last[0].Type != wire.Bye || len(last[0].Fields) != 1 || uintField(last[0], 1) != uint64(wire.CloseInternal) {
				t.Fatalf("final output = %v, %v", last, err)
			}
			r.game.fail(wire.CloseWorldClosed)
			if w.closes != 1 {
				t.Fatal("backend closed twice")
			}
		})
	}
}
