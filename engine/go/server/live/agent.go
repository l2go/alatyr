// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"context"
	"math"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
)

// RunAgent drives s until ctx ends or the session does: it rises after
// death, picks up what lies near, hunts the nearest creature, and between
// hunts talks to the villagers. It decides once per pace and keeps one
// attempt in flight, so the world answers it as it answers anyone else.
func RunAgent(ctx context.Context, s *Session, pace time.Duration) error {
	tick := time.NewTicker(pace)
	defer tick.Stop()
	var a agentMind
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-s.Done():
			return s.Err()
		case <-tick.C:
		}
		if s.Pending() > 0 {
			continue
		}
		act, ok := a.next(s.View(), time.Now())
		if !ok {
			continue
		}
		if err := s.Do(act); err != nil && err != ErrNotReady {
			return err
		}
	}
}

type agentMind struct {
	quarry    int32
	holdUntil time.Time
	talks     int
}

func (a *agentMind) next(v View, now time.Time) (Action, bool) {
	if !v.Ready {
		return Action{}, false
	}
	if v.Self.Dead {
		a.quarry = 0
		return Action{Verb: meaning.Rise}, true
	}
	if d := v.Dialog; d != nil && len(d.Options) > 0 && a.talks%3 == 1 {
		a.talks++
		return Action{Verb: meaning.Say, Option: d.Options[0].ID}, true
	}
	if drop, ok := nearest(v, func(o Object) bool { return o.Role == meaning.RoleDrop }); ok {
		return Action{Verb: meaning.Take, Target: drop.ID}, true
	}
	// A strike runs until its target falls; do not ask again while it does.
	for _, o := range v.Objects {
		if o.ID == a.quarry && !o.Dead && now.Before(a.holdUntil) {
			return Action{}, false
		}
	}
	if a.talks < 2 || a.talks%5 == 4 {
		if folk, ok := nearest(v, func(o Object) bool { return o.Role == "tutor" || o.Role == "provisioner" || o.Role == "folk" }); ok {
			a.talks++
			return Action{Verb: meaning.Talk, Target: folk.ID}, true
		}
	}
	if prey, ok := nearest(v, func(o Object) bool { return o.Role == "creature" && !o.Dead }); ok {
		a.quarry, a.holdUntil = prey.ID, now.Add(20*time.Second)
		a.talks++
		return Action{Verb: meaning.Strike, Target: prey.ID}, true
	}
	return Action{}, false
}

func nearest(v View, match func(Object) bool) (Object, bool) {
	best, found, dist := Object{}, false, math.Inf(1)
	for _, o := range v.Objects {
		if !match(o) {
			continue
		}
		if d := math.Hypot(float64(o.X-v.Self.X), float64(o.Y-v.Self.Y)); d < dist {
			best, found, dist = o, true, d
		}
	}
	return best, found
}
