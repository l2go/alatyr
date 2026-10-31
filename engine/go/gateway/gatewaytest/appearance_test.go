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
		r.emit(meaning.Event{Kind: meaning.Vitals, ID: 1, Appearance: &meaning.Appearance{Race: wire.RaceHuman, Sex: wire.SexMale, Branch: wire.BranchUnknown}})
		observer := gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeObserve, false)
		r.peers = append(r.peers, observer)
		r.sync()
		for _, peer := range []*gatewaytest.Peer{r.p, observer} {
			seen := false
			for _, record := range peer.Records {
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
			}
			if !seen {
				r.t.Fatal("live/checkpoint appearance missing")
			}
		}
	}, false)
}
