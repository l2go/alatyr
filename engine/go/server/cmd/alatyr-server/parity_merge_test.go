// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestRuntimeClassifiesActualHuntTargetsAndSavesPartyRewards(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cfg := configTest()
	admissions, err := cfg.validate()
	if err != nil {
		t.Fatal(err)
	}
	store := &hostMemoryStore{}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, Exp: 68, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -70357, Y: 256863, Z: -3104, Skills: []world.Skill{{ID: 3, Level: 1}}}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	characters := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
	w := world.New(cat, characters, world.WithReservedIDs([]int32{1, 2}), world.WithIDRange(1<<28, 1<<29))
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	t.Cleanup(func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	})
	r := &runtime{cfg: cfg, store: store, characters: characters, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: backend.Config{Meaning: limits(1), Origin: time.Now(), Policy: meaning.Policy{Classify: classify}}}
	port, state, err := r.open(t.Context(), "one")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := port.Close(); err != nil {
			t.Error(err)
		}
	})
	snap, err := state.Snapshot(0)
	if err != nil || !snap.Ready {
		t.Fatalf("snapshot: %v", err)
	}
	var target meaning.Ref
	for _, f := range snap.Facts {
		d, err := meaning.EventValue(f)
		if err != nil {
			t.Fatal(err)
		}
		if d.Event.Role == "creature" {
			target = d.Context.Ref
			break
		}
	}
	if target.ID == 0 {
		t.Fatal("actual worldbridge checkpoint has no creature")
	}
	for i, kind := range []string{meaning.Strike, meaning.Cast} {
		rec, err := meaning.CommandRecord(meaning.Command{Kind: kind, Target: target.ID, Item: 3}, meaning.CommandContext{N: uint64(i + 1), Target: target, SkillLevel: 1})
		if err != nil {
			t.Fatal(err)
		}
		if action, reason := state.Plan(rec, 0); reason != wire.ReasonNone || action == nil {
			t.Fatalf("server policy refused actual %s: %v", kind, reason)
		}
	}
	owner := r.ports["one"].port.Owner()
	other := c.Clone()
	other.ObjectID = 2
	other.Account = "other"
	other.Name = "Other"
	entry, err := w.Enter(t.Context(), other)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := w.SetParty(t.Context(), 0, []world.Ref{owner, entry.Self.Entity}, world.LootRoundRobin); err != nil {
		t.Fatal(err)
	}
	scene, err := w.Capture(t.Context(), owner)
	if err != nil {
		t.Fatal(err)
	}
	var npc world.Ref
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == 18003 {
			npc = o.Entity
			break
		}
	}
	if npc.ID == 0 {
		t.Fatal("authored hunt target absent")
	}
	if err := w.Harm(t.Context(), owner, npc, 1000000); err != nil {
		t.Fatal(err)
	}
	after, err := w.Snapshot(t.Context(), 1)
	if err != nil || after.Char.Exp <= 68 || after.Char.SP <= 100 {
		t.Fatalf("party reward absent: %+v/%v", after.Char, err)
	}
	if err := port.Close(); err != nil {
		t.Fatal(err)
	}
	saved, err := store.Character(t.Context(), c.Account, c.Name)
	if err != nil || saved.Exp != after.Char.Exp || saved.SP != after.Char.SP {
		t.Fatalf("server lost party reward: %+v/%v", saved, err)
	}
	if err := w.Leave(t.Context(), entry.Self.Entity); err != nil {
		t.Fatal(err)
	}
}
