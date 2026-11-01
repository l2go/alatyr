// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"context"
	"encoding/csv"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func spawnPoint(cat *catalog.Catalog, npc int32) (world.Point, bool) {
	for _, s := range cat.Spawns("WORLD") {
		if s.NPC == npc && s.PeriodOfDay == "ALWAYS" && s.Count >= 1 {
			return world.Point{X: s.Point.X, Y: s.Point.Y, Z: s.Point.Z}, true
		}
	}
	return world.Point{}, false
}

func npcViews(t *testing.T, r *rig, template int32) []world.NPCView {
	t.Helper()
	raw := r.port.Port.(*worldbridge.Client)
	scene, err := r.w.Capture(t.Context(), raw.Owner())
	if err != nil {
		t.Fatal(err)
	}
	var out []world.NPCView
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == template {
			out = append(out, n.NPC)
		}
	}
	if len(out) == 0 {
		t.Fatalf("template %d is not visible", template)
	}
	return out
}

func assertNPCForm(t *testing.T, records []wire.Record, id int32, want string) {
	t.Helper()
	found := false
	for _, record := range records {
		if record.Type != wire.Fact {
			continue
		}
		d, err := meaning.EventValue(record)
		if err != nil {
			t.Fatal(err)
		}
		_, present := wire.Find(record.Fields, wire.FormField)
		if d.Event.ID != id {
			continue
		}
		if d.Event.Kind != meaning.Appeared {
			if present || d.Event.Form != "" {
				t.Fatalf("id %d form on %s", id, d.Event.Kind)
			}
			continue
		}
		if d.Event.Form != want || present != (want != "") {
			t.Fatalf("id %d form %q present %v, want %q", id, d.Event.Form, present, want)
		}
		if present && string(wireField(record, wire.FormField)) != want {
			t.Fatal("dto and wire form differ")
		}
		found = true
	}
	if !found {
		t.Fatalf("missing appearance for %d", id)
	}
}

func npcAtSpawn(t *testing.T, r *rig, template int32, point world.Point) world.NPCView {
	t.Helper()
	var found world.NPCView
	ok := false
	for _, view := range npcViews(t, r, template) {
		if view.Pos.X == point.X && view.Pos.Y == point.Y {
			return view
		}
		found, ok = view, true
	}
	if ok {
		t.Fatalf("template %d nearest visible npc is at %v, spawn %v", template, found.Pos, point)
	}
	t.Fatalf("template %d is not visible at %v", template, point)
	return world.NPCView{}
}

func wireField(record wire.Record, n uint32) []byte {
	v, _ := wire.Find(record.Fields, n)
	return v.Data
}

func observeAt(t *testing.T, r *rig, cat *catalog.Catalog, point world.Point, id int32) []wire.Record {
	t.Helper()
	visitor := readyCharacter()
	visitor.ObjectID = id
	visitor.Account = fmt.Sprintf("guest%d", id)
	visitor.Name = fmt.Sprintf("Guest%d", id)
	visitor.Items = nil
	visitor.Skills = nil
	visitor.X, visitor.Y, visitor.Z = point.X, point.Y, point.Z
	origin := time.Now()
	raw, snap, err := worldbridge.Open(t.Context(), worldbridge.Config{World: r.w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) { return visitor, nil }}, bridge.Identity{Account: visitor.Account, Character: visitor.Name})
	if err != nil {
		t.Fatal(err)
	}
	session, scope, state, err := backend.New(raw, snap, backend.Config{Meaning: r.c, Origin: origin})
	if err != nil {
		t.Fatal(err)
	}
	g := gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) { return scope, state, nil }, origin)
	peer := gatewaytest.Join(t, g, r.c, "one", wire.ModeObserve, false)
	defer func() {
		if err := g.Close(); err != nil {
			t.Error(err)
		}
		peer.Close()
		if err := session.Close(); err != nil {
			t.Error(err)
		}
	}()
	r.sync()
	peer.Drain()
	return append([]wire.Record(nil), peer.Records...)
}

func resumeOwner(t *testing.T, r *rig) []wire.Record {
	t.Helper()
	owner := r.port.Port.(*worldbridge.Client).Owner()
	if _, _, err := r.w.Subscribe(t.Context(), owner); err != nil {
		t.Fatal(err)
	}
	r.sync()
	fresh, freshState, err := r.session.Resume(t.Context())
	if err != nil {
		t.Fatal(err)
	}
	r.mu.Lock()
	r.scope, r.state = fresh, freshState
	r.mu.Unlock()
	r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
	r.sync()
	return r.p.Records
}

func visitForm(t *testing.T, r *rig, cat *catalog.Catalog, char world.Character, template int32, want string, guest int32) {
	t.Helper()
	point, ok := spawnPoint(cat, template)
	if !ok {
		t.Fatalf("template %d has no spawn", template)
	}
	if err := r.w.Teleport(t.Context(), char.ObjectID, point, false); err != nil {
		t.Fatal(err)
	}
	if err := r.w.Appear(t.Context(), char.ObjectID); err != nil {
		t.Fatal(err)
	}
	r.advance(200 * time.Millisecond)
	view := npcAtSpawn(t, r, template, point)
	if view.Template.Form != want {
		t.Fatalf("template %d form %q, want %q", template, view.Template.Form, want)
	}
	assertNPCForm(t, r.p.Records, view.ObjectID, want)
	assertNPCForm(t, observeAt(t, r, cat, point, guest), view.ObjectID, want)
	assertNPCForm(t, resumeOwner(t, r), view.ObjectID, want)
}

func TestNPCFormsMatchAuthoredTemplates(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		r := newRig(t, cat, char, allSources(), nil)
		wants := []struct {
			id   int32
			form string
		}{{30001, "human_tutor"}, {30002, "human_merchant"}, {18003, "field_forager"}, {20120, "field_wolf"}, {20028, "field_spider"}}
		for i, want := range wants {
			tmpl, ok := cat.NPC(want.id)
			if !ok || tmpl.Form != want.form {
				t.Fatalf("catalog %d form %q", want.id, tmpl.Form)
			}
			visitForm(t, r, cat, char, want.id, want.form, int32(20+i))
		}
		raw := r.port.Port.(*worldbridge.Client)
		scene, err := r.w.Capture(t.Context(), raw.Owner())
		if err != nil {
			t.Fatal(err)
		}
		if err := checkNPCForms(r.p.Records, scene); err != nil {
			t.Fatal(err)
		}
	})
}

func TestNPCFormStaysOpaqueWhenTheTemplateChanges(t *testing.T) {
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
	idCol, formCol := -1, -1
	for i, name := range rows[0] {
		switch name {
		case "id":
			idCol = i
		case "client_class":
			formCol = i
		}
	}
	if idCol < 0 || formCol < 0 {
		t.Fatal("npc columns")
	}
	for _, row := range rows[1:] {
		switch row[idCol] {
		case "30001":
			row[formCol] = "marked_tutor"
		case "30002":
			row[formCol] = ""
		}
	}
	f, err = os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	w := csv.NewWriter(f)
	w.WriteAll(rows)
	err = w.Error()
	closeErr = f.Close()
	if err != nil || closeErr != nil {
		t.Fatal(err, closeErr)
	}
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		char := readyCharacter()
		r := newRig(t, cat, char, allSources(), nil)
		visitForm(t, r, cat, char, 30001, "marked_tutor", 21)
		visitForm(t, r, cat, char, 30002, "", 22)
	})
}
