// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import "testing"

func BenchmarkCursorCheckpoint128(b *testing.B) {
	cfg := testConfig()
	cfg.Objects = 128
	c, err := NewCursor(cfg)
	if err != nil {
		b.Fatal(err)
	}
	view := View{Incarnations: map[int32]uint64{}, Ready: true}
	for id := int32(1); id <= 128; id++ {
		view.Incarnations[id] = 1
		view.Events = append(view.Events, Input{Event: Event{Kind: Appeared, ID: id, Role: "creature", Name: "Observer"}})
	}
	b.ReportAllocs()
	for b.Loop() {
		if _, err := c.Capture(view, 0); err != nil {
			b.Fatal(err)
		}
	}
}
