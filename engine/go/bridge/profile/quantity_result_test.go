// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package profile

import (
	"bytes"
	"encoding/hex"
	"testing"
)

// The Java result producer asserts these same bytes after delivery of message
// 1036. This checks the language boundary without changing either codec.
func TestJavaQuantityRefusalResult(t *testing.T) {
	data, err := hex.DecodeString("0c2218012803380a5807680778008a0810782e6c326a667265652f627269646765980806")
	if err != nil {
		t.Fatal(err)
	}
	c := newCodec(t)
	records, err := c.Decode(data)
	if err != nil {
		t.Fatalf("decode Java quantity refusal: %v", err)
	}
	if len(records) != 1 {
		t.Fatalf("got %d records, want 1", len(records))
	}
	r := records[0]
	for field, want := range map[uint32]uint64{1: 1, 2: 3, 3: 10, 5: 7, 6: 7, 7: 0, 65: 6} {
		if got := U(r, field); got != want {
			t.Errorf("field %d = %d, want %d", field, got, want)
		}
	}
	if string(Data(r, 64)) != "x.l2jfree/bridge" || Has(r, 66) {
		t.Error("quantity refusal must carry its typed namespace and no effects")
	}
	encoded, err := c.Encode(records)
	if err != nil {
		t.Fatalf("encode quantity refusal: %v", err)
	}
	if !bytes.Equal(encoded, data) {
		t.Error("quantity refusal bytes changed on round trip")
	}
}
