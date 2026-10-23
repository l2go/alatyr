// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
	"time"
)

// Action selects an object, or acts on an already selected object.
func (w *World) Action(ctx context.Context, id, object int32) error {
	return w.act(ctx, id, VerbAction, func(s *state, p *player) {
		if p.dead || p.teleporting || !p.known[object] {
			p.send(ActionIgnored{})
			return
		}
		if n, ok := s.npcs[object]; ok {
			w.npcOnAction(s, p, n)
		}
		if q, ok := s.players[object]; ok && !q.teleporting {
			w.playerOnAction(s, p, q)
		}
		if _, ok := s.ground[object]; ok {
			w.setIntention(s, p, order{intent: intentPickUp, object: object})
		}
	})
}

// AttackRequest starts an attack intention against a known monster.
func (w *World) AttackRequest(ctx context.Context, id, object int32) error {
	return w.act(ctx, id, VerbAttack, func(s *state, p *player) {
		n, ok := s.npcs[object]
		if !ok || !n.mob() || n.dead || p.dead || p.teleporting || !p.known[object] || p.lastTarget == object && time.Since(p.lastTargetAt) < retargetBlocking {
			p.send(ActionIgnored{})
			return
		}
		w.setTarget(s, p, object)
		w.setIntention(s, p, order{intent: intentAttack, object: object})
	})
}
func (w *World) npcOnAction(s *state, p *player, n *npc) {
	p.lastFolk = n.id
	if p.target != n.id {
		w.setTarget(s, p, n.id)
		if n.mob() {
			p.send(w.vitals(n))
		}
		return
	}
	if n.mob() && !n.dead {
		if math.Abs(float64(p.pos.Z)-float64(n.pos.Z)) < 400 {
			w.setIntention(s, p, order{intent: intentAttack, object: n.id})
		}
		return
	}
	if n.npcInstance() {
		w.talk(s, p, n)
	}
}
func (w *World) setTarget(s *state, p *player, object int32) {
	old := p.target
	if object != 0 {
		t, ok := s.actor(object)
		if !ok {
			return
		}
		if q, ok := t.(*player); ok && q.teleporting {
			return
		}
		if t.base().id != p.id {
			p.send(PositionObserved{ObjectID: object, Pos: t.base().pos, Heading: t.base().heading})
		}
		if math.Abs(float64(t.base().pos.Z)-float64(p.pos.Z)) > 500 {
			return
		}
	}
	if o, ok := s.actor(old); ok {
		delete(o.base().listeners, p.id)
	}
	if object != 0 {
		t, _ := s.actor(object)
		t.base().listeners[p.id] = true
		w.broadcastToKnown(s, p, TargetChosen{ObjectID: p.id, TargetID: object, Pos: p.pos})
		diff := int32(0)
		if n, ok := t.(*npc); ok && n.mob() {
			diff = p.level() - n.level()
		}
		p.send(TargetDetails{TargetID: object, LevelDifference: diff})
	} else if old != 0 {
		w.broadcastToKnown(s, p, TargetCleared{ObjectID: p.id, Pos: p.pos})
	}
	p.target = object
	s.change(p.ref(), PropertyTarget, RefValue(s.ref(old)), RefValue(s.ref(object)))
	if old != 0 {
		p.lastTarget = old
		p.lastTargetAt = time.Now()
	}
}

// SetSitting requests sitting or standing under the world's posture rules.
func (w *World) SetSitting(ctx context.Context, id int32, sitting bool) error {
	return w.act(ctx, id, VerbPosture, func(s *state, p *player) {
		if p.teleporting {
			return
		}
		if sitting {
			w.sitDown(s, p)
		} else {
			w.standUp(s, p, false)
		}
	})
}

// SetRunning changes the player's movement mode.
func (w *World) SetRunning(ctx context.Context, id int32, running bool) error {
	return w.act(ctx, id, VerbGait, func(s *state, p *player) { w.setRunning(s, p, running) })
}

// ValidatePosition retains authoritative horizontal coordinates. A nearby
// host observation may update Z when no height provider knows the point.
func (w *World) ValidatePosition(ctx context.Context, id int32, at Point) error {
	return w.act(ctx, id, VerbPosition, func(s *state, p *player) {
		if p.dead || p.teleporting || at.X == 0 && at.Y == 0 && p.pos.X != 0 {
			return
		}
		dx, dy := float64(at.X)-float64(p.pos.X), float64(at.Y)-float64(p.pos.Y)
		if dx*dx+dy*dy < 250000 {
			if _, known := w.ground.Height(p.pos.X, p.pos.Y, p.pos.Z); !known {
				old := p.pos
				p.pos.Z = at.Z
				s.change(p.ref(), PropertyPosition, PointValue(old), PointValue(p.pos))
			}
		}
	})
}

// Teleport uses an explicit world point. It does not select a restart point,
// add a random offset or infer ground height. Appear completes the transition.
func (w *World) Teleport(ctx context.Context, id int32, to Point, revive bool) error {
	var result error
	err := w.act(ctx, id, VerbTeleport, func(s *state, p *player) {
		result = w.teleport(s, p, to, revive)
		if result != nil {
			s.finish(StageRefused)
		}
	})
	if err != nil {
		return err
	}
	return result
}

func (w *World) teleport(s *state, p *player, to Point, revive bool) error {
	if p.teleporting || revive && !p.dead {
		return errors.New("world: invalid teleport state")
	}
	if p.fence == ^Fence(0) {
		s.err = ErrExhausted
		return ErrExhausted
	}
	beforeFence := p.fence
	p.fence++
	s.change(p.ref(), PropertyFence, UintValue(uint64(beforeFence)), UintValue(uint64(p.fence)))
	p.attackSeq++
	p.castSeq++
	s.change(p.ref(), PropertyAttacking, boolValue(p.attacking != 0), IntValue(0))
	s.change(p.ref(), PropertyCasting, boolValue(p.casting), IntValue(0))
	s.change(p.ref(), PropertyMovement, boolValue(p.move != nil), IntValue(0))
	p.attacking = 0
	p.attackEnd = time.Time{}
	p.casting = false
	p.ai.next = nil
	p.move = nil
	w.decayPlayer(s, p)
	s.setBool(p.ref(), PropertyTeleporting, &p.teleporting, true)
	p.pendingRevive = revive
	old := p.pos
	p.pos = to
	s.change(p.ref(), PropertyPosition, PointValue(old), PointValue(to))
	p.region = regionOf(to)
	p.send(Teleported{ObjectID: p.id, Pos: to, Heading: p.heading, Entity: p.ref(), Fence: p.fence})
	return nil
}

// Logout is the voluntary exit and refuses an active combat stance. Leave
// remains the unconditional, retryable persistence path for disconnection.
func (w *World) Logout(ctx context.Context, owner Ref) error {
	return w.leave(ctx, owner, true)
}
