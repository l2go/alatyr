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
