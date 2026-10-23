// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"math"
	"time"
)

// Reason identifies a refusal before execution. It is not an error string.
type Reason string

const (
	// ReasonUnavailable means the world does not provide the requested operation.
	ReasonUnavailable Reason = "unavailable"
	// ReasonStale means an incarnation, affordance or fence no longer matches.
	ReasonStale Reason = "stale"
	// ReasonState means the current state does not admit the operation.
	ReasonState Reason = "state"
	// ReasonInvalid means the request parameters are invalid.
	ReasonInvalid Reason = "invalid"
)

// Request describes one bounded world operation. Owner and Target are captured
// incarnations. Deadline is absolute monotonic world time; zero has no limit.
// Move uses Position; Cast uses the exact Skill and Target; Choose and Learn
// use an issued AffordanceID. Appear uses Fence. Unused fields grant no authority.
// Intent identity is assigned by the world, not supplied by the caller.
type Request struct {
	Owner        Ref
	Verb         Verb
	Deadline     Time
	Target       Ref
	Position     Point
	Skill        Skill
	AffordanceID uint64
	Fence        Fence
	Ctrl, Shift  bool
	Revive       bool
}

// Execute fixes a command outcome and its source-authored groups in Run and
// returns that same detached Commit. Refusals are outcomes, not Go errors.
// Errors describe admission, world failure or external persistence. Once
// admitted, the bounded operation completes despite caller cancellation.
// A queued or started action remains Unknown; later rules never rewrite it.
// Learning's external write cannot change the in-memory Done commitment.
func (w *World) Execute(ctx context.Context, req Request) (Commit, error) {
	var commit Commit
	var learnt *Skill
	err := w.submit(ctx, func(s *state) {
		commit = s.commandUntil(req.Owner, req.Verb, req.Deadline, func() {
			p, ok := s.players[req.Owner.ID]
			if !ok || p.ref() != req.Owner {
				s.resolve(StageRefused, ReasonStale)
				return
			}
			learnt = w.execute(s, p, req)
		})
	})
	if err != nil {
		return cloneCommit(commit), err
	}
	if learnt != nil {
		err = w.saveSkill(ctx, req.Owner.ID, *learnt, commit.Intent.ID)
	}
	return cloneCommit(commit), err
}

func (w *World) execute(s *state, p *player, req Request) *Skill {
	if req.Deadline < 0 {
		s.resolve(StageRefused, ReasonInvalid)
		return nil
	}
	switch req.Verb {
	case VerbMove:
		if !w.validMove(p, req.Position) {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		to := req.Position
		if w.ground != nil {
			to.X, to.Y, to.Z = w.ground.Walk(p.pos.X, p.pos.Y, p.pos.Z, to.X, to.Y, to.Z)
		}
		w.setIntention(s, p, order{intent: intentMoveTo, pos: to})
	case VerbSelect:
		if p.dead || p.teleporting || !w.selectable(s, p, req.Target) {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		old := p.target
		w.setTarget(s, p, req.Target.ID)
		if p.target != old {
			s.finish(StageDone)
		}
	case VerbAttack:
		t, ok := s.actor(req.Target.ID)
		if !ok || t.base().ref() != req.Target {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		n, ok := s.npcs[req.Target.ID]
		if !ok || !n.mob() || n.dead || p.dead || p.teleporting || !p.known[n.id] || p.lastTarget == n.id && time.Since(p.lastTargetAt) < retargetBlocking {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		w.setTarget(s, p, n.id)
		w.setIntention(s, p, order{intent: intentAttack, object: n.id, target: n.ref()})
	case VerbPickup:
		g, ok := s.ground[req.Target.ID]
		if !ok || g.ref() != req.Target || p.dead || p.teleporting || !p.known[g.id] {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		w.setIntention(s, p, order{intent: intentPickUp, object: g.id, target: g.ref()})
	case VerbTalk:
		t, ok := s.actor(req.Target.ID)
		if !ok || t.base().ref() != req.Target {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		n, ok := s.npcs[req.Target.ID]
		if !ok || !n.npcInstance() || p.dead || p.teleporting || !p.known[n.id] || p.sitting {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		p.lastFolk = n.id
		if w.talk(s, p, n) {
			s.finish(StageDone)
		}
	case VerbCast:
		t, ok := s.actor(req.Target.ID)
		if !ok || t.base().ref() != req.Target {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		sk, ok := w.cat.Skill(req.Skill.ID, req.Skill.Level)
		_, targetOK := w.skillTarget(s, p, sk, req.Target.ID)
		if !ok || p.skillLevel(sk.ID) != sk.Level || !w.validCast(p, sk) || !targetOK {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		if p.reuse[sk.ID].until.After(time.Now()) {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		w.setIntention(s, p, order{intent: intentCast, target: req.Target, cast: &castRequest{skill: sk, target: req.Target, ctrl: req.Ctrl, shift: req.Shift}})
	case VerbChoose, VerbLearn:
		a, ok := p.affordance(req.AffordanceID)
		if !ok || a.Verb != req.Verb || a.Target != req.Target || a.Expires <= s.now() || s.ref(a.Target.ID) != a.Target {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		n := s.npcs[a.Target.ID]
		if n == nil || !n.trainer() || !w.canInteract(p, n) || p.teleporting || !w.cat.CanTeach(n.tmpl.ID, p.char.ClassID) {
			s.resolve(StageRefused, ReasonState)
			return nil
		}
		if req.Verb == VerbChoose {
			w.showSkillList(s, p, n)
			return nil
		}
		if !affordanceSkill(a, req.Skill) {
			s.resolve(StageRefused, ReasonInvalid)
			return nil
		}
		cost, available := w.cat.SkillCost(p.char.ClassID, req.Skill.ID, req.Skill.Level, p.level())
		if !available || len(a.Cost) != 1 || a.Cost[0] != (Cost{Resource: Resource{Kind: ResourceSP}, Quantity: int64(cost)}) {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		// learnFrom records every validation failure as a typed refusal. Its error
		// is for the legacy error-only API, not another command failure channel.
		k, err := w.learnFrom(s, p, n, req.Skill.ID, req.Skill.Level)
		if err != nil {
			return nil
		}
		return k
	case VerbTeleport:
		if err := w.teleport(s, p, req.Position, req.Revive); err != nil {
			s.resolve(StageRefused, ReasonState)
		}
	case VerbAppear:
		if !p.teleporting || req.Fence == 0 || req.Fence != p.fence {
			s.resolve(StageRefused, ReasonStale)
			return nil
		}
		w.appear(s, p)
		s.finish(StageDone)
	default:
		s.resolve(StageRefused, ReasonUnavailable)
	}
	return nil
}

func (w *World) validMove(p *player, to Point) bool {
	dx, dy := float64(to.X)-float64(p.pos.X), float64(to.Y)-float64(p.pos.Y)
	return math.Hypot(dx, dy) <= 9900 && !p.dead && !p.teleporting && !p.sitting && p.moveSpeed() > 0
}

func (w *World) selectable(s *state, p *player, target Ref) bool {
	if target == (Ref{}) {
		return true
	}
	a, ok := s.actor(target.ID)
	if !ok || a.base().ref() != target || target != p.ref() && !p.known[target.ID] {
		return false
	}
	if q, ok := a.(*player); ok && q.teleporting {
		return false
	}
	return math.Abs(float64(a.base().pos.Z)-float64(p.pos.Z)) <= 500
}
