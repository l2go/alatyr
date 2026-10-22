// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
)

func (s *state) live(a actor) bool { current, ok := s.actor(a.base().id); return ok && current == a }

func (w *World) doAttack(s *state, a, t actor) {
	c, tc := a.base(), t.base()
	if !s.live(a) || !s.live(t) || c.dead || tc.dead || w.attackingDisabled(s, a, time.Now()) {
		return
	}
	if p, ok := t.(*player); ok && (!p.stats.PDefKnown() || p.teleporting) {
		w.clientRequestIgnored(s, a)
		return
	}
	if n, ok := t.(*npc); ok {
		if p, ok := a.(*player); ok {
			w.learnPlayer(s, n, p)
		}
	}
	duration := time.Duration(500000/max(a.pAtkSpd(), 10)) * time.Millisecond
	c.attackSeq++
	seq := c.attackSeq
	c.attackEnd = time.Now().Add(duration)
	c.attacking = 1
	c.heading = headingTo(c.pos, tc.pos)
	miss := !w.calcHit(s, a, t)
	crit := false
	damage := 0
	if !miss {
		crit = w.calcCrit(s, a, t)
		damage = int(min(w.calcPhysDamage(s, a, t, crit), math.MaxInt32))
	}
	w.broadcastToKnown(s, a, AttackDone{AttackerID: c.id, TargetID: tc.id, Damage: int32(damage), Critical: crit, Miss: miss, From: c.pos, TargetPos: tc.pos})
	s.after(duration/2, func(s *state) {
		if s.live(a) && s.live(t) && c.attackSeq == seq {
			w.onHit(s, a, t, damage, crit, miss)
		}
	})
	s.after(duration, func(s *state) {
		if s.live(a) && c.attackSeq == seq && !c.dead {
			c.attacking = 0
			w.notifyReadyToAct(s, a)
		}
	})
	if miss {
		c.attacking = 0
	}
}
func (w *World) abortAttack(s *state, a actor) {
	a.base().attacking = 0
}
func (w *World) onHit(s *state, a, t actor, damage int, crit, miss bool) {
	c, tc := a.base(), t.base()
	if c.dead || tc.dead || !w.knows(s, a, tc.id) {
		w.notifyCancel(s, a)
		return
	}
	w.sendDamageMessage(s, a, t, damage, crit, miss)
	if c.attacking == 0 {
		return
	}
	if p, ok := t.(*player); ok {
		w.clientStartAutoAttack(s, p)
	}
	if !miss && damage > 0 {
		w.reduceHP(s, t, float64(damage), a)
		w.notifyAttacked(s, t, a)
		w.clientStartAutoAttack(s, a)
	}
}
func (w *World) sendDamageMessage(s *state, a, t actor, damage int, crit, miss bool) {
	e := DamageNotice{AttackerID: a.base().id, TargetID: t.base().id, Damage: float64(damage), Critical: crit, Miss: miss}
	if p, ok := a.(*player); ok {
		p.send(e)
	} else if p, ok := t.(*player); ok {
		p.send(e)
	}
}
func (w *World) reduceHP(s *state, t actor, value float64, attacker actor) {
	c := t.base()
	if c.dead || value < 0 || math.IsNaN(value) || math.IsInf(value, 0) {
		return
	}
	switch v := t.(type) {
	case *npc:
		if _, ok := attacker.(*player); ok && v.mob() {
			w.notifyAttacked(s, t, attacker)
			v.addHate(attacker.base().id, int64(value), int64(value)*100/int64(v.level()+7))
		}
		if v.mob() {
			v.takeOverhit(attacker.base().id, value)
		}
	case *player:
		if v.teleporting || !v.stats.PDefKnown() {
			return
		}
		if v.sitting {
			w.standUp(s, v, true)
		}
	}
	w.setHP(s, t, max(c.hp-value, 0))
	if c.hp < 0.5 {
		w.abortAttack(s, t)
		w.die(s, t, attacker)
	}
	if n, ok := t.(*npc); ok && !c.dead {
		n.overhit = false
	}
}
func (w *World) setHP(s *state, a actor, hp float64) {
	c := a.base()
	if c.dead {
		return
	}
	old := c.hp
	c.hp = min(max(hp, 0), float64(a.maxHP()))
	if c.hp != old {
		w.markBroadcast(s, a, broadcastStatus)
	}
	if c.hp < float64(a.maxHP()) {
		w.startRegen(s, a)
	}
}
func (w *World) setMP(s *state, a actor, mp float64) {
	c := a.base()
	if c.dead {
		return
	}
	old := c.mp
	c.mp = min(max(mp, 0), float64(a.maxMP()))
	if c.mp != old {
		w.markBroadcast(s, a, broadcastStatus)
	}
	if c.mp < float64(a.maxMP()) {
		w.startRegen(s, a)
	}
}

func (w *World) attackingDisabled(s *state, a actor, now time.Time) bool {
	c := a.base()
	if c.attackingNow(now) || c.dead {
		return true
	}
	if p, ok := a.(*player); ok {
		return p.tryingToSitOrStand(now)
	}
	return false
}

func (w *World) calcHit(s *state, a, t actor) bool {
	chance := float64(baseHitChance(a.accuracy() - t.evasion()))
	switch directionOf(a.base(), t.base()) {
	case side:
		chance *= 1.1
	case back:
		chance *= 1.2
	}
	chance *= 1 + heightModifier(a.base(), t.base(), 0.05)
	return chance > s.rng.Float64()*1000
}

func baseHitChance(diff int32) int32 {
	if diff >= 10 {
		return 980
	}
	table := map[int32]int32{
		9: 975, 8: 970, 7: 965, 6: 960, 5: 955, 4: 945, 3: 935, 2: 925, 1: 915, 0: 905,
		-1: 890, -2: 875, -3: 860, -4: 845, -5: 830, -6: 815, -7: 800, -8: 785, -9: 770, -10: 755,
		-11: 735, -12: 715, -13: 695, -14: 675, -15: 655, -16: 625, -17: 595, -18: 565, -19: 535,
		-20: 505, -21: 455, -22: 405, -23: 355, -24: 305,
	}
	if v, ok := table[diff]; ok {
		return v
	}
	return 275
}

func heightModifier(a, t *creature, base float64) float64 {
	return base * min(max((float64(a.pos.Z)-float64(t.pos.Z))/50, -1), 1)
}

func (w *World) calcCrit(s *state, a, t actor) bool {
	rate := float64(a.critical())
	switch directionOf(a.base(), t.base()) {
	case side:
		rate *= 1.2
	case back:
		rate *= 1.35
	}
	rate *= 1 + heightModifier(a.base(), t.base(), 0.15)
	if !(rate > s.rng.Float64()*1000) {
		return false
	}
	return 100 > s.rng.Float64()*100
}

func (w *World) calcPhysDamage(s *state, a, t actor, crit bool) float64 {
	damage := a.pAtk()
	if crit {
		damage *= 2
	}
	damage *= 70 / t.pDef()
	damage *= 1 + (2*s.rng.Float64()-1)*a.randomDamage()
	if damage > 0 && damage < 1 {
		damage = 1
	} else if damage < 0 {
		damage = 0
	}
	switch directionOf(a.base(), t.base()) {
	case side:
		damage *= 1.05
	case back:
		damage *= 1.2
	}
	return math.Max(1, damage)
}
