// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package hunt_test

import (
	"fmt"
	"math"
	"reflect"
	"slices"
	"testing"

	"github.com/l2go/alatyr/engine/go/world/hunt"
)

func TestCounterRandomVectors(t *testing.T) {
	for _, tc := range []struct {
		life  uint64
		want  uint64
		bound uint64
	}{
		{1, 0x6a2853f8b2f2397b, 414}, {2, 0xba8e228155ae27fe, 728},
	} {
		r := hunt.Random{World: "test-world", Life: tc.life}
		if got := r.Uint64(); got != tc.want {
			t.Fatalf("life %d draw 0: got %x, want %x", tc.life, got, tc.want)
		}
		r = hunt.Random{World: "test-world", Life: tc.life}
		if got := r.Below(1000); got != tc.bound {
			t.Fatalf("life %d below 1000: got %d, want %d", tc.life, got, tc.bound)
		}
	}
	r := hunt.Random{World: "test-world", Life: 1}
	if r.Below(0) != 0 || r.Uint64() != 0x6a2853f8b2f2397b || r.Uint64() != 0x3e55edc3f8c83c9d {
		t.Fatal("stream sequence or zero bound consumed a draw")
	}
}

func ExampleCalculate() {
	plan, err := hunt.Calculate(hunt.Snapshot{World: "example", Life: 1, Level: 1, XP: 100, SP: 10, Currency: 57,
		Sides: []hunt.Side{{ID: 1, Damage: 60, Level: 1, Members: []hunt.Member{{ID: 1, Level: 1, XPRate: 1}}}, {ID: 2, Damage: 40, Level: 1, Members: []hunt.Member{{ID: 2, Level: 1, XPRate: 1}}}},
		Drops: []hunt.Drop{{Item: 57, Count: 5}, {Item: 1060, Count: 1}}})
	fmt.Println(err, plan.Sides[0].Players[0].XP, plan.Sides[1].Coins, plan.Items[0].Side)
	// Output: <nil> 60 2 1
}

func TestCoinConservationAndPartyCursor(t *testing.T) {
	for _, coins := range []int64{0, 1, 2, 3, 4, 11, math.MaxInt64} {
		in := hunt.Snapshot{World: "test-world", Life: 1, Level: 1, XP: 120, SP: 24, Currency: 57,
			Sides: []hunt.Side{{ID: -1, Damage: 50, Level: 1, CoinCursor: 1, Members: []hunt.Member{{ID: 1, Level: 1, XPRate: 1}, {ID: 2, Level: 1, XPRate: 1}, {ID: 3, Level: 1, XPRate: 1}}}, {ID: 4, Damage: 50, Level: 1, Members: []hunt.Member{{ID: 4, Level: 1, XPRate: 1}}}}}
		if coins > 0 {
			in.Drops = []hunt.Drop{{Item: 57, Count: coins}}
		}
		out, err := hunt.Calculate(in)
		if err != nil {
			t.Fatal(err)
		}
		var sum int64
		for _, side := range out.Sides {
			sum += side.Coins
			personal := int64(0)
			for _, p := range side.Players {
				personal += p.Coins
				if side.ID == -1 && (p.XP != 20 || p.SP != 4) {
					t.Fatalf("party share applied twice: %+v", p)
				}
			}
			if personal != side.Coins {
				t.Fatalf("personal coins %d, want %d", personal, side.Coins)
			}
		}
		if sum != coins {
			t.Fatalf("coins %d: sum %d", coins, sum)
		}
		party := out.Sides[0]
		if party.NextCursor != (1+int(party.Coins%3))%3 {
			t.Fatalf("cursor: %+v", party)
		}
	}
}

