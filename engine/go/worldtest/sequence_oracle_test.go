// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"math"
	"slices"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func oracleResult(stage wire.Stage) wire.Record {
	return wire.Record{Type: wire.Result, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, uint64(stage)), wire.U(3, true, 0), wire.U(5, true, 5), wire.U(6, true, 5), wire.U(7, true, uint64(wire.DeliveryBackend))}}
}
func oracleFact() wire.Record {
	return wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 11), wire.U(2, true, 1), wire.S(3, true, 1), wire.U(4, true, 1), wire.Obj(10, true, []wire.Field{wire.S(1, true, 2), wire.U(2, true, 1)}), wire.U(32, true, 1)}}
}
func oracleAttempts() map[uint64]*sequenceAttempt {
	return map[uint64]*sequenceAttempt{1: {record: wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.Obj(10, true, []wire.Field{wire.S(1, true, 2), wire.U(2, true, 1)})}}, epoch: 1}}
}

func TestSequenceOutcomeOracleRejectsMutations(t *testing.T) {
	valid := []wire.Record{oracleFact(), oracleResult(wire.StageDone)}
	for _, tc := range []struct {
		name   string
		mutate func([]wire.Record) []wire.Record
	}{
		{"missing-terminal", func(rs []wire.Record) []wire.Record { return rs[:1] }},
		{"duplicate-terminal", func(rs []wire.Record) []wire.Record { return append(rs, rs[1]) }},
		{"reopened-terminal", func(rs []wire.Record) []wire.Record { return append(rs, oracleResult(wire.StageSent)) }},
		{"Done-without-fact", func(rs []wire.Record) []wire.Record { return rs[1:] }},
		{"wrong-epoch", func(rs []wire.Record) []wire.Record {
			rs[0].Fields[3] = wire.U(4, true, 2)
			return rs
		}},
		{"snapshot-cause", func(rs []wire.Record) []wire.Record {
			return append([]wire.Record{{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointBegin)), wire.U(2, true, 1)}}}, rs...)
		}},
		{"late-cause", func(rs []wire.Record) []wire.Record { rs[0].Fields[2] = wire.S(3, true, 10001); return rs }},
		{"unknown-reason", func(rs []wire.Record) []wire.Record {
			rs[1].Fields[1] = wire.U(2, true, uint64(wire.StageRefused))
			rs[1].Fields[2] = wire.U(3, true, 99)
			return rs[1:]
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if err := checkTranscript(valid, oracleAttempts(), true, 5000); err != nil {
				t.Fatal(err)
			}
			rs := slices.Clone(valid)
			for i := range rs {
				rs[i].Fields = slices.Clone(rs[i].Fields)
			}
			if err := checkTranscript(tc.mutate(rs), oracleAttempts(), true, 5000); err == nil {
				t.Fatal("mutated outcome escaped oracle")
			}
		})
	}
}

func TestSequenceRawOracleRejectsMutations(t *testing.T) {
	at := time.Unix(1, 0)
	for _, name := range []string{"receipt-only", "snapshot", "at-deadline", "wrong-receipt", "wrong-raw-epoch", "wrong-generation", "refused-write"} {
		t.Run(name, func(t *testing.T) {
			records := []wire.Record{oracleFact(), oracleResult(wire.StageDone)}
			h := []backend.Handoff{{N: 1, Role: gateway.StepEffect, Key: 7, RawN: 8}}
			trace := []bridge.Update{{Kind: bridge.LiveBatch, Batch: bridge.Batch{Records: []wire.Record{{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 11), wire.S(3, true, 1), wire.U(4, true, 1), wire.S(64, true, 2)}}}, ReceivedAt: []time.Time{at.Add(time.Millisecond)}, Receipts: []bridge.Receipt{{Key: 7, N: 8, GatewayEpoch: 1, Stage: wire.StageDone, ObservedAt: at}}}}}
			writes := []sequenceWrite{{command: bridge.Command{Key: 7}, at: at}}
			if err := checkRawEvidence(records, oracleAttempts(), h, trace, writes, 5000); err != nil {
				t.Fatal(err)
			}
			switch name {
			case "receipt-only":
				trace[0].Batch.Records = nil
			case "snapshot":
				trace[0].Batch.Snapshot = true
			case "at-deadline":
				trace[0].Batch.ReceivedAt[0] = at.Add(5 * time.Second)
			case "wrong-receipt":
				trace[0].Batch.Receipts[0].Key = 99
			case "wrong-raw-epoch":
				trace[0].Batch.Receipts[0].GatewayEpoch = 2
			case "wrong-generation":
				records[0].Fields[4] = wire.Obj(10, true, []wire.Field{wire.S(1, true, 2), wire.U(2, true, 2)})
			case "refused-write":
				records = []wire.Record{oracleResult(wire.StageRefused)}
			}
			if err := checkRawEvidence(records, oracleAttempts(), h, trace, writes, 5000); err == nil {
				t.Fatal("mutated raw evidence escaped oracle")
			}
		})
	}
}

