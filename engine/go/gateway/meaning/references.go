// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"errors"
	"math"
)

// ErrLimit means an explicit capacity, byte, or monotonic handle bound was exceeded.
var ErrLimit = errors.New("meaning: state limit exceeded")

// Observed is the current bounded target projection passed to a trusted classifier.
type Observed struct {
	// Ref is the current observed lifetime.
	Ref Ref
	// Role is an opaque world label, not permission.
	Role string
	// X is the current position's first coordinate.
	X int32
	// Y is the current position's second coordinate.
	Y int32
	// Z is the current position's third coordinate.
	Z int32
	// Dead is the currently reported death state.
	Dead bool
}

// Observations owns visible reference lifetimes; its zero value must be initialized with NewObservations.
type Observations struct {
	limit      int
	generation uint64
	objects    map[int32]Observed
}

// NewObservations constructs an actor-owned table with an explicit positive capacity.
func NewObservations(limit int) (*Observations, error) {
	if limit < 1 {
		return nil, ErrLimit
	}
	return &Observations{limit: limit, objects: map[int32]Observed{}}, nil
}

// Lookup returns a value copy of the currently observed object.
func (o *Observations) Lookup(id int32) (Observed, bool) { v, ok := o.objects[id]; return v, ok }

// Valid rejects retired lifetimes, including references with generation zero.
func (o *Observations) Valid(r Ref) bool {
	v, ok := o.objects[r.ID]
	return ok && r.Gen != 0 && r == v.Ref
}
func (o *Observations) ref(id int32) Ref {
	if v, ok := o.objects[id]; ok {
		return v.Ref
	}
	return Ref{ID: id}
}

// Reset retires every visible reference without resetting the lifetime counter.
func (o *Observations) Reset() { clear(o.objects) }

// Len returns the number of currently visible objects.
func (o *Observations) Len() int { return len(o.objects) }

// Apply obtains departing references before retiring them. Refreshing an actor
// does not create a new observation lifetime; re-entry after removal does.
func (o *Observations) Apply(ev Event) (Ref, Ref, error) {
	if ev.Kind == Appeared || ev.Kind == Dropped {
		v, ok := o.objects[ev.ID]
		if !ok {
			if len(o.objects) >= o.limit || o.generation == math.MaxUint64 {
				return Ref{}, Ref{}, ErrLimit
			}
			o.generation++
			v.Ref = Ref{ev.ID, o.generation}
		}
		v.Role = ev.Role
		v.X, v.Y, v.Z = ev.X, ev.Y, ev.Z
		o.objects[ev.ID] = v
	}
	id, target := o.ref(ev.ID), o.ref(ev.Target)
	if ev.Kind == Quest {
		id.Gen = 0
	}
	switch ev.Kind {
	case Moved, Stopped, Corrected:
		if v, ok := o.objects[ev.ID]; ok {
			v.X, v.Y, v.Z = ev.X, ev.Y, ev.Z
			o.objects[ev.ID] = v
		}
	case Died, Revived:
		if v, ok := o.objects[ev.ID]; ok {
			v.Dead = ev.Kind == Died
			o.objects[ev.ID] = v
		}
	case Gone:
		delete(o.objects, ev.ID)
	case Took:
		delete(o.objects, ev.Target)
	}
	return id, target, nil
}

// Reference returns a carrier with gen zero when the object is not observed.
func (o *Observations) Reference(id int32) Ref { return o.ref(id) }
