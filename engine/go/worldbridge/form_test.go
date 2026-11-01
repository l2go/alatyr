// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestNPCFormComesOnlyFromTheTemplate(t *testing.T) {
	tr, s := fixture()
	n := s.Objects[0].State.(world.NPCSeen).NPC
	n.Template.Name = "Wattlebank Wolf"
	n.Template.Form = "marked_tutor"
	f, err := tr.npc(n, s.Self.Char.ClassID)
	if err != nil {
		t.Fatal(err)
	}
	got, yes := wire.Find(f.fields, wire.FormField)
	if !yes || string(got.Data) != "marked_tutor" || got.Data[0] == 0 {
		t.Fatalf("form = %q present %v", got.Data, yes)
	}
	for _, field := range f.fields {
		if field.Number == 11 && string(field.Value.Data) == "marked_tutor" {
			t.Fatal("form replaced the role")
		}
	}
	n.Template.Form = ""
	f, err = tr.npc(n, s.Self.Char.ClassID)
	if err != nil {
		t.Fatal(err)
	}
	if _, yes := wire.Find(f.fields, wire.FormField); yes {
		t.Fatal("unset form became a field")
	}
	n.Template.Form = "Wolf"
	if _, err := tr.npc(n, s.Self.Char.ClassID); !errors.Is(err, errSource) {
		t.Fatalf("illegal token: %v", err)
	}
	self, err := tr.self(s.Self)
	if err != nil {
		t.Fatal(err)
	}
	for _, fact := range self {
		if _, yes := wire.Find(fact.fields, wire.FormField); yes {
			t.Fatal("player received a form")
		}
	}
	drop, err := tr.dropped(world.ItemDropped{ObjectID: 9, TemplateID: 201, Count: 1})
	if err != nil {
		t.Fatal(err)
	}
	if _, yes := wire.Find(drop.fields, wire.FormField); yes {
		t.Fatal("drop received a form")
	}
	blank := catalog.NPCTemplate{ID: 100, Role: catalog.Trainer, Name: "Teacher", WalkSpeed: 50, RunSpeed: 100}
	if blank.Form != "" {
		t.Fatal("fixture template invented a form")
	}
}
