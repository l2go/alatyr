// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/stat"
)

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
		p.send(ActionFailed{})
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
		p.send(ActionFailed{})
		return
	}
	if w.maybeMoveToPawn(s, p, target, req.skill.CastRange) {
		p.send(ActionFailed{})
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
