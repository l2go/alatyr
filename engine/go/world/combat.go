// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
)

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
	return base * min(max(float64(a.pos.Z-t.pos.Z)/50, -1), 1)
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
