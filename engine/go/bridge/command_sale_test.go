// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestCommandPreservesTheObservedZeroSaleList(t *testing.T) {
	for _, tc := range []struct {
		name string
		verb uint64
		list int64
		ok   bool
	}{
		{"ordinary-sale", 8, 0, true},
		{"leased-sale", 8, 1000001, true},
		{"negative-sale", 8, -1, false},
		{"zero-buy", 7, 0, false},
		{"ordinary-buy", 7, 731, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			cmd := Command{Key: 1, Verb: tc.verb, Payload: []wire.Field{wire.S(14, true, tc.list), wire.S(16, true, 1), wire.S(66, true, 1060)}}
			if tc.verb == 8 {
				cmd.Payload = append(cmd.Payload, wire.S(64, true, 70))
			}
			if err := validateCommand(cmd); (err == nil) != tc.ok {
				t.Fatalf("validateCommand error=%v, want valid=%v", err, tc.ok)
			}
			if !tc.ok {
				return
			}
			codec, err := profile.NewProfileCodec()
			if err != nil {
				t.Fatal(err)
			}
			encoded, err := codec.Encode([]wire.Record{commandRecord(cmd, 1, 1, 5000)})
			if err != nil {
				t.Fatal(err)
			}
			records, err := codec.Decode(encoded)
			if err != nil || len(records) != 1 || profile.S(records[0], 14) != tc.list {
				t.Fatalf("sale-list round trip error=%v, want list=%d", err, tc.list)
			}
		})
	}
}
