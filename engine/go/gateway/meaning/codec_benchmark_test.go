// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func knownFrame(t testing.TB, c *Codec) []byte {
	t.Helper()
	r, err := EventRecord(Event{Kind: Known, Complete: true, Skills: []Skill{{ID: 3, Level: 2}}}, FactContext{Seq: 1, Epoch: 1})
	if err != nil {
		t.Fatal(err)
	}
	b, err := c.Encode([]wire.Record{r})
	if err != nil {
		t.Fatal(err)
	}
	return b
}

func TestCodecDecodeAllocationBudget(t *testing.T) {
	c := codec(t)
	b := knownFrame(t, c)
	allocs := testing.AllocsPerRun(20, func() {
		records, err := c.Decode(b)
		if err != nil || len(records) != 1 {
			t.Fatal(records, err)
		}
	})
	// This fixed single-fact budget catches accidental unbounded table copies;
	// it is not a latency guarantee or a budget for arbitrary participant frames.
	if allocs > 32 {
		t.Fatalf("single-fact decode allocations = %.0f, want <= 32", allocs)
	}
}

func BenchmarkCodecDecode(b *testing.B) {
	c := codec(b)
	frame := knownFrame(b, c)
	b.ReportAllocs()
	b.SetBytes(int64(len(frame)))
	for b.Loop() {
		if _, err := c.Decode(frame); err != nil {
			b.Fatal(err)
		}
	}
}
