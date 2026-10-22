// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"slices"
	"time"
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
	c.heading = headingOf(cos, sin)
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

type Point struct{ X, Y, Z int32 }
type creature struct {
	id      int32
	pos     Point
	heading int32
	move    *moveData
}
type actor interface {
	base() *creature
	moveSpeed() float64
}

func (w *World) setPos(_ *state, a actor, p Point) { a.base().pos = p }
func (w *World) notifyArrived(_ *state, a actor) {
	p := a.(*player)
	p.move = nil
	p.send(Stopped{ObjectID: p.id, Pos: p.pos, Heading: p.heading})
}
func (w *World) tickMovement(s *state) {
	s.ticks++
	s.moving = slices.DeleteFunc(s.moving, func(id int32) bool {
		p, ok := s.players[id]
		if !ok {
			return true
		}
		if w.updatePosition(s, p, s.ticks) {
			w.notifyArrived(s, p)
			return true
		}
		return false
	})
	s.after(time.Second/ticksPerSecond, w.tickMovement)
}
