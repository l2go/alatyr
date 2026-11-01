// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"errors"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestNPCFormPresenceReplacementAndSnapshot(t *testing.T) {
	if eventStorage(Event{Form: "field_wolf"}) != eventStorage(Event{})+len("field_wolf") {
		t.Fatal("form bytes missing from storage")
	}
	s := newState(t, testConfig(), Policy{})
	if err := apply(t, s, Event{Kind: Appeared, ID: 7, Role: "creature", Form: "field_wolf"}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Moved, ID: 7}); err != nil {
		t.Fatal(err)
	}
	snapshot, err := s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	d, err := EventValue(snapshot.Facts[0])
	if err != nil {
		t.Fatal(err)
	}
	if d.Event.Kind != Appeared || d.Event.Form != "field_wolf" {
		t.Fatalf("snapshot form = %q kind %s", d.Event.Form, d.Event.Kind)
	}
	// A later full description replaces the label. Omission means no form.
	if err := apply(t, s, Event{Kind: Appeared, ID: 7, Role: "creature"}); err != nil {
		t.Fatal(err)
	}
	snapshot, err = s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	d, err = EventValue(snapshot.Facts[0])
	if err != nil || d.Event.Form != "" {
		t.Fatal("omitted form was retained")
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 7, Role: "creature", Form: "marked_tutor"}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Gone, ID: 7}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 7, Role: "creature"}); err != nil {
		t.Fatal(err)
	}
	snapshot, err = s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	d, err = EventValue(snapshot.Facts[0])
	if err != nil || d.Event.Form != "" {
		t.Fatal("form leaked into a later observation lifetime")
	}
}

func TestNPCFormRejectsWrongSubjectsAndTokens(t *testing.T) {
	ctx := FactContext{Seq: 1, Epoch: 1}
	for _, e := range []Event{
		{Kind: Appeared, Role: RolePlayer, Form: "field_wolf"},
		{Kind: Dropped, Role: RoleDrop, Form: "field_wolf"},
		{Kind: Moved, Role: "creature", Form: "field_wolf"},
		{Kind: Vitals, Role: "creature", Form: "field_wolf"},
		{Kind: Appeared, Role: "creature", Form: ""},
		{Kind: Appeared, Role: "creature", Form: "Wolf"},
		{Kind: Appeared, Role: "creature", Form: strings.Repeat("a", 33)},
		{Kind: Appeared, Form: "field_wolf"},
	} {
		if e.Form == "" {
			r, err := EventRecord(e, ctx)
			if err != nil {
				t.Fatal(err)
			}
			if _, yes := wire.Find(r.Fields, wire.FormField); yes {
				t.Fatal("empty form became a field")
			}
			continue
		}
		if _, err := EventRecord(e, ctx); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("%s/%s: %v", e.Kind, e.Role, err)
		}
	}
	c := codec(t)
	player, err := EventRecord(Event{Kind: Appeared, Role: RolePlayer}, ctx)
	if err != nil {
		t.Fatal(err)
	}
	drop, err := EventRecord(Event{Kind: Dropped, Role: RoleDrop}, ctx)
	if err != nil {
		t.Fatal(err)
	}
	npc, err := EventRecord(Event{Kind: Appeared, Role: "creature"}, ctx)
	if err != nil {
		t.Fatal(err)
	}
	wc, err := wire.New(testConfig().Wire, Registry(testConfig()))
	if err != nil {
		t.Fatal(err)
	}
	for _, rec := range []wire.Record{player, drop} {
		rec.Fields = append(rec.Fields, wire.Data(wire.FormField, false, "field_wolf"))
		if _, err := EventValue(rec); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("parse: %v", err)
		}
		if _, err := c.Encode([]wire.Record{rec}); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("encode: %v", err)
		}
		b, err := wc.Encode([]wire.Record{rec})
		if err != nil {
			t.Fatal(err)
		}
		if _, err := c.Decode(b); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("decode: %v", err)
		}
	}
	for _, token := range []string{"", "Wolf", strings.Repeat("a", 33)} {
		bad := npc
		bad.Fields = append(append([]wire.Field{}, npc.Fields...), wire.Data(wire.FormField, false, token))
		if _, err := c.Encode([]wire.Record{bad}); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("encode %q: %v", token, err)
		}
		if _, err := EventValue(bad); !errors.Is(err, wire.ErrFrame) {
			t.Fatalf("parse %q: %v", token, err)
		}
	}
	s := newState(t, testConfig(), Policy{})
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Role: RolePlayer, Form: "field_wolf"}); err == nil {
		t.Fatal("player acquired a form")
	}
	if err := apply(t, s, Event{Kind: Dropped, ID: 4, Role: RoleDrop, Form: "field_wolf"}); err == nil {
		t.Fatal("drop acquired a form")
	}
}
