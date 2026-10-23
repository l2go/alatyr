// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"math"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/stat"
)

type castRequest struct {
	skill       catalog.Skill
	ctrl, shift bool
}
type reuseStamp struct {
	until time.Time
	delay time.Duration
}
type castEnv struct {
	skill                                 catalog.Skill
	targets                               []int32
	originalTarget, skillTarget, coolTime int32
	seq                                   uint64
}

// MagicSkillUse requests a known skill using the current target and level.
func (w *World) MagicSkillUse(ctx context.Context, id, skillID int32, ctrl, shift bool) error {
	return w.act(ctx, id, VerbCast, func(s *state, p *player) {
		sk, ok := w.cat.Skill(skillID, p.skillLevel(skillID))
		if !ok || sk.Passive || sk.Type != "PDAM" || sk.Target != "TARGET_ONE" || p.teleporting || p.dead || p.sitting || sk.Precondition || sk.Lethal1 != 0 || sk.Lethal2 != 0 || sk.NeededCharges != 0 || sk.ItemConsume != 0 || sk.SoulCount != 0 {
			p.send(ActionIgnored{})
			return
		}
		if w.skillDisabled(s, p, skillID) {
			w.sendReuseMessage(p, sk)
			return
		}
		w.setIntention(s, p, order{intent: intentCast, cast: &castRequest{skill: sk, ctrl: ctrl, shift: shift}})
	})
}
func (w *World) sendReuseMessage(p *player, sk catalog.Skill) {
	p.send(SkillReuse{SkillID: sk.ID, RemainingMilliseconds: max(time.Until(p.reuse[sk.ID].until).Milliseconds(), 0)})
}
func (w *World) skillTarget(s *state, p *player, sk catalog.Skill, target int32) (actor, bool) {
	t, ok := s.actor(target)
	if !ok || t == actor(p) || t.base().dead || !p.known[target] {
		return nil, false
	}
	if n, ok := t.(*npc); !ok || !n.mob() {
		return nil, false
	}
	return t, true
}
func (w *World) doCast(s *state, p *player, req *castRequest, target int32) {
	sk := req.skill
	t, ok := w.skillTarget(s, p, sk, target)
	if !ok || p.dead || p.teleporting || p.sitting || w.skillDisabled(s, p, sk.ID) || !weaponAllowed(sk, p.stats.Worn) || p.mp < float64(sk.MPConsume)+float64(sk.MPInitialConsume) || p.hp <= float64(sk.HPConsume) {
		p.send(ActionIgnored{})
		w.setIntention(s, p, order{intent: intentActive})
		return
	}
	hit, cool := sk.HitTime, sk.CoolTime
	if !sk.StaticHitTime {
		speed := float64(max(p.pAtkSpd(), 1))
		if sk.Magic {
			speed = float64(max(p.stats.MAtkSpd(), 1))
		}
		multiplier := 333.3 / speed
		if hit >= 500 {
			multiplier = max(multiplier, 500/float64(hit))
		}
		hit = int32(float64(hit) * multiplier)
		cool = int32(float64(cool) * multiplier)
	}
	s.setBool(p.ref(), PropertyCasting, &p.casting, true)
	p.castSeq++
	seq := p.castSeq
	reuse := sk.ReuseDelay
	// Keep the mastery draw even when there are no mastery effects.
	s.rng.IntN(100)
	if sk.MPInitialConsume > 0 {
		w.setMP(s, p, p.mp-float64(sk.MPInitialConsume))
	}
	p.disableSkill(sk.ID, time.Duration(reuse)*time.Millisecond)
	if r, ok := p.reuse[sk.ID]; ok {
		s.group([]Effect{CapabilityChanged{Entity: p.ref(), Capability: CapabilitySkillEnabled, ID: sk.ID, Before: 1}})
		s.afterRule(time.Until(r.until), RuleSkillReuse, func(s *state) {
			if s.players[p.id] == p && p.reuse[sk.ID] == r {
				delete(p.reuse, sk.ID)
				s.group([]Effect{CapabilityChanged{Entity: p.ref(), Capability: CapabilitySkillEnabled, ID: sk.ID, After: 1}})
			}
		})
	}
	oldHeading := p.heading
	p.heading = headingTo(p.pos, t.base().pos)
	s.change(p.ref(), PropertyHeading, IntValue(int64(oldHeading)), IntValue(int64(p.heading)))
	env := castEnv{skill: sk, targets: []int32{target}, originalTarget: target, skillTarget: target, coolTime: cool, seq: seq}
	w.broadcastToKnown(s, p, SkillCast{CasterID: p.id, TargetID: target, SkillID: sk.ID, Level: sk.Level, HitTime: hit, Reuse: reuse, From: p.pos, TargetPos: t.base().pos})
	launch := func(s *state) {
		if s.players[p.id] == p && p.castSeq == seq && p.casting && !p.dead && !p.teleporting {
			w.onMagicLaunched(s, p, env)
		}
	}
	if hit < 10 {
		launch(s)
	} else {
		s.afterRule(time.Duration(hit)*time.Millisecond, RuleCastLaunch, launch)
	}
}
func (w *World) onMagicLaunched(s *state, p *player, env castEnv) {
	var targets []int32
	for _, id := range env.targets {
		t, ok := w.skillTarget(s, p, env.skill, id)
		if !ok {
			continue
		}
		radius := float64(env.skill.EffectRange) + float64(p.collision()) + float64(t.collision())
		if env.skill.EffectRange > 0 && distance3DSq(p.pos, t.base().pos) > radius*radius {
			continue
		}
		targets = append(targets, id)
	}
	env.targets = targets
	p.send(SkillLaunched{CasterID: p.id, SkillID: env.skill.ID, Level: env.skill.Level, Targets: append([]int32(nil), targets...)})
	w.setMP(s, p, max(p.mp-float64(env.skill.MPConsume), 0))
	for _, id := range targets {
		t, ok := s.actor(id)
		if !ok || t.base().dead {
			continue
		}
		if n, ok := t.(*npc); ok && env.skill.OverHit {
			n.overhit = true
		}
		if !env.skill.Magic {
			s.rng.IntN(100)
		}
		damage := w.skillPhysDamage(s, p, t, env.skill)
		crit := w.skillCrit(s, p, t, env.skill)
		if crit {
			damage *= 2
		}
		damage = min(damage, math.MaxInt32)
		w.skillReflect(s, env.skill)
		w.sendDamageMessage(s, p, t, int(damage), crit, false)
		w.reduceHP(s, t, damage, p)
		s.rng.IntN(1000)
		w.notifyAttacked(s, t, p)
	}
	finish := func(s *state) {
		if s.players[p.id] == p && p.castSeq == env.seq && p.casting && !p.dead && !p.teleporting {
			w.onMagicFinalizer(s, p, env)
		}
	}
	if env.coolTime < 10 {
		finish(s)
	} else {
		s.afterRule(time.Duration(env.coolTime)*time.Millisecond, RuleCastReady, finish)
	}
}
func (w *World) skillReflect(s *state, sk catalog.Skill) {
	if sk.IgnoreResists || !sk.CanBeReflected || !sk.Magic && (sk.CastRange == -1 || sk.CastRange > playerAttackRange) {
		return
	}
	s.rng.IntN(100)
	s.rng.IntN(100)
}
func (w *World) onMagicFinalizer(s *state, p *player, env castEnv) {
	s.setBool(p.ref(), PropertyCasting, &p.casting, false)
	n, ok := s.npcs[env.skillTarget]
	if p.target == env.skillTarget && ok && !n.dead && n.mob() && p.ai.intention != intentMoveTo && (p.ai.next == nil || p.ai.next.intent == intentIdle) &&
		(env.skill.CastRange < 200 || float64(p.attackRange()+200) > distance2D(p.pos, n.pos)) {
		w.setIntention(s, p, order{intent: intentAttack, object: n.id})
	}
	w.clientStartAutoAttack(s, p)
	w.executeNextIntention(s, p)
}
func (w *World) abortCast(s *state, p *player) {
	if !p.casting {
		return
	}
	s.setBool(p.ref(), PropertyCasting, &p.casting, false)
	p.castSeq++
	w.executeNextIntention(s, p)
	w.broadcastToKnown(s, p, SkillCanceled{ObjectID: p.id})
}

