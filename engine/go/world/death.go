// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func (w *World) startDying(s *state, a actor) {
	c := a.base()
	c.dying = dyingQuiet
	w.fullInfo(s, a)
	s.after(dyingTime, func(s *state) {
		was := c.dying
		c.dying = notDying
		if was == dyingBroadcastNeeded {
			w.markBroadcast(s, a, broadcastFullInfo)
		}
	})
}

func (n *npc) expAndSP(diff int32, damage int64) (int64, int32) {
	maxHP := float64(n.maxHP())
	xp := float64(n.tmpl.RewardExp) * float64(damage) / maxHP
	sp := float64(n.tmpl.RewardSP) * float64(damage) / maxHP
	if diff > 5 {
		pow := math.Pow(5.0/6.0, float64(diff-5))
		xp *= pow
		sp *= pow
	}
	return int64(xp), int32(sp)
}

func dropChance(chance float64, d *catalog.Drop, levelMod int) int64 {
	if levelMod > 0 {
		chance /= 3
	}
	chance *= (100.0 - float64(levelMod)) / 100.0
	return int64(chance)
}

func dropCount(s *state, chance int64, d *catalog.Drop) int64 {
	multiplier := chance / catalog.MaxDropChance
	chance %= catalog.MaxDropChance
	if int64(s.rng.IntN(catalog.MaxDropChance)) < chance {
		multiplier++
	}
	if multiplier <= 0 {
		return 0
	}
	lo, hi := d.Min*multiplier, d.Max*multiplier
	switch {
	case lo == hi:
		return lo
	case lo < hi:
		return lo + int64(s.rng.Float64()*float64(hi-lo+1))
	}
	return multiplier
}

func (w *World) tickDecay(s *state) {
	now := time.Now()
	kept := s.decay[:0]
	for _, d := range s.decay {
		if !now.After(d.at) {
			kept = append(kept, d)
			continue
		}
		n, ok := s.npcs[d.id]
		if !ok {
			continue
		}
		w.decay(s, n)
	}
	s.decay = kept
	s.after(decayPeriod, w.tickDecay)
}

func (w *World) decay(s *state, n *npc) {
	for _, p := range s.players {
		if !p.known[n.id] {
			continue
		}
		if p.target == n.id {
			w.setTarget(s, p, 0)
		}
		delete(p.known, n.id)
		p.send(ObjectGone{ObjectID: n.id})
		w.notifyForget(s, p, n.id)
	}
	r := s.region(n.region)
	for i, id := range r.objects {
		if id == n.id {
			r.objects = append(r.objects[:i], r.objects[i+1:]...)
			break
		}
	}
	delete(s.npcs, n.id)
	delay := max(time.Duration(n.spawn.RespawnDelay)*time.Second, minRespawn)
	sp, t := n.spawn, n.tmpl
	s.after(delay, func(s *state) { w.respawn(s, sp, t) })
}

func (w *World) respawn(s *state, sp catalog.Spawn, t catalog.NPCTemplate) {
	n := w.spawnNPC(s, sp, t)
	now := time.Now()
	for _, p := range s.players {
		if distance3D(n.pos, p.pos) > playerWatch(len(p.known)) {
			continue
		}
		k := regionOf(p.pos)
		near := false
		for _, nk := range neighbourhood(k) {
			if nk == n.region {
				near = true
			}
		}
		if !near {
			continue
		}
		if !p.known[n.id] {
			p.known[n.id] = true
			p.send(NPCSeen{NPC: n.view(now)})
		}
		w.learnPlayer(s, n, p)
	}
}

func vitalityRate(points int32) float32 {
	switch {
	case points > 18200:
		return 3
	case points > 14600:
		return 2.5
	case points > 1800:
		return 2
	case points > 240:
		return 1.5
	}
	return 1
}
