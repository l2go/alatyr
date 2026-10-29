// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
	"slices"
)

// LootRule selects the party's ordinary-item looter. Spoil is outside this world.
type LootRule uint8

// Ordinary party loot policies.
const (
	LootFinder     LootRule = iota // The contributing representative, if eligible.
	LootRandom                     // A random eligible member with inventory capacity.
	LootRoundRobin                 // The next eligible member in party order.
)

type party struct {
	id                     uint64
	members                []*player
	loot                   LootRule
	coinCursor, itemCursor int
}

// ErrParty reports invalid membership or an unknown party identity.
var ErrParty = errors.New("world: invalid party")

// SetParty is a trusted host operation for reward membership, not a wire command.
// ID zero creates a fresh identity; an empty member list disbands an existing
// party. A player must leave another party before joining. Members are current
// incarnations, in leader-first order. All validation precedes any mutation.
// Storage and participant admission remain the host's responsibilities.
func (w *World) SetParty(ctx context.Context, id uint64, members []Ref, loot LootRule) (uint64, error) {
	members = slices.Clone(members)
	var result uint64
	var err error
	if e := w.do(ctx, func(s *state) {
		group := s.parties[id]
		if loot > LootRoundRobin || len(members) > 9 || id != 0 && group == nil || id == 0 && (len(members) == 0 || s.partySeq == math.MaxInt64) {
			err = ErrParty
			return
		}
		seen := make(map[Ref]bool)
		var next []*player
		for _, ref := range members {
			p := s.players[ref.ID]
			if p == nil || p.ref() != ref || seen[ref] || p.party != nil && p.party != group {
				err = ErrParty
				return
			}
			seen[ref] = true
			next = append(next, p)
		}
		if group == nil {
			s.partySeq++
			group = &party{id: s.partySeq}
			if s.parties == nil {
				s.parties = make(map[uint64]*party)
			}
			s.parties[group.id] = group
		}
		for _, p := range group.members {
			if !seen[p.ref()] {
				p.party = nil
			}
		}
		group.members, group.loot = next, loot
		for _, p := range next {
			p.party = group
		}
		if len(next) == 0 {
			delete(s.parties, group.id)
		}
		result = group.id
	}); e != nil {
		return 0, e
	}
	return result, err
}

func (w *World) leaveParty(s *state, p *player) {
	if p.party == nil {
		return
	}
	group := p.party
	group.members = slices.DeleteFunc(group.members, func(member *player) bool { return member == p })
	p.party = nil
	if len(group.members) == 0 {
		delete(s.parties, group.id)
	}
}
