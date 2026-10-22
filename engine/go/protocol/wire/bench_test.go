// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func benchRecords() []wire.Record {
	return []wire.Record{
		{Type: wire.Fact, Fields: []wire.Field{
			wire.U(1, true, 1), wire.U(2, true, 42), wire.S(3, true, 1200), wire.U(4, true, 3),
			wire.Data(5, true, "objects"), wire.U(6, true, 1),
		}},
		{Type: wire.Result, Fields: []wire.Field{
			wire.U(1, true, 9), wire.U(2, true, uint64(wire.StageDone)), wire.U(3, true, uint64(wire.ReasonNone)),
			wire.U(5, true, 4), wire.U(6, true, 4), wire.U(7, true, uint64(wire.DeliveryBackend)),
		}},
	}
}

func BenchmarkEncode(b *testing.B) {
	c := exampleCodec()
	records := benchRecords()
	b.ReportAllocs()
	for b.Loop() {
		if _, err := c.Encode(records); err != nil {
			b.Fatal(err)
		}
	}
}

func BenchmarkDecode(b *testing.B) {
	c := exampleCodec()
	frame, err := c.Encode(benchRecords())
	if err != nil {
		b.Fatal(err)
	}
	b.SetBytes(int64(len(frame)))
	b.ReportAllocs()
	for b.Loop() {
		if _, err := c.Decode(frame); err != nil {
			b.Fatal(err)
		}
	}
}
