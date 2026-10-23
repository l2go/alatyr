// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestMalformedFrameAppliesNoPrefix(t *testing.T) {
	both(t, func(r *rig) {
		codec, err := meaning.NewCodec(r.c)
		if err != nil {
			r.t.Fatal(err)
		}
		b, err := codec.Encode([]wire.Record{r.intent(1, 1, wire.At(11, true, 1, 2, 3)), r.intent(2, 1, wire.At(11, true, 4, 5, 6))})
		if err != nil {
			r.t.Fatal(err)
		}
		r.p.Raw(b[:len(b)-1])
		r.p.Until(func() bool {
			for _, rec := range r.p.Records {
				if rec.Type == wire.Bye {
					return true
				}
			}
			return false
		})
		r.sync()
		log := r.log()
		if len(log) != 1 || log[0].Command.Verb != 1 || log[0].Command.Payload[0].Value.XYZ != [3]int32{} {
			r.t.Fatal("malformed frame applied a valid prefix")
		}
		r.mu.Lock()
		defer r.mu.Unlock()
		if len(r.handoffs) != 1 || r.handoffs[0].N != 0 {
			r.t.Fatal("malformed frame admitted a participant action")
		}
	})
}

func TestDuplicateMismatchDoesNotWrite(t *testing.T) {
	both(t, func(r *rig) {
		r.p.Send(r.intent(1, 1, wire.At(11, true, 1, 2, 3)))
		r.p.Result(1, wire.StageSent)
		r.p.Send(r.intent(1, 1, wire.At(11, true, 4, 5, 6)))
		o := r.p.Result(1, wire.StageRefused)
		if gatewaytest.U(o, 3) != uint64(wire.ReasonDuplicateMismatch) {
			r.t.Fatal("duplicate mismatch executed", o)
		}
		r.sync()
		// Losing control may write one guarded system stop, not the duplicate.
		r.mu.Lock()
		defer r.mu.Unlock()
		if len(r.handoffs) != 2 || r.handoffs[0].N != 1 || r.handoffs[1].N != 0 {
			r.t.Fatal("duplicate created an extra participant handoff", r.handoffs)
		}
	})
}

func TestWatchAdmissionAndSlowReaderIsolation(t *testing.T) {
	both(t, func(r *rig) {
		watch := gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeObserve, false)
		defer watch.Close()
		r.sync()
		watch.Send(r.intent(1, 1, wire.At(11, true, 1, 2, 3)))
		o := watch.Result(1, wire.StageRefused)
		if gatewaytest.U(o, 3) != uint64(wire.ReasonWatch) || len(r.log()) != 0 {
			r.t.Fatal("observer admitted an effect", o)
		}
		stream, err := r.g.Pipe(r.t.Context(), "one")
		if err != nil {
			r.t.Fatal(err)
		}
		defer stream.Close()
		codec, err := meaning.NewCodec(r.c)
		if err != nil {
			r.t.Fatal(err)
		}
		hello, err := codec.Encode([]wire.Record{{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeObserve))}}})
		if err != nil {
			r.t.Fatal(err)
		}
		if err := stream.Write(hello, time.Now().Add(time.Second)); err != nil {
			r.t.Fatal(err)
		}
		// Never read its checkpoint. Its bounded write expires independently.
		r.advance(time.Second)
		status, err := r.g.Status(r.t.Context(), "one")
		if err != nil || status.Connections != 2 {
			r.t.Fatalf("connections=%d error=%v, want two healthy peers", status.Connections, err)
		}
		r.p.Send(r.intent(1, 1, wire.At(11, true, 7, 8, 9)))
		r.expect(1, 1, 1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
	})
}
