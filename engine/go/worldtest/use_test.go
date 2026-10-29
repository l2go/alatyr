// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"reflect"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func healingRequest(t *testing.T, r *rig) world.Request {
	t.Helper()
	owner := r.port.Port.(*worldbridge.Client).Owner()
	for _, row := range inventory(t, r) {
		if row.Item.TemplateID == 1060 {
			return world.Request{Owner: owner, Verb: world.VerbUseItem, Target: row.Entity}
		}
	}
	t.Fatal("healing row absent")
	return world.Request{}
}

func TestHealingItemLaunchIsAtomicAndLifeFenced(t *testing.T) {
	for _, branch := range []string{"launch", "death-revival", "teleport", "deadline", "close"} {
		t.Run(branch, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.CurHP = 20
				char.Items[2].Count = 1
				r := newRig(t, cat, char, allSources(), nil)
				req := healingRequest(t, r)
				if branch == "deadline" {
					scene, err := r.w.Capture(t.Context(), req.Owner)
					if err != nil {
						t.Fatal(err)
					}
					req.Deadline = scene.End.Time + world.Time(200*time.Millisecond)
				}
				c, err := r.w.Execute(t.Context(), req)
				if err != nil || c.Outcome.Stage == world.StageRefused {
					t.Fatalf("use: %+v/%v", c, err)
				}
				r.advance(100 * time.Millisecond)
				before, err := r.w.Snapshot(t.Context(), char.ObjectID)
				if err != nil {
					t.Fatal(err)
				}
				if before.Char.CurHP != 20 || quantity(inventory(t, r), 1060) != 1 {
					t.Fatal("effect or cost happened before launch")
				}
				switch branch {
				case "death-revival":
					if err := r.w.Harm(t.Context(), world.Ref{}, req.Owner, 10000); err != nil {
						t.Fatal(err)
					}
					if err := r.w.Teleport(t.Context(), char.ObjectID, world.Point{X: char.X, Y: char.Y, Z: char.Z}, true); err != nil {
						t.Fatal(err)
					}
					if err := r.w.Appear(t.Context(), char.ObjectID); err != nil {
						t.Fatal(err)
					}
				case "teleport":
					if err := r.w.Teleport(t.Context(), char.ObjectID, world.Point{X: char.X + 10, Y: char.Y, Z: char.Z}, false); err != nil {
						t.Fatal(err)
					}
					if err := r.w.Appear(t.Context(), char.ObjectID); err != nil {
						t.Fatal(err)
					}
				case "close":
					if err := r.session.Close(); err != nil {
						t.Fatal(err)
					}
				}
				r.advance(500 * time.Millisecond)
				if branch == "close" {
					saved, err := r.store.Characters(t.Context(), char.Account)
					if err != nil || len(saved) != 1 {
						t.Fatalf("save: %v", err)
					}
					if saved[0].CurHP != 20 || !reflect.DeepEqual(saved[0].Items, char.Items) {
						t.Fatal("pending item changed Close snapshot")
					}
					return
				}
				after, err := r.w.Snapshot(t.Context(), char.ObjectID)
				if err != nil {
					t.Fatal(err)
				}
				want := int64(1)
				if branch == "launch" {
					want = 0
					if after.Char.CurHP != 92 {
						t.Fatalf("HP=%g, want 92", after.Char.CurHP)
					}
				}
				if quantity(inventory(t, r), 1060) != want {
					t.Fatalf("item count after %s: %+v", branch, after.Char.Items)
				}
				if branch == "launch" {
					c, err = r.w.Execute(t.Context(), req)
					if err != nil || c.Outcome.Stage != world.StageRefused || c.Outcome.Reason != world.ReasonStale {
						t.Fatal("consumed row retained authority")
					}
				}
			})
		})
	}
}

func TestHealingReuseAndAdmissionBudget(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.CurHP = 20
		r := newRig(t, cat, char, allSources(), nil)
		req := healingRequest(t, r)
		scene, err := r.w.Capture(t.Context(), req.Owner)
		if err != nil {
			t.Fatal(err)
		}
		req.AdmissionDeadline = scene.End.Time + world.Time(100*time.Millisecond)
		if _, err := r.w.Execute(t.Context(), req); err != nil {
			t.Fatal(err)
		}
		c, err := r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageRefused {
			t.Fatal("reuse admitted a second effect")
		}
		r.advance(500 * time.Millisecond)
		if quantity(inventory(t, r), 1060) != 2 {
			t.Fatal("admission budget revoked launched effect or reuse consumed twice")
		}
		before := inventory(t, r)
		c, err = r.w.Execute(t.Context(), req)
		if err != nil || c.Outcome.Stage != world.StageExpired || !reflect.DeepEqual(before, inventory(t, r)) {
			t.Fatal("expired admission changed inventory")
		}
	})
}
