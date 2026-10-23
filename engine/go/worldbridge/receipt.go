// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// receipt does not consume live events or later commitments. The caller
// supplies only Execute's returned commitment and external-write result.
func (t translator) receipt(p prepared, c world.Commit, executionErr error, n, epoch uint64, observed time.Time) (bridge.Receipt, error) {
	r := bridge.Receipt{N: n, Key: p.command.Key, GatewayEpoch: epoch, Requested: p.command.Verb, Effective: p.command.Verb, Stage: wire.StageUnknown, Reason: wire.ReasonTimeout, Delivery: wire.DeliveryBackend, ObservedAt: observed}
	if n == 0 || epoch == 0 || p.command.Key == 0 {
		return r, errSource
	}
	setReason := func(reason world.Reason) {
		r.Stage = wire.StageRefused
		r.Reason = wire.ReasonWorld
		r.Delivery = wire.DeliveryNone
		var code uint64
		switch reason {
		case world.ReasonUnavailable:
			code = 18
		case world.ReasonInvalid:
			r.Reason = wire.ReasonInvalid
			code = 6
		case world.ReasonState, world.ReasonStale: // The source does not prove a narrower bridge reason.
		default:
			r.Stage = wire.StageUnknown
			r.Reason = wire.ReasonTimeout
			r.Delivery = wire.DeliveryBackend
		}
		if code != 0 {
			r.Namespace = profile.Namespace
			r.BridgeReason = &code
		}
	}
	owned := c.Intent.ID.N != 0 && c.Intent.ID.Owner == p.request.Owner && c.Outcome.Intent == c.Intent.ID && c.Intent.Verb == p.request.Verb && c.Intent.Deadline == p.request.Deadline
	if executionErr != nil {
		r.Reason = wire.ReasonBackendError
		code := uint64(20)
		r.Namespace = profile.Namespace
		r.BridgeReason = &code
		if len(c.Groups) != 0 {
			r.Delivery = wire.DeliveryPartial
		}
	} else if p.refusal != "" && c.Intent.ID == (world.IntentID{}) && len(c.Groups) == 0 {
		setReason(p.refusal)
	} else if owned && (c.Outcome.Stage == world.StageRefused || c.Outcome.Reason == "") {
		switch c.Outcome.Stage {
		case world.StageRefused:
			if len(c.Groups) == 0 {
				setReason(c.Outcome.Reason)
			}
		case world.StageExpired:
			if len(c.Groups) == 0 {
				r.Stage = wire.StageRefused
				r.Reason = wire.ReasonQueueExpired
				r.Delivery = wire.DeliveryNone
			}
		case world.StageCanceled, world.StageYielded:
			r.Reason = wire.ReasonInterrupted
			r.Delivery = wire.DeliveryPartial
		case world.StageUnknown:
			if p.command.Verb == 1 || p.command.Verb == 3 || p.command.Verb == 12 {
				r.Stage = wire.StageSent
				r.Reason = wire.ReasonNone
			}
		case world.StageDone:
			if effects, ok := t.effects(p, c); ok {
				r.Stage = wire.StageDone
				r.Reason = wire.ReasonNone
				r.Effects = effects
				r.EffectsPresent = true
			}
		}
	}
	rs, err := encode([]wire.Record{receiptRecord(r)})
	if err != nil {
		return bridge.Receipt{}, err
	}
	if rs[0].EncodedBytes > maxReceiptBytes || len(r.Effects) > maxEffects {
		r.Stage = wire.StageUnknown
		r.Reason = wire.ReasonBackendError
		r.Delivery = wire.DeliveryPartial
		r.Effects = nil
		r.EffectsPresent = false
		code := uint64(20)
		r.Namespace = profile.Namespace
		r.BridgeReason = &code
	}
	return r, nil
}

func receiptRecord(r bridge.Receipt) wire.Record {
	fs := []wire.Field{wire.U(1, true, r.N), wire.U(2, true, uint64(r.Stage)), wire.U(3, true, uint64(r.Reason)), wire.U(5, true, r.Requested), wire.U(6, true, r.Effective), wire.U(7, true, uint64(r.Delivery))}
	if r.BridgeReason != nil {
		fs = append(fs, wire.Data(64, true, r.Namespace), wire.U(65, true, *r.BridgeReason))
	}
	if r.EffectsPresent {
		fs = append(fs, wire.Items(66, true, r.Effects))
	}
	return wire.Record{Type: wire.Result, Fields: fs}
}

func targetEffect(id int32) wire.Value {
	return wire.Value{Fields: []wire.Field{wire.U(1, true, 5), wire.S(8, true, int64(id))}}
}
func effectValue(kind uint64, fields ...wire.Field) wire.Value {
	return wire.Value{Fields: append([]wire.Field{wire.U(1, true, kind)}, fields...)}
}

