// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type guardedStopState struct {
	State
	step  *Step
	calls int
}

func (s *guardedStopState) Stop() *Step { s.calls++; return s.step }

func stopBudgetRig(t *testing.T) (*scheduleRig, *budgetUnavailableWorld, *guardedStopState) {
	t.Helper()
	r, w, state := budgetRig(t)
	s := &guardedStopState{State: state, step: &Step{Value: "stop"}}
	r.game.state = s
	r.game.drop(r.conn, wire.CloseNormal)
	if owner, _ := r.game.attempts.Controller(); owner != 0 || !r.game.stop {
		t.Fatal("disconnect did not schedule a guarded stop")
	}
	return r, w, s
}

func TestStopMustWaitForBudget(t *testing.T) {
	r, w, state := stopBudgetRig(t)
	r.game.service(context.Background())
	if w.called || r.game.closed || !r.game.stop || state.calls != 1 {
		t.Fatal("unready stop must remain pending without writing or closing")
	}
	state.step = &Step{Value: "updated-position"}
	w.ready = true
	r.game.service(context.Background())
	if r.game.closed || r.game.stop || state.calls != 2 || len(w.writes) != 1 || w.writes[0] != "updated-position" {
		t.Fatal("ready stop must use fresh state and write once", w.writes)
	}
}

func TestPendingStopCanceledByNewController(t *testing.T) {
	r, w, state := stopBudgetRig(t)
	r.game.service(context.Background())
	if _, err := r.game.attempts.Take(2); err != nil {
		t.Fatal(err)
	}
	w.ready = true
	r.game.service(context.Background())
	if w.called || r.game.closed || r.game.stop || state.calls != 1 {
		t.Fatal("new controller must cancel pending stop before asking state or writer")
	}
}

func TestPendingStopCanceledWhenKnowledgeLost(t *testing.T) {
	r, w, state := stopBudgetRig(t)
	r.game.service(context.Background())
	state.step = nil
	w.ready = true
	r.game.service(context.Background())
	if w.called || r.game.closed || r.game.stop || state.calls != 2 {
		t.Fatal("nil fresh stop must clear pending without writing")
	}
}

func TestGuardStopWithoutPacer(t *testing.T) {
	r, w, state := stopBudgetRig(t)
	r.game.pacer = nil
	w.ready = true
	r.game.service(context.Background())
	if r.game.closed || r.game.stop || state.calls != 1 || w.checks != 0 || len(w.writes) != 1 || w.writes[0] != "stop" {
		t.Fatal("world without Pacer must retain immediate guarded stop", w.writes)
	}
}

func TestUnreadyStopDoesNotDelayAttemptRefusal(t *testing.T) {
	for _, local := range []bool{false, true} {
		name := "remote"
		if local {
			name = "local"
		}
		t.Run(name, func(t *testing.T) {
			r, w, _ := stopBudgetRig(t)
			r.game.actions[2].action.Local = local
			r.game.service(context.Background())
			entry, _ := r.game.attempts.Lookup(2)
			if !r.game.stop || w.called || entry.Contact || !entry.Terminal || entry.Result.Reason != wire.ReasonStaleControl || len(r.game.actions) != 0 {
				t.Fatal("waiting stop prevented the old plan's CheckWrite refusal", entry)
			}
		})
	}
}
