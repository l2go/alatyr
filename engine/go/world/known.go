// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"time"
)

func (w *World) fullInfo(s *state, a actor) {
	switch v := a.(type) {
	case *player:
		v.send(SelfInfo{Self: w.selfOf(v)})
		var seen *PlayerSeen
		for _, q := range s.players {
			if q != v && q.known[v.id] {
				if seen == nil {
					seen = &PlayerSeen{Player: w.selfOf(v)}
				}
				q.send(*seen)
			}
		}
	case *npc:
		now := time.Now()
		w.broadcastToKnown(s, a, NPCSeen{NPC: v.view(now)})
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
	s.after(revalidatePeriod, w.tickRevalidate)
}

func (w *World) addStance(s *state, a actor) {
	c := a.base()
	if s.stance[c.id].IsZero() {
		s.stanceOrder = append(s.stanceOrder, c.id)
	}
	s.stance[c.id] = time.Now().Add(combatTime)
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
			w.broadcastToKnown(s, a, CombatStance{ObjectID: id})
			a.base().ai.autoAttacking = false
		}
		return true
	})
	s.after(stancePeriod, w.tickStance)
}

func (w *World) broadcastToKnown(s *state, a actor, e Event) {
	switch v := a.(type) {
	case *player:
		v.send(e)
		for _, p := range s.players {
			if p != v && p.known[v.id] {
				p.send(e)
			}
		}
	case *npc:
		for id := range v.knownPlayers {
			if p, ok := s.players[id]; ok {
				p.send(e)
			}
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
	c := a.base()
	now := time.Now()
	if now.Sub(c.lastKnown) < 100*time.Millisecond {
		return
	}
	c.lastKnown = now
	switch v := a.(type) {
	case *player:
		w.playerForget(s, v)
		w.playerLearn(s, v, now)
	case *npc:
		for id := range v.knownPlayers {
			p, ok := s.players[id]
			if !ok || distance3D(c.pos, p.pos) > v.forgetDistance(id) {
				w.forgetPlayer(s, v, id)
			}
		}
		for _, k := range neighbourhood(c.region) {
			if s.regions[k] == nil || s.regions[k].players == 0 {
				continue
			}
			for _, p := range s.players {
				if regionOf(p.pos) == k {
					w.learnPlayer(s, v, p)
				}
			}
		}
	}
}

func (w *World) playerForget(s *state, p *player) {
	limit := playerForgetDistance(len(p.known))
	for id := range p.known {
		pos, _, _, ok := s.objectAt(id)
		if !ok || distance3D(p.pos, pos) > limit {
			w.playerForgetObject(s, p, id)
		}
		if q, ok := s.players[id]; ok && q.known[p.id] && distance3D(q.pos, p.pos) > playerForgetDistance(len(q.known)) {
			w.playerForgetObject(s, q, p.id)
		}
	}
}

func (w *World) playerForgetObject(s *state, p *player, id int32) {
	delete(p.known, id)
	if n, ok := s.npcs[id]; ok {
		delete(n.base().listeners, p.id)
		w.forgetPlayer(s, n, p.id)
	}
	if p.target == id {
		w.setTarget(s, p, 0)
	}
	p.send(ObjectGone{ObjectID: id})
	w.notifyForget(s, p, id)
}

func (w *World) playerLearn(s *state, p *player, now time.Time) {
	for _, nk := range neighbourhood(regionOf(p.pos)) {
		nr, ok := s.regions[nk]
		if !ok {
			continue
		}
		for _, id := range nr.objects {
			if q, ok := s.players[id]; ok {
				if q != p {
					w.playerKnow(s, q, p)
					w.playerKnow(s, p, q)
				}
				continue
			}
			n, ok := s.npcs[id]
			if !ok {
				continue
			}
			w.learnPlayer(s, n, p)
			if distance3D(n.pos, p.pos) <= playerWatch(len(p.known)) && !p.known[id] {
				p.known[id] = true
				p.send(NPCSeen{NPC: n.view(now)})
				w.describeState(s, n, p)
			}
		}
	}
}

func (w *World) playerKnow(s *state, p, q *player) {
	if p.known[q.id] || distance3D(p.pos, q.pos) > playerWatch(len(p.known)) {
		return
	}
	p.known[q.id] = true
	p.send(PlayerSeen{Player: w.selfOf(q)})
	w.describeState(s, q, p)
}

func (w *World) describeState(s *state, a actor, p *player) {
	c := a.base()
	if !c.ai.clientMoving {
		return
	}
	if c.ai.pawnOffset != 0 && c.ai.follow != 0 {
		if pos, _, _, ok := s.objectAt(c.ai.follow); ok {
			p.send(MoveToPawn{ObjectID: c.id, TargetID: c.ai.follow, Distance: c.ai.pawnOffset, From: c.pos, To: pos})
			return
		}
	}
	dest := c.pos
	if c.move != nil {
		dest = c.move.dest
	}
	p.send(MoveStarted{ObjectID: c.id, From: c.pos, To: dest})
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
