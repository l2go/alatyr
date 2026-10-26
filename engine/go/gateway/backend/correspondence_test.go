// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"errors"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func capturedWalk(t *testing.T, st gateway.State) gateway.Step {
	t.Helper()
	a, reason := st.Plan(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 42), wire.U(2, true, 1), wire.At(11, true, 1, 2, 3)}}, 0)
	if reason != wire.ReasonNone {
		t.Fatal(reason)
	}
	step := a.Steps[0]
	step.N, step.Role, step.DeadlineMS = 42, gateway.StepEffect, 5000
	return step
}

func TestStepMetadataMatchesCapturedPlan(t *testing.T) {
	for _, field := range []string{"attempt", "index", "role", "system"} {
		t.Run(field, func(t *testing.T) {
			_, w, st, f := setup(t)
			step := capturedWalk(t, st)
			switch field {
			case "attempt":
				step.N++
			case "index":
				step.Index++
			case "role":
				step.Role = gateway.StepHelper
			case "system":
				step.Role = gateway.StepSystem
			}
			if err := w.Write(t.Context(), step); !errors.Is(err, meaning.ErrValue) {
				t.Fatalf("Write error=%v, want value error", err)
			}
			if len(f.Writes()) != 0 {
				t.Fatal("mismatched metadata reached port")
			}
		})
	}
}

func TestReceiptCorrespondenceAndOriginalDeadline(t *testing.T) {
	for _, field := range []string{"attempt", "requested", "effective", "epoch", "deadline"} {
		t.Run(field, func(t *testing.T) {
			s, w, st, _ := setup(t)
			step := capturedWalk(t, st)
			if err := w.Write(t.Context(), step); err != nil {
				t.Fatal(err)
			}
			r := bridge.Receipt{N: 1, Key: 1, GatewayEpoch: 1, Requested: 1, Effective: 1, Stage: wire.StageRefused, Reason: wire.ReasonWorld, ObservedAt: s.cfg.Origin.Add(time.Millisecond)}
			switch field {
			case "attempt":
				r.N++
			case "requested":
				r.Requested++
			case "effective":
				r.Effective++
			case "epoch":
				r.GatewayEpoch++
			case "deadline":
				r.ObservedAt = s.cfg.Origin.Add(5 * time.Second)
			}
			obs, err := st.Apply(gateway.Update{Value: packet{kind: bridge.LiveBatch, receipts: []bridge.Receipt{r}}}, 10000)
			if field == "deadline" {
				if err != nil || len(obs.Failures) != 0 {
					t.Fatalf("deadline receipt created failure: %+v/%v", obs, err)
				}
				return
			}
			if !errors.Is(err, wire.ErrFrame) {
				t.Fatalf("Apply error=%v, want frame error", err)
			}
		})
	}
}

func TestProposalBirthKeepsOriginalSessionClock(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s, w, _, f := setup(t)
		defer s.Close()
		e := meaning.Event{Kind: meaning.Spoke}
		r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.S(74, true, 10)}}
		first, err := w.state.eventInput(r, e, 100)
		if err != nil {
			t.Fatal(err)
		}
		if err := f.Emit(t.Context(), bridge.Update{Kind: bridge.Detached}); err != nil {
			t.Fatal(err)
		}
		<-w.Updates()
		if err := w.Detach(); err != nil {
			t.Fatal(err)
		}
		time.Sleep(20 * time.Second)
		snap := snapshot()
		snap.GatewayEpoch = 2
		for i := range snap.Facts {
			for j := range snap.Facts[i].Fields {
				if snap.Facts[i].Fields[j].Number == 4 {
					snap.Facts[i].Fields[j] = wire.U(4, true, 2)
				}
			}
		}
		f.Recover(bridge.Recovery{Snapshot: snap})
		w, _, err = s.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		// The successor's snapshot uses the original origin, not the resume time.
		if s.cfg.Origin.IsZero() {
			t.Fatal("missing original scope")
		}
		later, err := w.state.eventInput(r, e, 20000)
		if err != nil {
			t.Fatal(err)
		}
		if *first.ProposalBorn != 10 || *later.ProposalBorn != *first.ProposalBorn {
			t.Fatalf("birth %d -> %d, want 10", *first.ProposalBorn, *later.ProposalBorn)
		}
	})
}

func TestProposalBirthUsesCanonicalField(t *testing.T) {
	for _, tc := range []struct {
		name   string
		fields []wire.Field
		valid  bool
	}{
		{"canonical", []wire.Field{wire.S(74, true, 10)}, true},
		{"training-type-is-not-time", []wire.Field{wire.U(69, true, 2), wire.S(74, true, 10)}, true},
		{"missing", nil, false},
		{"training-type-only", []wire.Field{wire.U(69, true, 10)}, false},
		{"negative", []wire.Field{wire.S(74, true, -1)}, false},
		{"overflow", []wire.Field{wire.S(74, true, 1<<63-1)}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				session, w, _, _ := setup(t)
				defer session.Close()
				in, err := w.state.eventInput(wire.Record{Type: wire.Fact, Fields: tc.fields}, meaning.Event{Kind: meaning.Spoke}, 100)
				if tc.valid {
					if err != nil || in.ProposalBorn == nil || *in.ProposalBorn != 10 {
						t.Fatalf("got %+v/%v, want birth 10", in, err)
					}
				} else if !errors.Is(err, wire.ErrFrame) {
					t.Fatalf("got %v, want frame error", err)
				}
			})
		})
	}
}
