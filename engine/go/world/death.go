// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"maps"
	"math"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
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
	s.setBool(c.ref(), PropertyDead, &c.dead, true)
	if p, ok := a.(*player); ok {
		if p.lifeVersion == math.MaxUint64 {
			s.err = ErrExhausted
			return
		}
		p.lifeVersion++
		w.setTarget(s, p, 0)
		p.castSeq++
		s.setBool(p.ref(), PropertyCasting, &p.casting, false)
	}
	s.change(c.ref(), PropertyTarget, RefValue(s.ref(c.target)), RefValue(Ref{}))
	c.target = 0
	w.stopMove(s, a)
	c.regenOn = false
	c.ai.next = nil
	if n, ok := a.(*npc); ok && n.mob() {
		s.rule(RuleReward, func() { w.calculateRewards(s, n, killer) })
	}
	w.markBroadcast(s, a, broadcastStatus|broadcastFullInfo)
	w.notifyDead(s, a)
	if n, ok := a.(*npc); ok {
		s.decay = append(s.decay, decayEntry{id: n.id, at: time.Now().Add(decayTime)})
	}
}
func (w *World) addExpAndSP(s *state, p *player, exp int64, sp int32) {
	exp = max(min(exp, math.MaxInt64-p.char.Exp), 0)
	sp = max(min(sp, math.MaxInt32-p.char.SP), 0)
	oldXP, oldSP := p.char.Exp, p.char.SP
	w.addExp(s, p, exp)
	p.char.SP += sp
	s.group([]Effect{
		ResourceTransferred{Resource: Resource{Kind: ResourceXP}, From: ResourceLocation{Place: PlaceReward}, To: ResourceLocation{Entity: p.ref(), Place: PlaceCharacter}, Amount: IntegerAmount(exp), ToBalance: Balance{Before: IntegerAmount(oldXP), After: IntegerAmount(p.char.Exp)}},
		ResourceTransferred{Resource: Resource{Kind: ResourceSP}, From: ResourceLocation{Place: PlaceReward}, To: ResourceLocation{Entity: p.ref(), Place: PlaceCharacter}, Amount: IntegerAmount(int64(sp)), ToBalance: Balance{Before: IntegerAmount(int64(oldSP)), After: IntegerAmount(int64(p.char.SP))}},
	})
	p.send(Rewarded{ObjectID: p.id, XP: exp, SP: sp})
	p.send(SelfChanged{})
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
	s.change(p.ref(), PropertyLevel, IntValue(int64(before)), IntValue(int64(level)))
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

func dropChance(chance float64, d *catalog.Drop, levelMod int) int64 {
	if levelMod > 0 {
		chance /= 3
	}
	chance *= (100.0 - float64(levelMod)) / 100.0
	return int64(chance)
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
	s.afterRule(decayPeriod, RuleDecay, w.tickDecay)
}

func (w *World) decay(s *state, n *npc) {
	for _, id := range slices.Sorted(maps.Keys(s.players)) {
		p := s.players[id]
		if !p.known[n.id] {
			continue
		}
		if p.target == n.id {
			w.setTarget(s, p, 0)
		}
		delete(p.known, n.id)
		s.change(p.ref(), PropertyKnownObject, RefValue(n.ref()), RefValue(Ref{}))
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
	s.group([]Effect{ExistenceChanged{Entity: n.ref(), Before: true}})
	delete(s.npcs, n.id)
	delay := max(time.Duration(n.spawn.RespawnDelay)*time.Second, minRespawn)
	sp, t := n.spawn, n.tmpl
	s.afterRule(delay, RuleRespawn, func(s *state) { w.respawn(s, sp, t) })
}

func (w *World) respawn(s *state, sp catalog.Spawn, t catalog.NPCTemplate) {
	n := w.spawnNPC(s, sp, t)
	if n == nil {
		return
	}
	now := time.Now()
	for _, id := range slices.Sorted(maps.Keys(s.players)) {
		p := s.players[id]
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
			s.change(p.ref(), PropertyKnownObject, RefValue(Ref{}), RefValue(n.ref()))
			p.send(NPCSeen{NPC: n.view(now)})
		}
		w.learnPlayer(s, n, p)
	}
}
