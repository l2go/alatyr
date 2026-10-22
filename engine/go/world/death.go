// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"math"
	"time"
)

const (
	decayTime     = 8500 * time.Millisecond
	decayPeriod   = time.Second
	dyingTime     = 2 * time.Second
	minRespawn    = 10 * time.Second
	altPartyRange = 1600
)

type decayEntry struct {
	id int32
	at time.Time
}

func (w *World) die(s *state, a, killer actor) {
	c := a.base()
	if c.dead {
		return
	}
	w.startDying(s, a)
	w.setHP(s, a, 0)
	c.dead = true
	if p, ok := a.(*player); ok {
		w.setTarget(s, p, 0)
		p.castSeq++
		p.casting = false
	}
	c.target = 0
	w.stopMove(s, a)
	c.regenOn = false
	c.ai.next = nil
	if n, ok := a.(*npc); ok && n.mob() {
		w.calculateRewards(s, n, killer)
	}
	w.markBroadcast(s, a, broadcastStatus|broadcastFullInfo)
	w.notifyDead(s, a)
	if n, ok := a.(*npc); ok {
		s.decay = append(s.decay, decayEntry{id: n.id, at: time.Now().Add(decayTime)})
	}
}
func (w *World) calculateRewards(s *state, n *npc, killer actor) {
	if len(n.hate) == 0 {
		return
	}
	if p, ok := killer.(*player); ok {
		w.doItemDrop(s, n, p)
	}
	for _, h := range n.hate {
		p, ok := s.players[h.id]
		if !ok || h.damage <= 1 || p.dead || p.teleporting || distance3D(n.pos, p.pos) > altPartyRange || !p.known[n.id] {
			continue
		}
		exp, sp := n.expAndSP(p.level()-n.level(), min(h.damage, int64(n.maxHP())))
		if n.overhit && n.overhitAttacker == p.id {
			pct := min(n.overhitDamage*100/float64(n.maxHP()), 25)
			exp += int64(int32(math.Round(pct / 100 * float64(exp))))
		}
		w.addExpAndSP(s, p, exp, sp, true)
	}
}
func (w *World) doItemDrop(s *state, n *npc, p *player) {
	levelMod := 0
	if p.level()-9 >= n.level() {
		levelMod = int(p.level()-(n.level()+8)) * 9
	}
	for _, cat := range w.cat.DropCategories(n.tmpl.ID) {
		if cat.Type < 0 {
			continue
		}
		if int64(s.rng.IntN(catalog.MaxDropChance)) >= dropChance(float64(cat.Chance), nil, levelMod) {
			continue
		}
		d := cat.DropOne(s.rng.IntN)
		if d == nil {
			continue
		}
		count := dropCount(s, max(dropChance(float64(d.Chance), d, levelMod), catalog.MaxDropChance), d)
		if count <= 0 {
			continue
		}
		t, _ := w.cat.Item(d.ItemID)
		if t.ConsumeType == "asset" {
			w.dropOnGround(s, n, p, d.ItemID, count)
		} else {
			w.giveItem(s, p, n, d.ItemID, count)
		}
	}
}
func (w *World) addExpAndSP(s *state, p *player, exp int64, sp int32, vitality bool) {
	if vitality {
		rate := vitalityRate(p.char.Vitality)
		scaled := float64(float32(exp) * rate)
		if scaled >= float64(math.MaxInt64) {
			exp = math.MaxInt64
		} else {
			exp = int64(scaled)
		}
		sp = int32(min(float64(float32(sp)*rate), math.MaxInt32))
	}
	exp = max(min(exp, math.MaxInt64-p.char.Exp), 0)
	sp = max(min(sp, math.MaxInt32-p.char.SP), 0)
	w.addExp(s, p, exp)
	p.char.SP += sp
	p.send(Rewarded{ObjectID: p.id, Exp: exp, SP: sp})
	p.send(SelfInfo{})
}
func (w *World) addExp(s *state, p *player, value int64) {
	p.char.Exp += value
	if len(w.experience) > 0 {
		level := int32(1)
		for i, threshold := range w.experience {
			if p.char.Exp >= threshold {
				level = int32(i + 1)
			}
		}
		if level != p.level() {
			w.addLevel(s, p, level)
		}
	}
}
func (w *World) addLevel(s *state, p *player, level int32) {
	before := p.level()
	p.char.Level = level
	p.stats.Level = level
	if level > before {
		w.setHP(s, p, float64(p.maxHP()))
		w.setMP(s, p, float64(p.maxMP()))
		w.setCP(s, p, float64(p.stats.MaxCP()))
	}
	w.markBroadcast(s, p, broadcastFullInfo)
	p.send(LevelChanged{ObjectID: p.id, Before: before, After: level})
}

func (w *World) startDying(s *state, a actor) {
	c := a.base()
	c.dying = dyingQuiet
	w.fullInfo(s, a)
	s.after(dyingTime, func(s *state) {
		was := c.dying
		c.dying = notDying
		if s.live(a) && was == dyingBroadcastNeeded {
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
	if n == nil {
		return
	}
	now := time.Now()
	for _, p := range s.players {
		if p.teleporting || distance3D(n.pos, p.pos) > playerWatch(len(p.known)) {
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
