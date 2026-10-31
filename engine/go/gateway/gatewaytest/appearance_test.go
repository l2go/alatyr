// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestG12PlayerAppearance(t *testing.T) {
	runBoth(t, func(r *rig) {
		want := meaning.Appearance{Race: wire.RaceKruvan, Sex: wire.SexFemale, Branch: wire.BranchMystic}
		r.emit(meaning.Event{Kind: meaning.Appeared, ID: 3, Role: meaning.RolePlayer, Appearance: &want})
		self := meaning.Appearance{Race: wire.RaceHuman, Sex: wire.SexMale, Branch: wire.BranchUnknown}
		liveStart := len(r.p.Records)
		r.emit(meaning.Event{Kind: meaning.Vitals, ID: 1, Appearance: &self})
		observer := gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeObserve, false)
		r.peers = append(r.peers, observer)
		r.sync()
		for _, tc := range []struct {
			peer       *gatewaytest.Peer
			selfStart  int
			checkpoint bool
		}{{r.p, liveStart, false}, {observer, 0, true}} {
			seen, seenSelf, inCheckpoint := false, false, false
			for i, record := range tc.peer.Records {
				if record.Type == wire.Checkpoint {
					inCheckpoint = gatewaytest.U(record, 1) == uint64(wire.CheckpointBegin)
				}
				if record.Type != wire.Fact {
					continue
				}
				d, err := meaning.EventValue(record)
				if err != nil {
					r.t.Fatal(err)
				}
				if d.Event.Kind == meaning.Appeared && d.Event.ID == 3 {
					if d.Event.Appearance == nil || *d.Event.Appearance != want {
						r.t.Fatalf("appearance = %+v, want %+v", d.Event.Appearance, want)
					}
					seen = true
				}
				if i >= tc.selfStart && d.Event.Kind == meaning.Vitals && d.Event.ID == 1 {
					if d.Event.Appearance == nil || *d.Event.Appearance != self {
						r.t.Fatalf("self Vitals appearance = %+v, want %+v", d.Event.Appearance, self)
					}
					if inCheckpoint != tc.checkpoint {
						r.t.Fatalf("self Vitals checkpoint = %v, want %v", inCheckpoint, tc.checkpoint)
					}
					seenSelf = true
				}
			}
			if !seen {
				r.t.Fatal("live/checkpoint appearance missing")
			}
			if !seenSelf {
				r.t.Fatalf("self Vitals appearance missing (checkpoint=%v)", tc.checkpoint)
			}
		}
	}, false)
}
