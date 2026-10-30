// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import "testing"

func TestConnectionsFollowAdmissionOrder(t *testing.T) {
	s := &game{gate: &Gateway{}, conns: make(map[uint64]*connection)}
	for i := uint64(16); i > 0; i-- {
		s.conns[i] = &connection{id: i}
	}
	for range 4 {
		for i, c := range s.connections() {
			if c.id != uint64(i+1) {
				t.Fatalf("peer %d: got ID %d, want %d", i, c.id, i+1)
			}
		}
	}
}
