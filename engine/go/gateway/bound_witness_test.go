// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type boundWitnessState struct {
	*scheduleState
	witness Witness
}

func (s *boundWitnessState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	return &Action{Effective: 4, Class: "take", Waiting: true, Steps: []Step{{Value: "effect"}}}, wire.ReasonNone
}
func (s *boundWitnessState) Apply(Update, int64) (Observation, error) {
	return Observation{Facts: []wire.Record{record(wire.Fact)}}, nil
}
func (s *boundWitnessState) Witness(*Action, Update) (Witness, error) { return s.witness, nil }

// TestBoundWitnessFinishesAfterUnknownInClass taints the take class with an
// Unknown. A later unbound match or candidate must not finish the next take,
// while evidence bound to that attempt does.
func TestBoundWitnessFinishesAfterUnknownInClass(t *testing.T) {
	r := scheduleNewRig(t)
	r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
	r.game.services = nil
	st := &boundWitnessState{scheduleState: r.state}
	r.game.state = st
	_, control := r.game.attempts.Controller()
	r.game.intent(r.conn, testIntent(2, 4, control))
	if err := r.game.attempts.Contact(2, 0); err != nil {
		t.Fatal(err)
	}
	if _, err := r.game.attempts.Sent(2, false); err != nil {
		t.Fatal(err)
	}
	if o := r.game.attempts.Expire(5000); len(o) != 1 || o[0].Stage != wire.StageUnknown {
		t.Fatalf("taint: %+v", o)
	}
	delete(r.game.actions, 2)
	r.game.intent(r.conn, testIntent(3, 4, control))
	if err := r.game.attempts.Contact(3, 5001); err != nil {
		t.Fatal(err)
	}
	if _, err := r.game.attempts.Sent(3, false); err != nil {
		t.Fatal(err)
	}
	r.drainOutput()
	r.now = r.game.gate.start.Add(5002 * time.Millisecond)
	for _, witness := range []Witness{WitnessMatch, WitnessCandidate} {
		st.witness = witness
		if !r.game.observe(Update{}) || r.game.closed {
			t.Fatal("unbound evidence closed the participant")
		}
		if e, _ := r.game.attempts.Lookup(3); e.Terminal || r.game.actions[3].hasCandidate {
			t.Fatalf("unbound witness %d used after an Unknown in the class", witness)
		}
	}
	st.witness = WitnessBound
	if !r.game.observe(Update{}) || r.game.closed {
		t.Fatal("bound evidence closed the participant")
	}
	e, _ := r.game.attempts.Lookup(3)
	if !e.Terminal || e.Result == nil || e.Result.Stage != wire.StageDone {
		t.Fatalf("bound evidence outcome = %+v, want Done", e.Result)
	}
	if _, ok := r.game.actions[3]; ok {
		t.Fatal("finished action retained")
	}
}
