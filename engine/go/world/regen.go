// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"time"

	"github.com/l2go/alatyr/engine/go/world/stat"
)

const regenInterval = 3 * time.Second

func (w *World) startRegen(s *state, a actor) {
	c := a.base()
	if c.dead || c.regenOn {
		return
	}
	c.regenOn = true
	s.regenOrder = append(s.regenOrder, c.id)
}
func (w *World) tickRegen(s *state) {
	now := time.Now()
	kept := s.regenOrder[:0]
	for _, id := range s.regenOrder {
		a, ok := s.actor(id)
		if !ok || !a.base().regenOn {
			continue
		}
		if w.regenTask(s, a, now) {
			kept = append(kept, id)
		} else {
			a.base().regenOn = false
		}
	}
	s.regenOrder = kept
	s.after(regenPeriod, w.tickRegen)
}
func (w *World) regenTask(s *state, a actor, now time.Time) bool {
	c := a.base()
	if c.dead {
		return false
	}
	if now.Before(c.regen.Add(regenInterval)) {
		return true
	}
	c.regen = now
	need := false
	if maxHP := float64(a.maxHP()); c.hp < maxHP {
		c.hp = min(c.hp+w.hpRegen(a), maxHP)
		need = need || c.hp < maxHP
		w.markBroadcast(s, a, broadcastStatus)
	}
	if maxMP := float64(a.maxMP()); c.mp < maxMP {
		c.mp = min(c.mp+w.mpRegen(a), maxMP)
		need = need || c.mp < maxMP
		w.markBroadcast(s, a, broadcastStatus)
	}
	if p, ok := a.(*player); ok {
		if maxCP := float64(p.stats.MaxCP()); p.cp < maxCP {
			p.cp = min(p.cp+w.cpRegen(p), maxCP)
			need = need || p.cp < maxCP
			w.markBroadcast(s, a, broadcastStatus)
		}
	}
	return need
}
func movementRate(p *player) float64 {
	switch {
	case p.sitting:
		return 2.5
	case p.running:
		return 0.7
	case p.move != nil:
		return 1.1
	}
	return 1.5
}
func (w *World) hpRegen(a actor) float64 {
	switch v := a.(type) {
	case *player:
		init := playerBaseRegen(v.level(), [...]float64{2.0, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5})
		bonus := 0.0
		if v.sitting && v.level() < 41 {
			init *= 1.5
			bonus += float64(40-v.level()) * 0.7
		} else {
			init *= movementRate(v)
		}
		init *= v.stats.LevelMod() * stat.CONBonus(v.stats.CON())
		return v.stats.Regen(stat.RegenHP, max(init, 1)) + bonus
	case *npc:
		return max(npcBaseHPRegen(v), 1)
	}
	return 0
}
func (w *World) mpRegen(a actor) float64 {
	switch v := a.(type) {
	case *player:
		init := playerBaseRegen(v.level(), [...]float64{0.9, 1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0})
		init *= movementRate(v)
		init *= v.stats.LevelMod() * stat.MENBonus(v.stats.MEN())
		return v.stats.Regen(stat.RegenMP, max(init, 1))
	case *npc:
		return max(npcBaseMPRegen(v), 1)
	}
	return 0
}
func (w *World) cpRegen(p *player) float64 {
	init := 1.5
	if p.level() > 10 {
		init += float64(p.level()-1) / 10.0
	} else {
		init += 0.5
	}
	switch {
	case p.sitting:
		init *= 1.5
	case p.move == nil:
		init *= 1.1
	case p.running:
		init *= 0.7
	}
	init *= p.stats.LevelMod() * stat.CONBonus(p.stats.CON())
	return p.stats.Regen(stat.RegenCP, max(init, 1))
}
func playerBaseRegen(level int32, bands [8]float64) float64 {
	switch {
	case level >= 71:
		return bands[7]
	case level >= 61:
		return bands[6]
	case level >= 51:
		return bands[5]
	case level >= 41:
		return bands[4]
	case level >= 31:
		return bands[3]
	case level >= 21:
		return bands[2]
	case level >= 11:
		return bands[1]
	}
	return bands[0]
}
func npcBaseHPRegen(n *npc) float64 {
	if r := float64(float32(n.tmpl.HPRegen)); r > 0 {
		return r
	}
	return 1.5 + float64(n.tmpl.Level-1)/10.0
}

func npcBaseMPRegen(n *npc) float64 {
	if r := float64(float32(n.tmpl.MPRegen)); r > 0 {
		return r
	}
	return 0.9 + 0.3*(float64(n.tmpl.Level-1)/10.0)
}
