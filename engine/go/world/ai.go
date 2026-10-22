// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
)

type intention uint8

const (
	intentIdle intention = iota
	intentActive
	intentRest
	intentAttack
	intentMoveTo
	intentPickUp
	intentCast
	intentInteract
)
type order struct {
	intent intention
	object int32
	pos    Point
	cast   *castRequest
}

type ai struct {
	intention intention
	object    int32
	pos       Point
	cast      *castRequest
	next      *order

	target      int32
	follow      int32
	followRange int32
	followTask  bool

	clientMoving  bool
	pawnOffset    int32
	pawnTimeout   int64
	autoAttacking bool
	thinking      bool
	attackTimeout int64
}
const maxAttackTimeout = 300
func (w *World) inProtectedAction(s *state, a actor) bool {
	now := time.Now()
	if a.base().attackingNow(now) || a.base().casting {
		return true
	}
	if p, ok := a.(*player); ok {
		return p.tryingToSitOrStand(now)
	}
	return false
}
func (w *World) setIntention(s *state, a actor, o order) {
	c := a.base()
	if w.inProtectedAction(s, a) {
		w.clientActionFailed(s, a)
		c.ai.next = &o
		return
	}
	c.ai.next = nil
	if o.intent != intentAttack {
		w.stopFollow(s, a)
	}
	switch o.intent {
	case intentIdle:
		w.onIntentionIdle(s, a)
	case intentActive:
		w.onIntentionActive(s, a)
	case intentRest:
		w.onIntentionRest(s, a)
	case intentAttack:
		w.onIntentionAttack(s, a, o.object)
	case intentMoveTo:
		w.onIntentionMoveTo(s, a, o.pos)
	case intentPickUp:
		w.onIntentionPickUp(s, a, o.object)
	case intentCast:
		if p, ok := a.(*player); ok {
			w.onIntentionCast(s, p, o.cast)
		}
	case intentInteract:
		if p, ok := a.(*player); ok {
			w.onIntentionInteract(s, p, o.object)
		}
	}
}
func (w *World) changeIntention(s *state, a actor, in intention, object int32, pos Point) {
	c := a.base()
	if n, ok := a.(*npc); ok && n.mob() {
		if in == intentIdle || in == intentActive {
			if !c.dead {
				if len(n.knownPlayers) > 0 || distance3D(c.pos, n.home()) > 2*maxDriftRange {
					in = intentActive
				}
			}
			if in == intentIdle {
				c.ai.intention, c.ai.object, c.ai.pos = intentIdle, 0, Point{}
				w.stopAITask(s, n)
				return
			}
		}
		c.ai.intention, c.ai.object, c.ai.pos = in, object, pos
		w.startAITask(s, n)
		return
	}
	c.ai.intention, c.ai.object, c.ai.pos = in, object, pos
}

func (w *World) onIntentionIdle(s *state, a actor) {
	w.changeIntention(s, a, intentIdle, 0, Point{})
	w.clientStopMoving(s, a)
	w.clientStopAutoAttack(s, a)
}

func (w *World) onIntentionActive(s *state, a actor) {
	if a.isPlayer() {
		w.setIntention(s, a, order{intent: intentIdle})
		return
	}
	c := a.base()
	c.ai.attackTimeout = math.MaxInt32
	if c.ai.intention != intentActive {
		w.changeIntention(s, a, intentActive, 0, Point{})
		w.clientStopMoving(s, a)
		w.clientStopAutoAttack(s, a)
		if n := a.(*npc); n.mob() {
			w.tryAnimation(s, n, false, true)
		}
		w.notifyThink(s, a)
	}
}

func (w *World) onIntentionRest(s *state, a actor) {
	c := a.base()
	if !a.isPlayer() {
		w.setIntention(s, a, order{intent: intentIdle})
		return
	}
	if c.ai.intention != intentRest {
		w.changeIntention(s, a, intentRest, 0, Point{})
		c.ai.target = 0
		w.clientStopMoving(s, a)
	}
}

func (w *World) onIntentionAttack(s *state, a actor, target int32) {
	c := a.base()
	if !a.isPlayer() {
		c.ai.attackTimeout = maxAttackTimeout + s.ticks
	}
	if target == 0 || c.ai.intention == intentRest {
		w.clientActionFailed(s, a)
		return
	}
	if c.ai.intention == intentAttack && c.ai.object == target {
		w.clientActionFailed(s, a)
		return
	}
	w.changeIntention(s, a, intentAttack, target, Point{})
	w.stopFollow(s, a)
	w.notifyThink(s, a)
}

