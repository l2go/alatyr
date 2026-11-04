// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"context"
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

func TestTalkCommitKeepsSourceTimeAcrossObserverReplyDelay(t *testing.T) {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	for _, originBeforeWorld := range []bool{false, true} {
		name := "gateway-starts-later"
		if originBeforeWorld {
			name = "gateway-starts-earlier"
		}
		t.Run(name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				worldOrigin := time.Now()
				w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1}))
				origin := worldOrigin.Add(-250 * time.Microsecond)
				if !originBeforeWorld {
					time.Sleep(1500 * time.Microsecond)
					origin = time.Now()
				}
				ctx, cancel := context.WithCancel(context.Background())
				done := make(chan error, 1)
				go func() { done <- w.Run(ctx) }()
				t.Cleanup(func() {
					cancel()
					if err := <-done; err != nil {
						t.Error(err)
					}
				})
				char := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, CurHP: 100, X: -71057, Y: 258243 - 400, Z: -3104}
				cfg, calls := gatewaytest.Config(), 0
				var intent world.IntentID
				source, state, err := Open(t.Context(), Config{World: w, Catalog: cat, Meaning: cfg, Origin: origin,
					Now: func() time.Time {
						calls++
						if calls == 2 {
							time.Sleep(3 * time.Millisecond)
						}
						return time.Now()
					},
					Load: func(context.Context, Identity) (world.Character, error) { return char, nil },
					Policy: meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
						return meaning.Capabilities{Talk: o.Role == "provisioner"}
					}},
					Handoff: func(h Handoff) { intent = h.Intent },
				}, Identity{Account: char.Account, Character: char.Name})
				if err != nil {
					t.Fatal(err)
				}
				t.Cleanup(func() {
					if err := source.Close(); err != nil {
						t.Error(err)
					}
				})
				snapshot, err := state.Snapshot(time.Since(origin).Milliseconds())
				if err != nil {
					t.Fatal(err)
				}
				var target meaning.Ref
				for _, fact := range snapshot.Facts {
					e, err := meaning.EventValue(fact)
					if err != nil {
						t.Fatal(err)
					}
					if e.Event.Role == "provisioner" {
						target = e.Context.Ref
					}
				}
				record, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Talk, Target: target.ID}, meaning.CommandContext{N: 1, Control: 1, Target: target})
				if err != nil {
					t.Fatal(err)
				}
				contact := time.Since(origin).Milliseconds()
				action, reason := state.Plan(record, contact)
				if reason != wire.ReasonNone {
					t.Fatalf("plan refusal=%v", reason)
				}
				step := action.Steps[0]
				step.N, step.Role, step.ContactMS, step.DeadlineMS = 1, gateway.StepEffect, contact, contact+cfg.Attempts.EvidenceMS
				if err := source.Write(t.Context(), step); err != nil {
					t.Fatal(err)
				}
				scene, err := w.Capture(t.Context(), source.Owner())
				if err != nil || !scene.Moving || scene.Dialogue.Page != "" {
					t.Fatalf("talk must require an approach: moving=%v page=%q err=%v", scene.Moving, scene.Dialogue.Page, err)
				}
				spoke := false
				for range 40 {
					for {
						u, ok := source.Receive()
						if !ok {
							break
						}
						p := u.Value.(Publication)
						if page, ok := p.Event.(world.DialoguePage); ok {
							if page.Intent() != intent {
								t.Fatalf("speech intent=%+v, want %+v", page.Intent(), intent)
							}
							spoke = true
						}
						if p.Commit.Outcome.Intent != intent || p.Commit.Outcome.Stage != world.StageDone {
							continue
						}
						if !spoke || p.Commit.Intent.ID.N != 0 {
							t.Fatal("completion is not the original talk's continuation after speech")
						}
						now := time.Since(origin).Milliseconds()
						observation, err := state.Apply(u, now)
						if err != nil || len(observation.Resolutions) != 1 {
							t.Fatalf("completion=%+v/%v", observation, err)
						}
						got := observation.Resolutions[0]
						want := max(contact, worldOrigin.Add(time.Duration(p.Commit.Time)).Sub(origin).Milliseconds())
						if got.N != 1 || got.Stage != wire.StageDone || got.ObservedMS != want || got.ObservedMS > now {
							t.Fatalf("talk completion=%+v, want original attempt at %d ms (now %d)", got, want, now)
						}
						return
					}
					time.Sleep(100 * time.Millisecond)
					synctest.Wait()
				}
				t.Fatal("mailbox lost the original talk's completion")
			})
		})
	}
}