func (t translator) effects(p prepared, c world.Commit) ([]wire.Value, bool) {
	if p.command.Verb == 1 || p.command.Verb == 3 || p.command.Verb == 9 || p.command.Verb == 12 {
		return nil, false
	}
	if len(c.Groups) > maxRows {
		return nil, false
	}
	result := []wire.Value{}
	targets := 0
	items := 0
	speech := 0
	learning := 0
	for _, g := range c.Groups {
		if len(g.Facts) > maxRows || len(g.Effects) > maxEffects {
			return nil, false
		}
		if g.Cause != (world.Cause{Intent: c.Intent.ID}) {
			return nil, false
		}
		for _, f := range g.Facts {
			if f.Property != world.PropertyTarget {
				continue
			}
			before, beforeOK := f.Before.Ref()
			after, afterOK := f.After.Ref()
			if !beforeOK || !afterOK || before != (world.Ref{}) && !validRef(before) || f.Subject != p.request.Owner || after != p.request.Target || before == after || targets != 0 {
				return nil, false
			}
			if p.command.Verb != 2 && p.command.Verb != 10 && p.command.Verb != 4 {
				return nil, false
			}
			targets++
			result = append(result, targetEffect(after.ID))
		}
		// Learning must be one complete atomic SP+skill group. Separate groups
		// cannot be assembled into a claimed atomic learning transition.
		if p.command.Verb == 13 && len(g.Effects) > 0 {
			es, ok := learnEffects(p, g)
			if !ok || learning != 0 || len(result)+len(es) > maxEffects {
				return nil, false
			}
			learning++
			result = append(result, es...)
			continue
		}
		for _, e := range g.Effects {
			switch v := e.(type) {
			case world.SpeechCompleted:
				if p.command.Verb != 5 && p.command.Verb != 6 || v.Speaker != p.request.Target || v.Listener != p.request.Owner {
					return nil, false
				}
				if _, err := parseHTML(v.Text); err != nil {
					return nil, false
				}
				speech++
			case world.ResourceTransferred:
				if p.command.Verb != 4 || v.Resource.Kind != world.ResourceItem || v.From.Entity != p.request.Target || v.From.Place != world.PlaceGround || v.To.Entity != p.request.Owner || v.To.Place != world.PlaceInventory || !validRef(v.To.Item) {
					return nil, false
				}
				amount, ok := v.Amount.Integer()
				before, bok := v.ToBalance.Before.Integer()
				after, aok := v.ToBalance.After.Integer()
				fromBefore, fbok := v.FromBalance.Before.Integer()
				fromAfter, faok := v.FromBalance.After.Integer()
				if !ok || !bok || !aok || !fbok || !faok || amount <= 0 || before < 0 || after < before || after-before != amount || fromBefore < fromAfter || fromAfter < 0 || fromBefore-fromAfter != amount || t.catalog == nil {
					return nil, false
				}
				item, ok := t.catalog.Item(v.Resource.TemplateID)
				if !ok || item.ID != v.Resource.TemplateID || item.ConsumeType == "asset" {
					return nil, false
				}
				result = append(result, effectValue(1, wire.S(2, true, int64(item.ID)), wire.S(3, true, int64(v.To.Item.ID)), wire.S(4, true, amount)))
				items++
			case world.ExistenceChanged:
				if p.command.Verb != 4 || v.Entity != p.request.Target || !v.Before || v.After {
					return nil, false
				}
			default:
				return nil, false
			}
		}
		if len(result) > maxEffects {
			return nil, false
		}
	}
	switch p.command.Verb {
	case 2, 10:
		return result, targets == 1 && len(result) == 1
	case 4:
		return result, items > 0 && targets <= 1
	case 5, 6:
		return []wire.Value{}, speech == 1 && len(result) == 0
	case 13:
		return result, learning == 1
	default:
		return nil, false
	}
}

func learnEffects(p prepared, g world.EffectGroup) ([]wire.Value, bool) {
	if len(g.Effects) != 2 {
		return nil, false
	}
	var transfer world.ResourceTransferred
	var skill world.CapabilityChanged
	haveSP, haveSkill := false, false
	for _, e := range g.Effects {
		switch v := e.(type) {
		case world.ResourceTransferred:
			if haveSP {
				return nil, false
			}
			transfer = v
			haveSP = true
		case world.CapabilityChanged:
			if haveSkill {
				return nil, false
			}
			skill = v
			haveSkill = true
		default:
			return nil, false
		}
	}
	if !haveSP || !haveSkill || skill.Entity != p.request.Owner || skill.Capability != world.CapabilitySkill || skill.ID != p.request.Skill.ID || skill.After != p.request.Skill.Level || skill.Before < 0 || skill.Before >= skill.After {
		return nil, false
	}
	if transfer.Resource != (world.Resource{Kind: world.ResourceSP}) || transfer.From != (world.ResourceLocation{Entity: p.request.Owner, Place: world.PlaceCharacter}) || transfer.To != (world.ResourceLocation{Place: world.PlaceLearning}) {
		return nil, false
	}
	amount, ok := transfer.Amount.Integer()
	before, bok := transfer.FromBalance.Before.Integer()
	after, aok := transfer.FromBalance.After.Integer()
	if !ok || !bok || !aok || amount != p.cost || amount < 0 || before < after || after < 0 || before-after != amount {
		return nil, false
	}
	es := []wire.Value{}
	if amount != 0 {
		es = append(es, effectValue(4, wire.S(4, true, -amount), wire.S(6, true, before), wire.S(7, true, after)))
	}
	es = append(es, effectValue(3, wire.S(5, true, int64(skill.ID)), wire.S(6, true, int64(skill.Before)), wire.S(7, true, int64(skill.After))))
	return es, true
}