func (w *World) onIntentionMoveTo(s *state, a actor, to Point) {
	c := a.base()
	if c.ai.intention == intentRest {
		w.clientActionFailed(s, a)
		return
	}
	w.changeIntention(s, a, intentMoveTo, 0, to)
	w.clientStopAutoAttack(s, a)
	w.abortAttack(s, a)
	w.aiMoveTo(s, a, to)
}

func (w *World) onIntentionPickUp(s *state, a actor, item int32) {
	c := a.base()
	if c.ai.intention == intentRest {
		w.clientActionFailed(s, a)
		return
	}
	w.clientStopAutoAttack(s, a)
	if _, onGround := s.ground[item]; !onGround {
		return
	}
	w.changeIntention(s, a, intentPickUp, item, Point{})
	c.ai.target = item
	w.moveToPawn(s, a, item, 20)
}
func (w *World) notifyThink(s *state, a actor) {
	if w.inProtectedAction(s, a) {
		return
	}
	c := a.base()
	if a.isPlayer() {
		if c.ai.thinking && c.ai.intention != intentCast {
			w.clientActionFailed(s, a)
			return
		}
		c.ai.thinking = true
		defer func() { c.ai.thinking = false }()
		switch c.ai.intention {
		case intentAttack:
			w.thinkAttackPlayer(s, a)
		case intentCast:
			w.thinkCast(s, a.(*player))
		case intentPickUp:
			w.thinkPickUp(s, a.(*player))
		case intentInteract:
			w.thinkInteract(s, a.(*player))
		}
		return
	}
	n := a.(*npc)
	if c.ai.thinking || !n.mob() {
		return
	}
	c.ai.thinking = true
	defer func() { c.ai.thinking = false }()
	switch c.ai.intention {
	case intentActive:
		w.think(s, n)
	case intentAttack:
		w.thinkAttackMob(s, n)
	}
}
func (w *World) notifyArrived(s *state, a actor) {
	c := a.base()
	w.revalidateZone(s, a)
	c.move = nil
	w.clientStoppedMoving(s, a)
	if c.ai.intention == intentMoveTo {
		w.setIntention(s, a, order{intent: intentActive})
	}
	w.notifyThink(s, a)
}
func (w *World) notifyArrivedRevalidate(s *state, a actor) {
	if a.isPlayer() {
		w.updateKnown(s, a)
	}
	w.followTarget(s, a)
}
func (w *World) notifyReadyToAct(s *state, a actor) { w.executeNextIntention(s, a) }
func (w *World) executeNextIntention(s *state, a actor) {
	c := a.base()
	if next := c.ai.next; next != nil {
		c.ai.next = nil
		w.setIntention(s, a, *next)
	} else if c.ai.intention == intentCast {
		w.setIntention(s, a, order{intent: intentIdle})
	}
	w.notifyThink(s, a)
}
func (w *World) notifyAttacked(s *state, a actor, attacker actor) {
	c := a.base()
	if c.dead {
		return
	}
	if n, ok := a.(*npc); ok && n.mob() {
		c.ai.attackTimeout = maxAttackTimeout + s.ticks
		if n.globalAggro < 0 {
			n.globalAggro = 0
		}
		n.addHate(attacker.base().id, 0, 1)
		if !c.running {
			w.setRunning(s, a, true)
		}
		if c.ai.intention != intentAttack || n.mostHated() != c.ai.object {
			w.setIntention(s, a, order{intent: intentAttack, object: attacker.base().id})
		}
	}
	w.clientStartAutoAttack(s, a)
}
func (w *World) notifyCancel(s *state, a actor) {
	c := a.base()
	c.ai.next = nil
	if p, ok := a.(*player); ok {
		w.abortCast(s, p)
	}
	w.stopFollow(s, a)
	if s.stance[c.id].IsZero() {
		w.broadcastToKnown(s, a, CombatStance{ObjectID: c.id})
	}
	w.notifyThink(s, a)
}
func (w *World) notifyForget(s *state, a actor, object int32) {
	c := a.base()
	if c.ai.target == object {
		c.ai.target = 0
		if c.ai.intention == intentPickUp || c.ai.intention == intentInteract {
			w.setIntention(s, a, order{intent: intentActive})
		}
	}
	if (c.ai.intention == intentAttack || c.ai.intention == intentCast) && c.ai.object == object {
		w.setIntention(s, a, order{intent: intentActive})
	}
	if c.ai.follow == object {
		w.clientStopMoving(s, a)
		w.stopFollow(s, a)
		w.setIntention(s, a, order{intent: intentActive})
	}
}
func (w *World) clientActionFailed(s *state, a actor) {
	if p, ok := a.(*player); ok {
		p.send(ActionFailed{})
	}
}
func (w *World) moveToPawn(s *state, a actor, pawn int32, offset int32) {
	c := a.base()
	ppos, pcoll, creature, ok := s.objectAt(pawn)
	if !ok {
		return
	}
	minOffset := a.collision() + pcoll
	offset = max(10, offset, minOffset)
	send := true
	if c.ai.clientMoving && c.ai.target == pawn {
		if c.ai.pawnOffset == offset {
			if s.ticks < c.ai.pawnTimeout {
				return
			}
			send = false
		}
	}
	c.ai.clientMoving = true
	c.ai.pawnOffset = offset
	c.ai.target = pawn
	c.ai.pawnTimeout = s.ticks + 200/100
	walk := offset
	if creature {
		if p, _ := s.actor(pawn); p.base().move != nil {
			speed := p.moveSpeed() / ticksPerSecond
			walk = int32(float64(walk) + speed*math.Cos(angleDifference(c.pos, p.base().pos, p.base().heading)*math.Pi/180))
		}
	}
	w.moveTo(s, a, ppos, walk)
	if c.move == nil {
		if p, ok := a.(*player); ok {
			p.send(ActionFailed{})
		}
		return
	}
	if creature {
		if send {
			w.broadcastToKnown(s, a, MoveToPawn{ObjectID: c.id, TargetID: pawn, Distance: c.ai.pawnOffset, From: c.pos, To: ppos})
		}
		return
	}
	w.broadcastToKnown(s, a, MoveStarted{ObjectID: c.id, From: c.pos, To: c.move.dest})
}
func (w *World) aiMoveTo(s *state, a actor, to Point) {
	c := a.base()
	c.ai.clientMoving = true
	c.ai.pawnOffset = 0
	w.moveTo(s, a, to, 0)
	dest := c.pos
	if c.move != nil {
		dest = c.move.dest
	}
	w.broadcastToKnown(s, a, MoveStarted{ObjectID: c.id, From: c.pos, To: dest})
}
func (w *World) clientStopMoving(s *state, a actor) {
	c := a.base()
	if c.move != nil {
		w.stopMove(s, a)
	}
	c.ai.pawnOffset = 0
	if c.ai.clientMoving {
		c.ai.clientMoving = false
		w.broadcastToKnown(s, a, Stopped{ObjectID: c.id, Pos: c.pos, Heading: c.heading})
	}
}
func (w *World) clientStoppedMoving(s *state, a actor) {
	c := a.base()
	if c.ai.pawnOffset > 0 {
		c.ai.pawnOffset = 0
		w.broadcastToKnown(s, a, Stopped{ObjectID: c.id, Pos: c.pos, Heading: c.heading})
	}
	c.ai.clientMoving = false
}
func (w *World) clientStartAutoAttack(s *state, a actor) {
	c := a.base()
	if !c.ai.autoAttacking {
		w.broadcastToKnown(s, a, CombatStance{ObjectID: c.id, On: true})
		c.ai.autoAttacking = true
	}
	w.addStance(s, a)
}
func (w *World) clientStopAutoAttack(s *state, a actor) {
	c := a.base()
	if a.isPlayer() {
		if s.stance[c.id].IsZero() && c.ai.autoAttacking {
			w.addStance(s, a)
		}
		return
	}
	if c.ai.autoAttacking {
		w.broadcastToKnown(s, a, CombatStance{ObjectID: c.id})
		c.ai.autoAttacking = false
	}
}
func (w *World) startFollow(s *state, a actor, target int32, rng int32) {
	c := a.base()
	t, ok := s.actor(target)
	if !ok {
		w.stopFollow(s, a)
		return
	}
	c.ai.follow = target
	c.ai.followRange = rng + a.collision() + t.collision()
	if !c.ai.followTask {
		c.ai.followTask = true
		s.followers = append(s.followers, c.id)
	}
	w.followTarget(s, a)
}

