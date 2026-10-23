// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package profile

import (
	"bytes"
	"errors"
	"math"
	"reflect"
	"testing"

	"github.com/l2go/alatyr/engine/go/bridge/internal/fixtures"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func newCodec(t testing.TB) *Codec {
	t.Helper()
	c, err := NewProfileCodec()
	if err != nil {
		t.Fatal(err)
	}
	return c
}
func TestSharedProfileVectors(t *testing.T) {
	c := newCodec(t)
	decoded := map[string]wire.Record{}
	for _, v := range fixtures.Load(t, true) {
		t.Run(v.ID, func(t *testing.T) {
			data := fixtures.Bytes(t, v)
			var rs []wire.Record
			var err error
			if v.Transport == "stream" {
				rs, err = c.Read(bytes.NewReader(data))
			} else {
				rs, err = c.Decode(data)
			}
			if v.Expected != "ok" {
				want := wire.ErrFrame
				if v.Expected == "unsupported" {
					want = wire.ErrUnsupported
				}
				if !errors.Is(err, want) || rs != nil {
					t.Fatalf("category/atomicity: %v", err)
				}
				return
			}
			if err != nil || len(rs) != 1 {
				t.Fatalf("decode: %v", err)
			}
			decoded[v.ID] = rs[0]
			var out bytes.Buffer
			if v.Transport == "stream" {
				err = c.Write(&out, rs)
			} else {
				var b []byte
				b, err = c.Encode(rs)
				out.Write(b)
			}
			if err != nil || !bytes.Equal(data, out.Bytes()) {
				t.Fatalf("canonical input changed: %v", err)
			}
		})
	}
	b := decoded["BV06"]
	effects, _ := wire.Find(b.Fields, 66)
	if U(b, 1) != 1 || U(b, 2) != 2 || U(b, 3) != 0 || U(b, 5) != 7 || U(b, 6) != 7 || U(b, 7) != 2 || Has(b, 64) || Has(b, 65) || len(effects.Items) != 2 {
		t.Fatal("buy typed view")
	}
	for n, want := range map[uint32]int64{2: 1060, 3: 70, 4: 2} {
		v, _ := wire.Find(effects.Items[0].Fields, n)
		if v.S != want {
			t.Fatal("item effect")
		}
	}
	coin, _ := wire.Find(effects.Items[1].Fields, 4)
	if coin.S != -13 {
		t.Fatal("coin effect")
	}
	cp := decoded["BV11"]
	inc, _ := wire.Find(cp.Fields, 4)
	if U(cp, 1) != 2 || U(cp, 2) != 1 || !Has(cp, 3) || U(cp, 3) != 0 || len(inc.Items) != 1 || string(inc.Items[0].Data) != "objects" {
		t.Fatal("checkpoint presence")
	}
}
func TestHistoricalVectors(t *testing.T) {
	c, err := wire.New(Limits(), NeutralRegistry())
	if err != nil {
		t.Fatal(err)
	}
	vectors := fixtures.Load(t, false)
	for _, v := range vectors {
		t.Run(v.ID, func(t *testing.T) {
			b := fixtures.Bytes(t, v)
			var rs []wire.Record
			var err error
			if v.ID == "V17" || v.ID == "V21" {
				rs, err = c.Read(bytes.NewReader(b))
			} else {
				rs, err = c.Decode(b)
			}
			switch v.ID {
			case "V11":
				if !errors.Is(err, wire.ErrUnsupported) {
					t.Fatal(err)
				}
				return
			case "V12", "V13", "V18", "V19", "V20", "V21":
				if !errors.Is(err, wire.ErrFrame) || rs != nil {
					t.Fatal(err)
				}
				return
			case "V10":
				if err != nil || len(rs) != 0 {
					t.Fatal(err)
				}
				return
			case "V06":
				if err != nil || len(rs) != 1 || !rs[0].Unsupported {
					t.Fatal(err)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			encoded, err := c.Encode(rs)
			if err != nil {
				t.Fatal(err)
			}
			if v.ID == "V05" {
				if !bytes.Equal(encoded, fixtures.Bytes(t, vectors[2])) {
					t.Fatal("known fields view")
				}
			} else if v.ID != "V17" && !bytes.Equal(encoded, b) {
				t.Fatal("canonical core input")
			}
		})
	}
}
func result(stage, reason, delivery uint64, code *uint64, effects []wire.Value, present bool) wire.Record {
	f := []wire.Field{wire.U(1, true, 1), wire.U(2, true, stage), wire.U(3, true, reason), wire.U(5, true, 1), wire.U(6, true, 1), wire.U(7, true, delivery)}
	if code != nil {
		f = append(f, wire.Data(64, true, Namespace), wire.U(65, true, *code))
	}
	if present {
		f = append(f, wire.Items(66, true, effects))
	}
	return wire.Record{Type: wire.Result, Fields: f}
}
func ptr(v uint64) *uint64 { return &v }
func fact(extra ...wire.Field) wire.Record {
	return wire.Record{Type: wire.Fact, Fields: append([]wire.Field{wire.U(1, true, 4), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "objects"), wire.U(6, true, 1)}, extra...)}
}
func TestProfileCategoryOrder(t *testing.T) {
	c := newCodec(t)
	registry := Registry()
	generation := registry[wire.Fact][66]
	generation.Nonzero = false
	registry[wire.Fact][66] = generation
	raw, err := wire.New(Limits(), registry)
	if err != nil {
		t.Fatal(err)
	}
	cases := []struct {
		name string
		r    wire.Record
		want error
	}{
		{"local sent", result(1, 0, 1, nil, nil, false), wire.ErrFrame},
		{"done absent", result(2, 0, 2, nil, nil, false), wire.ErrFrame},
		{"effects before done", result(1, 0, 2, nil, nil, true), wire.ErrFrame},
		{"zero code", result(2, 0, 2, ptr(0), nil, true), wire.ErrFrame},
		{"unrecoverable missing", result(5, 20, 0, nil, nil, false), wire.ErrFrame},
		{"unrecoverable wrong", result(5, 20, 0, ptr(1), nil, false), wire.ErrFrame},
		{"unrecoverable 99", result(5, 20, 0, ptr(99), nil, false), wire.ErrFrame},
		{"sent local 99", result(1, 0, 1, ptr(99), nil, false), wire.ErrFrame},
		{"unknown code before effects", result(3, 16, 0, ptr(99), nil, true), wire.ErrUnsupported},
		{"generation alone", fact(wire.U(66, true, 1)), wire.ErrFrame},
		{"own alone", fact(wire.U(67, true, 1)), wire.ErrFrame},
		{"own false", fact(wire.U(67, true, 0)), wire.ErrFrame},
		{"own false generation", fact(wire.U(66, true, 1), wire.U(67, true, 0)), wire.ErrFrame},
		{"zero generation", fact(wire.U(66, true, 0), wire.U(67, true, 1)), wire.ErrFrame},
		{"cause", fact(wire.U(32, true, 1)), wire.ErrFrame},
	}
	for _, code := range []uint64{11, 12, 13, 19} {
		cases = append(cases, struct {
			name string
			r    wire.Record
			want error
		}{"forbidden result reason", result(3, 16, 0, ptr(code), nil, false), wire.ErrFrame})
	}
	for _, code := range []uint64{1, 5, 6, 7, 8, 9, 10, 14, 18, 20, 21} {
		cases = append(cases, struct {
			name string
			r    wire.Record
			want error
		}{"wrong pair", result(3, 2, 0, ptr(code), nil, false), wire.ErrFrame})
	}
	wrongNamespace := result(3, 16, 0, ptr(99), nil, false)
	wrongNamespace.Fields[6] = wire.Data(64, true, "wrong")
	cases = append(cases, struct {
		name string
		r    wire.Record
		want error
	}{"namespace before unknown", wrongNamespace, wire.ErrFrame})
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			data, err := raw.Encode([]wire.Record{tc.r})
			if err != nil {
				t.Fatal("test generator", err)
			}
			if rs, err := c.Decode(data); !errors.Is(err, tc.want) || rs != nil {
				t.Fatalf("decode got %v want %v", err, tc.want)
			}
			if _, err := c.Encode([]wire.Record{tc.r}); !errors.Is(err, tc.want) {
				t.Fatalf("encode got %v want %v", err, tc.want)
			}
		})
	}
	// A valid first record cannot escape a bad second record.
	good, _ := c.Encode([]wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 1)}}})
	bad, _ := raw.Encode([]wire.Record{cases[0].r})
	if rs, err := c.Decode(append(good, bad...)); err == nil || rs != nil {
		t.Fatal("partial frame")
	}
}
func TestAllLegalReasonPairs(t *testing.T) {
	c := newCodec(t)
	for code := uint64(1); code <= 21; code++ {
		for core := uint64(0); core <= 24; core++ {
			if !profilePair(code, core) {
				continue
			}
			stage, delivery := uint64(3), uint64(0)
			if code == 20 {
				stage = 4
				delivery = 2
			}
			if code == 21 {
				stage = 5
			}
			r := result(stage, core, delivery, ptr(code), nil, false)
			b, err := c.Encode([]wire.Record{r})
			if err != nil {
				t.Fatalf("pair %d/%d: %v", code, core, err)
			}
			if _, err = c.Decode(b); err != nil {
				t.Fatal(err)
			}
		}
	}
	for _, v := range []uint64{0, 1, math.MaxUint64} {
		r := wire.Record{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, v)}}
		b, err := c.Encode([]wire.Record{r})
		if err != nil {
			t.Fatal(err)
		}
		rs, err := c.Decode(b)
		if err != nil || U(rs[0], 1) != v {
			t.Fatal("unsigned extreme")
		}
	}
	for _, v := range []int64{math.MinInt64, math.MaxInt64} {
		r := wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.U(3, true, 1), wire.S(16, true, v)}}
		b, err := c.Encode([]wire.Record{r})
		if err != nil {
			t.Fatal(err)
		}
		rs, err := c.Decode(b)
		if err != nil || S(rs[0], 16) != v {
			t.Fatal("signed extreme")
		}
	}
}
func FuzzDecodeProfile(f *testing.F) {
	c := newCodec(f)
	for _, v := range fixtures.Load(f, true) {
		f.Add(v.Transport == "stream", fixtures.Bytes(f, v))
	}
	f.Add(false, []byte{})
	f.Add(true, []byte{128})
	f.Fuzz(func(t *testing.T, stream bool, b []byte) {
		decode := func() ([]wire.Record, error) {
			if stream {
				return c.Read(bytes.NewReader(b))
			}
			return c.Decode(b)
		}
		rs, err := decode()
		again, e2 := decode()
		category := func(err error) int {
			if err == nil {
				return 0
			}
			if errors.Is(err, wire.ErrUnsupported) {
				return 1
			}
			return 2
		}
		if category(err) != category(e2) || !reflect.DeepEqual(rs, again) {
			t.Fatal("unstable parser")
		}
		if err != nil {
			if rs != nil {
				t.Fatal("partial application")
			}
			return
		}
		if len(rs) == 0 {
			return
		}
		for _, r := range rs {
			if r.Unsupported {
				return
			}
		}
		canon, err := c.Encode(rs)
		if err != nil {
			t.Fatal(err)
		}
		round, err := c.Decode(canon)
		if err != nil {
			t.Fatal(err)
		}
		final, err := c.Encode(round)
		if err != nil || !bytes.Equal(canon, final) {
			t.Fatal("unstable canonical form")
		}
	})
}
