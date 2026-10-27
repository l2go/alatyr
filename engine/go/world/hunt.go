// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"slices"

	"github.com/l2go/alatyr/engine/go/world/hunt"
)

// Integer micro-HP preserves fractional spell damage without accumulating
// floating-point credit. Differences of rounded endpoints conserve removed HP.
const hpUnits = 1_000_000

type huntLife struct {
	life               uint64
	bound              map[int32]int64
	parties            map[*party]int64
	sides              map[int64]*huntSide
	returned, rewarded bool
}

type huntSide struct {
	damage         uint64
	group          *party
	representative *player
	contributors   []*player
}

func (n *npc) resetHuntIfHome() {
	if n.hunt.returned && !n.dead && n.hp >= float64(n.maxHP()) && distance3D(n.pos, n.home()) <= 5 {
		n.hunt = huntLife{life: n.hunt.life}
		n.hate = nil
	}
}

func (w *World) recordHuntDamage(s *state, n *npc, attacker actor, before, after float64) {
	if !n.mob() || n.hunt.rewarded || before <= after {
		return
	}
	n.resetHuntIfHome()
	credit := uint64(math.Round(before*hpUnits) - math.Round(after*hpUnits))
	if credit == 0 {
		return
	}
	h := &n.hunt
	if h.bound == nil {
		h.bound = make(map[int32]int64)
		h.parties = make(map[*party]int64)
		h.sides = make(map[int64]*huntSide)
	}
	var p *player
	if attacker != nil {
		p, _ = attacker.(*player)
	}
	id := int32(0)
	if p != nil {
		id = p.id
	}
	side, fixed := h.bound[id]
	if !fixed {
		side = int64(id)
		if p != nil && p.party != nil {
			var found bool
			side, found = h.parties[p.party]
			if !found {
				side = -int64(len(h.parties) + 1)
				h.parties[p.party] = side
			}
		}
		h.bound[id] = side
		if h.sides[side] == nil {
			entry := &huntSide{representative: p}
			if p != nil {
				entry.group = p.party
			}
			h.sides[side] = entry
		}
		if p != nil {
			h.sides[side].contributors = append(h.sides[side].contributors, p)
		}
	}
	entry := h.sides[side]
	if credit > math.MaxUint64-entry.damage {
		s.err = ErrExhausted
		return
	}
	entry.damage += credit
}

func (w *World) huntSnapshot(s *state, n *npc) (hunt.Snapshot, map[int32]*player) {
	in := hunt.Snapshot{World: w.cat.ID(), Life: n.hunt.life, Level: n.level(), XP: int64(n.tmpl.RewardExp), SP: n.tmpl.RewardSP, Currency: w.cat.Currency()}
	eligible := make(map[int32]*player)
	var ids []int64
	for id := range n.hunt.sides {
		ids = append(ids, id)
	}
	slices.Sort(ids)
	for _, id := range ids {
		side := n.hunt.sides[id]
		entry := hunt.Side{ID: id, Damage: side.damage}
		var candidates []*player
		if side.group != nil {
			candidates = append(candidates, side.group.members...)
			entry.CoinCursor = side.group.coinCursor
		} else if side.representative != nil {
			entry.Level = side.representative.level()
		}
		for _, p := range side.contributors {
			if !slices.Contains(candidates, p) {
				candidates = append(candidates, p)
			}
		}
		seen := make(map[int32]bool)
		for _, previous := range candidates {
			if seen[previous.id] {
				continue
			}
			seen[previous.id] = true
			p := previous
			if current := s.players[p.id]; current != nil && current.char.Account == p.char.Account {
				p = current
			}
			bound, fixed := n.hunt.bound[p.id]
			if fixed && bound != id {
				continue
			}
			if fixed {
				entry.Level = max(entry.Level, p.level())
			}
			if s.players[p.id] != p || p.dead || p.teleporting || distance3D(n.pos, p.pos) > altPartyRange {
				continue
			}
			entry.Level = max(entry.Level, p.level())
			eligible[p.id] = p
			rate := 1.0
			if n.overhit && n.overhitAttacker == p.id {
				rate += min(0.25, n.overhitDamage/float64(n.maxHP()))
			}
			entry.Members = append(entry.Members, hunt.Member{ID: p.id, Level: p.level(), XPRate: rate})
		}
		if entry.Level == 0 {
			entry.Level = n.level()
		}
		in.Sides = append(in.Sides, entry)
	}
	return in, eligible
}

