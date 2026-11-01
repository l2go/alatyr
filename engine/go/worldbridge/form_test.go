// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"encoding/csv"
	"errors"
	"os"
	"path/filepath"
	"strings"
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
}

func TestCatalogAcceptsTheWireFormLabels(t *testing.T) {
	tokens := []string{
		"", "a", "field_wolf", "a1", "a_b", strings.Repeat("a", 32),
		"A", "Wolf", "1a", "_a", "a-b", "a b", strings.Repeat("a", 33), "field_wolf!",
	}
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(root, "catalog", "npc_template.csv")
	f, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	rows, err := csv.NewReader(f).ReadAll()
	closeErr := f.Close()
	if err != nil || closeErr != nil {
		t.Fatal(err, closeErr)
	}
	form := -1
	for i, name := range rows[0] {
		if name == "client_class" {
			form = i
		}
	}
	if form < 0 || rows[1][0] != "30001" {
		t.Fatal("npc template header")
	}
	for _, token := range tokens {
		rows[1][form] = token
		f, err := os.Create(path)
		if err != nil {
			t.Fatal(err)
		}
		w := csv.NewWriter(f)
		w.WriteAll(rows)
		err = w.Error()
		closeErr := f.Close()
		if err != nil || closeErr != nil {
			t.Fatal(err, closeErr)
		}
		cat, err := catalog.Load(root)
		// An empty cell is not a label. The catalog stores no form, and the
		// wire rejects it.
		if token == "" {
			if err != nil || wire.ValidForm(token) {
				t.Fatalf("empty cell: load %v wire %v", err, wire.ValidForm(token))
			}
			got, ok := cat.NPC(30001)
			if !ok || got.Form != "" {
				t.Fatalf("empty form %q present %v", got.Form, ok)
			}
			continue
		}
		if accepted := err == nil; accepted != wire.ValidForm(token) {
			t.Fatalf("%q catalog %v wire %v load %v", token, accepted, wire.ValidForm(token), err)
		}
		if err != nil {
			if !strings.Contains(err.Error(), "form") {
				t.Fatalf("%q: %v", token, err)
			}
			continue
		}
		got, ok := cat.NPC(30001)
		if !ok || got.Form != token {
			t.Fatalf("%q stored %q present %v", token, got.Form, ok)
		}
	}
}
