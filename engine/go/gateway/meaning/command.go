// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// CommandContext carries captured reference lifetimes and proposal handles for an Intent.
type CommandContext struct {
	// N is the nonzero participant attempt number.
	N uint64
	// Control is the participant control epoch.
	Control uint64
	// Target is the captured target lifetime.
	Target Ref
	// Item is the captured collectible lifetime.
	Item Ref
	// Dialog is the saved dialogue handle.
	Dialog uint64
	// Option is the numbered saved choice.
	Option uint64
	// Offer is the saved row handle.
	Offer uint64
	// List is the captured backend list ID.
	List int32
	// Price is the captured unit price, never a replacement price.
	Price int64
	// SkillLevel is the known skill level for Cast.
	SkillLevel int32
	// QueueMS is an optional lifetime; nil and explicit zero remain distinct.
	QueueMS *uint64
}

// CommandRecord constructs an Intent with captured values, never resolves arbitrary action text.
// The codec must validate its structural bounds before planning.
func CommandRecord(cmd Command, ctx CommandContext) (wire.Record, error) {
	v, ok := VerbByCommand(cmd.Kind)
	if !ok {
		return wire.Record{}, wire.ErrUnsupported
	}
	if ctx.N == 0 {
		return wire.Record{}, wire.ErrFrame
	}
	f := []wire.Field{wire.U(1, true, ctx.N), wire.U(2, true, v.Code), wire.U(3, true, ctx.Control)}
	if ctx.QueueMS != nil {
		f = append(f, wire.U(4, true, *ctx.QueueMS))
	}
	switch cmd.Kind {
	case Walk:
		f = append(f, wire.At(11, true, cmd.X, cmd.Y, cmd.Z))
	case Aim, Focus, Strike, Talk, Engage, Cast:
		if ctx.Target.Gen == 0 || ctx.Target.ID != cmd.Target {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.Obj(10, true, refValue(ctx.Target).Fields))
		if cmd.Kind == Cast {
			f = append(f, wire.Obj(18, true, []wire.Field{wire.S(1, true, int64(cmd.Item)), wire.S(2, true, int64(ctx.SkillLevel))}))
		}
		if cmd.Kind == Strike || cmd.Kind == Engage || cmd.Kind == Cast {
			f = append(f, wire.U(19, true, boolean(cmd.Force)))
		}
	case Take:
		if ctx.Item.Gen == 0 || ctx.Item.ID != cmd.Target {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.Obj(20, true, refValue(ctx.Item).Fields))
	case Say:
		if ctx.Dialog == 0 || ctx.Option == 0 {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.U(12, true, ctx.Dialog), wire.U(13, true, ctx.Option))
	case Buy, Sell:
		if ctx.Offer == 0 {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.S(14, true, int64(ctx.List)), wire.U(15, true, ctx.Offer), wire.S(16, true, cmd.Count), wire.S(17, true, ctx.Price))
	case Learn:
		if ctx.Offer == 0 {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.S(14, true, int64(ctx.List)), wire.U(15, true, ctx.Offer))
	case UseItem:
		if cmd.Target <= 0 {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.S(21, true, int64(cmd.Target)))
	case Rise:
	}
	return wire.Record{Type: wire.Intent, Fields: f}, nil
}

// Environment supplies actor-owned observations and trusted classification for planning.
type Environment struct {
	// Observed supplies live reference lifetimes.
	Observed *Observations
	// Proposals supplies saved action handles.
	Proposals *Proposals
	// Known supplies actor-owned known skills.
	Known map[int32]Skill
	// Inventory supplies currently carried rows.
	Inventory map[int32]Good
	// Alive states that a live self is observed.
	Alive bool
	// Dead states that the observed self is dead.
	Dead bool
	// Self is the observed own actor ID.
	Self int32
	// Now is gateway-relative time in milliseconds.
	Now int64
	// Classify is a trusted callback; nil grants no target capability.
	Classify func(Observed) Capabilities
}

// Plan is a captured effect and backend command; no member grants causal authority.
type Plan struct {
	// Effect is the permission-resolved participant action.
	Effect Effect
	// Command is the captured backend request.
	Command Command
	// Target is the captured target lifetime.
	Target Ref
	// Item is the captured collectible lifetime.
	Item Ref
	// Dialog is the captured dialogue handle.
	Dialog uint64
	// Offer is the captured proposal handle.
	Offer uint64
	// SkillID is the captured skill ID.
	SkillID int32
	// SkillLevel is the captured known skill level.
	SkillLevel int32
}

func has(f []wire.Field, n uint32) bool { _, ok := wire.Find(f, n); return ok }

