// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"fmt"
	"io"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type refusingWorld struct {
	World
	err    error
	writes int
}

func (w *refusingWorld) Write(context.Context, Step) error {
	w.writes++
	return w.err
}

func TestSynchronousWriteRefusalRequiresValidSourceProof(t *testing.T) {
	for _, tc := range []struct {
		name   string
		err    error
		closed bool
		stage  wire.Stage
		reason wire.Reason
	}{
		{name: "exact", err: &WriteRefusal{Reason: wire.ReasonWorld}, stage: wire.StageRefused, reason: wire.ReasonWorld},
		{name: "wrapped", err: fmt.Errorf("write: %w", &WriteRefusal{Reason: wire.ReasonWorld}), stage: wire.StageRefused, reason: wire.ReasonWorld},
		{name: "before-contact", err: &WriteRefusal{Reason: wire.ReasonWorld, ObservedMS: -1}, closed: true},
		{name: "future", err: &WriteRefusal{Reason: wire.ReasonWorld, ObservedMS: 1}, closed: true},
		{name: "no-reason", err: &WriteRefusal{}, closed: true},
		{name: "ambiguous", err: io.ErrUnexpectedEOF, stage: wire.StageUnknown, reason: wire.ReasonBackendError},
		{name: "nil-proof", err: (*WriteRefusal)(nil), stage: wire.StageUnknown, reason: wire.ReasonBackendError},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r, _, _ := budgetRig(t)
			w := &refusingWorld{World: r.world, err: tc.err}
			r.game.world, r.game.pacer = w, nil
			var results []Summary
			r.game.gate.cfg.Diagnostic = func(s Summary) {
				if s.Record == wire.Result && s.Connection == 0 {
					results = append(results, s)
				}
			}
			r.game.service(context.Background())
			if r.game.closed != tc.closed || w.writes != 1 {
				t.Fatalf("closed=%v writes=%d, want closed=%v and one write", r.game.closed, w.writes, tc.closed)
			}
			if tc.closed {
				r.conn.mu.Lock()
				last := r.conn.queue[len(r.conn.queue)-1].raw
				r.conn.mu.Unlock()
				records, err := r.game.gate.cfg.Codec.Decode(last)
				if err != nil || len(records) != 1 || records[0].Type != wire.Bye || uintField(records[0], 1) != uint64(wire.CloseInternal) {
					t.Fatalf("invalid proof close=%+v/%v, want internal bye", records, err)
				}
				return
			}
			entry, _ := r.game.attempts.Lookup(2)
			if !entry.Terminal || entry.Result == nil || entry.Result.Stage != tc.stage || entry.Result.Reason != tc.reason || len(r.game.actions) != 0 {
				t.Fatalf("entry=%+v, want terminal %v/%v", entry, tc.stage, tc.reason)
			}
			r.game.service(context.Background())
			if len(results) != 1 || results[0].Stage != uint64(tc.stage) || w.writes != 1 {
				t.Fatalf("results=%+v writes=%d, want one terminal result and one write", results, w.writes)
			}
		})
	}
}
