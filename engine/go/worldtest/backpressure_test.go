// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestOverloadResynchronizesWithoutLosingAttemptOutcome(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		var scheduled *SequenceReceiver
		r := newRigWithPort(t, cat, readyCharacter(), allSources(), nil, parityPolicy(), func(w gateway.World) gateway.World { scheduled = NewSequenceReceiver(w); return scheduled })
		owner := r.port.Port.Owner()
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.p.Result(1, wire.StageSent)
		// The source keeps running while the gateway's receiver is held. These are
		// real authored pages and issued choices, not forged packet-sized payloads.
		for range r.c.Objects * 8 {
			c, err := r.w.Execute(t.Context(), world.Request{Owner: owner, Verb: world.VerbTalk, Target: target})
			if err != nil || c.Outcome.Stage != world.StageDone {
				t.Fatalf("host talk=%+v/%v", c.Outcome, err)
			}
		}
		synctest.Wait()
		for scheduled.Flush() {
			r.sync()
		}
		r.sync()
		r.p.Result(1, wire.StageDone)
		begins := 0
		for _, record := range r.p.Records {
			if record.Type == wire.Bye {
				t.Fatalf("overload closed participant: reason %d", gatewaytest.U(record, 1))
			}
			if record.Type == wire.Checkpoint && gatewaytest.U(record, 1) == uint64(wire.CheckpointBegin) {
				begins++
			}
		}
		if begins < 2 || !r.p.Ready {
			t.Fatalf("checkpoint count=%d ready=%v", begins, r.p.Ready)
		}
		if r.port.Port.Owner() != owner {
			t.Fatal("resynchronization replaced the incarnation")
		}
		if participantCommands(r.port.commands()) != 1 {
			t.Fatal("resynchronization reexecuted an attempt")
		}
	})
}