// PlanIntent consumes a structurally validated record and checks verb-specific
// payload fields before producing a command.
// It performs no IO and never substitutes a pickup for a skill action.
func PlanIntent(r wire.Record, env Environment) (Plan, wire.Reason) {
	if r.Type != wire.Intent {
		return Plan{}, wire.ReasonInvalid
	}
	if r.Unsupported {
		return Plan{}, wire.ReasonUnknownEssential
	}
	f := r.Fields
	verb, known := VerbByCode(getU(f, 2))
	if !known {
		return Plan{}, wire.ReasonNoAction
	}
	allowed := map[uint32]bool{}
	required := []uint32{}
	switch verb.Command {
	case Walk:
		required = []uint32{11}
	case Aim, Focus, Talk:
		required = []uint32{10}
	case Strike, Engage:
		required = []uint32{10}
		allowed[19] = true
	case Cast:
		required = []uint32{10, 18}
		allowed[19] = true
	case Take:
		required = []uint32{20}
	case Say:
		required = []uint32{12, 13}
	case Buy, Sell:
		required = []uint32{14, 15, 16, 17}
	case UseItem:
		required = []uint32{21}
	case Learn:
		required = []uint32{14, 15}
	}
	for _, n := range required {
		if !has(f, n) {
			return Plan{}, wire.ReasonInvalid
		}
		allowed[n] = true
	}
	for _, field := range f {
		if field.Number >= 10 && !allowed[field.Number] {
			return Plan{}, wire.ReasonInvalid
		}
	}
	plan := Plan{Target: getRef(f, 10), Item: getRef(f, 20), Dialog: getU(f, 12), Offer: getU(f, 15)}
	var target Observed
	capability := Capabilities{}
	if has(f, 10) || has(f, 20) {
		ref := plan.Target
		if has(f, 20) {
			ref = plan.Item
		}
		if env.Observed == nil || !env.Observed.Valid(ref) {
			return Plan{}, wire.ReasonStaleTarget
		}
		target, _ = env.Observed.Lookup(ref.ID)
		if env.Classify != nil {
			capability = env.Classify(target)
		}
	}
	effect, reason := Resolve(verb.Code, capability, getU(f, 19) == 1)
	if reason != wire.ReasonNone {
		return Plan{}, reason
	}
	plan.Effect = effect
	if verb.Command != Aim && verb.Command != Focus {
		if verb.Command == Rise {
			if !env.Dead {
				return Plan{}, wire.ReasonNotAlive
			}
		} else if !env.Alive {
			return Plan{}, wire.ReasonNotAlive
		}
		if (has(f, 10) || has(f, 20)) && target.Dead {
			return Plan{}, wire.ReasonStaleTarget
		}
	}
	cmd := Command{Kind: verb.Command, Target: plan.Target.ID, Force: getU(f, 19) == 1}
	switch verb.Command {
	case UseItem:
		cmd.Target = int32(getS(f, 21))
		if row, ok := env.Inventory[cmd.Target]; cmd.Target <= 0 || !ok || row.Count <= 0 {
			return Plan{}, wire.ReasonStaleTarget
		}
	case Walk:
		v, _ := wire.Find(f, 11)
		cmd.X, cmd.Y, cmd.Z = v.XYZ[0], v.XYZ[1], v.XYZ[2]
	case Take:
		cmd.Target = plan.Item.ID
		cmd.X, cmd.Y, cmd.Z = target.X, target.Y, target.Z
	case Talk:
		cmd.X, cmd.Y, cmd.Z = target.X, target.Y, target.Z
	case Engage:
		if effect.Effective == 5 {
			cmd.Kind = Talk
			cmd.X, cmd.Y, cmd.Z = target.X, target.Y, target.Z
		} else {
			cmd.Kind = Strike
		}
	case Say:
		if env.Proposals == nil {
			return Plan{}, wire.ReasonStaleOffer
		}
		var npc Ref
		cmd, npc, reason = env.Proposals.choose(plan.Dialog, getU(f, 13), env.Now)
		cmd.Target = npc.ID
		if reason == wire.ReasonNone && (env.Observed == nil || !env.Observed.Valid(npc)) {
			return Plan{}, wire.ReasonStaleOffer
		}
	case Buy, Sell:
		if env.Proposals == nil {
			return Plan{}, wire.ReasonStaleOffer
		}
		kind := Shop
		if verb.Command == Sell {
			kind = Offer
		}
		cmd, reason = env.Proposals.trade(kind, int32(getS(f, 14)), plan.Offer, getS(f, 16), getS(f, 17), env.Now)
	case Learn:
		if env.Proposals == nil {
			return Plan{}, wire.ReasonStaleOffer
		}
		cmd, reason = env.Proposals.learn(int32(getS(f, 14)), plan.Offer, env.Now)
	case Cast:
		use, _ := wire.Find(f, 18)
		id, level := int32(getS(use.Fields, 1)), getS(use.Fields, 2)
		skill, ok := env.Known[id]
		if id < 1 || level < 1 {
			return Plan{}, wire.ReasonInvalid
		}
		if !ok || int64(skill.Level) != level {
			return Plan{}, wire.ReasonSkillUnknown
		}
		if skill.Passive {
			return Plan{}, wire.ReasonSkillPassive
		}
		if skill.Disabled {
			return Plan{}, wire.ReasonSkillDisabled
		}
		cmd.Item = id
		plan.SkillID, plan.SkillLevel = id, int32(level)
	}
	if reason != wire.ReasonNone {
		return Plan{}, reason
	}
	plan.Command = cmd
	return plan, wire.ReasonNone
}
