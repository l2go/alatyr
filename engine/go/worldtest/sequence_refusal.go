// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func checkRefusalEvidence(result wire.Record, handoffs []backend.Handoff, trace []bridge.Update, writes []sequenceWrite, evidenceMS int64) error {
	n := gatewaytest.U(result, 1)
	fail := func() error { return fmt.Errorf("refused attempt %d has no exclusive live proof of no effects", n) }
	if len(handoffs) != 1 || handoffs[0].Role != gateway.StepEffect || handoffs[0].RawN == 0 || gatewaytest.U(result, 7) != uint64(wire.DeliveryNone) {
		return fail()
	}
	h := handoffs[0]
	var w *sequenceWrite
	for i := range writes {
		if writes[i].command.Key == h.Key {
			if w != nil {
				return fail()
			}
			w = &writes[i]
		}
	}
	if w == nil || w.epoch == 0 || w.owner == (world.Ref{}) || !w.audited || w.failed {
		return fail()
	}
	// Correlate the actual commands committed during this port write. Rule
	// commitments and internal Appear are independent of the participant verb.
	verb := committedVerb(w.command.Verb)
	if verb == "" {
		return fail()
	}
	matched := 0
	for _, c := range w.commits {
		if c.Intent.ID.N == 0 || c.Intent.Verb != verb {
			continue
		}
		matched++
		if matched > 1 || c.Intent.ID.Owner != w.owner || c.Outcome.Intent != c.Intent.ID || len(c.Groups) != 0 || c.Outcome.Stage != world.StageRefused && c.Outcome.Stage != world.StageExpired {
			return fail()
		}
	}
	deadline := w.at.Add(time.Duration(evidenceMS) * time.Millisecond)
	for _, u := range trace {
		if u.Kind != bridge.LiveBatch || u.Batch.Snapshot {
			continue
		}
		for _, r := range u.Batch.Receipts {
			if r.Key == h.Key && r.N == h.RawN && r.GatewayEpoch == w.epoch && !r.Replay && r.Stage == wire.StageRefused && r.Delivery == wire.DeliveryNone && len(r.Effects) == 0 && r.Reason != wire.ReasonNone && uint64(r.Reason) == gatewaytest.U(result, 3) && r.Requested == w.command.Verb && r.Effective == w.command.Verb && !r.ObservedAt.Before(w.at) && r.ObservedAt.Before(deadline) {
				return nil
			}
		}
	}
	return fail()
}

func committedVerb(verb uint64) world.Verb {
	switch verb {
	case verbWalk:
		return world.VerbMove
	case verbAim, verbFocus:
		return world.VerbSelect
	case verbStrike:
		return world.VerbAttack
	case verbTake:
		return world.VerbPickup
	case verbTalk:
		return world.VerbTalk
	case verbChoose:
		return world.VerbChoose
	case verbBuy:
		return world.VerbBuy
	case verbSell:
		return world.VerbSell
	case verbRise:
		return world.VerbRestart
	case verbCast:
		return world.VerbCast
	case verbLearn:
		return world.VerbLearn
	case verbUseItem:
		return world.VerbUseItem
	default:
		return ""
	}
}