func (p *player) skillDisabled(id int32) bool {
	r, ok := p.reuse[id]
	if !ok {
		return false
	}
	if !time.Now().Before(r.until) {
		delete(p.reuse, id)
		return false
	}
	return true
}
func (w *World) skillDisabled(s *state, p *player, id int32) bool {
	_, had := p.reuse[id]
	disabled := p.skillDisabled(id)
	if had && !disabled {
		s.rule(RuleSkillReuse, func() {
			s.group([]Effect{CapabilityChanged{Entity: p.ref(), Capability: CapabilitySkillEnabled, ID: id, After: 1}})
		})
	}
	return disabled
}

func (p *player) disableSkill(id int32, delay time.Duration) {
	if delay < 100*time.Millisecond {
		return
	}
	now := time.Now()
	if r, ok := p.reuse[id]; ok && r.until.After(now) && r.until.Sub(now)+50*time.Millisecond >= delay {
		return
	}
	if p.reuse == nil {
		p.reuse = map[int32]reuseStamp{}
	}
	p.reuse[id] = reuseStamp{until: now.Add(delay), delay: delay}
}

func (w *World) onIntentionCast(s *state, p *player, req *castRequest) {
	c := p.base()
	if c.ai.intention == intentRest && req.skill.Magic {
		p.send(ActionIgnored{})
		return
	}
	if req.skill.HitTime > 50 {
		w.abortAttack(s, p)
	}
	target := int32(0)
	if t, ok := w.skillTarget(s, p, req.skill, p.target); ok {
		target = t.base().id
	}
	w.changeIntention(s, p, intentCast, target, Point{})
	c.ai.cast = req
	w.notifyThink(s, p)
}

