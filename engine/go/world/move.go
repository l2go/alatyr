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
	dx, dy, dz := float64(to.X-cur.X), float64(to.Y-cur.Y), float64(to.Z-cur.Z)
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
	dz := float64(m.dest.Z - zPrev)
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
		w.setPos(s, a, Point{int32(m.xExact), int32(m.yExact), zPrev + int32(dz*fraction+0.5)})
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
