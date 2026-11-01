// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestFormTokenGrammarAndPresence(t *testing.T) {
	limits := wire.Limits{FrameBytes: 4096, StringBytes: 256, ListItems: 16, Depth: 4, Records: 8, Fields: 16, IdentifierBytes: 64, WorldBytes: 64, ClientBytes: 64}
	registry := wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits)
	registry[wire.Fact][wire.FormField] = wire.FormSpec()
	codec, err := wire.New(limits, registry)
	if err != nil {
		t.Fatal(err)
	}
	loose := wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits)
	loose[wire.Fact][wire.FormField] = wire.Optional(wire.String)
	plain, err := wire.New(limits, loose)
	if err != nil {
		t.Fatal(err)
	}
	fact := func(token string, present, essential bool) wire.Record {
		r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "objects"), wire.U(6, true, 1)}}
		if present {
			r.Fields = append(r.Fields, wire.Data(wire.FormField, essential, token))
		}
		return r
	}
	for _, token := range []string{"a", "field_wolf", "a1", "a_b", strings.Repeat("a", 32)} {
		t.Run("valid/"+token, func(t *testing.T) {
			if !wire.ValidForm(token) {
				t.Fatal("rejected a token inside the grammar")
			}
			b, err := codec.Encode([]wire.Record{fact(token, true, false)})
			if err != nil {
				t.Fatal(err)
			}
			rs, err := codec.Decode(b)
			if err != nil {
				t.Fatal(err)
			}
			got, yes := wire.Find(rs[0].Fields, wire.FormField)
			if !yes || string(got.Data) != token {
				t.Fatalf("form = %q present %v", got.Data, yes)
			}
			old, err := wire.New(limits, wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits))
			if err != nil {
				t.Fatal(err)
			}
			rs, err = old.Decode(b)
			if err != nil || rs[0].Unsupported {
				t.Fatalf("old decoder: %v/%v", rs, err)
			}
			if _, yes := wire.Find(rs[0].Fields, wire.FormField); yes {
				t.Fatal("old decoder retained an unknown form")
			}
		})
	}
	for _, token := range []string{"", "A", "Wolf", "1a", "_a", "a-b", "a b", strings.Repeat("a", 33), "field_wolf!"} {
		t.Run("invalid/"+token, func(t *testing.T) {
			if wire.ValidForm(token) {
				t.Fatal("accepted a token outside the grammar")
			}
			if _, err := codec.Encode([]wire.Record{fact(token, true, false)}); !errors.Is(err, wire.ErrFrame) {
				t.Fatalf("encode: %v", err)
			}
			b, err := plain.Encode([]wire.Record{fact(token, true, false)})
			if err != nil {
				t.Fatal(err)
			}
			if _, err := codec.Decode(b); !errors.Is(err, wire.ErrFrame) {
				t.Fatalf("decode: %v", err)
			}
		})
	}
	if _, err := codec.Encode([]wire.Record{fact("field_wolf", true, true)}); !errors.Is(err, wire.ErrFrame) {
		t.Fatal("essential form accepted")
	}
	b, err := codec.Encode([]wire.Record{fact("", false, false)})
	if err != nil {
		t.Fatal(err)
	}
	rs, err := codec.Decode(b)
	if err != nil {
		t.Fatal(err)
	}
	if _, yes := wire.Find(rs[0].Fields, wire.FormField); yes {
		t.Fatal("absent form became a value")
	}
	bad := wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits)
	s := wire.FormSpec()
	s.Shape = wire.UInt
	bad[wire.Fact][wire.FormField] = s
	if _, err := wire.New(limits, bad); !errors.Is(err, wire.ErrConfig) {
		t.Fatalf("token on a non-string: %v", err)
	}
}
