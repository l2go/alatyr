// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"testing"
	"testing/synctest"
)

func TestDepartureForgetsObjectsInIDOrder(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, syntheticCatalog(t))
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		var want []int32
		for _, o := range scene.Objects {
			want = append(want, o.Entity.ID)
		}
		slices.Sort(want)
		if len(want) < 2 {
			t.Fatal("fixture needs multiple visible objects")
		}
		drainEvents(entry.Events)
		if err := w.Leave(ctx, entry.Self.Entity); err != nil {
			t.Fatal(err)
		}
		var got []int32
		for _, e := range drainEvents(entry.Events) {
			if v, ok := e.(ObjectGone); ok {
				got = append(got, v.ObjectID)
			}
		}
		if !slices.Equal(got, want) {
			t.Fatalf("forgotten objects: got %v, want %v", got, want)
		}
	})
}
