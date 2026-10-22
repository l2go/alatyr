// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
)
type creature struct {
	id      int32
	pos     Point
	heading int32
	move    *moveData
	running bool
	region  regionKey
	ai      ai

	hp, mp float64
	dead   bool
	dying  dyingState
	target int32
	attackEnd time.Time
	attackSeq uint64
	attacking int32
	casting bool
	castSeq uint64
	broadcast broadcastMask
	queued    bool
	revalidate bool
	lastKnown  time.Time
	listeners map[int32]bool
	regen   time.Time
	regenOn bool
}
type actor interface {
	base() *creature
	moveSpeed() float64
	collision() int32
	level() int32
	maxHP() int32
	maxMP() int32
	pAtk() float64
	pDef() float64
	pAtkSpd() int32
	accuracy() int32
	evasion() int32
	critical() int32
	attackRange() int32
	dex() int32
	randomDamage() float64
	isPlayer() bool
}

type dyingState uint8

const (
	notDying dyingState = iota
	dyingQuiet
	dyingBroadcastNeeded
)
func (c *creature) attackingNow(now time.Time) bool { return now.Before(c.attackEnd) }
func headingTo(from, to Point) int32 {
	return headingOf(float64(to.X-from.X), float64(to.Y-from.Y))
}
func angleDifference(obj, src Point, srcHeading int32) float64 {
	angle := validDegree(math.Atan2(float64(obj.Y-src.Y), float64(obj.X-src.X)) * 180 / math.Pi)
	diff := angle - validDegree(float64(srcHeading)/182.044444444)
	for diff > 180 {
		diff -= 360
	}
	for diff < -180 {
		diff += 360
	}
	return math.Abs(diff)
}

func validDegree(d float64) float64 {
	for d < 0 {
		d += 360
	}
	for d >= 360 {
		d -= 360
	}
	return d
}
type direction uint8

const (
	front direction = iota
	side
	back
)

func directionOf(attacker, target *creature) direction {
	switch d := angleDifference(attacker.pos, target.pos, target.heading); {
	case d <= 60:
		return front
	case d <= 120:
		return side
	}
	return back
}
