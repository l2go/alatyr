// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestSayWindowRequiresChoiceAttributionAndNPC(t *testing.T) {
	for _, kind := range []string{Shop, Offer, Practice} {
		for _, branch := range []string{"match", "no-source", "no-key", "wrong-choice", "wrong-npc", "talk", "speech-receipt-only"} {
			t.Run(kind+"/"+branch, func(t *testing.T) {
				s := newState(t, testConfig(), Policy{})
				for _, ev := range []Event{{Kind: Appeared, ID: 7}, {Kind: Appeared, ID: 8}} {
					if err := apply(t, s, ev); err != nil {
						t.Fatal(err)
					}
				}
				p := &planned{owner: s, npc: s.observed.Reference(7), plan: Plan{Command: Command{Kind: Say}}, record: wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 42)}}}
				input := Input{Chosen: 42, Source: Source{Dialogue: true}, Event: Event{Kind: kind, ID: 7}}
				switch branch {
				case "no-source":
					input.Source = Source{}
				case "no-key":
					input.Chosen = 0
				case "wrong-choice":
					input.Chosen = 41
				case "wrong-npc":
					input.Event.ID = 8
				case "talk":
					p.plan.Command.Kind = Talk
				case "speech-receipt-only":
					input.Chosen = 0
					input.Completed = 42
				}
				u := gateway.Update{Value: input}
				if _, err := s.Apply(u, 0); err != nil {
					t.Fatal(err)
				}
				got, err := s.Witness(&gateway.Action{Value: p}, u)
				want := gateway.WitnessNone
				if branch == "match" {
					want = gateway.WitnessMatch
				}
				if err != nil || got != want {
					t.Fatalf("witness=%v/%v want %v", got, err, want)
				}
			})
		}
	}
}
