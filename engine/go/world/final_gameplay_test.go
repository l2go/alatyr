// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"math"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestFinalCheckpointIncludesHuntEquipmentHealingAndHiddenItems(t *testing.T) {
	cat, err := catalog.Load(repositoryWorld())
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		w := New(cat, &testStore{}, WithReservedIDs([]int32{1, 2, 10, 11, 12, 13, 14}))
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		c := Character{ObjectID: 1, Account: "one", Name: "One", ClassID: 0, Level: 2, Exp: 68, SP: 100, CurHP: 20.25, CurMP: 50.25, CurCP: 10.75, X: -71057, Y: 257883, Z: -3104, Items: []Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 12, TemplateID: 1060, Count: 3, Slot: -1}, {ObjectID: 13, TemplateID: 23, Count: 1, Slot: -1}, {ObjectID: 14, TemplateID: 9999001, Count: 7, Slot: -1}}}
		one := enterTest(t, w, ctx, c)
		other := c.Clone()
		other.ObjectID = 2
		other.Account = "two"
		other.Name = "Two"
		other.Items = nil
		two := enterTest(t, w, ctx, other)
		use := func(id int32) {
			t.Helper()
			scene := captureTest(t, w, ctx, one.Self.Entity)
			i := slices.IndexFunc(scene.Inventory, func(row Carried) bool { return row.Item.ObjectID == id })
			if i < 0 {
				t.Fatalf("missing carried row %d", id)
			}
			result := executeTest(t, w, ctx, Request{Owner: one.Self.Entity, Verb: VerbUseItem, Target: scene.Inventory[i].Entity})
			if result.Outcome.Stage == StageRefused {
				t.Fatalf("use %d: %+v", id, result.Outcome)
			}
		}
		use(13)
		use(12)
		time.Sleep(500 * time.Millisecond)
		synctest.Wait()
		if _, err := w.SetParty(ctx, 0, []Ref{one.Self.Entity, two.Self.Entity}, LootRoundRobin); err != nil {
			t.Fatal(err)
		}
		npcID := objectOf(t, w, ctx, 18003)
		var npcRef Ref
		if err := w.do(ctx, func(s *state) {
			n := s.npcs[npcID]
			n.pos = s.players[1].pos
			n.hp = 100
			n.tmpl.MaxHP = 100
			n.tmpl.RewardExp = 100
			n.tmpl.RewardSP = 20
			npcRef = n.ref()
		}); err != nil {
			t.Fatal(err)
		}
		harmTest(t, w, ctx, one.Self.Entity, npcRef, 1000)
		before, err := w.Snapshot(ctx, 1)
		if err != nil {
			t.Fatal(err)
		}
		if before.Char.Exp != 118 || before.Char.SP != 110 || before.Char.CurHP != 92.25 {
			t.Fatalf("gameplay did not commit before shutdown: %+v", before.Char)
		}
		potion := slices.IndexFunc(before.Char.Items, func(it Item) bool { return it.TemplateID == 1060 })
		coat := slices.IndexFunc(before.Char.Items, func(it Item) bool { return it.TemplateID == 23 })
		if potion < 0 || before.Char.Items[potion].Count != 2 || coat < 0 || before.Char.Items[coat].Slot != catalog.SlotChest {
			t.Fatal("healing/equipment did not commit")
		}
		cancel()
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		final, err := w.Final(t.Context())
		if err != nil || len(final) != 2 {
			t.Fatalf("Final: %+v/%v", final, err)
		}
		i := slices.IndexFunc(final, func(p Checkpoint) bool { return p.Owner == one.Self.Entity })
		if i < 0 {
			t.Fatal("owner lost")
		}
		want := before.Char.Clone()
		want.CurHP = math.Round(want.CurHP)
		want.CurMP = math.Round(want.CurMP)
		want.CurCP = math.Round(want.CurCP)
		want.Items = append(want.Items, c.Items[4])
		want.LastAccess = final[i].Character.LastAccess
		if want.LastAccess == 0 || !reflect.DeepEqual(final[i].Character, want) {
			t.Fatalf("Final lost gameplay/hidden state:\ngot %+v\nwant %+v", final[i].Character, want)
		}
		final[i].Character.Items[len(final[i].Character.Items)-1].Count = 999
		again, err := w.Final(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		j := slices.IndexFunc(again, func(p Checkpoint) bool { return p.Owner == one.Self.Entity })
		if j < 0 || !reflect.DeepEqual(again[j].Character, want) {
			t.Fatal("Final aliases a caller, including hidden items")
		}
	})
}
