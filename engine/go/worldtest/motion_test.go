// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"testing"
	"time"
)

func TestRepeatedDescriptionRepublishesCurrentMotion(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		r.p.Send(r.intent(1, 1, wire.At(11, true, -70557, 257883, -3104)))
		r.p.Result(1, wire.StageSent)
		r.advance(200 * time.Millisecond)
		if err := r.w.SetRunning(t.Context(), 1, false); err != nil {
			t.Fatal(err)
		}
		r.advance(300 * time.Millisecond)
		latestAppearance, latestMotion := -1, -1
		var at, moving meaning.Event
		for i, rec := range r.p.Records {
			if rec.Type != wire.Fact {
				continue
			}
			d, err := meaning.EventValue(rec)
			if err != nil {
				t.Fatal(err)
			}
			if d.Event.ID != 1 {
				continue
			}
			if d.Event.Kind == meaning.Appeared {
				latestAppearance = i
				at = d.Event
			}
			if d.Event.Kind == meaning.Moved {
				latestMotion = i
				moving = d.Event
			}
		}
		if latestAppearance < 0 || latestMotion <= latestAppearance {
			t.Fatalf("description/motion order: %d/%d", latestAppearance, latestMotion)
		}
		if at.X <= -71057 || moving.X != at.X || moving.Y != at.Y || moving.Z != at.Z {
			t.Fatalf("refreshed source: appeared=%+v motion=%+v", at, moving)
		}
		watch := gatewaytest.Join(t, r.g, r.c, "one", wire.ModeObserve, false)
		captured := false
		for _, rec := range watch.Records {
			if rec.Type != wire.Fact {
				continue
			}
			d, err := meaning.EventValue(rec)
			if err != nil {
				t.Fatal(err)
			}
			if d.Event.ID == 1 && d.Event.Kind == meaning.Moved {
				captured = true
				if d.Event.X != at.X || d.Event.Y != at.Y || d.Event.Z != at.Z {
					t.Fatal("checkpoint restored old motion origin")
				}
			}
		}
		if !captured {
			t.Fatal("checkpoint lost current movement")
		}
	})
}
