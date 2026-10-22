// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"testing"
)

func TestMovementKeepsFractionalCoordinates(t *testing.T) {
	p := &player{creature: creature{pos: Point{X: 100, Y: 100, Z: 20}}}
	// Exercise the transferred integrator with an explicit synthetic speed.
	a := &testActor{creature: p.creature, speed: 3.25}
	s := &state{}
	w := &World{}
	w.moveTo(s, a, Point{X: 110, Y: 110, Z: 20}, 0)
	w.updatePosition(s, a, 1)
	w.updatePosition(s, a, 2)
	want := 100 + 3.25*0.2/math.Sqrt2
	if math.Abs(a.move.xExact-want) > 1e-10 {
		t.Fatalf("lost exact position: %v", a.move.xExact)
	}
}

type testActor struct {
	creature
	actor
	speed float64
}

func (a *testActor) base() *creature    { return &a.creature }
func (a *testActor) moveSpeed() float64 { return a.speed }
func TestMovementExtremeVerticalDifference(t *testing.T) {
	a := &testActor{creature: creature{pos: Point{X: 0, Y: 0, Z: math.MinInt32}}, speed: 100}
	s := &state{}
	w := &World{}
	w.moveTo(s, a, Point{X: 1, Y: 0, Z: math.MaxInt32}, 0)
	if !w.updatePosition(s, a, 1) || a.pos.Z != math.MaxInt32 {
		t.Fatal("overflow in vertical arrival")
	}
}
