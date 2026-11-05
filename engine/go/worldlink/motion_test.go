// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestCheckpointMovedUsesAppearanceSpeed(t *testing.T) {
	self := world.Self{
		Entity: world.Ref{ID: 1, Gen: 1},
		Char: world.Character{
			ObjectID: 1, Level: 1, CurHP: 10, MaxHP: 10, CurMP: 5, MaxMP: 5, X: 3, Y: 4, Z: 5,
		},
		RunSpeed: 100, WalkSpeed: 70, Running: true,
	}
	npc := world.NPCView{
		Entity: world.Ref{ID: 9, Gen: 2}, ObjectID: 9,
		Template: catalog.NPCTemplate{ID: 9101, Role: catalog.Monster, WalkSpeed: 30, RunSpeed: 60},
		Pos:      world.Point{X: 8, Y: 1, Z: 2}, Running: false,
	}
	scene := world.Scene{
		Begin: world.Marker{Sequence: 1, Seq: 1, Time: 1},
		Self:  self, Moving: true, Destination: world.Point{X: 30, Y: 4, Z: 5},
		Objects: []world.Observation{{
			Entity: npc.Entity, State: world.NPCSeen{NPC: npc},
			Moving: true, Destination: world.Point{X: 80, Y: 1, Z: 2},
		}},
		End: world.Marker{Sequence: 1, Seq: 1, Time: 1, Ready: true},
	}
	view, err := (translator{}).view(scene)
	if err != nil {
		t.Fatal(err)
	}
	got := map[int32]meaning.Event{}
	for _, in := range view.Events {
		if in.Event.Kind == meaning.Moved {
			got[in.Event.ID] = in.Event
		}
	}
	if got[1].Speed != self.RunSpeed || got[1].X != 3 || got[1].ToX != 30 {
		t.Fatalf("self moved = %+v", got[1])
	}
	if got[9].Speed != 30 || got[9].ToX != 80 {
		t.Fatalf("npc moved = %+v", got[9])
	}
	self.Running = false
	scene.Self = self
	view, err = (translator{}).view(scene)
	if err != nil {
		t.Fatal(err)
	}
	for _, in := range view.Events {
		if in.Event.Kind == meaning.Moved && in.Event.ID == 1 && in.Event.Speed != self.WalkSpeed {
			t.Fatalf("walking self moved = %+v", in.Event)
		}
	}
}

func TestLiveMovedCopiesSpeed(t *testing.T) {
	tr := translator{}
	moving, _, err := tr.events(world.Moving{ObjectID: 4, From: world.Point{X: 1, Y: 2, Z: 3}, To: world.Point{X: 9, Y: 8, Z: 7}, Speed: 120}, 0, world.Ref{})
	if err != nil || len(moving) != 1 || moving[0].Event.Speed != 120 || moving[0].Event.Kind != meaning.Moved || moving[0].Event.X != 1 || moving[0].Event.ToX != 9 {
		t.Fatalf("moving=%+v err=%v", moving, err)
	}
	following, _, err := tr.events(world.Following{ObjectID: 4, TargetID: 5, From: world.Point{X: 1}, To: world.Point{X: 9}, Speed: 80}, 0, world.Ref{})
	if err != nil || len(following) != 1 || following[0].Event.Speed != 80 || following[0].Event.Kind != meaning.Moved {
		t.Fatalf("following=%+v err=%v", following, err)
	}
	if _, _, err := tr.events(world.Moving{ObjectID: 4, Speed: -1}, 0, world.Ref{}); !errors.Is(err, errSource) {
		t.Fatalf("negative moving speed err=%v", err)
	}
	if _, _, err := tr.events(world.Following{ObjectID: 4, Speed: -1}, 0, world.Ref{}); !errors.Is(err, errSource) {
		t.Fatalf("negative following speed err=%v", err)
	}
}
