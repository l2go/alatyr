// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestAppearanceClosedVocabularyAndPresence(t *testing.T) {
	limits := wire.Limits{FrameBytes: 4096, StringBytes: 256, ListItems: 16, Depth: 4, Records: 8, Fields: 16, IdentifierBytes: 64, WorldBytes: 64, ClientBytes: 64}
	registry := wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits)
	registry[wire.Fact][wire.AppearanceField] = wire.AppearanceSpec()
	codec, err := wire.New(limits, registry)
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		name           string
		fields         []wire.Field
		present, valid bool
	}{
		{"absent", nil, false, true},
		{"unknown", []wire.Field{wire.U(1, true, 0), wire.U(2, true, 0), wire.U(3, true, 0)}, true, true},
		{"empty", []wire.Field{}, true, false},
		{"missing-branch", []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1)}, true, false},
		{"bad-race", []wire.Field{wire.U(1, true, 6), wire.U(2, true, 1), wire.U(3, true, 1)}, true, false},
		{"bad-sex", []wire.Field{wire.U(1, true, 1), wire.U(2, true, 3), wire.U(3, true, 1)}, true, false},
		{"bad-branch", []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.U(3, true, 3)}, true, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "objects"), wire.U(6, true, 1)}}
			if tc.present {
				r.Fields = append(r.Fields, wire.Obj(wire.AppearanceField, false, tc.fields))
			}
			b, err := codec.Encode([]wire.Record{r})
			if !tc.valid {
				if err == nil {
					t.Fatal("invalid appearance accepted")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			rs, err := codec.Decode(b)
			if err != nil {
				t.Fatal(err)
			}
			if _, yes := wire.Find(rs[0].Fields, wire.AppearanceField); yes != tc.present {
				t.Fatal("appearance presence changed")
			}
			// An older registry skips the nonessential object, including its
			// essential members, without invalidating the containing Fact.
			old, err := wire.New(limits, wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits))
			if err != nil {
				t.Fatal(err)
			}
			rs, err = old.Decode(b)
			if err != nil || rs[0].Unsupported {
				t.Fatalf("old decoder: %v/%v", rs, err)
			}
		})
	}
	for race := wire.RaceHuman; race <= wire.RaceDwarf; race++ {
		for sex := wire.SexMale; sex <= wire.SexFemale; sex++ {
			for branch := wire.BranchUnknown; branch <= wire.BranchMystic; branch++ {
				r := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "objects"), wire.U(6, true, 1), wire.Obj(wire.AppearanceField, false, []wire.Field{wire.U(1, true, uint64(race)), wire.U(2, true, uint64(sex)), wire.U(3, true, uint64(branch))})}}
				b, err := codec.Encode([]wire.Record{r})
				if err != nil {
					t.Fatal(err)
				}
				rs, err := codec.Decode(b)
				if err != nil {
					t.Fatal(err)
				}
				a, _ := wire.Find(rs[0].Fields, wire.AppearanceField)
				for i, want := range []uint64{uint64(race), uint64(sex), uint64(branch)} {
					got, _ := wire.Find(a.Fields, uint32(i+1))
					if got.U != want {
						t.Fatal("appearance vocabulary changed")
					}
				}
			}
		}
	}
	// Registries must not share mutable schema or enum slices.
	a, b := wire.AppearanceSpec(), wire.AppearanceSpec()
	a.Object[1].Enum[0] = 99
	if b.Object[1].Enum[0] != 0 {
		t.Fatal("shared appearance schema")
	}
}
