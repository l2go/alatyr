// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestServiceSkipsActionInterruptedEarlierInPass(t *testing.T) {
	for _, waiting := range []bool{false, true} {
		t.Run(map[bool]string{false: "queued helper", true: "waiting helper"}[waiting], func(t *testing.T) {
			r, w, _ := budgetRig(t)
			r.game.actions[2].action.Local = true
			_, control := r.game.attempts.Controller()
			a := r.game.attempts.Reserve(1, true, true, proto.Attempt{N: 3, Verb: 11, Control: control, Payload: []byte{3}, EncodedBytes: 1}, r.game.gate.now())
			if !a.Allocated || a.Result != nil {
				t.Fatal(a)
			}
			if _, err := r.game.attempts.Prepare(3, 5, "take", true); err != nil {
				t.Fatal(err)
			}
			if waiting {
				if err := r.game.attempts.Contact(3, r.game.gate.now()); err != nil {
					t.Fatal(err)
				}
			}
			r.game.actions[3] = &execution{action: &Action{Effective: 5, Class: "take", Waiting: true, Steps: []Step{{Await: true}}}, waiting: waiting}
			var results []Summary
			r.game.gate.cfg.Diagnostic = func(s Summary) {
				if s.Record == wire.Result && s.Connection == 0 {
					results = append(results, s)
				}
			}
			r.game.service(context.Background())
			// The sorted key snapshot still contains 3 after local action 2 removes it.
			if r.game.closed || w.called || len(r.game.actions) != 0 {
				t.Fatalf("closed=%t writes=%t actions=%d", r.game.closed, w.called, len(r.game.actions))
			}
			entry, _ := r.game.attempts.Lookup(3)
			want := wire.StageRefused
			if waiting {
				want = wire.StageUnknown
			}
			if !entry.Terminal || entry.Result == nil || entry.Result.Stage != want || entry.Result.Reason != wire.ReasonInterrupted {
				t.Fatalf("interrupted entry=%+v", entry)
			}
			if len(results) != 2 || results[0].N != 3 || results[1].N != 2 {
				t.Fatalf("result order=%+v", results)
			}
			r.game.service(context.Background())
			if len(results) != 2 {
				t.Fatalf("duplicate results=%+v", results)
			}
		})
	}
}
