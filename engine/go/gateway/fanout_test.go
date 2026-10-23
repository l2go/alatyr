// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import "testing"

func BenchmarkConnections(b *testing.B) {
	s := &game{gate: &Gateway{}, conns: make(map[uint64]*connection, 4)}
	for i := range uint64(4) {
		s.conns[i] = &connection{id: i}
	}
	b.ReportAllocs()
	for b.Loop() {
		if len(s.connections()) != 4 {
			b.Fatal("connection snapshot lost a peer")
		}
	}
}

func TestConnectionsAllocationBudget(t *testing.T) {
	s := &game{gate: &Gateway{}, conns: make(map[uint64]*connection, 4)}
	for i := range uint64(4) {
		s.conns[i] = &connection{id: i}
	}
	allocs := testing.AllocsPerRun(10, func() { s.connections() })
	if allocs > 1 {
		t.Fatalf("connections allocations = %g, want at most 1", allocs)
	}
}
