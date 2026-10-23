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
