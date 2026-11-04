// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"testing"
	"testing/synctest"
	"time"
)

func TestTalkCompletionsInOneMovementTurnReachEveryMailbox(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		var owners []Ref
		var boxes []*Mailbox
		var intents []IntentID
		for _, id := range []int32{1, 2} {
			c := syntheticCharacter(id)
			c.X = -300
			entry := enterTest(t, w, ctx, c)
			_, box, err := w.Observe(ctx, entry.Self.Entity, 256, 2)
			if err != nil {
				t.Fatal(err)
			}
			owners, boxes = append(owners, entry.Self.Entity), append(boxes, box)
		}
		teacher := objectOf(t, w, ctx, 9102)
		for i, owner := range owners {
			scene, err := w.Capture(ctx, owner)
			if err != nil {
				t.Fatal(err)
			}
			var target Ref
			for _, o := range scene.Objects {
				if o.Entity.ID == teacher {
					target = o.Entity
				}
			}
			c, err := w.Execute(ctx, Request{Owner: owner, Observer: boxes[i], Verb: VerbTalk, Target: target})
			if err != nil || c.Outcome.Stage != StageUnknown {
				t.Fatalf("approaching talk=%+v/%v", c.Outcome, err)
			}
			intents = append(intents, c.Intent.ID)
		}
		advanceVirtual(4 * time.Second)
		var completedAt Time
		for i, box := range boxes {
			pages, outcomes := 0, 0
			for {
				p, ok := box.Next()
				if !ok {
					break
				}
				if page, ok := p.Event.(DialoguePage); ok {
					if page.Intent() != intents[i] {
						t.Fatalf("owner %v page cause=%+v", owners[i], page.Intent())
					}
					pages++
				}
				if p.Commit.Outcome.Intent != intents[i] {
					continue
				}
				if pages != 1 || p.Commit.Outcome.Stage != StageDone {
					t.Fatalf("owner %v outcome before its speech: %+v", owners[i], p)
				}
				outcomes++
				if i == 0 {
					completedAt = p.Commit.Time
				} else if p.Commit.Time != completedAt {
					t.Fatal("talks did not complete in the same movement turn")
				}
			}
			if pages != 1 || outcomes != 1 {
				t.Fatalf("owner %v got %d pages / %d outcomes, want 1 / 1", owners[i], pages, outcomes)
			}
		}
		for _, owner := range owners {
			if err := w.Leave(ctx, owner); err != nil {
				t.Error(err)
			}
		}
	})
}
