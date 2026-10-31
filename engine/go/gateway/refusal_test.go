// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type refusalState struct {
	*scheduleState
	proof   proto.RefusalEvidence
	applied int64
	onApply func()
}

func (s *refusalState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	return &Action{Effective: 4, Class: "take", Waiting: true, Steps: []Step{{Value: "effect"}}}, wire.ReasonNone
}
func (s *refusalState) Apply(_ Update, now int64) (Observation, error) {
	s.applied = now
	if s.onApply != nil {
		s.onApply()
	}
	return Observation{Refusals: []Refusal{{N: 2, Evidence: s.proof}}}, nil
}

func TestObserveRefusalUsesOneTimeAndKeepsUncertaintyLocal(t *testing.T) {
	for _, tc := range []struct {
		name          string
		now, observed int64
		reason        wire.Reason
		stage         wire.Stage
	}{
		{"clock-crosses-deadline", 4999, 4999, wire.ReasonWorld, wire.StageRefused},
		{"future-observation", 4998, 4999, wire.ReasonWorld, wire.StageUnknown},
		{"observation-at-deadline", 4999, 5000, wire.ReasonWorld, wire.StageUnknown},
		{"observation-before-contact", 4999, -1, wire.ReasonWorld, wire.StageUnknown},
		{"invalid-reason", 4999, 4998, wire.ReasonNone, wire.StageUnknown},
		{"already-at-deadline", 5000, 4999, wire.ReasonWorld, wire.StageUnknown},
		{"already-after-deadline", 5001, 4999, wire.ReasonWorld, wire.StageUnknown},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := scheduleNewRig(t)
			r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
			r.state.ready = true
			r.game.services = nil
			st := &refusalState{scheduleState: r.state, proof: proto.RefusalEvidence{Reason: tc.reason, ObservedMS: tc.observed}}
			r.game.state = st
			_, control := r.game.attempts.Controller()
			r.game.intent(r.conn, testIntent(2, 4, control))
			if err := r.game.attempts.Contact(2, 0); err != nil {
				t.Fatal(err)
			}
			if _, err := r.game.attempts.Sent(2, false); err != nil {
				t.Fatal(err)
			}
			r.drainOutput()
			origin := r.game.gate.start
			r.now = origin.Add(time.Duration(tc.now) * time.Millisecond)
			reads := 0
			r.game.gate.cfg.Now = func() time.Time { reads++; return r.now }
			st.onApply = func() { r.now = origin.Add(5002 * time.Millisecond) }
			if !r.game.observe(Update{}) || r.game.closed || r.conn.closing {
				t.Fatal("refusal proof closed the participant")
			}
			e, ok := r.game.attempts.Lookup(2)
			if !ok || !e.Terminal || e.Result == nil || e.Result.Stage != tc.stage {
				t.Fatalf("outcome = %+v, want terminal %v", e, tc.stage)
			}
			if tc.stage == wire.StageUnknown && e.Result.Reason != wire.ReasonTimeout {
				t.Fatalf("unknown reason = %v, want timeout", e.Result.Reason)
			}
			if reads != 1 || st.applied != tc.now {
				t.Fatalf("clock reads = %d, Apply time = %d; want 1, %d", reads, st.applied, tc.now)
			}
			if len(r.game.actions) != 0 {
				t.Fatal("terminal action retained")
			}
			if err := r.game.attempts.Contact(2, tc.now); err == nil {
				t.Fatal("terminal refusal/timeout reopened")
			}
			st.onApply = nil
			if !r.game.observe(Update{}) {
				t.Fatal("duplicate terminal proof closed participant")
			}
			if len(r.game.actions) != 0 {
				t.Fatal("duplicate terminal proof restored action")
			}
			// A second action in the same class distinguishes slot release from taint.
			r.game.intent(r.conn, testIntent(3, 4, control))
			if err := r.game.attempts.Contact(3, 5002); err != nil {
				t.Fatal(err)
			}
			if r.game.attempts.Eligible(3, 5003) != (tc.stage == wire.StageRefused) {
				t.Fatal("class release or uncertainty taint changed")
			}
		})
	}
}