func TestSnapshotValidationAndOwnership(t *testing.T) {
	valid := hunt.Snapshot{World: "example", Life: 1, Level: 1, XP: 100, SP: 10, Currency: 57,
		Sides: []hunt.Side{{ID: 1, Damage: 60, Level: 1, Members: []hunt.Member{{ID: 1, Level: 1, XPRate: 1}}}, {ID: 2, Damage: 40, Level: 1}}, Drops: []hunt.Drop{{Item: 57, Count: 5}}}
	for _, change := range []func(*hunt.Snapshot){
		func(s *hunt.Snapshot) { s.Sides[0].Damage = 0 },
		func(s *hunt.Snapshot) { s.Sides[1].ID = 1 },
		func(s *hunt.Snapshot) { s.Sides[1].Members = []hunt.Member{{ID: 1, Level: 1, XPRate: 1}} },
		func(s *hunt.Snapshot) { s.Sides[0].ID = 0 },
		func(s *hunt.Snapshot) { s.Sides[0].Members[0].XPRate = math.Inf(1) },
		func(s *hunt.Snapshot) { s.Sides[0].Members[0].XPRate = math.NaN() },
		func(s *hunt.Snapshot) { s.Sides[0].Members[0].XPRate = -1 },
		func(s *hunt.Snapshot) { s.Sides[0].Members[0].Level = math.MaxInt32 },
		func(s *hunt.Snapshot) { s.Drops[0].Count = 0 },
		func(s *hunt.Snapshot) { s.Drops = []hunt.Drop{{Item: 57, Count: math.MaxInt64}, {Item: 57, Count: 1}} },
		func(s *hunt.Snapshot) { s.XP = math.MaxInt64; s.Sides[0].Members[0].XPRate = 100 },
	} {
		in := valid
		in.Sides = slices.Clone(valid.Sides)
		in.Drops = slices.Clone(valid.Drops)
		for i := range in.Sides {
			in.Sides[i].Members = slices.Clone(in.Sides[i].Members)
		}
		change(&in)
		if out, err := hunt.Calculate(in); err == nil || len(out.Sides) != 0 || len(out.Items) != 0 {
			t.Fatalf("invalid snapshot returned a partial plan: %+v/%v", out, err)
		}
	}
	before := valid
	before.Sides = slices.Clone(valid.Sides)
	before.Drops = slices.Clone(valid.Drops)
	out, err := hunt.Calculate(valid)
	if err != nil {
		t.Fatal(err)
	}
	out.Sides[0].Players[0].XP = -1
	if !reflect.DeepEqual(before, valid) {
		t.Fatal("calculation aliased input")
	}
}

func TestLostAndNeutralSharesAreNotRedistributed(t *testing.T) {
	for _, id := range []int64{0, 1} {
		out, err := hunt.Calculate(hunt.Snapshot{World: "example", Life: 1, Level: 1, XP: 100, SP: 10, Currency: 57,
			Sides: []hunt.Side{{ID: id, Damage: 60, Level: 1}, {ID: 2, Damage: 40, Level: 1, Members: []hunt.Member{{ID: 2, Level: 1, XPRate: 1}}}}, Drops: []hunt.Drop{{Item: 57, Count: 5}, {Item: 4, Count: 1}}})
		if err != nil || len(out.Items) != 1 || !out.Items[0].Unclaimed || out.Items[0].Side != id || out.Sides[0].Coins != 3 || out.Sides[1].Players[0].XP != 40 || out.Sides[1].Players[0].Coins != 2 {
			t.Fatalf("lost dominant side %d was redistributed: %+v/%v", id, out, err)
		}
	}
}

func TestAdmissionFallbackAndNoCrossKillRemainders(t *testing.T) {
	in := hunt.Snapshot{World: "example", Life: 1, Level: 1, XP: 110, SP: 22, Currency: 57, Drops: []hunt.Drop{{Item: 57, Count: 11}}}
	for i := int32(1); i <= 11; i++ {
		in.Sides = append(in.Sides, hunt.Side{ID: int64(i), Damage: 1, Level: 1, Members: []hunt.Member{{ID: i, Level: 1, XPRate: 1}}})
	}
	out, err := hunt.Calculate(in)
	if err != nil {
		t.Fatal(err)
	}
	for _, side := range out.Sides {
		if side.Coins != 1 || side.Players[0].XP != 10 {
			t.Fatalf("fallback lost an admitted share: %+v", side)
		}
	}
	again, err := hunt.Calculate(in)
	if err != nil || !reflect.DeepEqual(out, again) {
		t.Fatal("independent calculation retained a previous kill's remainder")
	}
}
