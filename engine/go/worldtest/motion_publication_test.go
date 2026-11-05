// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestParticipantMovedCarriesGaitSpeed(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		runSpeed := appearanceSpeed(t, r.p.Records, 1)
		if runSpeed <= 0 {
			t.Fatalf("appearance speed = %d", runSpeed)
		}
		from := len(r.p.Records)
		walk(t, r, 1, -70557, 257883, -3104)
		moved := motionAfter(t, r.p.Records[from:], 1, meaning.Moved)
		if speed := fieldSpeed(t, moved); speed != runSpeed {
			t.Fatalf("run moved speed = %d, appearance %d", speed, runSpeed)
		}
		if err := r.w.SetRunning(t.Context(), 1, false); err != nil {
			t.Fatal(err)
		}
		r.sync()
		walkSpeed := appearanceSpeed(t, r.p.Records, 1)
		if walkSpeed <= 0 || walkSpeed >= runSpeed {
			t.Fatalf("walk appearance = %d, run %d", walkSpeed, runSpeed)
		}
		from = len(r.p.Records)
		walk(t, r, 2, -70057, 257883, -3104)
		moved = motionAfter(t, r.p.Records[from:], 1, meaning.Moved)
		if speed := fieldSpeed(t, moved); speed != walkSpeed {
			t.Fatalf("walk moved speed = %d, appearance %d", speed, walkSpeed)
		}
	})
}

func TestParticipantArrivalEndsStopped(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		self := appearanceOf(t, r.p.Records, 1)
		from := len(r.p.Records)
		walk(t, r, 1, self.X, self.Y, self.Z)
		var stopped, moved int
		for _, rec := range r.p.Records[from:] {
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
			switch d.Event.Kind {
			case meaning.Stopped:
				stopped++
				if d.Event.X != self.X || d.Event.Y != self.Y || d.Event.Z != self.Z {
					t.Fatalf("stopped at %+v, want appearance %+v", d.Event, self)
				}
			case meaning.Moved:
				moved++
			}
		}
		if stopped != 1 || moved != 0 {
			t.Fatalf("arrival facts stopped=%d moved=%d", stopped, moved)
		}
	})
}

func TestObserverCheckpointMovedCarriesSpeed(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		walk(t, r, 1, -70557, 257883, -3104)
		watch := gatewaytest.Join(t, r.g, r.c, "one", wire.ModeObserve, false)
		var appeared, moved meaning.Event
		sawMoved := false
		for _, rec := range watch.Records {
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
			switch d.Event.Kind {
			case meaning.Appeared:
				appeared = d.Event
			case meaning.Moved:
				sawMoved = true
				moved = d.Event
				if speed := fieldSpeed(t, rec); speed != appeared.Speed || speed <= 0 {
					t.Fatalf("checkpoint moved speed = %d, appearance %d", speed, appeared.Speed)
				}
			}
		}
		if !sawMoved || appeared.Speed <= 0 {
			t.Fatalf("checkpoint appearance=%+v moved=%v %+v", appeared, sawMoved, moved)
		}
	})
}

func walk(t *testing.T, r *rig, n uint64, x, y, z int32) {
	t.Helper()
	r.p.Send(r.intent(n, 1, wire.At(11, true, x, y, z)))
	r.p.Result(n, wire.StageSent)
	r.sync()
}

func appearanceOf(t *testing.T, records []wire.Record, id int32) meaning.Event {
	t.Helper()
	var found meaning.Event
	ok := false
	for _, rec := range records {
		if rec.Type != wire.Fact {
			continue
		}
		d, err := meaning.EventValue(rec)
		if err != nil {
			t.Fatal(err)
		}
		if d.Event.ID == id && d.Event.Kind == meaning.Appeared {
			found = d.Event
			ok = true
		}
	}
	if !ok {
		t.Fatalf("no appearance for %d", id)
	}
	return found
}

func appearanceSpeed(t *testing.T, records []wire.Record, id int32) int32 {
	t.Helper()
	return appearanceOf(t, records, id).Speed
}

func motionAfter(t *testing.T, records []wire.Record, id int32, kind string) wire.Record {
	t.Helper()
	var found wire.Record
	ok := false
	for _, rec := range records {
		if rec.Type != wire.Fact {
			continue
		}
		d, err := meaning.EventValue(rec)
		if err != nil {
			t.Fatal(err)
		}
		if d.Event.ID == id && d.Event.Kind == kind {
			found = rec
			ok = true
		}
	}
	if !ok {
		t.Fatalf("no %s for %d", kind, id)
	}
	return found
}

func fieldSpeed(t *testing.T, rec wire.Record) int32 {
	t.Helper()
	d, err := meaning.EventValue(rec)
	if err != nil {
		t.Fatal(err)
	}
	raw, ok := wire.Find(rec.Fields, 15)
	if !ok || int32(raw.U) != d.Event.Speed {
		t.Fatalf("field 15 = %+v, decoded speed %d", raw, d.Event.Speed)
	}
	return d.Event.Speed
}
