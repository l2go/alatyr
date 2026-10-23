// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func testConfig(t *testing.T) Config {
	t.Helper()
	bounds := wire.Limits{FrameBytes: 4096, StringBytes: 256, ListItems: 16,
		Depth: 4, Records: 16, Fields: 32, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128}
	advertised := wire.Schema{30: wire.Required(wire.UInt)}
	codec, err := wire.New(bounds, wire.Envelopes(advertised, bounds))
	if err != nil {
		t.Fatal(err)
	}
	return Config{
		Codec: codec,
		Factory: func(context.Context, string) (World, State, error) {
			return &scheduleWorld{}, &testState{}, nil
		},
		Identities: map[string]Identity{"one": {Act: true}, "two": {Act: true}},
		Versions:   []uint64{1}, Meanings: []string{"test/1"}, World: "test",
		Limits: []wire.Field{wire.U(30, true, 65536)},
		Attempts: proto.Config{Results: 64, Pending: 8, IntentBytes: 1024,
			EvidenceMS: 5000, QueueMS: 5000, Classes: []string{"take", "talk"}},
		FrameBytes: 4096, OutBytes: 8192, Sessions: 2, Connections: 4, Teleports: 4,
		IntentBurst: 10, IntentRate: 20, ServiceBurst: 4, ServiceRate: 2,
		Hello: time.Second, Write: time.Second, Checkpoint: time.Second,
		Ping: 15 * time.Second, Pong: 30 * time.Second, Batch: time.Millisecond,
		TargetWait: 1500 * time.Millisecond,
		Ticks: func(d time.Duration) (<-chan time.Time, func()) {
			ticker := time.NewTicker(d)
			return ticker.C, ticker.Stop
		},
	}
}

func testIntent(n, verb, control uint64) wire.Record {
	return record(wire.Intent, wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, control))
}

type testState struct{}

func (*testState) Apply(Update, int64) (Observation, error) { return Observation{}, nil }
func (*testState) Snapshot(int64) (Snapshot, error)         { return Snapshot{Ready: true}, nil }
func (*testState) Plan(wire.Record, int64) (*Action, wire.Reason) {
	return &Action{Effective: 2, Local: true}, wire.ReasonNone
}
func (*testState) Validate(*Action, int64) wire.Reason       { return wire.ReasonNone }
func (*testState) Start(*Action)                             {}
func (*testState) Confirmed(*Action) bool                    { return false }
func (*testState) Witness(*Action, Update) Witness           { return NoWitness }
func (*testState) CandidateValid(*Action) bool               { return false }
func (*testState) Proof(*Action, int64) (wire.Record, error) { return wire.Record{}, nil }
func (*testState) Invalidate()                               {}
func (*testState) Stop() *Step                               { return nil }