func (w *World) calculateRewards(s *state, n *npc, _ actor) {
	if n.hunt.rewarded || len(n.hunt.sides) == 0 {
		return
	}
	n.hunt.rewarded = true
	in, eligible := w.huntSnapshot(s, n)
	random := &hunt.Random{World: in.World, Life: in.Life}
	highest := n.level()
	for _, side := range n.hunt.sides {
		for _, p := range side.contributors {
			highest = max(highest, p.level())
		}
	}
	levelMod := 0
	if highest-n.level() >= 9 {
		levelMod = int(highest-n.level()-8) * 9
	}
	// Roll each category once for this life, before deciding any recipient.
	for _, category := range w.cat.DropCategories(n.tmpl.ID) {
		if category.Type < 0 || int64(random.Below(1_000_000)) >= dropChance(float64(category.Chance), nil, levelMod) {
			continue
		}
		d := category.DropOne(func(bound int) int { return int(random.Below(uint64(bound))) })
		if d == nil {
			continue
		}
		count := d.Min
		if d.Max > d.Min {
			count += int64(random.Below(uint64(d.Max-d.Min) + 1))
		}
		tmpl, _ := w.cat.Item(d.ItemID)
		if tmpl.Stackable() {
			in.Drops = append(in.Drops, hunt.Drop{Item: d.ItemID, Count: count})
		} else {
			for range count {
				in.Drops = append(in.Drops, hunt.Drop{Item: d.ItemID, Count: 1})
			}
		}
	}
	plan, err := hunt.Calculate(in)
	if err != nil {
		s.err = err
		return
	}
	shared := len(in.Sides) > 1
	if shared {
		var unclaimed int64
		for _, side := range plan.Sides {
			if len(side.Players) == 0 {
				unclaimed += side.Coins
			}
		}
		w.logger.Debug("hunt settlement", "world", in.World, "life", in.Life, "sides", len(plan.Sides), "items", len(plan.Items), "unclaimed_coins", unclaimed)
	}
	for _, side := range plan.Sides {
		runtime := n.hunt.sides[side.ID]
		atDeath := shared || runtime.group != nil
		for _, reward := range side.Players {
			p := eligible[reward.ID]
			w.addExpAndSP(s, p, reward.XP, reward.SP)
			if atDeath && reward.Coins > 0 {
				w.giveItem(s, p, n, in.Currency, reward.Coins)
			}
		}
		if atDeath && runtime.group != nil {
			runtime.group.coinCursor = side.NextCursor
		}
		if !atDeath && len(side.Players) > 0 && side.Coins > 0 {
			w.dropHuntOnGround(s, n, runtime.representative, in.Currency, side.Coins, false, random)
		}
	}
	for _, item := range plan.Items {
		if item.Unclaimed {
			continue
		}
		side := n.hunt.sides[item.Side]
		owner := side.representative
		if side.group != nil {
			var recipients []*player
			for _, reward := range plan.Sides {
				if reward.ID == item.Side {
					for _, p := range reward.Players {
						recipients = append(recipients, eligible[p.ID])
					}
				}
			}
			owner = w.huntLooter(side.group, owner, recipients, item.Drop.Item, n.pos, random)
		}
		w.dropHuntOnGround(s, n, owner, item.Drop.Item, item.Drop.Count, shared || side.group != nil, random)
	}
}

func (w *World) huntLooter(group *party, representative *player, recipients []*player, template int32, point Point, random *hunt.Random) *player {
	if len(recipients) == 0 {
		return nil
	}
	fallback := recipients[0]
	if slices.Contains(recipients, representative) {
		fallback = representative
	}
	canLoot := func(p *player) bool {
		if !slices.Contains(recipients, p) || distance3D(p.pos, point) > 1400 {
			return false
		}
		tmpl, _ := w.cat.Item(template)
		if tmpl.Stackable() {
			for _, it := range p.char.Items {
				if it.TemplateID == template && !it.Equipped() {
					return len(p.char.Items) <= maxTradeRows
				}
			}
		}
		return len(p.char.Items) < maxTradeRows
	}
	switch group.loot {
	case LootRandom:
		var available []*player
		for _, p := range group.members {
			if canLoot(p) {
				available = append(available, p)
			}
		}
		if len(available) > 0 {
			return available[random.Below(uint64(len(available)))]
		}
	case LootRoundRobin:
		for range len(group.members) {
			group.itemCursor = (group.itemCursor + 1) % len(group.members)
			if p := group.members[group.itemCursor]; canLoot(p) {
				return p
			}
		}
	}
	return fallback
}
