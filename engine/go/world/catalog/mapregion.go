// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

type mapRegion struct {
	restarts [6]int32
	special    bool
	xs, ys     []int32
	xMin, xMax int32
	yMin, yMax int32
	zMin, zMax int32
}
func (r mapRegion) contains(x, y, z int32) bool {
	if !r.special {
		return r.xMin <= x && x <= r.xMax && r.yMin <= y && y <= r.yMax
	}
	if z != -1 && (z < r.zMin || r.zMax < z) {
		return false
	}
	if x < r.xMin || r.xMax < x || y < r.yMin || r.yMax < y {
		return false
	}
	cn := 0
	for i := 0; i < len(r.xs)-1; i++ {
		if (r.ys[i] <= y && r.ys[i+1] > y) || (r.ys[i] > y && r.ys[i+1] <= y) {
			vt := float32(y-r.ys[i]) / float32(r.ys[i+1]-r.ys[i])
			if float32(x) < float32(r.xs[i])+vt*float32(r.xs[i+1]-r.xs[i]) {
				cn++
			}
		}
	}
	return cn&1 == 1
}
