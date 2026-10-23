// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"slices"
)

const ticksPerSecond = 10

type moveData struct {
	dest           Point
	startTick      int64
	lastTick       int64
	xExact, yExact float64
}

func (w *World) moveTo(s *state, a actor, to Point, offset int32) {
	c := a.base()
	to = w.onGround(to)

	speed := a.moveSpeed()
	if speed <= 0 {
		return
	}
	cur := c.pos
	dx, dy, dz := float64(to.X)-float64(cur.X), float64(to.Y)-float64(cur.Y), float64(to.Z)-float64(cur.Z)
	distance := math.Sqrt(dx*dx + dy*dy)
	var cos, sin float64
	if offset != 0 || distance < 1 {
		off := float64(offset) - math.Abs(dz)
		if distance < 1 || distance-off <= 0 {
			w.notifyArrived(s, a)
			return
		}
		sin, cos = dy/distance, dx/distance
		distance -= off - 5
		to.X = cur.X + int32(distance*cos)
		to.Y = cur.Y + int32(distance*sin)
	} else {
		sin, cos = dy/distance, dx/distance
	}
	oldHeading := c.heading
	c.heading = headingOf(cos, sin)
	s.change(c.ref(), PropertyHeading, IntValue(int64(oldHeading)), IntValue(int64(c.heading)))
	s.change(c.ref(), PropertyMovement, boolValue(c.move != nil), IntValue(1))
	oldDest := c.pos
	if c.move != nil {
		oldDest = c.move.dest
	}
	s.change(c.ref(), PropertyDestination, PointValue(oldDest), PointValue(to))
	c.move = &moveData{dest: to, startTick: s.ticks}
	if !slices.Contains(s.moving, c.id) {
		s.moving = append(s.moving, c.id)
	}
}

func (w *World) updatePosition(s *state, a actor, tick int64) bool {
	c := a.base()
	m := c.move
	if m == nil {
		return true
	}
	if m.lastTick == 0 {
		m.lastTick = m.startTick
		m.xExact, m.yExact = float64(c.pos.X), float64(c.pos.Y)
	}
	if m.lastTick == tick {
		return false
	}
	zPrev := c.pos.Z
	dx, dy := float64(m.dest.X)-m.xExact, float64(m.dest.Y)-m.yExact
	dz := float64(m.dest.Z) - float64(zPrev)
	passed := a.moveSpeed() * float64(tick-m.lastTick) / ticksPerSecond
	var fraction float64
	if dx*dx+dy*dy < 10000 && dz*dz > 2500 {
		fraction = passed / math.Sqrt(dx*dx+dy*dy)
	} else {
		fraction = passed / math.Sqrt(dx*dx+dy*dy+dz*dz)
	}
	if fraction > 1 {
		w.setPos(s, a, m.dest)
	} else {
		m.xExact += dx * fraction
		m.yExact += dy * fraction
		w.setPos(s, a, Point{int32(m.xExact), int32(m.yExact), int32(int64(zPrev) + int64(dz*fraction+0.5))})
	}
	m.lastTick = tick
	return fraction > 1
}

func headingOf(dx, dy float64) int32 {
	h := math.Atan2(dy, dx) * 180 / math.Pi * 182.044444444
	for h < 0 {
		h += 65536
	}
	for h >= 65536 {
		h -= 65536
	}
	return int32(h)
}

// Point is an authoritative world position in the authored coordinate system.
type Point struct{ X, Y, Z int32 }

func (w *World) setPos(s *state, a actor, pos Point) {
	c := a.base()
	old := c.region
	oldPos := c.pos
	c.pos = pos
	s.change(c.ref(), PropertyPosition, PointValue(oldPos), PointValue(pos))
	c.region = regionOf(pos)
	if old != c.region {
		r := s.region(old)
		r.objects = slices.DeleteFunc(r.objects, func(id int32) bool { return id == c.id })
		s.region(c.region).objects = append(s.region(c.region).objects, c.id)
		if p, ok := a.(*player); ok && !p.teleporting {
			r.players--
			s.region(c.region).players++
			w.activate(s, c.region)
		}
	}
	w.queueRevalidate(s, a)
}
func (w *World) stopMove(s *state, a actor) {
	c := a.base()
	if c.move != nil {
		w.updatePosition(s, a, s.ticks)
		before := c.move != nil
		c.move = nil
		s.change(c.ref(), PropertyMovement, boolValue(before), boolValue(c.move != nil))
	}
	w.broadcastToKnown(s, a, Stopped{ObjectID: c.id, Pos: c.pos, Heading: c.heading})
}
func (w *World) tickMovement(s *state) {
	s.ticks++
	var arrived, followers []actor
	for _, id := range s.moving {
		a, ok := s.actor(id)
		if !ok || a.base().move == nil {
			continue
		}
		c := a.base()
		if a.isPlayer() && (c.ai.deadline != 0 && s.now() >= c.ai.deadline || c.ai.refTarget != (Ref{}) && s.ref(c.ai.refTarget.ID) != c.ai.refTarget) {
			w.haltIntention(s, a)
			continue
		}
		done := w.updatePosition(s, a, s.ticks)
		if a.base().ai.follow == 0 {
			if done {
				arrived = append(arrived, a)
			}
		} else {
			followers = append(followers, a)
		}
	}
	followers = slices.DeleteFunc(followers, func(a actor) bool {
		if w.insideActingRadius(s, a) {
			arrived = append(arrived, a)
			return true
		}
		return false
	})
	s.moving = slices.DeleteFunc(s.moving, func(id int32) bool {
		a, ok := s.actor(id)
		return !ok || a.base().move == nil || slices.Contains(arrived, a)
	})
	for _, a := range arrived {
		w.updateKnown(s, a)
		w.notifyArrived(s, a)
	}
	for _, a := range followers {
		w.notifyArrivedRevalidate(s, a)
	}
	s.afterRule(tickPeriod, RuleMovement, w.tickMovement)
}

func distance2D(a, b Point) float64 {
	return math.Hypot(float64(a.X)-float64(b.X), float64(a.Y)-float64(b.Y))
}
