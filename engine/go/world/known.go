// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"maps"
	"slices"
	"time"
)

func (w *World) fullInfo(s *state, a actor) {
	switch v := a.(type) {
	case *player:
		v.send(s, SelfChanged{Self: w.selfOf(v)})
		w.describeState(s, v, v)
		var seen *PlayerSeen
		for _, q := range s.players {
			if q != v && q.known[v.id] {
				if seen == nil {
					seen = &PlayerSeen{Player: w.selfOf(v)}
				}
				q.send(s, *seen)
				w.describeState(s, v, q)
			}
		}
	case *npc:
		now := time.Now()
		w.broadcastToKnown(s, a, NPCSeen{NPC: v.view(now)})
		for _, p := range s.players {
			if p.known[v.id] {
				w.describeState(s, v, p)
			}
		}
	}
}

func (w *World) queueRevalidate(s *state, a actor) {
	c := a.base()
	if !c.revalidate {
		c.revalidate = true
		s.revalidateQueue = append(s.revalidateQueue, c.id)
	}
}

func (w *World) tickRevalidate(s *state) {
	queue := s.revalidateQueue
	s.revalidateQueue = nil
	for _, id := range queue {
		a, ok := s.actor(id)
		if !ok {
			continue
		}
		a.base().revalidate = false
		w.updateKnown(s, a)
		w.revalidateZone(s, a)
	}
	s.afterRule(revalidatePeriod, RuleVisibility, w.tickRevalidate)
}

func (w *World) addStance(s *state, a actor) {
	c := a.base()
	old := s.stance[c.id]
	if s.stance[c.id].IsZero() {
		s.stanceOrder = append(s.stanceOrder, c.id)
	}
	s.stance[c.id] = time.Now().Add(combatTime)
	s.change(c.ref(), PropertyCombatUntil, TimeValue(s.at(old)), TimeValue(s.at(s.stance[c.id])))
}

func (w *World) tickStance(s *state) {
	now := time.Now()
	s.stanceOrder = slices.DeleteFunc(s.stanceOrder, func(id int32) bool {
		until, ok := s.stance[id]
		if !ok {
			return true
		}
		if !now.After(until) {
			return false
		}
		delete(s.stance, id)
		if a, ok := s.actor(id); ok {
			s.change(a.base().ref(), PropertyCombatUntil, TimeValue(s.at(until)), TimeValue(0))
			w.broadcastToKnown(s, a, CombatStance{ObjectID: id})
			s.change(a.base().ref(), PropertyCombat, boolValue(a.base().ai.autoAttacking), IntValue(0))
			a.base().ai.autoAttacking = false
		}
		return true
	})
	s.afterRule(stancePeriod, RuleCombatStance, w.tickStance)
}

// broadcastToKnown delivers an event about a to every player whose interest
// set holds a, and to a itself. An NPC's own perception of players is AI
// state; it never chooses who hears about the NPC.
func (w *World) broadcastToKnown(s *state, a actor, e Event) {
	if p, ok := a.(*player); ok {
		w.deliver(p, e)
	}
	for id := range s.watchers[a.base().id] {
		if q, ok := s.players[id]; ok {
			w.deliver(q, e)
		}
	}
}

func (w *World) knows(s *state, a actor, id int32) bool {
	switch v := a.(type) {
	case *player:
		return v.known[id]
	case *npc:
		return v.knownPlayers[id]
	}
	return false
}

func (w *World) updateKnown(s *state, a actor) {
	if p, ok := a.(*player); ok && p.teleporting {
		return
	}
	c := a.base()
	now := time.Now()
	if now.Sub(c.lastKnown) < 100*time.Millisecond {
		return
	}
	c.lastKnown = now
	switch v := a.(type) {
	case *player:
		v.displaced = 0
		w.playerForget(s, v)
		w.playerLearn(s, v, now)
	case *npc:
		for _, id := range slices.Sorted(maps.Keys(v.knownPlayers)) {
			p, ok := s.players[id]
			if !ok || p.teleporting || distance3D(c.pos, p.pos) > v.forgetDistance(id) {
				w.forgetPlayer(s, v, id)
			}
		}
		for _, k := range neighbourhood(c.region) {
			if s.regions[k] == nil || s.regions[k].players == 0 {
				continue
			}
			for _, id := range s.regions[k].objects {
				p, ok := s.players[id]
				if ok && !p.teleporting && regionOf(p.pos) == k {
					w.learnPlayer(s, v, p)
				}
			}
		}
	}
}

// playerForget drops what p no longer sees, and p from the players who see it
// but are out of range. Visibility is not symmetric under the interest bound,
// so the second part walks p's watchers, not p's own set. An NPC displaced
// from p's set still perceives p; p's pass ends that perception by the NPC's
// own forget distance, as the NPC's pass would.
func (w *World) playerForget(s *state, p *player) {
	limit := playerForgetDistance(len(p.known))
	for _, id := range slices.Sorted(maps.Keys(p.known)) {
		pos, _, _, ok := s.objectAt(id)
		if !ok || p.teleporting || distance3D(p.pos, pos) > limit {
			w.playerForgetObject(s, p, id)
		}
	}
	for _, q := range s.watching(p.id) {
		if distance3D(q.pos, p.pos) > playerForgetDistance(len(q.known)) {
			w.playerForgetObject(s, q, p.id)
		}
	}
	for _, n := range s.perceiving(p.id) {
		if !p.known[n.id] && (p.teleporting || distance3D(n.pos, p.pos) > n.forgetDistance(p.id)) {
			w.forgetPlayer(s, n, p.id)
		}
	}
}

