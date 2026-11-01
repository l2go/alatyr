// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestG13NPCForm(t *testing.T) {
	runBoth(t, func(r *rig) {
		liveStart := len(r.p.Records)
		r.emit(meaning.Event{Kind: meaning.Appeared, ID: 8, Role: "creature", Form: "field_wolf"})
		r.emit(meaning.Event{Kind: meaning.Appeared, ID: 9, Role: "tutor"})
		observer := gatewaytest.Join(r.t, r.g, r.c, "two", wire.ModeObserve, false)
		r.peers = append(r.peers, observer)
		r.sync()
		for _, tc := range []struct {
			peer       *gatewaytest.Peer
			start      int
			checkpoint bool
		}{{r.p, liveStart, false}, {observer, 0, true}} {
			seenWolf, seenPlain := false, false
			inCheckpoint := false
			for i, record := range tc.peer.Records {
				if record.Type == wire.Checkpoint {
					inCheckpoint = gatewaytest.U(record, 1) == uint64(wire.CheckpointBegin)
				}
				if record.Type != wire.Fact || i < tc.start {
					continue
				}
				d, err := meaning.EventValue(record)
				if err != nil {
					r.t.Fatal(err)
				}
				_, present := wire.Find(record.Fields, wire.FormField)
				switch {
				case d.Event.Kind == meaning.Appeared && d.Event.ID == 8:
					if d.Event.Form != "field_wolf" || !present {
						r.t.Fatalf("form = %q present %v", d.Event.Form, present)
					}
					if inCheckpoint != tc.checkpoint {
						r.t.Fatalf("checkpoint = %v, want %v", inCheckpoint, tc.checkpoint)
					}
					seenWolf = true
				case d.Event.Kind == meaning.Appeared && d.Event.ID == 9:
					if d.Event.Form != "" || present {
						r.t.Fatal("unset form became a value")
					}
					seenPlain = true
				default:
					if d.Event.Form != "" || (present && d.Event.Kind != meaning.Appeared) {
						r.t.Fatalf("form on %s/%d", d.Event.Kind, d.Event.ID)
					}
				}
			}
			if !seenWolf || !seenPlain {
				r.t.Fatalf("wolf %v plain %v checkpoint %v", seenWolf, seenPlain, tc.checkpoint)
			}
		}
	}, false)
}
