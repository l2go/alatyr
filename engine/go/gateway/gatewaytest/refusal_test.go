// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestG11ProvenRefusalPreservesClass(t *testing.T) {
	runBoth(t, func(r *rig) {
		r.p.Send(r.intent(1, 5, r.ref(2)))
		r.p.Result(1, wire.StageSent)
		log := r.log()
		r.reply(log[0], wire.StageRefused, wire.ReasonWorld, meaning.Event{})
		r.expect(1, 5, 5, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone, 0)
		r.p.Send(r.intent(2, 5, r.ref(2)))
		r.p.Result(2, wire.StageSent)
		log = r.log()
		if len(log) != 2 {
			r.t.Fatal("refusal retained the class slot")
		}
		r.reply(log[1], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Spoke, ID: 2, Text: "A fresh answer."})
		r.expect(2, 5, 5, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	}, false)
}