func (w *World) playerForgetObject(s *state, p *player, id int32) { w.hide(s, p, id, true) }

// hide removes an object from p's interest set. An NPC forgets p as well only
// when perceive is set: displacement from the set is not AI perception.
func (w *World) hide(s *state, p *player, id int32, perceive bool) {
	if p.known[id] {
		s.change(p.ref(), PropertyKnownObject, RefValue(s.ref(id)), RefValue(Ref{}))
	}
	delete(p.known, id)
	unlink(s.watchers, id, p.id)
	if n, ok := s.npcs[id]; ok {
		delete(n.base().listeners, p.id)
		if perceive {
			w.forgetPlayer(s, n, p.id)
		}
	}
	if p.target == id {
		w.setTarget(s, p, 0)
	}
	p.send(s, ObjectGone{ObjectID: id})
	w.notifyForget(s, p, id)
}

// playerLearn admits unknown objects within the watch radius, most important
// first, until the interest set refuses one. Every later candidate is less
// important, so the set would refuse it as well.
func (w *World) playerLearn(s *state, p *player, now time.Time) {
	if p.teleporting {
		return
	}
	watch := playerWatch(len(p.known))
	var cs []candidate
	consider := func(id int32, pos Point) {
		if !p.known[id] && distance3D(p.pos, pos) <= watch {
			cs = append(cs, candidate{id: id, pos: pos, rank: w.rank(s, p, id, pos)})
		}
	}
	for _, nk := range neighbourhood(regionOf(p.pos)) {
		nr, ok := s.regions[nk]
		if !ok {
			continue
		}
		for _, id := range nr.objects {
			if q, ok := s.players[id]; ok {
				if q != p && !q.teleporting {
					w.playerKnow(s, q, p)
					consider(q.id, q.pos)
				}
				continue
			}
			n, ok := s.npcs[id]
			if !ok {
				continue
			}
			w.learnPlayer(s, n, p)
			consider(n.id, n.pos)
		}
	}
	for _, id := range slices.Sorted(maps.Keys(s.ground)) {
		consider(id, s.ground[id].pos)
	}
	sortCandidates(cs)
	for _, c := range cs {
		// The watch radius still narrows as the set fills.
		if distance3D(p.pos, c.pos) > playerWatch(len(p.known)) {
			continue
		}
		if !w.reveal(s, p, c.id, c.pos, now) {
			return
		}
	}
}

// reveal admits one object into p's interest set and sends its appearance.
func (w *World) reveal(s *state, p *player, id int32, pos Point, now time.Time) bool {
	if p.known[id] {
		return true
	}
	if !w.admit(s, p, id, pos) {
		return false
	}
	var a actor
	switch {
	case s.players[id] != nil:
		q := s.players[id]
		w.deliver(p, PlayerSeen{Player: w.selfOf(q)})
		a = q
	case s.npcs[id] != nil:
		n := s.npcs[id]
		w.deliver(p, NPCSeen{NPC: n.view(now)})
		a = n
	case s.ground[id] != nil:
		g := s.ground[id]
		t, _ := w.cat.Item(g.templateID)
		w.deliver(p, ItemDropped{ObjectID: g.id, TemplateID: g.templateID, Count: g.count, Pos: g.pos, Stackable: t.Stackable()})
	}
	if a != nil {
		w.describeState(s, a, p)
		// The actor's held target, resolved as a scene capture resolves it.
		if c := a.base(); s.ref(c.target) != (Ref{}) {
			w.deliver(p, TargetChosen{ObjectID: c.id, TargetID: c.target, Pos: c.pos})
		}
	}
	return true
}

func (w *World) playerKnow(s *state, p, q *player) {
	if p.teleporting || q.teleporting || p.known[q.id] || distance3D(p.pos, q.pos) > playerWatch(len(p.known)) {
		return
	}
	w.reveal(s, p, q.id, q.pos, time.Now())
}

func (w *World) describeState(s *state, a actor, p *player) {
	c := a.base()
	if !c.ai.clientMoving {
		return
	}
	if c.ai.pawnOffset != 0 && c.ai.follow != 0 {
		if pos, _, _, ok := s.objectAt(c.ai.follow); ok {
			w.deliver(p, Following{ObjectID: c.id, TargetID: c.ai.follow, Distance: c.ai.pawnOffset, From: c.pos, To: pos})
			return
		}
	}
	dest := c.pos
	if c.move != nil {
		dest = c.move.dest
	}
	w.deliver(p, Moving{ObjectID: c.id, From: c.pos, To: dest})
}

