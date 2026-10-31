// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestSequenceRefusalOracleRejectsMutations(t *testing.T) {
	for _, name := range []string{"valid", "preflight", "missing", "key", "raw-number", "epoch", "replay", "snapshot", "deadline", "before-contact", "partial", "effects", "reason", "busy", "timeout", "interrupted", "stale-control", "backend-error", "requested", "effective", "helper", "other-step", "repeated-write", "world-effects", "world-done", "world-owner", "world-intent", "write-error", "unaudited"} {
		t.Run(name, func(t *testing.T) {
			at := time.Unix(1, 0)
			r := oracleResult(wire.StageRefused)
			r.Fields[2] = wire.U(3, true, uint64(wire.ReasonWorld))
			r.Fields[5] = wire.U(7, true, uint64(wire.DeliveryNone))
			h := []backend.Handoff{{N: 1, Role: gateway.StepEffect, Key: 7, RawN: 8}}
			receipt := bridge.Receipt{Key: 7, N: 8, GatewayEpoch: 2, Requested: verbTalk, Effective: verbTalk, Stage: wire.StageRefused, Reason: wire.ReasonWorld, Delivery: wire.DeliveryNone, ObservedAt: at.Add(time.Millisecond)}
			trace := []bridge.Update{{Kind: bridge.LiveBatch, Batch: bridge.Batch{Receipts: []bridge.Receipt{receipt}}}}
			id := world.IntentID{Owner: world.Ref{ID: 1, Gen: 4}, N: 3}
			commit := world.Commit{Intent: world.Intent{ID: id, Verb: world.VerbTalk}, Outcome: world.Outcome{Intent: id, Stage: world.StageRefused, Reason: world.ReasonState}}
			writes := []sequenceWrite{{command: bridge.Command{Key: 7, Verb: verbTalk}, at: at, epoch: 2, owner: id.Owner, audited: true, commits: []world.Commit{commit}}}
			if err := checkRawEvidence([]wire.Record{r}, oracleAttempts(), h, trace, writes, 5000); err != nil {
				t.Fatalf("valid refusal rejected: %v", err)
			}
			switch name {
			case "valid":
				return
			case "preflight":
				writes[0].commits = nil
				if err := checkRawEvidence([]wire.Record{r}, oracleAttempts(), h, trace, writes, 5000); err != nil {
					t.Fatal(err)
				}
				return
			case "missing":
				trace[0].Batch.Receipts = nil
			case "key":
				receipt.Key++
			case "raw-number":
				receipt.N++
			case "epoch":
				receipt.GatewayEpoch++
			case "replay":
				receipt.Replay = true
			case "snapshot":
				trace[0].Batch.Snapshot = true
			case "deadline":
				receipt.ObservedAt = at.Add(5 * time.Second)
			case "before-contact":
				receipt.ObservedAt = at.Add(-time.Millisecond)
			case "partial":
				receipt.Delivery = wire.DeliveryPartial
			case "effects":
				receipt.Effects = []wire.Value{{U: 1}}
			case "reason":
				receipt.Reason = wire.ReasonNone
			case "busy", "timeout", "interrupted", "stale-control", "backend-error":
				reasons := map[string]wire.Reason{"busy": wire.ReasonBusy, "timeout": wire.ReasonTimeout, "interrupted": wire.ReasonInterrupted, "stale-control": wire.ReasonStaleControl, "backend-error": wire.ReasonBackendError}
				receipt.Reason = reasons[name]
				r.Fields[2] = wire.U(3, true, uint64(receipt.Reason))
			case "requested":
				receipt.Requested++
			case "effective":
				receipt.Effective++
			case "helper":
				h[0].Role = gateway.StepHelper
			case "other-step":
				h = append(h, backend.Handoff{N: 1, Role: gateway.StepHelper, Key: 6, RawN: 7})
			case "repeated-write":
				writes = append(writes, writes[0])
			case "world-effects":
				writes[0].commits[0].Groups = []world.EffectGroup{{Facts: []world.Fact{{Property: world.PropertyTarget}}}}
			case "world-done":
				writes[0].commits[0].Outcome.Stage = world.StageDone
			case "world-owner":
				writes[0].commits[0].Intent.ID.Owner.Gen++
				writes[0].commits[0].Outcome.Intent = writes[0].commits[0].Intent.ID
			case "world-intent":
				writes[0].commits[0].Outcome.Intent.N++
			case "write-error":
				writes[0].failed = true
			case "unaudited":
				writes[0].audited = false
			}
			if name != "missing" {
				trace[0].Batch.Receipts = []bridge.Receipt{receipt}
			}
			if err := checkRawEvidence([]wire.Record{r}, oracleAttempts(), h, trace, writes, 5000); err == nil {
				t.Fatal("unproven refusal escaped oracle")
			}
		})
	}
}

func TestSequenceRefusalCannotHaveLaterCause(t *testing.T) {
	r := oracleResult(wire.StageRefused)
	r.Fields[2] = wire.U(3, true, uint64(wire.ReasonWorld))
	r.Fields[5] = wire.U(7, true, uint64(wire.DeliveryNone))
	if err := checkTranscript([]wire.Record{r}, oracleAttempts(), true, 5000); err != nil {
		t.Fatal(err)
	}
	for _, rs := range [][]wire.Record{{oracleFact(), r}, {r, oracleFact()}} {
		if err := checkTranscript(rs, oracleAttempts(), true, 5000); err == nil {
			t.Fatal("causal effect attached to Refused")
		}
	}
}

func TestSequenceRefusalCannotHaveDeferredWorldEffects(t *testing.T) {
	id := world.IntentID{Owner: world.Ref{ID: 1, Gen: 1}, N: 1}
	c := world.Commit{Intent: world.Intent{ID: id, Verb: world.VerbUseItem}, Outcome: world.Outcome{Intent: id, Stage: world.StageRefused, Reason: world.ReasonState}}
	if err := checkEconomy([]world.Commit{c}); err != nil {
		t.Fatal(err)
	}
	later := world.Commit{Groups: []world.EffectGroup{{Cause: world.Cause{Intent: id}, Facts: []world.Fact{{Subject: id.Owner, Property: world.PropertyTarget}}}}}
	if err := checkEconomy([]world.Commit{c, later}); err == nil {
		t.Fatal("deferred world effect escaped refusal oracle")
	}
}
