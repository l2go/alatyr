// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestAppearancePresenceOwnershipAndProjection(t *testing.T) {
	s := newState(t, testConfig(), Policy{})
	a := &Appearance{Race: wire.RaceHuman, Sex: wire.SexFemale, Branch: wire.BranchMystic}
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer, Appearance: a}); err != nil {
		t.Fatal(err)
	}
	a.Branch = wire.BranchFighter
	// A partial refresh is no report, not an erasure of immutable metadata.
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer}); err != nil {
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
	if d.Event.Appearance == nil || d.Event.Appearance.Branch != wire.BranchMystic {
		t.Fatal("input alias or absent report erased appearance")
	}
	d.Event.Appearance.Branch = wire.BranchFighter
	snapshot, err = s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	d, err = EventValue(snapshot.Facts[0])
	if err != nil {
		t.Fatal(err)
	}
	if d.Event.Appearance.Branch != wire.BranchMystic {
		t.Fatal("snapshot alias changed appearance")
	}
	unknown := &Appearance{Race: wire.RaceHuman, Sex: wire.SexFemale, Branch: wire.BranchUnknown}
	if err := apply(t, s, Event{Kind: Vitals, ID: 1, Appearance: unknown, Stats: &Stats{HP: Number(0)}}); err != nil {
		t.Fatal(err)
	}
	snapshot, err = s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	for _, r := range snapshot.Facts {
		d, err := EventValue(r)
		if err != nil {
			t.Fatal(err)
		}
		if d.Event.Kind == Appeared || d.Event.Kind == Vitals {
			if d.Event.Appearance == nil || d.Event.Appearance.Branch != wire.BranchUnknown {
				t.Fatal("explicit unknown became absent or guessed")
			}
		}
	}
	if err := apply(t, s, Event{Kind: Gone, ID: 1}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer}); err != nil {
		t.Fatal(err)
	}
	snapshot, err = s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	d, err = EventValue(snapshot.Facts[0])
	if err != nil {
		t.Fatal(err)
	}
	if d.Event.Appearance != nil {
		t.Fatal("appearance leaked into a later observation lifetime")
	}
}

func TestAppearanceRejectsNPCAndUnrelatedFacts(t *testing.T) {
	a := &Appearance{Race: wire.RaceHuman, Sex: wire.SexMale, Branch: wire.BranchFighter}
	ctx := FactContext{Seq: 1, Epoch: 1}
	for _, e := range []Event{{Kind: Appeared, Role: "creature", Appearance: a}, {Kind: Moved, Role: RolePlayer, Appearance: a}, {Kind: Appeared, Role: RolePlayer, Appearance: &Appearance{Race: 99}}} {
		if _, err := EventRecord(e, ctx); err == nil {
			t.Fatal("invalid appearance fact accepted")
		}
	}
	s := newState(t, testConfig(), Policy{})
	if err := apply(t, s, Event{Kind: Appeared, ID: 7, Role: "creature"}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Vitals, ID: 7, Appearance: a}); err == nil {
		t.Fatal("NPC acquired player appearance")
	}
	// Direct DTO decoding also preserves the distinction from an empty object.
	r, err := EventRecord(Event{Kind: Appeared, Role: RolePlayer}, ctx)
	if err != nil {
		t.Fatal(err)
	}
	r.Fields = append(r.Fields, wire.Obj(wire.AppearanceField, false, nil))
	if _, err := EventValue(r); err == nil {
		t.Fatal("empty appearance became unknown")
	}
}
