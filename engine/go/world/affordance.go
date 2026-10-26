// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"strconv"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// AffordancesChanged replaces the player's current dialogue and lesson choices.
// Values retain their original identities and expiry during scene capture.
type AffordancesChanged struct {
	stamp
	Affordances []Affordance
}

func (AffordancesChanged) event() {}

// affordanceLifetime bounds interaction authority, not a client clock estimate.
const affordanceLifetime = Time(time.Minute)

func (s *state) nextAffordance(verb Verb, target Ref) Affordance {
	if s.affordanceSeq == ^uint64(0) || s.now() > Time(1<<63-1)-affordanceLifetime {
		s.err = ErrExhausted
		return Affordance{}
	}
	s.affordanceSeq++
	now := s.now()
	return Affordance{ID: s.affordanceSeq, Verb: verb, Target: target, Issued: now, Expires: now + affordanceLifetime}
}

func (w *World) issueChoices(s *state, p *player, n *npc) {
	s.clearAffordances(p)
	for _, action := range p.bypass {
		allowed := n.trainer() && action == "npc_"+strconv.Itoa(int(n.id))+"_SkillList"
		if n.tmpl.Role == catalog.Merchant {
			_, allowed = w.merchantChoice(n, action)
		}
		if !allowed {
			continue
		}
		a := s.nextAffordance(VerbChoose, n.ref())
		a.Parameters = []Parameter{{Name: "action", Value: LabelValue(action)}}
		if a.ID != 0 {
			p.affordances = append(p.affordances, a)
			s.change(p.ref(), PropertyAffordance, UintValue(0), UintValue(a.ID))
		}
	}

}

func (w *World) issueLessons(s *state, p *player, n *npc, skills []catalog.SkillLearn) {
	s.clearAffordances(p)
	for _, k := range skills {
		a := s.nextAffordance(VerbLearn, n.ref())
		if a.ID == 0 {
			break
		}
		a.Parameters = []Parameter{{Name: "skill", Value: IntValue(int64(k.SkillID))}, {Name: "level", Value: IntValue(int64(k.Level))}}
		a.Cost = []Cost{{Resource: Resource{Kind: ResourceSP}, Quantity: int64(k.SP)}}
		p.affordances = append(p.affordances, a)
		s.change(p.ref(), PropertyAffordance, UintValue(0), UintValue(a.ID))
	}
}

func (p *player) affordance(id uint64) (Affordance, bool) {
	for _, a := range p.affordances {
		if id != 0 && a.ID == id {
			return a, true
		}
	}
	return Affordance{}, false
}

func (s *state) clearAffordances(p *player) {
	for _, a := range p.affordances {
		s.change(p.ref(), PropertyAffordance, UintValue(a.ID), UintValue(0))
	}
	p.affordances = nil
}

func affordanceSkill(a Affordance, k Skill) bool {
	if len(a.Parameters) != 2 {
		return false
	}
	id, idOK := a.Parameters[0].Value.Integer()
	level, levelOK := a.Parameters[1].Value.Integer()
	return idOK && levelOK && id == int64(k.ID) && level == int64(k.Level)
}

func affordanceAction(a Affordance, action string) bool {
	if len(a.Parameters) != 1 || a.Parameters[0].Name != "action" {
		return false
	}
	label, ok := a.Parameters[0].Value.Label()
	return ok && label == action
}

func cloneAffordances(values []Affordance) []Affordance {
	out := slices.Clone(values)
	for i := range out {
		out[i].Parameters = slices.Clone(out[i].Parameters)
		out[i].Cost = slices.Clone(out[i].Cost)
	}
	return out
}
