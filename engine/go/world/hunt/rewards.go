// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package hunt

import (
	"cmp"
	"errors"
	"math"
	"math/big"
	"slices"
	"strconv"
)

// Member is one live, in-range recipient, in the party's captured member order.
type Member struct {
	ID    int32
	Level int32
	// XPRate includes an eligible overhit bonus. Middle has no vitality rate.
	XPRate float64
}

// Side is one frozen contribution: zero denotes neutral, negative IDs parties.
// A side without members remains in every denominator and may win lost loot.
type Side struct {
	ID         int64
	Damage     uint64
	Level      int32
	Members    []Member
	CoinCursor int
}

// Drop is one indivisible table result; nonstackable copies are separate drops.
type Drop struct {
	Item  int32
	Count int64
}

// Snapshot contains the inputs for a single x1 ordinary-monster settlement.
type Snapshot struct {
	World    string
	Life     uint64
	Level    int32
	XP       int64
	SP       int32
	Currency int32
	Sides    []Side
	Drops    []Drop
}

// PlayerReward is the final personal share, before inventory capacity checks.
type PlayerReward struct {
	ID    int32
	XP    int64
	SP    int32
	Coins int64
}

// SideReward retains lost shares and the party's next coin remainder cursor.
type SideReward struct {
	ID         int64
	Coins      int64
	NextCursor int
	Players    []PlayerReward
}

// ItemReward selects a side for exactly one non-currency table result.
// Unclaimed results are discarded, never redistributed to another side.
type ItemReward struct {
	Side      int64
	Drop      Drop
	Unclaimed bool
}

// Result is an owned reward plan; Calculate does not mutate its input.
type Result struct {
	Sides []SideReward
	Items []ItemReward
}

func decimal(v float64) *big.Rat {
	r, _ := new(big.Rat).SetString(strconv.FormatFloat(v, 'g', -1, 64))
	return r
}

func integer(v int64) *big.Rat { return new(big.Rat).SetInt64(v) }

func levelRate(diff int32) float64 {
	if diff <= 5 {
		return 1
	}
	return math.Pow(5.0/6, float64(diff-5))
}