func playerForgetDistance(known int) float64 {
	switch {
	case known <= 25:
		return 4000
	case known <= 35:
		return 3500
	case known <= 70:
		return 2910
	}
	return 2310
}

func (s *state) actor(id int32) (actor, bool) {
	if p, ok := s.players[id]; ok {
		return p, true
	}
	if n, ok := s.npcs[id]; ok {
		return n, true
	}
	return nil, false
}

func (s *state) objectAt(id int32) (Point, int32, bool, bool) {
	if a, ok := s.actor(id); ok {
		return a.base().pos, a.collision(), true, true
	}
	if g, ok := s.ground[id]; ok {
		return g.pos, 0, false, true
	}
	return Point{}, 0, false, false
}

func (w *World) place(s *state, p *player) {
	p.region = regionOf(p.pos)
	r := s.region(p.region)
	r.objects = append(r.objects, p.id)
	r.players++
	w.activate(s, p.region)
	k := p.region
	s.after(neighbourTurnOn, func(s *state) {
		if s.players[p.id] != p || p.teleporting {
			return
		}
		for _, nk := range neighbourhood(k) {
			if nk != k {
				w.activate(s, nk)
			}
		}
	})
	p.displaced = 0
	w.playerLearn(s, p, time.Now())
	p.lastKnown = time.Now()
}
func (w *World) revalidateZone(s *state, a actor) {
	if p, ok := a.(*player); ok && !p.teleporting {
		zone := w.cat.ZoneAt(p.pos.X, p.pos.Y, p.pos.Z)
		if zone != p.zone {
			old := p.zone
			p.zone = zone
			s.group(nil,
				Fact{Subject: p.ref(), Property: PropertyPeaceZone, Before: boolValue(old.Peace), After: boolValue(zone.Peace)},
				Fact{Subject: p.ref(), Property: PropertyPvPZone, Before: boolValue(old.PvP), After: boolValue(zone.PvP)})
			p.send(s, ZoneChanged{ObjectID: p.id, Zone: zone})
		}
	}
}

type broadcastMask uint8

const (
	broadcastFullInfo broadcastMask = 1 << iota
	broadcastStatus
	broadcastSkillList
)
const (
	tickPeriod         = 100 * time.Millisecond
	broadcastPeriod    = 100 * time.Millisecond
	revalidatePeriod   = 500 * time.Millisecond
	attackFollowPeriod = 500 * time.Millisecond
	stancePeriod       = time.Second
	regenPeriod        = time.Second
	combatTime         = 15 * time.Second
)

func (w *World) startManagers(s *state) {
	s.afterRule(tickPeriod, RuleMovement, w.tickMovement)
	for _, m := range []struct {
		period time.Duration
		run    func(*state)
		rule   Rule
	}{
		{broadcastPeriod, w.tickBroadcast, RuleRefresh}, {revalidatePeriod, w.tickRevalidate, RuleVisibility},
		{attackFollowPeriod, w.tickFollow, RuleFollow}, {stancePeriod, w.tickStance, RuleCombatStance}, {regenPeriod, w.tickRegen, RuleRegeneration},
		{aiPeriod, w.tickAI, RuleAI}, {aiPeriod, w.tickAnimation, RuleAnimation},
	} {
		s.afterRule(m.period, m.rule, m.run)
	}
}
func (w *World) markBroadcast(s *state, a actor, m broadcastMask) {
	c := a.base()
	c.broadcast |= m
	if !c.queued {
		c.queued = true
		s.broadcastQueue = append(s.broadcastQueue, c.id)
	}
}
func (w *World) tickBroadcast(s *state) {
	queue := s.broadcastQueue
	s.broadcastQueue = nil
	for _, id := range queue {
		a, ok := s.actor(id)
		if !ok {
			continue
		}
		c := a.base()
		m := c.broadcast
		c.broadcast = 0
		c.queued = false
		if m&broadcastFullInfo != 0 {
			if c.dying != notDying {
				c.dying = dyingBroadcastNeeded
			} else {
				w.fullInfo(s, a)
			}
		}
		if m&broadcastStatus != 0 {
			w.statusInfo(s, a)
		}
		if m&broadcastSkillList != 0 {
			if p, ok := a.(*player); ok && !p.slow {
				skills, complete := w.knownSkills(s, p, time.Now())
				p.send(s, SkillsShown{Known: skills, Complete: complete})
			}
		}
	}
	s.afterRule(broadcastPeriod, RuleRefresh, w.tickBroadcast)
}
func (w *World) vitals(a actor) Vitals {
	c := a.base()
	v := Vitals{ObjectID: c.id, HP: c.hp, MP: c.mp, MaxHP: a.maxHP(), MaxMP: a.maxMP()}
	if p, ok := a.(*player); ok {
		v.CP = p.cp
		v.MaxCP = p.stats.MaxCP()
	}
	return v
}
func (w *World) statusInfo(s *state, a actor) {
	v := w.vitals(a)
	if p, ok := a.(*player); ok {
		p.send(s, v)
	}
	for id := range a.base().listeners {
		if p, ok := s.players[id]; ok && p != a {
			w.deliver(p, v)
		}
	}
}
