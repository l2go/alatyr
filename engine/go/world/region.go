// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"
)
const (
	regionShift     = 12
	regionOffsetX   = 80
	regionOffsetY   = 64
	neighbourTurnOn = time.Second
)

type regionKey struct{ x, y int32 }

func regionOf(p Point) regionKey {
	return regionKey{p.X>>regionShift + regionOffsetX, p.Y>>regionShift + regionOffsetY}
}
type region struct {
	objects []int32
	players int
	active  bool
}

func (s *state) region(k regionKey) *region {
	r, ok := s.regions[k]
	if !ok {
		r = &region{}
		s.regions[k] = r
	}
	return r
}
func neighbourhood(k regionKey) []regionKey {
	out := make([]regionKey, 0, 9)
	for x := k.x - 1; x <= k.x+1; x++ {
		for y := k.y - 1; y <= k.y+1; y++ {
			out = append(out, regionKey{x, y})
		}
	}
	return out
}
type Point struct{ X, Y, Z int32 }
func distance3D(a, b Point) float64 {
	dx, dy, dz := float64(a.X-b.X), float64(a.Y-b.Y), float64(a.Z-b.Z)
	return math.Sqrt(dx*dx + dy*dy + dz*dz)
}