// Calculate applies contribution shares once, 10% admission, 60% item dominance,
// largest coin remainders, and equal party shares. Invalid inputs return no plan.
func Calculate(in Snapshot) (Result, error) {
	fail := func() (Result, error) { return Result{}, errors.New("hunt: invalid death snapshot") }
	if in.XP < 0 || in.SP < 0 || in.Currency <= 0 || in.Level < 1 || in.Level > 1000 || len(in.World) > 1024 {
		return fail()
	}
	sides := slices.Clone(in.Sides)
	slices.SortFunc(sides, func(a, b Side) int { return cmp.Compare(a.ID, b.ID) })
	weights := make([]*big.Rat, len(sides))
	totalWeight, totalDamage := new(big.Rat), new(big.Rat)
	players := make(map[int32]bool)
	for i, side := range sides {
		if side.Damage == 0 || side.Level < 1 || side.Level > 1000 || i > 0 && sides[i-1].ID == side.ID || side.ID == 0 && len(side.Members) != 0 {
			return fail()
		}
		for _, m := range side.Members {
			if m.ID <= 0 || m.Level < 1 || m.Level > 1000 || players[m.ID] || m.XPRate < 0 || math.IsNaN(m.XPRate) || math.IsInf(m.XPRate, 0) {
				return fail()
			}
			players[m.ID] = true
		}
		damage := new(big.Rat).SetInt(new(big.Int).SetUint64(side.Damage))
		weight := new(big.Rat).Set(damage)
		if side.ID != 0 && side.Level-in.Level > 5 {
			weight.Mul(weight, decimal(math.Pow(0.5, float64(side.Level-in.Level-5)/3)))
		}
		weights[i] = weight
		totalWeight.Add(totalWeight, weight)
		totalDamage.Add(totalDamage, damage)
	}
	coins := int64(0)
	for _, d := range in.Drops {
		if d.Item <= 0 || d.Count <= 0 {
			return fail()
		}
		if d.Item == in.Currency {
			if d.Count > math.MaxInt64-coins {
				return fail()
			}
			coins += d.Count
		}
	}
	if len(sides) == 0 {
		return Result{}, nil
	}
	if totalWeight.Sign() <= 0 {
		return fail()
	}
	var admitted []int
	dominant := -1
	maximum := new(big.Rat)
	for i, weight := range weights {
		if weight.Cmp(maximum) > 0 {
			maximum.Set(weight)
		}
		ten := new(big.Rat).Mul(weight, integer(10))
		if ten.Cmp(totalWeight) >= 0 {
			admitted = append(admitted, i)
		}
		if ten.Cmp(new(big.Rat).Mul(totalWeight, integer(6))) >= 0 {
			dominant = i
		}
	}
	if len(admitted) == 0 {
		for i, weight := range weights {
			if new(big.Rat).Mul(weight, integer(2)).Cmp(maximum) >= 0 {
				admitted = append(admitted, i)
			}
		}
	}
	random := &Random{World: in.World, Life: in.Life}
	shares := divideCoins(coins, admitted, weights, random)
	var out Result
	for _, d := range in.Drops {
		if d.Item == in.Currency {
			continue
		}
		winner := dominant
		if winner < 0 {
			winner = drawSide(admitted, weights, random)
		}
		out.Items = append(out.Items, ItemReward{Side: sides[winner].ID, Drop: d, Unclaimed: sides[winner].ID == 0 || len(sides[winner].Members) == 0})
	}
	for i, side := range sides {
		r := SideReward{ID: side.ID, Coins: shares[i], NextCursor: side.CoinCursor}
		count := len(side.Members)
		if count > 0 {
			cursor := side.CoinCursor % count
			if cursor < 0 {
				cursor += count
			}
			for j, m := range side.Members {
				share := new(big.Rat).SetFrac(new(big.Int).SetUint64(side.Damage), big.NewInt(int64(count)))
				share.Quo(share, totalDamage)
				xp := new(big.Rat).Mul(share, integer(in.XP))
				xp.Mul(xp, decimal(m.XPRate*levelRate(m.Level-in.Level)))
				sp := new(big.Rat).Mul(share, integer(int64(in.SP)))
				sp.Mul(sp, decimal(levelRate(m.Level-in.Level)))
				x := new(big.Int).Quo(xp.Num(), xp.Denom())
				p := new(big.Int).Quo(sp.Num(), sp.Denom())
				if !x.IsInt64() || !p.IsInt64() || p.Int64() > math.MaxInt32 {
					return fail()
				}
				coin := shares[i] / int64(count)
				offset := j - cursor
				if offset < 0 {
					offset += count
				}
				if offset < int(shares[i]%int64(count)) {
					coin++
				}
				r.Players = append(r.Players, PlayerReward{ID: m.ID, XP: x.Int64(), SP: int32(p.Int64()), Coins: coin})
			}
			r.NextCursor = int((uint64(cursor) + uint64(shares[i]%int64(count))) % uint64(count))
		}
		out.Sides = append(out.Sides, r)
	}
	return out, nil
}

func divideCoins(coins int64, admitted []int, weights []*big.Rat, random *Random) []int64 {
	total := new(big.Rat)
	for _, i := range admitted {
		total.Add(total, weights[i])
	}
	shares := make([]int64, len(weights))
	remainders := make([]*big.Rat, len(weights))
	left := coins
	for _, i := range admitted {
		share := new(big.Rat).Mul(integer(coins), weights[i])
		share.Quo(share, total)
		shares[i] = new(big.Int).Quo(share.Num(), share.Denom()).Int64()
		remainders[i] = new(big.Rat).Sub(share, integer(shares[i]))
		left -= shares[i]
	}
	order := slices.Clone(admitted)
	slices.SortStableFunc(order, func(a, b int) int { return remainders[b].Cmp(remainders[a]) })
	for start := 0; start < len(order) && int64(start) < left; {
		end := start + 1
		for end < len(order) && remainders[order[start]].Cmp(remainders[order[end]]) == 0 {
			end++
		}
		if int64(end) > left {
			for i := end - 1; i > start; i-- {
				j := start + int(random.Below(uint64(i-start+1)))
				order[i], order[j] = order[j], order[i]
			}
		}
		start = end
	}
	for i := int64(0); i < left; i++ {
		shares[order[i]]++
	}
	return shares
}

func drawSide(admitted []int, weights []*big.Rat, random *Random) int {
	total := new(big.Rat)
	for _, i := range admitted {
		total.Add(total, weights[i])
	}
	u := new(big.Rat).SetFrac(new(big.Int).SetUint64(random.Uint64()), new(big.Int).Lsh(big.NewInt(1), 64))
	u.Mul(u, total)
	for _, i := range admitted {
		u.Sub(u, weights[i])
		if u.Sign() < 0 {
			return i
		}
	}
	// Exact positive weights and a draw below one make this unreachable.
	return admitted[len(admitted)-1]
}
