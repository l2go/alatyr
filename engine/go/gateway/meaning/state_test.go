// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func apply(t *testing.T, s *State, e Event) error {
	t.Helper()
	_, err := s.Apply(gateway.Update{Value: Input{Event: e}}, 0)
	return err
}
func TestBoundedProjectionAndUnknownCompleteness(t *testing.T) {
	c := testConfig()
	c.Objects = 2
	s := newState(t, c, Policy{})
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Role: RolePlayer, Mine: true}); err != nil {
		t.Fatal(err)
	}
	snapshot, err := s.Snapshot(0)
	if snapshot.Ready || len(snapshot.Domains) == 0 || err != nil {
		t.Fatal(snapshot, err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 2}); err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 3}); err == nil {
		t.Fatal("object cap ignored")
	}
	c = testConfig()
	s = newState(t, c, Policy{})
	goods := make([]Good, c.Wire.ListItems+1)
	if err := apply(t, s, Event{Kind: Shop, Goods: goods, Complete: true}); err == nil {
		t.Fatal("list cap ignored")
	}
	c = testConfig()
	c.WindowBytes = 32
	s = newState(t, c, Policy{})
	if err := apply(t, s, Event{Kind: Spoke, ID: 1, Text: "A proposal too large for the configured window", Complete: true}); err == nil {
		t.Fatal("window cap ignored")
	}
	c = testConfig()
	c.Advertised[25] = 100
	s = newState(t, c, Policy{})
	if err := apply(t, s, Event{Kind: Appeared, ID: 1}); err == nil {
		t.Fatal("aggregate cap ignored")
	}
}
func TestSnapshotDoesNotRenewOfferAndPriceIsCaptured(t *testing.T) {
	c := testConfig()
	s := newState(t, c, Policy{})
	apply(t, s, Event{Kind: Appeared, ID: 5, Mine: true, Role: RolePlayer})
	apply(t, s, Event{Kind: Vitals, ID: 5, Stats: &Stats{HP: Number(1)}})
	observation, err := s.Apply(gateway.Update{Value: Input{Event: Event{Kind: Shop, List: 4, Complete: true, Goods: []Good{{ID: 30, Price: 5, Count: 10}}}}}, 0)
	if err != nil {
		t.Fatal(err)
	}
	decoded, _ := EventValue(observation.Facts[0])
	ctx := CommandContext{N: 1, Control: 1, Offer: decoded.Context.Offers[0], List: 4, Price: 6}
	r, _ := CommandRecord(Command{Kind: Buy, Count: 1}, ctx)
	if _, reason := s.Plan(r, 0); reason != wire.ReasonStaleOffer {
		t.Fatal(reason)
	}
	ctx.Price = 5
	r, _ = CommandRecord(Command{Kind: Buy, Count: 1}, ctx)
	action, reason := s.Plan(r, 0)
	if reason != wire.ReasonNone {
		t.Fatal(reason)
	}
	if _, err = s.Snapshot(c.ProposalMS - 1); err != nil {
		t.Fatal(err)
	}
	if reason = s.Validate(action, c.ProposalMS); reason != wire.ReasonStaleOffer {
		t.Fatal("replay renewed lifetime", reason)
	}
}

func TestItemWitnessRequiresAttributedTransition(t *testing.T) {
	for _, tc := range []struct {
		name   string
		row    Good
		used   uint64
		source bool
		kind   string
		want   gateway.Witness
	}{
		{"equipment", Good{Object: 42, ID: 4, Count: 1, Slot: Number(7)}, 1, true, Inventory, gateway.WitnessMatch},
		{"consumption", Good{Object: 42, ID: 4, Count: 0, Slot: Number(-1)}, 1, true, Inventory, gateway.WitnessMatch},
		{"unchanged", Good{Object: 42, ID: 4, Count: 1, Slot: Number(-1)}, 1, true, Inventory, gateway.WitnessNone},
		{"other object", Good{Object: 43, ID: 4, Count: 0}, 1, true, Inventory, gateway.WitnessNone},
		{"other template", Good{Object: 42, ID: 5, Count: 0}, 1, true, Inventory, gateway.WitnessNone},
		{"increase", Good{Object: 42, ID: 4, Count: 2, Slot: Number(-1)}, 1, true, Inventory, gateway.WitnessNone},
		{"no source", Good{Object: 42, ID: 4, Count: 0}, 1, false, Inventory, gateway.WitnessNone},
		{"other attempt", Good{Object: 42, ID: 4, Count: 0}, 2, true, Inventory, gateway.WitnessNone},
		{"bare receipt", Good{Object: 42, ID: 4, Count: 0}, 0, true, Inventory, gateway.WitnessNone},
		{"snapshot", Good{Object: 42, ID: 4, Count: 0}, 1, true, Carried, gateway.WitnessNone},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := newState(t, testConfig(), Policy{})
			for _, e := range []Event{{Kind: Appeared, ID: 1, Mine: true}, {Kind: Carried, Goods: []Good{{Object: 42, ID: 4, Count: 1, Slot: Number(-1)}}}} {
				if err := apply(t, s, e); err != nil {
					t.Fatal(err)
				}
			}
			r, err := CommandRecord(Command{Kind: UseItem, Target: 42}, CommandContext{N: 1, Control: 1})
			if err != nil {
				t.Fatal(err)
			}
			a, reason := s.Plan(r, 0)
			if reason != wire.ReasonNone {
				t.Fatal(reason)
			}
			u := gateway.Update{Value: Input{Completed: 1, Used: tc.used, Source: Source{ItemUse: tc.source}, Event: Event{Kind: tc.kind, Goods: []Good{tc.row}}}}
			if got, err := s.Witness(a, u); err != nil || got != tc.want {
				t.Fatalf("witness=%v/%v want %v", got, err, tc.want)
			}
		})
	}
}
