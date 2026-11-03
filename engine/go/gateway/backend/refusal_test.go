// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"fmt"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestRefusalNeedsExclusiveLiveNoEffectReceipt(t *testing.T) {
	for _, name := range []string{"valid", "helper-sent", "multiple-writes", "write-error", "partial", "effects", "replay", "snapshot", "early", "deadline", "helper-role", "system-role"} {
		t.Run(name, func(t *testing.T) {
			s, w, st, _ := setup(t)
			step := capturedWalk(t, st)
			if err := w.Write(t.Context(), step); err != nil {
				t.Fatal(err)
			}
			binding := w.pending[1]
			r := bridge.Receipt{N: 1, Key: 1, GatewayEpoch: 1, Requested: 1, Effective: 1, Stage: wire.StageRefused, Reason: wire.ReasonWorld, Delivery: wire.DeliveryNone, ObservedAt: s.cfg.Origin.Add(time.Millisecond)}
			p := packet{kind: bridge.LiveBatch, receipts: []bridge.Receipt{r}}
			switch name {
			case "helper-sent":
				binding.plan.helperSent = true
			case "multiple-writes":
				binding.plan.writes++
			case "write-error":
				binding.plan.writeFailed = true
			case "partial":
				r.Delivery = wire.DeliveryPartial
			case "effects":
				r.Effects = []wire.Value{{U: 1}}
			case "replay":
				r.Replay = true
			case "snapshot":
				p.snapshot = true
			case "early":
				r.ObservedAt = s.cfg.Origin.Add(-time.Millisecond)
			case "deadline":
				r.ObservedAt = s.cfg.Origin.Add(5 * time.Second)
			case "helper-role":
				binding.role = gateway.StepHelper
			case "system-role":
				binding.role = gateway.StepSystem
			}
			w.pending[1] = binding
			p.receipts = []bridge.Receipt{r}
			obs, err := st.Apply(gateway.Update{Value: p}, 2)
			if err != nil {
				t.Fatal(err)
			}
			if name == "valid" {
				if len(obs.Refusals) != 1 || len(obs.Failures) != 0 || obs.Refusals[0].N != 42 || obs.Refusals[0].Evidence.Reason != wire.ReasonWorld || len(w.pending) != 0 {
					t.Fatalf("proof lost or binding retained: observation=%+v pending=%d", obs, len(w.pending))
				}
			} else if len(obs.Refusals) != 0 {
				t.Fatalf("unproven refusal: %+v", obs)
			}
			if (name == "snapshot" || name == "replay") && len(w.pending) != 1 {
				t.Fatal("diagnostic receipt consumed live binding")
			}
		})
	}
}

func TestReceiptOnlySnapshotCannotCertifyRefusal(t *testing.T) {
	s, w, st, f := setup(t)
	step := capturedWalk(t, st)
	if err := w.Write(t.Context(), step); err != nil {
		t.Fatal(err)
	}
	r := bridge.Receipt{N: 1, Key: 1, GatewayEpoch: 1, Requested: 1, Effective: 1, Stage: wire.StageRefused, Reason: wire.ReasonWorld, Delivery: wire.DeliveryNone, ObservedAt: s.cfg.Origin.Add(time.Millisecond)}
	if err := f.Emit(t.Context(), bridge.Update{Kind: bridge.LiveBatch, Batch: bridge.Batch{Snapshot: true, Receipts: []bridge.Receipt{r}}}); err != nil {
		t.Fatal(err)
	}
	u := <-w.Updates()
	obs, err := st.Apply(u, 2)
	if err != nil || len(obs.Refusals) != 0 || len(obs.Failures) != 0 || len(w.pending) != 1 {
		t.Fatalf("receipt-only snapshot gained authority: %+v/%v", obs, err)
	}
}

func TestOnlyWorldRefusalReasonsCertifyNoEffects(t *testing.T) {
	for reason := wire.ReasonNone; reason <= wire.ReasonBackendError+1; reason++ {
		t.Run(fmt.Sprint(reason), func(t *testing.T) {
			s, w, st, _ := setup(t)
			if err := w.Write(t.Context(), capturedWalk(t, st)); err != nil {
				t.Fatal(err)
			}
			r := bridge.Receipt{N: 1, Key: 1, GatewayEpoch: 1, Requested: 1, Effective: 1, Stage: wire.StageRefused, Reason: reason, Delivery: wire.DeliveryNone, ObservedAt: s.cfg.Origin.Add(time.Millisecond)}
			obs, err := st.Apply(gateway.Update{Value: packet{kind: bridge.LiveBatch, receipts: []bridge.Receipt{r}}}, 2)
			if err != nil {
				t.Fatal(err)
			}
			want := reason == wire.ReasonWorld || reason == wire.ReasonInvalid || reason == wire.ReasonQueueExpired
			if (len(obs.Refusals) == 1) != want || len(w.pending) != 0 {
				t.Fatalf("reason %v: refusals=%v pending=%d, want proof=%v and no binding", reason, obs.Refusals, len(w.pending), want)
			}
			if want && (len(obs.Failures) != 0 || obs.Refusals[0].Evidence.Reason != reason) {
				t.Fatalf("world reason lost: %+v", obs)
			}
			if !want && (len(obs.Failures) != 1 || obs.Failures[0].Reason != wire.ReasonInterrupted && obs.Failures[0].Reason != wire.ReasonBackendError) {
				t.Fatalf("unsupported reason lost uncertainty: %+v", obs)
			}
		})
	}
}
