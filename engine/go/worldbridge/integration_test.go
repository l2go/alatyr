// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"path/filepath"
	"testing"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func TestAuthoredWorldCommitMatchesRawReceipt(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) {
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1}))
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		defer func() {
			cancel()
			if err := <-done; err != nil {
				t.Error(err)
			}
		}()
		entry, err := w.Enter(ctx, world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104})
		if err != nil {
			t.Fatal(err)
		}
		scene, _, err := w.Subscribe(ctx, entry.Self.Entity)
		if err != nil {
			t.Fatal(err)
		}
		tr := translator{catalog: cat}
		cp, _, err := tr.checkpoint(scene, 1, 1, scene.Begin.Time)
		if err != nil || !cp.Ready {
			t.Fatalf("authored capture: %+v/%v", cp, err)
		}
		var target world.Ref
		for _, o := range scene.Objects {
			if v, ok := o.State.(world.NPCSeen); ok && cat.CanTeach(v.NPC.Template.ID, scene.Self.Char.ClassID) {
				target = o.Entity
			}
		}
		if target == (world.Ref{}) {
			t.Fatal("authored scene contains no trainer")
		}
		c := bridge.Command{Key: 1, Verb: 2, Payload: []wire.Field{wire.S(64, true, int64(target.ID))}}
		p, err := prepare(c, scene, scene.End.Time)
		if err != nil {
			t.Fatal(err)
		}
		commit, err := w.Execute(ctx, p.request)
		r := receiptTest(t, tr, p, commit, err)
		if r.Stage != wire.StageDone || !r.EffectsPresent || len(r.Effects) != 1 {
			t.Fatalf("selection: %+v, want one source-proven target effect", r)
		}
		same, err := w.Execute(ctx, p.request)
		r = receiptTest(t, tr, p, same, err)
		if r.Stage != wire.StageUnknown || r.EffectsPresent {
			t.Fatalf("unchanged target: %+v, want unknown", r)
		}
		c.Verb = 5
		p, err = prepare(c, scene, scene.End.Time)
		if err != nil {
			t.Fatal(err)
		}
		talk, err := w.Execute(ctx, p.request)
		r = receiptTest(t, tr, p, talk, err)
		if r.Stage != wire.StageDone || !r.EffectsPresent || len(r.Effects) != 0 {
			t.Fatalf("talk: %+v, want attributed speech with empty effects", r)
		}
		scene, err = w.Capture(ctx, entry.Self.Entity)
		if err != nil {
			t.Fatal(err)
		}
		_, opts, err := choices(scene.Dialogue)
		if err != nil || len(opts) == 0 {
			t.Fatalf("authored choices: %v", err)
		}
		action, _ := wire.Find(opts[0].Fields, 3)
		c.Verb = 6
		c.Payload = append(c.Payload, wire.Field{Number: 68, Essential: true, Type: wire.LengthDelimited, Value: wire.Value{Data: action.Data}})
		p, err = prepare(c, scene, scene.End.Time)
		if err != nil {
			t.Fatal(err)
		}
		chosen, err := w.Execute(ctx, p.request)
		r = receiptTest(t, tr, p, chosen, err)
		if r.Stage != wire.StageUnknown {
			t.Fatalf("lesson list: %+v, want unknown without speech", r)
		}
		scene, err = w.Capture(ctx, entry.Self.Entity)
		if err != nil {
			t.Fatal(err)
		}
		if len(scene.Dialogue.Affordances) == 0 {
			t.Fatal("authored trainer has no lesson")
		}
		id, level, ok := lesson(scene.Dialogue.Affordances[0])
		if !ok {
			t.Fatal("invalid authored lesson")
		}
		c = bridge.Command{Key: 2, Verb: 13, Payload: []wire.Field{wire.S(14, true, 0), wire.Obj(18, true, []wire.Field{wire.S(1, true, int64(id)), wire.S(2, true, int64(level))})}}
		p, err = prepare(c, scene, scene.End.Time)
		if err != nil {
			t.Fatal(err)
		}
		learned, err := w.Execute(ctx, p.request)
		r = receiptTest(t, tr, p, learned, err)
		wantEffects := 1
		if p.cost != 0 {
			wantEffects = 2
		}
		if r.Stage != wire.StageDone || len(r.Effects) != wantEffects {
			t.Fatalf("learn: %+v, want %d effects for the exact source cost", r, wantEffects)
		}
		if err := w.Leave(ctx, 1); err != nil {
			t.Fatal(err)
		}
	})
}
