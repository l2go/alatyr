// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"bytes"
	"errors"
	"io"
	"math"
	"net"
	"reflect"
	"testing"
)

func testLimits() Limits {
	return Limits{FrameBytes: 2048, StringBytes: 128, ListItems: 7, Depth: 3, Records: 9, Fields: 12, IdentifierBytes: 16, WorldBytes: 32, ClientBytes: 32}
}
func testCodec(t *testing.T) *Codec {
	t.Helper()
	l := testLimits()
	r := Envelopes(Schema{1: Required(UInt)}, l)
	r[Fact][10] = Essential(Int)
	c, e := New(l, r)
	if e != nil {
		t.Fatal(e)
	}
	return c
}
func TestFrameAtomicityAndExtensionDisposition(t *testing.T) {
	c := testCodec(t)
	for _, tc := range []struct {
		name  string
		data  []byte
		err   error
		count int
	}{
		{"empty", nil, ErrFrame, 0}, {"valid", []byte{1, 2, 24, 0}, nil, 1},
		{"trailing byte", []byte{1, 2, 24, 0, 0}, ErrFrame, 0},
		{"duplicate", []byte{1, 4, 24, 0, 24, 1}, ErrFrame, 0},
		{"nonminimal", []byte{129, 0, 2, 24, 0}, ErrFrame, 0},
		{"overflow", []byte{255, 255, 255, 255, 255, 255, 255, 255, 255, 2}, ErrFrame, 0},
		{"optional record opaque", []byte{17, 1, 255, 1, 2, 24, 0}, nil, 1},
		{"essential record", []byte{1, 2, 24, 0, 18, 0}, ErrUnsupported, 0},
		{"unknown wire type", []byte{1, 2, 28, 0}, ErrFrame, 0},
		{"zero field", []byte{1, 2, 8, 0}, ErrFrame, 0},
		{"wrong bit", []byte{1, 2, 16, 0}, ErrFrame, 0},
		{"missing field", []byte{1, 0}, ErrFrame, 0},
		{"missing fact header with extension", []byte{8, 14, 40, 1, 57, 0, 72, 1, 90, 1, 'd', 104, 1, 184, 12, 0}, ErrFrame, 0},
		{"missing intent header with extension", []byte{10, 7, 40, 1, 56, 1, 184, 12, 0}, ErrUnsupported, 0},
		{"optional field", []byte{1, 5, 24, 0, 160, 1, 0}, nil, 1},
		{"essential field", []byte{1, 5, 24, 0, 168, 1, 0}, ErrUnsupported, 0},
		{"essential intent field", []byte{10, 9, 24, 1, 40, 1, 56, 1, 184, 12, 0}, nil, 1},
		{"essential fact field", []byte{8, 16, 24, 1, 40, 1, 57, 0, 72, 1, 90, 1, 'd', 104, 1, 184, 12, 0}, nil, 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			records, e := c.Decode(tc.data)
			if !errors.Is(e, tc.err) || len(records) != tc.count {
				t.Fatalf("records=%d error=%v", len(records), e)
			}
			if tc.name == "essential intent field" || tc.name == "essential fact field" {
				if !records[0].Unsupported {
					t.Fatal("missing extension disposition")
				}
			}
		})
	}
}
func TestSignedExtremesAndOwnership(t *testing.T) {
	c := testCodec(t)
	for _, n := range []int64{0, -1, 1, math.MinInt64, math.MaxInt64, 5000000000} {
		r := Record{Type: Fact, Fields: []Field{U(1, true, 1), U(2, true, 1), S(3, true, 0), U(4, true, 1), Data(5, true, "d"), U(6, true, 1), S(10, true, n)}}
		b, e := c.Encode([]Record{r})
		if e != nil {
			t.Fatal(e)
		}
		got, e := c.Decode(b)
		if e != nil {
			t.Fatal(e)
		}
		v, _ := Find(got[0].Fields, 10)
		if v.S != n {
			t.Fatalf("%d != %d", v.S, n)
		}
		domain, _ := Find(got[0].Fields, 5)
		clear(b)
		if string(domain.Data) != "d" {
			t.Fatal("retained input alias")
		}
	}
}
func TestAllEnvelopeRecordsAndReasonCodes(t *testing.T) {
	c := testCodec(t)
	records := []Record{
		{Type: Hello, Fields: []Field{U(1, true, 1), U(2, true, 1), Data(3, true, "v/1"), U(4, true, uint64(ParticipantPerson)), U(5, true, uint64(ModeAct))}},
		{Type: Welcome, Fields: []Field{U(1, true, 1), Data(2, true, "v/1"), Data(3, true, "world"), U(4, true, 1), U(5, true, uint64(ModeAct)), U(6, true, 0), Obj(7, true, []Field{U(1, true, 7)})}},
		{Type: Checkpoint, Fields: []Field{U(1, true, uint64(CheckpointBegin)), U(2, true, 1), U(3, true, 0)}},
		{Type: Control, Fields: []Field{U(1, true, uint64(ControlGranted)), U(2, true, 1), U(3, true, 1)}},
		{Type: Pong, Fields: []Field{U(1, true, math.MaxUint64)}},
	}
	for _, r := range records {
		encoded, e := c.Encode([]Record{r})
		if e != nil {
			t.Fatal(e)
		}
		got, e := c.Decode(encoded)
		got[0].EncodedBytes = 0
		if e != nil || !reflect.DeepEqual(got[0], r) {
			t.Fatalf("%d: %v %#v", r.Type, e, got)
		}
	}
	reasons := []Reason{ReasonNone, ReasonNotReady, ReasonWatch, ReasonStaleControl, ReasonStaleTarget, ReasonStaleOffer, ReasonQueueExpired, ReasonRate, ReasonBusy, ReasonUnknownEssential, ReasonInvalid, ReasonNoAction, ReasonNotAlive, ReasonSkillUnknown, ReasonSkillPassive, ReasonSkillDisabled, ReasonWorld, ReasonTimeout, ReasonWorldClosed, ReasonInterrupted, ReasonTooOld, ReasonOutOfOrder, ReasonDuplicateMismatch, ReasonTargetUnconfirmed, ReasonBackendError}
	for i, reason := range reasons {
		if uint64(reason) != uint64(i) {
			t.Fatal("reason registry changed")
		}
		stage, delivery := StageRefused, DeliveryNone
		if reason == ReasonNone {
			stage, delivery = StageSent, DeliveryLocal
		}
		r := Record{Type: Result, Fields: []Field{U(1, true, 1), U(2, true, uint64(stage)), U(3, true, uint64(reason)), U(5, true, 1), U(6, true, 1), U(7, true, uint64(delivery))}}
		if _, e := c.Encode([]Record{r}); e != nil {
			t.Fatalf("reason %d: %v", reason, e)
		}
	}
	for i, reason := range []CloseReason{CloseNormal, CloseUnsupported, CloseFrameError, CloseSlowConsumer, CloseFull, CloseAuth, CloseOrigin, CloseInternal, CloseWorldClosed} {
		if uint64(reason) != uint64(i) {
			t.Fatal("close registry changed")
		}
		if _, e := c.Encode([]Record{{Type: Bye, Fields: []Field{U(1, true, uint64(reason))}}}); e != nil {
			t.Fatal(e)
		}
	}
}
func TestStructuralLimitsBeforeAllocation(t *testing.T) {
	c := testCodec(t)
	oversized := bytes.Repeat([]byte{0}, c.limits.FrameBytes+1)
	if r, e := c.Decode(oversized); !errors.Is(e, ErrFrame) || r != nil {
		t.Fatal(e)
	}
	if _, e := c.Read(bytes.NewReader([]byte{129, 16})); !errors.Is(e, ErrFrame) {
		t.Fatal("length was not rejected before reading body", e)
	}
	if _, e := c.Read(bytes.NewReader(nil)); e != io.EOF {
		t.Fatal(e)
	}
	for _, data := range [][]byte{{128}, {0}, {1, 1}, {128, 0}, {255, 255, 255, 255, 255, 255, 255, 255, 255, 2}} {
		if _, e := c.Read(bytes.NewReader(data)); !errors.Is(e, ErrFrame) {
			t.Fatalf("%x: %v", data, e)
		}
	}
	l := testLimits()
	el := Required(String)
	r := Envelopes(Schema{1: Required(UInt)}, l)
	r[Ping][2] = Spec{Shape: List, Essential: true, Element: &el}
	d, e := New(l, r)
	if e != nil {
		t.Fatal(e)
	}
	items := make([]Value, l.ListItems+1)
	if _, e = d.Encode([]Record{{Type: Ping, Fields: []Field{U(1, true, 1), Items(2, true, items)}}}); !errors.Is(e, ErrFrame) {
		t.Fatal("list cap", e)
	}
	raw := bytes.Repeat([]byte{'x'}, l.StringBytes+1)
	if _, e = d.Encode([]Record{{Type: Hello, Fields: []Field{U(1, true, 1), U(2, true, 1), Data(3, true, "v"), U(4, true, uint64(ParticipantPerson)), U(5, true, uint64(ModeAct)), Data(6, false, string(raw))}}}); !errors.Is(e, ErrFrame) {
		t.Fatal("string cap", e)
	}
	badControl := Record{Type: Control, Fields: []Field{U(1, true, uint64(ControlGranted))}}
	if _, e = c.Encode([]Record{badControl}); !errors.Is(e, ErrFrame) {
		t.Fatal(e)
	}
}
func TestStreamShortWritesAndPipe(t *testing.T) {
	c := testCodec(t)
	r := Record{Type: Ping, Fields: []Field{U(1, true, 7)}}
	var b bytes.Buffer
	short := &shortWriter{writer: &b}
	if e := c.Write(short, []Record{r}); e != nil {
		t.Fatal(e)
	}
	got, e := c.Read(&b)
	if len(got) == 1 {
		got[0].EncodedBytes = 0
	}
	if e != nil || !reflect.DeepEqual(got, []Record{r}) {
		t.Fatal(got, e)
	}
	left, right := net.Pipe()
	defer left.Close()
	defer right.Close()
	finished := make(chan error, 1)
	go func() {
		if e := c.Write(left, []Record{r}); e != nil {
			finished <- e
			return
		}
		_, e := c.Read(left)
		finished <- e
	}()
	if _, e := c.Read(right); e != nil {
		t.Fatal(e)
	}
	if e := c.Write(right, []Record{{Type: Pong, Fields: []Field{U(1, true, 7)}}}); e != nil {
		t.Fatal(e)
	}
	if e := <-finished; e != nil {
		t.Fatal(e)
	}
}

