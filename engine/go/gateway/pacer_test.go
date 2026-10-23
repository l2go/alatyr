// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type budgetUnavailableWorld struct {
	World
	game              *game
	ready             bool
	called, contacted bool
	checks            int
	writes            []string
	onReady           func()
}

func (w *budgetUnavailableWorld) Ready(Step, int64) bool {
	w.checks++
	if w.onReady != nil {
		w.onReady()
	}
	return w.ready
}
func (w *budgetUnavailableWorld) Write(_ context.Context, step Step) error {
	w.called = true
	entry, _ := w.game.attempts.Lookup(2)
	w.contacted = entry.Contact
	if !w.ready {
		return errors.New("offline credit window is not ready")
	}
	w.writes = append(w.writes, step.Value.(string))
	return nil
}

type budgetCommandState struct {
	State
	validations, updates int
	confirmed            bool
	order                []string
}

func (s *budgetCommandState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	return &Action{Effective: 1, Steps: []Step{{Value: "walk"}}}, wire.ReasonNone
}
func (s *budgetCommandState) Start(*Action) {}
func (s *budgetCommandState) Validate(*Action, int64) wire.Reason {
	s.validations++
	s.order = append(s.order, "validate")
	return wire.ReasonNone
}
func (s *budgetCommandState) Confirmed(*Action) bool {
	s.order = append(s.order, "confirmed")
	return s.confirmed
}
func (s *budgetCommandState) Apply(u Update, now int64) (Observation, error) {
	s.updates++
	return s.State.Apply(u, now)
}

func budgetRig(t *testing.T) (*scheduleRig, *budgetUnavailableWorld, *budgetCommandState) {
	t.Helper()
	r := scheduleNewRig(t)
	r.game.service(context.Background())
	r.game.service(context.Background())
	r.state.ready = true
	r.conn.ready, r.conn.transfer, r.conn.awaitComplete = true, false, false
	s := &budgetCommandState{State: r.state}
	r.game.state = s
	w := &budgetUnavailableWorld{World: r.world, game: r.game}
	r.game.world, r.game.pacer = w, w
	_, control := r.game.attempts.Controller()
	command := testIntent(2, 1, control)
	raw, err := r.game.gate.cfg.Codec.Encode([]wire.Record{command})
	if err != nil {
		t.Fatal(err)
	}
	records, err := r.game.gate.cfg.Codec.Decode(raw)
	if err != nil {
		t.Fatal(err)
	}
	r.game.process(input{conn: r.conn, records: records, bytes: len(raw)})
	entry, ok := r.game.attempts.Lookup(2)
	if !ok || entry.Contact || entry.Terminal {
		t.Fatal("command must be queued without contact", entry)
	}
	return r, w, s
}

func TestBudgetMustDeferBeforeContact(t *testing.T) {
	r, w, state := budgetRig(t)
	r.game.service(context.Background())
	entry, _ := r.game.attempts.Lookup(2)
	if w.called || entry.Contact || entry.Terminal || w.checks != 1 || state.validations != 1 {
		t.Fatal("unready step did not remain queued before contact", entry, w)
	}
	r.now = r.now.Add(200 * time.Millisecond)
	w.ready = true
	r.game.service(context.Background())
	entry, _ = r.game.attempts.Lookup(2)
	if !w.called || !w.contacted || !entry.Contact || !entry.Sent || !entry.Terminal || len(w.writes) != 1 || state.validations != 2 {
		t.Fatal("ready step did not resume after validation", entry, w)
	}
}

func TestPacerWaitKeepsOriginalQueueDeadline(t *testing.T) {
	r, w, _ := budgetRig(t)
	r.game.service(context.Background())
	r.now = r.now.Add(5001 * time.Millisecond)
	w.ready = true
	r.game.service(context.Background())
	entry, _ := r.game.attempts.Lookup(2)
	if w.called || entry.Contact || !entry.Terminal || entry.Result == nil || entry.Result.Stage != wire.StageRefused || entry.Result.Reason != wire.ReasonQueueExpired || w.checks != 1 {
		t.Fatal("queue deadline must expire before pacing is retried", entry, w)
	}
}

func TestUnreadyServiceAllowsInputAndUpdates(t *testing.T) {
	r, w, state := budgetRig(t)
	r.game.services = []Step{{Value: "ack"}}
	processed := 0
	r.game.gate.cfg.Diagnostic = func(s Summary) {
		if s.Record == wire.Intent {
			processed++
		}
	}
	r.game.input <- r.input // Replay of the completed first Aim.
	r.game.service(context.Background())
	if processed != 1 || len(r.game.services) != 1 || w.called {
		t.Fatal("unready service blocked participant input")
	}
	r.world.updates = make(chan Update, 1)
	r.world.updates <- Update{}
	r.game.service(context.Background())
	if state.updates != 1 || len(r.game.services) != 1 || w.called {
		t.Fatal("unready service blocked world updates")
	}
	w.ready = true
	r.game.service(context.Background())
	if len(r.game.services) != 0 || !reflect.DeepEqual(w.writes, []string{"ack"}) {
		t.Fatal("service did not retain its place while unready", w.writes)
	}
}

