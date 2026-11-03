// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestFastFocusThenEngageKeepsGatewayAlive(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		// Local focus has nothing to interrupt: Engage waits for no selection.
		r.p.Send(r.intent(1, 10, r.ref(target.ID, 10)), r.intent(2, 11, r.ref(target.ID, 10)))
		r.p.Result(1, wire.StageSent)
		r.expect(2, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.p.Send(r.intent(3, 11, r.ref(target.ID, 10)))
		r.expect(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		if cmds := r.port.commands(); len(cmds) != 2 || cmds[0].Verb != 5 || cmds[1].Verb != 5 {
			t.Fatalf("each Engage must be one talk: %+v", cmds)
		}
	})
}
