// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestWindowAttributionMatchesIntentVerbAndIncarnation(t *testing.T) {
	npc := world.Ref{ID: 7, Gen: 9}
	intent := world.IntentID{Owner: world.Ref{ID: 1, Gen: 1}, N: 2}
	for _, kind := range []string{"trade", "learn"} {
		for _, branch := range []string{"match", "other-intent", "other-owner", "other-verb", "other-npc", "other-incarnation", "no-intent", "replay"} {
			t.Run(kind+"/"+branch, func(t *testing.T) {
				s := &session{intentBindings: []intentBinding{{intent: intent, key: 42, verb: 6, target: npc}}}
				id, speaker := intent, npc
				switch branch {
				case "other-intent":
					id.N++
				case "other-owner":
					id.Owner.ID++
				case "other-verb":
					s.intentBindings[0].verb = 14
				case "other-npc":
					speaker.ID++
				case "other-incarnation":
					speaker.Gen++
				case "no-intent":
					id = world.IntentID{}
				}
				var e world.Event = world.TradeList{ChoiceIntent: id, Dialogue: world.Dialogue{Speaker: speaker}}
				if kind == "learn" {
					e = world.LearnList{ChoiceIntent: id, Dialogue: world.Dialogue{Speaker: speaker}}
				}
				fs := []fact{{kind: 12}}
				if branch == "replay" {
					s.attribute(e, fs)
					fs = []fact{{kind: 12}}
				}
				s.attribute(e, fs)
				field, ok := wire.Find(fs[0].fields, 75)
				if want := branch == "match"; ok != want || ok && field.U != 42 {
					t.Fatalf("attribution=%v/%d want %v", ok, field.U, want)
				}
				if branch == "match" && len(s.intentBindings) != 0 {
					t.Fatal("consumed binding retained")
				}
			})
		}
	}
}

// TestCompletionAttributionMatchesIntentVerbAndTarget covers events that may
// complete an intent after the world walked to its target: a dialogue page for
// talk, a pickup for take, and a merchant exchange for buy and sell.
func TestCompletionAttributionMatchesIntentVerbAndTarget(t *testing.T) {
	owner := world.Ref{ID: 1, Gen: 1}
	npc := world.Ref{ID: 7, Gen: 9}
	item := world.Ref{ID: 70, Gen: 3}
	intent := world.IntentID{Owner: owner, N: 2}
	for _, tc := range []struct {
		name   string
		verb   uint64
		target world.Ref
		event  world.Event
		want   bool
	}{
		{"page", 5, npc, world.DialoguePage{SpeechIntent: intent, Dialogue: world.Dialogue{Speaker: npc}}, true},
		{"page/choice key", 6, npc, world.DialoguePage{SpeechIntent: intent, Dialogue: world.Dialogue{Speaker: npc}}, false},
		{"page/other speaker", 5, npc, world.DialoguePage{SpeechIntent: intent, Dialogue: world.Dialogue{Speaker: world.Ref{ID: 8, Gen: 9}}}, false},
		{"page/other incarnation", 5, npc, world.DialoguePage{SpeechIntent: intent, Dialogue: world.Dialogue{Speaker: world.Ref{ID: 7, Gen: 10}}}, false},
		{"page/unprompted", 5, npc, world.DialoguePage{Dialogue: world.Dialogue{Speaker: npc}}, false},
		{"pickup", 4, item, world.ItemPicked{PickupIntent: intent, PlayerID: owner.ID, ObjectID: item.ID}, true},
		{"pickup/other item", 4, item, world.ItemPicked{PickupIntent: intent, PlayerID: owner.ID, ObjectID: item.ID + 1}, false},
		{"pickup/other picker", 4, item, world.ItemPicked{PickupIntent: intent, PlayerID: owner.ID + 1, ObjectID: item.ID}, false},
		{"pickup/other intent", 4, item, world.ItemPicked{PickupIntent: world.IntentID{Owner: owner, N: 3}, PlayerID: owner.ID, ObjectID: item.ID}, false},
		{"buy", 7, npc, world.InventoryChanged{TradeIntent: intent}, true},
		{"sell", 8, npc, world.InventoryChanged{TradeIntent: intent}, true},
		{"trade/item-use key", 14, world.Ref{}, world.InventoryChanged{TradeIntent: intent}, false},
		{"item use/trade key", 7, npc, world.InventoryChanged{UseIntent: intent}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := &session{client: &Client{owner: owner}, intentBindings: []intentBinding{{intent: intent, key: 42, verb: tc.verb, target: tc.target}}}
			fs := []fact{{kind: 1}, {kind: 2}}
			s.attribute(tc.event, fs)
			for _, f := range fs {
				field, ok := wire.Find(f.fields, 75)
				if ok != tc.want || ok && field.U != 42 {
					t.Fatalf("attribution=%v/%d want %v", ok, field.U, tc.want)
				}
			}
		})
	}
}
