// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"strings"
	"testing"
)

// The envelope vectors travel with this package without a profile dependency.
func TestFrozenEnvelopeVectors(t *testing.T) {
	b, e := os.ReadFile("../testdata/vectors.json")
	if e != nil {
		t.Fatal(e)
	}
	var data struct{ Vectors []struct{ ID, Hex string } }
	if e = json.Unmarshal(b, &data); e != nil {
		t.Fatal(e)
	}
	if len(data.Vectors) != 28 {
		t.Fatal("missing historical vectors")
	}
	l := testLimits()
	c, e := New(l, Envelopes(Schema{1: Required(UInt)}, l))
	if e != nil {
		t.Fatal(e)
	}
	for _, v := range data.Vectors {
		t.Run(v.ID, func(t *testing.T) {
			b, e := hex.DecodeString(strings.ReplaceAll(v.Hex, " ", ""))
			if e != nil {
				t.Fatal(e)
			}
			var r []Record
			if v.ID == "V17" || v.ID == "V21" {
				r, e = c.Read(bytes.NewReader(b))
			} else {
				r, e = c.Decode(b)
			}
			var want error
			switch v.ID {
			case "V11":
				want = ErrUnsupported
			case "V12", "V13", "V18", "V19", "V20", "V21":
				want = ErrFrame
			}
			if !errors.Is(e, want) {
				t.Fatalf("%s: %v", v.ID, e)
			}
			if e != nil && r != nil {
				t.Fatal("partial frame")
			}
		})
	}
}
