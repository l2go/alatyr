// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import "testing"

// flushScene enqueues and delivers 32 small notifications per step. Setup and
// retained history are excluded. The inactive stream exercises the same sends
// with observation closed; it must not clone data or allocate publications.
func flushScene(active, observe bool) func() {
	w := New(nil, nil)
	p := &player{stream: newEventStream(), slow: !active}
	w.s.players[1] = p
	if observe {
		w.s.observe = func(Commit) {}
	}
	return func() {
		for range 32 {
			p.send(ActionIgnored{})
		}
		w.flush(w.s)
		for len(p.stream.events) > 0 {
			<-p.stream.events
		}
	}
}

func BenchmarkFlush(b *testing.B) {
	for _, tc := range []struct {
		name            string
		active, observe bool
	}{
		{"inactive", false, false},
		{"live", true, false},
		{"live with commits", true, true},
	} {
		b.Run(tc.name, func(b *testing.B) {
			step := flushScene(tc.active, tc.observe)
			b.ReportAllocs()
			for b.Loop() {
				step()
			}
		})
	}
}

func TestFlushAllocations(t *testing.T) {
	for _, tc := range []struct {
		name            string
		active, observe bool
		budget          float64
	}{
		{"inactive", false, false, 0},
		{"live", true, false, 40},
		{"live with commits", true, true, 40},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := testing.AllocsPerRun(10, flushScene(tc.active, tc.observe))
			t.Logf("32 notifications: %.0f allocations/flush", got)
			if got > tc.budget {
				t.Fatalf("allocations/flush: got %.0f, budget %.0f", got, tc.budget)
			}
		})
	}
}
