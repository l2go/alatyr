// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"reflect"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestCompleteEnvelopeRegistrySurface(t *testing.T) {
	c := codec(t)
	cfg := testConfig()
	for _, kind := range []wire.Participant{wire.ParticipantPerson, wire.ParticipantBot, wire.ParticipantAgent} {
		for _, mode := range []wire.Mode{wire.ModeAct, wire.ModeObserve} {
			r := wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, Meaning), wire.U(4, true, uint64(kind)), wire.U(5, true, uint64(mode)), wire.Data(6, false, "client")}}
			b, e := c.Encode([]wire.Record{r})
			if e != nil {
				t.Fatal(e)
			}
			got, e := c.Decode(b)
			if e != nil || getString(got[0].Fields, 6) != "client" {
				t.Fatal(got, e)
			}
		}
	}
	welcome := wire.Record{Type: wire.Welcome, Fields: []wire.Field{wire.U(1, true, 1), wire.Data(2, true, Meaning), wire.Data(3, true, "world release"), wire.U(4, true, 1), wire.U(5, true, uint64(wire.ModeAct)), wire.U(6, true, 0), wire.Obj(7, true, cfg.LimitsFields())}}
	b, e := c.Encode([]wire.Record{welcome})
	if e != nil {
		t.Fatal(e)
	}
	records, e := c.Decode(b)
	if e != nil {
		t.Fatal(e)
	}
	limits, _ := wire.Find(records[0].Fields, 7)
	if !reflect.DeepEqual(limits.Fields, cfg.LimitsFields()) {
		t.Fatal("advertised limit registry changed")
	}
	end := wire.Record{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointEnd)), wire.U(2, true, 1), wire.U(3, true, 1), wire.Items(4, true, []wire.Value{{Data: []byte("history")}})}}
	if _, e = c.Encode([]wire.Record{end}); e != nil {
		t.Fatal(e)
	}
	for _, op := range []wire.ControlOp{wire.ControlTake, wire.ControlGranted, wire.ControlLost, wire.ControlResync} {
		f := []wire.Field{wire.U(1, true, uint64(op))}
		if op != wire.ControlTake {
			f = append(f, wire.U(2, true, 1))
		}
		if op == wire.ControlGranted {
			f = append(f, wire.U(3, true, 1))
		}
		if _, e = c.Encode([]wire.Record{{Type: wire.Control, Fields: f}}); e != nil {
			t.Fatal(op, e)
		}
	}
	text := []wire.Field{wire.Data(1, true, ""), wire.U(2, true, uint64(wire.ProvenanceSystem)), wire.Data(3, false, "en-US")}
	for _, tc := range []struct {
		stage    wire.Stage
		reason   wire.Reason
		delivery wire.Delivery
	}{{wire.StageSent, wire.ReasonNone, wire.DeliveryLocal}, {wire.StageDone, wire.ReasonNone, wire.DeliveryBackend}, {wire.StageRefused, wire.ReasonInvalid, wire.DeliveryNone}, {wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend}, {wire.StageUnrecoverable, wire.ReasonTooOld, wire.DeliveryNone}} {
		r := wire.Record{Type: wire.Result, Fields: []wire.Field{wire.U(1, true, 7), wire.U(2, true, uint64(tc.stage)), wire.U(3, true, uint64(tc.reason)), wire.Obj(4, false, text), wire.U(5, true, 4), wire.U(6, true, 4), wire.U(7, true, uint64(tc.delivery))}}
		b, e := c.Encode([]wire.Record{r})
		if e != nil {
			t.Fatal(e)
		}
		records, e := c.Decode(b)
		if e != nil {
			t.Fatal(e)
		}
		v, _ := wire.Find(records[0].Fields, 4)
		if !reflect.DeepEqual(v.Fields, text) {
			t.Fatal("text presence/provenance/language changed")
		}
	}
	if _, e = c.Encode([]wire.Record{{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0), wire.Obj(2, false, text)}}}); e != nil {
		t.Fatal(e)
	}
	for _, origin := range []wire.Provenance{wire.ProvenanceAuthored, wire.ProvenanceSystem, wire.ProvenancePlayer, wire.ProvenanceUnclassified} {
		v := append([]wire.Field(nil), text...)
		v[1] = wire.U(2, true, uint64(origin))
		if _, e = c.Encode([]wire.Record{{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0), wire.Obj(2, false, v)}}}); e != nil {
			t.Fatal(e)
		}
	}
}
func TestOptionalAttemptAndCauseMetadata(t *testing.T) {
	c := codec(t)
	cause := uint64(7)
	ev := Event{Kind: Known, Skills: []Skill{}, Complete: true}
	r, e := EventRecord(ev, FactContext{Seq: 1, Epoch: 1, Cause: &cause})
	if e != nil {
		t.Fatal(e)
	}
	b, e := c.Encode([]wire.Record{r})
	if e != nil {
		t.Fatal(e)
	}
	records, e := c.Decode(b)
	if e != nil {
		t.Fatal(e)
	}
	d, e := EventValue(records[0])
	if e != nil || d.Context.Cause == nil || *d.Context.Cause != 7 {
		t.Fatal(d, e)
	}
	ttl := uint64(0)
	r, e = CommandRecord(Command{Kind: Walk, X: 1, Y: 2, Z: 3}, CommandContext{N: 1, Control: 1, QueueMS: &ttl})
	if e != nil {
		t.Fatal(e)
	}
	b, e = c.Encode([]wire.Record{r})
	if e != nil {
		t.Fatal(e)
	}
	records, e = c.Decode(b)
	if e != nil {
		t.Fatal(e)
	}
	if v, ok := wire.Find(records[0].Fields, 4); !ok || v.U != 0 {
		t.Fatal("explicit zero queue lifetime lost")
	}
}