func (w *World) stopFollow(s *state, a actor) {
	c := a.base()
	c.ai.follow = 0
	c.ai.followTask = false
}
func (w *World) followTarget(s *state, a actor) {
	c := a.base()
	t, ok := s.actor(c.ai.follow)
	if !ok {
		return
	}
	d := distance2D(c.pos, t.base().pos)
	if d > 3000 && (d > 6000 || !w.knows(s, a, c.ai.follow)) {
		w.setIntention(s, a, order{intent: intentIdle})
		return
	}
	if d >= float64(c.ai.followRange) {
		w.moveToPawn(s, a, c.ai.follow, c.ai.followRange)
	}
}

func (w *World) insideActingRadius(s *state, a actor) bool {
	c := a.base()
	t, ok := s.actor(c.ai.follow)
	if !ok {
		return false
	}
	return distance2D(c.pos, t.base().pos) < float64(c.ai.followRange)
}
func (w *World) tickFollow(s *state) {
	kept := s.followers[:0]
	for _, id := range s.followers {
		a, ok := s.actor(id)
		if !ok || !a.base().ai.followTask {
			continue
		}
		w.followTarget(s, a)
		if a.base().ai.followTask {
			kept = append(kept, id)
		}
	}
	s.followers = kept
	s.after(attackFollowPeriod, w.tickFollow)
}
func (w *World) maybeMoveToPawn(s *state, a actor, target int32, offset int32) bool {
	c := a.base()
	orig := offset
	tpos, tcoll, creature, ok := s.objectAt(target)
	if !ok || offset < 0 {
		return false
	}
	offset += a.collision() + tcoll
	d := distance2D(c.pos, tpos)
	if d > float64(offset) {
		if c.ai.follow != 0 {
			return true
		}
		if p, ok := a.(*player); ok && c.ai.intention == intentCast && c.ai.cast != nil && c.ai.cast.shift {
			p.send(ActionFailed{})
			return true
		}
		if !c.running && !a.isPlayer() {
			w.setRunning(s, a, true)
		}
		w.stopFollow(s, a)
		if creature {
			w.startFollow(s, a, target, orig)
		} else {
			w.moveToPawn(s, a, target, offset)
		}
		return true
	}
	if c.ai.follow != 0 {
		w.stopFollow(s, a)
	}
	return false
}
func (w *World) thinkAttackPlayer(s *state, a actor) {
	c := a.base()
	t, ok := s.actor(c.ai.object)
	if !ok {
		w.clientActionFailed(s, a)
		return
	}
	if t.base().dead {
		w.setIntention(s, a, order{intent: intentActive})
		w.clientActionFailed(s, a)
		return
	}
	if w.maybeMoveToPawn(s, a, c.ai.object, a.attackRange()) {
		w.clientActionFailed(s, a)
		return
	}
	w.doAttack(s, a, t)
}
func (w *World) thinkPickUp(s *state, p *player) {
	c := p.base()
	if _, ok := s.ground[c.ai.target]; !ok {
		w.setIntention(s, p, order{intent: intentActive})
		w.clientActionFailed(s, p)
		return
	}
	if w.maybeMoveToPawn(s, p, c.ai.target, 36) {
		w.clientActionFailed(s, p)
		return
	}
	item := c.ai.target
	w.setIntention(s, p, order{intent: intentIdle})
	w.doPickup(s, p, item)
}
func (w *World) notifyDead(s *state, a actor) {
	c := a.base()
	if n, ok := a.(*npc); ok && n.mob() {
		w.stopAITask(s, n)
	}
	w.stopFollow(s, a)
	if a.isPlayer() {
		c.ai.pawnOffset = 0
		c.ai.clientMoving = false
	}
	w.broadcastToKnown(s, a, Died{ObjectID: c.id, ToVillage: a.isPlayer()})
	c.ai.intention, c.ai.object = intentIdle, 0
	c.ai.target = 0
	w.stopFollow(s, a)
	if !a.isPlayer() {
		w.setRunning(s, a, false)
	}
}
