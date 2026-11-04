// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

type canceledSkillStore struct {
	*memstore.Store
	started chan struct{}
}

func (s *canceledSkillStore) PutSkill(ctx context.Context, _ int32, _ world.Skill) error {
	close(s.started)
	<-ctx.Done()
	return ctx.Err()
}

func TestCloseCancelsPersistenceWithoutLosingCommittedLearning(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		char := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
		store := &canceledSkillStore{Store: memstore.New(), started: make(chan struct{})}
		w := world.New(cat, store, world.WithReservedIDs([]int32{1}))
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		t.Cleanup(func() {
			cancel()
			if err := <-done; err != nil {
				t.Error(err)
			}
		})
		cfg := gatewaytest.Config()
		source, dictionary, err := Open(t.Context(), Config{World: w, Catalog: cat, Meaning: cfg, Origin: time.Now(), Load: func(context.Context, Identity) (world.Character, error) { return char, nil }}, Identity{Account: char.Account, Character: char.Name})
		if err != nil {
			t.Fatal(err)
		}
		scene, err := w.Capture(t.Context(), source.Owner())
		if err != nil {
			t.Fatal(err)
		}
		var tutor world.Ref
		for _, o := range scene.Objects {
			if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == 30001 {
				tutor = o.Entity
			}
		}
		if c, err := w.Execute(t.Context(), world.Request{Owner: source.Owner(), Verb: world.VerbTalk, Target: tutor}); err != nil || c.Outcome.Stage != world.StageDone {
			t.Fatalf("talk=%+v/%v", c.Outcome, err)
		}
		if err := w.Bypass(t.Context(), char.ObjectID, fmt.Sprintf("npc_%d_SkillList", tutor.ID)); err != nil {
			t.Fatal(err)
		}
		snapshot, err := dictionary.Snapshot(0)
		if err != nil {
			t.Fatal(err)
		}
		var intent wire.Record
		for _, record := range snapshot.Facts {
			event, err := meaning.EventValue(record)
			if err != nil {
				t.Fatal(err)
			}
			if event.Event.Kind != meaning.Practice {
				continue
			}
			for i, row := range event.Event.Goods {
				if row.ID == 257 {
					intent = wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 13), wire.U(3, true, 1), wire.S(14, true, int64(event.Event.List)), wire.U(15, true, event.Context.Offers[i])}}
				}
			}
		}
		action, reason := dictionary.Plan(intent, 0)
		if reason != wire.ReasonNone || action == nil {
			t.Fatalf("learn plan=%v/%v", action, reason)
		}
		step := action.Steps[0]
		step.N, step.Role, step.DeadlineMS = 1, gateway.StepEffect, 5000
		writeDone := make(chan error, 1)
		go func() { writeDone <- source.Write(context.Background(), step) }()
		<-store.started
		closeErr := source.Close()
		if err := <-writeDone; err != nil {
			t.Fatalf("in-memory Done lost to canceled save: %v", err)
		}
		if source.Close() != closeErr {
			t.Fatal("close result changed")
		}
		if !source.mailbox.Closed() {
			t.Fatal("close retained observer")
		}
		// Closing may race the store's cancellation acknowledgement. The host's
		// fenced retry works whether Close observed ErrSaving or the save error.
		if closeErr != nil && !errors.Is(closeErr, world.ErrSaving) && !errors.Is(closeErr, context.Canceled) {
			t.Fatalf("close=%v", closeErr)
		}
		if err := w.Leave(t.Context(), source.Owner()); err != nil {
			t.Fatal(err)
		}
		chars, err := store.Characters(t.Context(), char.Account)
		if err != nil || len(chars) != 1 || chars[0].SP >= char.SP {
			t.Fatalf("saved learning=%+v/%v", chars, err)
		}
		learned := false
		for _, skill := range chars[0].Skills {
			learned = learned || skill.ID == 257 && skill.Level == 1
		}
		if !learned {
			t.Fatal("fenced retry lost committed skill")
		}
	})
}