func TestPacerAfterValidationAndConfirmationBeforeTaint(t *testing.T) {
	r, w, state := budgetRig(t)
	state.confirmed = true
	a := r.game.actions[2]
	a.action.Steps = []Step{{Value: "Q", Await: true}, {Value: "walk", Interferes: []string{"take"}}}
	a.index = 1
	_, control := r.game.attempts.Controller()
	admission := r.game.attempts.Reserve(1, true, true, proto.Attempt{N: 3, Verb: 4, Control: control, Payload: []byte("opaque third attempt"), EncodedBytes: 1}, r.game.gate.now())
	if !admission.Allocated || admission.Result != nil {
		t.Fatal(admission)
	}
	if _, err := r.game.attempts.Prepare(3, 4, "take", true); err != nil {
		t.Fatal(err)
	}
	if err := r.game.attempts.Contact(3, r.game.gate.now()); err != nil {
		t.Fatal(err)
	}
	if _, err := r.game.attempts.Sent(3, false); err != nil {
		t.Fatal(err)
	}
	r.game.actions[3] = &execution{action: &Action{Effective: 4, Class: "take", Waiting: true}}
	if !r.game.attempts.Eligible(3, r.game.gate.now()) {
		t.Fatal("witness must initially be eligible")
	}
	w.onReady = func() {
		if !reflect.DeepEqual(state.order, []string{"validate", "confirmed"}) {
			t.Fatal("Ready ran before validation/confirmation", state.order)
		}
	}
	r.game.service(context.Background())
	entry, _ := r.game.attempts.Lookup(2)
	if w.called || entry.Contact || !r.game.attempts.Eligible(3, r.game.gate.now()) {
		t.Fatal("unready step contacted or tainted the competing class")
	}
}

func TestPacerTargetIsRecheckedAfterWaiting(t *testing.T) {
	r, w, state := budgetRig(t)
	state.confirmed = true
	a := r.game.actions[2]
	a.action.Steps = []Step{{Value: "Q", Await: true}, {Value: "walk"}}
	a.index = 1
	r.game.service(context.Background())
	w.ready, state.confirmed = true, false
	r.game.service(context.Background())
	entry, _ := r.game.attempts.Lookup(2)
	if w.called || entry.Contact || !entry.Terminal || entry.Result.Reason != wire.ReasonTargetUnconfirmed || w.checks != 1 {
		t.Fatal("target changed while waiting, but pacing bypassed its guard", entry)
	}
}

func TestPacerWaitingPlanDoesNotSurviveControlOrEpochChange(t *testing.T) {
	for _, change := range []string{"control", "epoch"} {
		t.Run(change, func(t *testing.T) {
			r, w, _ := budgetRig(t)
			r.game.service(context.Background())
			if change == "control" {
				if _, err := r.game.attempts.Take(2); err != nil {
					t.Fatal(err)
				}
			} else if !r.game.observe(Update{Relocate: true}) {
				t.Fatal("relocation failed")
			}
			w.ready = true
			r.game.service(context.Background())
			entry, _ := r.game.attempts.Lookup(2)
			if w.called || entry.Contact || !entry.Terminal {
				t.Fatal("stale waiting plan reached its writer", entry)
			}
		})
	}
}

func TestWorldPacerCapturedAtConstruction(t *testing.T) {
	r, w, _ := budgetRig(t)
	c := testConfig(t)
	cfg := r.game.gate.cfg
	cfg.Factory = func(context.Context, string) (World, State, error) { return w, r.state, nil }
	cfg.Identities = map[string]Identity{"one": {Act: true}}
	cfg.Versions, cfg.Meanings = []uint64{1}, []string{"test/1"}
	cfg.FrameBytes, cfg.Sessions, cfg.Connections = c.FrameBytes, 1, 4
	cfg.IntentBurst, cfg.IntentRate, cfg.ServiceBurst, cfg.ServiceRate = 10, 20, 4, 2
	cfg.Batch = time.Millisecond
	g, err := New(cfg)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := g.Close(); err != nil {
			t.Error(err)
		}
	})
	if err := g.Bind(context.Background(), "one", scheduleTransport{}); err != nil {
		t.Fatal(err)
	}
	g.mu.Lock()
	pacer := g.games["one"].pacer
	g.mu.Unlock()
	if pacer != w {
		t.Fatal("optional world pacer was not captured at construction")
	}
}
