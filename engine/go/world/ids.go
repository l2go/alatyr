// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"sync"
)

type ids struct {
	mu   sync.Mutex
	last int32
	used []int32
}

const firstObjectID = 1 << 28

func newIDs(used []int32) *ids {
	used = slices.Clone(used)
	slices.Sort(used)
	i, _ := slices.BinarySearch(used, firstObjectID)
	return &ids{last: firstObjectID - 1, used: slices.Compact(used[i:])}
}

func (d *ids) next() int32 {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.last == 1<<31-1 {
		return 0
	}
	d.last++
	for len(d.used) > 0 && d.used[0] <= d.last {
		if d.used[0] == d.last {
			if d.last == 1<<31-1 {
				return 0
			}
			d.last++
		}
		d.used = d.used[1:]
	}
	return d.last
}

func (d *ids) reserve(id int32) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if id <= d.last {
		return
	}
	i, found := slices.BinarySearch(d.used, id)
	if !found {
		d.used = slices.Insert(d.used, i, id)
	}
}