func (w *World) thinkCast(s *state, p *player) {
	c := p.base()
	req, target := c.ai.cast, c.ai.object
	if _, ok := s.actor(target); !ok || req == nil {
		w.setIntention(s, p, order{intent: intentActive})
		p.send(ActionIgnored{})
		return
	}
	if w.maybeMoveToPawn(s, p, target, req.skill.CastRange) {
		p.send(ActionIgnored{})
		return
	}
	if req.skill.HitTime > 50 {
		w.clientStopMoving(s, p)
	}
	w.doCast(s, p, req, target)
}

func (w *World) skillPhysDamage(s *state, p *player, t actor, sk catalog.Skill) float64 {
	damage := p.pAtk() + sk.Power
	damage *= 70 / t.pDef()
	damage *= 1 + (2*s.rng.Float64()-1)*p.randomDamage()
	if damage > 0 && damage < 1 {
		damage = 1
	} else if damage < 0 {
		damage = 0
	}
	switch directionOf(p.base(), t.base()) {
	case side:
		damage *= 1.05
	case back:
		damage *= 1.2
	}
	return math.Max(1, damage)
}

func (w *World) skillCrit(s *state, p *player, t actor, sk catalog.Skill) bool {
	rate := float64(sk.BaseCritRate) * 10 * stat.STRBonus(p.stats.STR())
	switch directionOf(p.base(), t.base()) {
	case side:
		rate *= 1.2
	case back:
		rate *= 1.35
	}
	rate *= 1 + heightModifier(p.base(), t.base(), 0.15)
	return rate > s.rng.Float64()*1000
}