type shortWriter struct{ writer io.Writer }

func (w *shortWriter) Write(b []byte) (int, error) { return w.writer.Write(b[:min(1, len(b))]) }

func TestUnsupportedRequestIdentityUsesCanonicalOrder(t *testing.T) {
	limits := Limits{FrameBytes: 1024, StringBytes: 100, ListItems: 10, Depth: 3, Records: 10, Fields: 20, IdentifierBytes: 64, WorldBytes: 256, ClientBytes: 128}
	registry := Envelopes(Schema{}, limits)
	registry[Intent][10] = Spec{Shape: Object, Essential: true, Object: Schema{1: Required(UInt), 2: Required(UInt)}}
	codec, err := New(limits, registry)
	if err != nil {
		t.Fatal(err)
	}
	base := []Field{U(1, true, 1), U(2, true, 1), U(3, true, 1), Obj(10, true, []Field{U(1, true, 1), U(2, true, 2)})}
	raw, err := codec.Encode([]Record{{Type: Intent, Fields: base}})
	if err != nil {
		t.Fatal(err)
	}
	raw[1] += 3
	raw = append(raw, 0xb8, 0x0c, 0)
	one, err := codec.Decode(raw)
	if err != nil {
		t.Fatal(err)
	}
	// Reverse the two fields inside the already validated nested object.
	modified := append([]byte(nil), raw...)
	for i := 0; i < len(modified)-5; i++ {
		if modified[i] == 0x18 && modified[i+1] == 1 && modified[i+2] == 0x28 && modified[i+3] == 2 {
			copy(modified[i:i+4], []byte{0x28, 2, 0x18, 1})
			break
		}
	}
	two, err := codec.Decode(modified)
	if err != nil || one[0].UnsupportedHash != two[0].UnsupportedHash {
		t.Fatal("ordering changed identity", err)
	}
	modified[len(modified)-1] = 1
	three, err := codec.Decode(modified)
	if err != nil || three[0].UnsupportedHash == one[0].UnsupportedHash {
		t.Fatal("changed extension reused identity", err)
	}
}
