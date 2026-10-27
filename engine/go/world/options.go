// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"errors"
	"log/slog"
	"math/rand/v2"
	"slices"

	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

// Option configures a world before its loop owns the state.
type Option func(*World)

// WithLogger supplies diagnostics for the owning loop. Its handler must be
// bounded and safe for the host's other callers. Nil retains slog.Default().
func WithLogger(logger *slog.Logger) Option {
	return func(w *World) {
		if logger != nil {
			w.logger = logger
		}
	}
}

// WithRandomSource supplies the single-loop random source before spawning.
// The default remains a crypto-seeded ChaCha8 source. A supplied source must
// belong exclusively to this world; this option does not promise bit-exact replay.
func WithRandomSource(source rand.Source) Option {
	return func(w *World) {
		if source != nil {
			w.s.rng = rand.New(source)
		}
	}
}

// WithCommits installs a trusted host observer. It receives detached
// in-memory commitments from New (initial spawn) and the owning loop. It must
// be bounded, must not call the world, and must project knowledge before any
// external delivery. There is no retained history or automatic retry here.
func WithCommits(observe func(Commit)) Option {
	return func(w *World) { w.s.observe = observe }
}

// WithDurability observes completion of external skill writes separately from
// gameplay commitments. It runs on the caller, may run concurrently, and must
// be bounded. An error never rewrites the corresponding in-memory transition.
func WithDurability(observe func(Durability)) Option {
	return func(w *World) { w.durability = observe }
}

// Ground is a host-supplied geometry adapter. This module includes no terrain
// data. Implementations must be safe for the world's single owning loop.
type Ground interface {
	Height(x, y, z int32) (int32, bool)
	Walk(fx, fy, fz, tx, ty, tz int32) (int32, int32, int32)
}

// NoGround preserves positions without claiming a known terrain height.
type NoGround struct{}

// Height preserves Z and reports that no terrain height is known.
func (NoGround) Height(_, _, z int32) (int32, bool) { return z, false }

// Walk preserves the requested destination without terrain constraints.
func (NoGround) Walk(_, _, _, x, y, z int32) (int32, int32, int32) { return x, y, z }

// WithGround supplies geometry constraints; nil preserves the default.
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
