// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"errors"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
	"slices"
)

// Option configures a world before its loop owns the state.
type Option func(*World)

// Ground is a host-supplied geometry adapter. This module includes no terrain
// data. Implementations must be safe for the world's single owning loop.
type Ground interface {
	Height(x, y, z int32) (int32, bool)
	Walk(fx, fy, fz, tx, ty, tz int32) (int32, int32, int32)
}
type NoGround struct{}

func (NoGround) Height(_, _, z int32) (int32, bool)                { return z, false }
func (NoGround) Walk(_, _, _, x, y, z int32) (int32, int32, int32) { return x, y, z }
func WithGround(ground Ground) Option {
	return func(w *World) {
		if ground != nil {
			w.ground = ground
		}
	}
}
func (w *World) onGround(pos Point) Point {
	if w.ground == nil {
		return pos
	}
	if height, known := w.ground.Height(pos.X, pos.Y, pos.Z); known {
		pos.Z = height
	}
	return pos
}

// WithAnnouncements supplies authored notices at entry. Nothing is supplied
// by default, and the world does not derive other server-state messages.
func WithAnnouncements(messages []string) Option {
	messages = slices.Clone(messages)
	return func(w *World) { w.announcements = slices.Clone(messages) }
}

// WithItemLog installs a synchronous journal. Its writer must be bounded.
func WithItemLog(log *itemlog.Log) Option {
	return func(w *World) {
		if log != nil {
			w.items = log
		}
	}
}

// WithReservedIDs reserves persisted character and item IDs before spawning.
// Hosts must supply all IDs their store holds, including offline characters.
func WithReservedIDs(ids []int32) Option {
	ids = slices.Clone(ids)
	return func(w *World) {
		for _, id := range ids {
			if id > 0 {
				w.s.reserved[id] = true
			}
		}
	}
}

// WithExperienceLevels supplies explicit cumulative thresholds for levels
// 1..N. No progression table is inferred from a class or bundled in the code.
// Invalid thresholds are returned as errors before a world is constructed.
func WithExperienceLevels(thresholds []int64) (Option, error) {
	thresholds = slices.Clone(thresholds)
	if len(thresholds) == 0 || len(thresholds) > 1000 || thresholds[0] != 0 {
		return nil, errors.New("world: invalid experience thresholds")
	}
	for i := 1; i < len(thresholds); i++ {
		if thresholds[i] <= thresholds[i-1] {
			return nil, errors.New("world: unordered experience thresholds")
		}
	}
	return func(w *World) {
		w.experience = slices.Clone(thresholds)
	}, nil
}

func (s *state) newID() int32 {
	return s.ids.next()
}