func TestSequenceDrainOracleRejectsMutations(t *testing.T) {
	if err := checkRetained(sequenceRetention{games: 1}, 2); err != nil {
		t.Fatal(err)
	}
	for _, r := range []sequenceRetention{{games: 3}, {opening: 1}, {connections: 1}, {actions: 9}, {bindings: 9}} {
		if err := checkRetained(r, 2); err == nil {
			t.Fatalf("retained state escaped oracle: %+v", r)
		}
	}
}
func TestSequenceWorldOracleRejectsMutations(t *testing.T) {
	valid := world.Character{CurHP: 10, MaxHP: 20, CurMP: 2, MaxMP: 3, Items: []world.Item{{ObjectID: 1, Count: 1}}}
	if err := checkCharacter(valid); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"negative-HP", "excess-MP", "NaN", "negative-count", "duplicate-row", "saved-state"} {
		t.Run(name, func(t *testing.T) {
			c := valid.Clone()
			switch name {
			case "negative-HP":
				c.CurHP = -1
			case "excess-MP":
				c.CurMP = 4
			case "NaN":
				c.CurHP = math.NaN()
			case "negative-count":
				c.Items[0].Count = -1
			case "duplicate-row":
				c.Items = append(c.Items, c.Items[0])
			case "saved-state":
				c.LastAccess = 1
				c.Items[0].Count = 2
				if err := checkSaved(valid, c); err == nil {
					t.Fatal("bad save escaped oracle")
				}
				return
			}
			if err := checkCharacter(c); err == nil {
				t.Fatal("mutated world state escaped oracle")
			}
		})
	}
	owner := world.Ref{ID: 1, Gen: 1}
	item := world.Ref{ID: 2, Gen: 1}
	transfer := world.ResourceTransferred{Resource: world.Resource{Kind: world.ResourceItem}, From: world.ResourceLocation{Entity: owner, Item: item, Place: world.PlaceInventory}, To: world.ResourceLocation{Place: world.PlaceConsumption}, Amount: world.IntegerAmount(1), FromBalance: world.Balance{Before: world.IntegerAmount(2), After: world.IntegerAmount(1)}}
	group := world.EffectGroup{Cause: world.Cause{Intent: world.IntentID{Owner: owner, N: 1}}, Effects: []world.Effect{transfer}}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group}}}); err != nil {
		t.Fatal(err)
	}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group, group}}}); err == nil {
		t.Fatal("double consumption escaped oracle")
	}
	transfer.FromBalance.After = world.IntegerAmount(math.MinInt64)
	group.Effects = []world.Effect{transfer}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group}}}); err == nil {
		t.Fatal("balance overflow escaped oracle")
	}
}

func TestSequenceLearningProofUsesOriginalLiveCandidate(t *testing.T) {
	at := time.Unix(1, 0)
	records := []wire.Record{oracleFact(), oracleResult(wire.StageDone)}
	records[0].Fields[0] = wire.U(1, true, 23)
	records[0].Fields[2] = wire.S(3, true, 5000)
	records[1].Fields[3] = wire.U(5, true, 13)
	records[1].Fields[4] = wire.U(6, true, 13)
	h := []backend.Handoff{{N: 1, Role: gateway.StepEffect, Key: 7, RawN: 8}}
	trace := []bridge.Update{{Kind: bridge.LiveBatch, Batch: bridge.Batch{Records: []wire.Record{{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 23), wire.S(3, true, 1), wire.U(4, true, 1), wire.S(64, true, 2)}}}, ReceivedAt: []time.Time{at.Add(time.Millisecond)}, Receipts: []bridge.Receipt{{Key: 7, N: 8, GatewayEpoch: 1, Stage: wire.StageDone, ObservedAt: at}}}}}
	writes := []sequenceWrite{{command: bridge.Command{Key: 7}, at: at}}
	if err := checkRawEvidence(records, oracleAttempts(), h, trace, writes, 5000); err != nil {
		t.Fatal(err)
	}
	trace[0].Batch.ReceivedAt[0] = at.Add(5 * time.Second)
	if err := checkRawEvidence(records, oracleAttempts(), h, trace, writes, 5000); err == nil {
		t.Fatal("late learning candidate escaped oracle")
	}
	if err := checkRetained(sequenceRetention{games: 2}, 1); err == nil {
		t.Fatal("host game budget escaped oracle")
	}
}
