// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"slices"
)

func (w *World) decayPlayer(s *state, p *player) {
	r := s.region(p.region)
	r.objects = slices.DeleteFunc(r.objects, func(id int32) bool { return id == p.id })
	if r.players > 0 {
		r.players--
	}
	for _, q := range s.players {
		if q != p && q.known[p.id] {
			w.playerForgetObject(s, q, p.id)
		}
	}
	for id := range p.known {
		w.playerForgetObject(s, p, id)
		if q, ok := s.players[id]; ok {
			w.playerForgetObject(s, q, p.id)
		}
	}
	w.setTarget(s, p, 0)
	w.stopFollow(s, p)
	s.change(p.ref(), PropertyCombat, boolValue(p.ai.autoAttacking), IntValue(0))
	p.ai = ai{}
	p.lastFolk = 0
	s.change(p.ref(), PropertyDialogue, LabelValue(p.dialogue), LabelValue(""))
	p.dialogue = ""
	p.bypass = nil
	p.bypassPrefix = nil
	s.change(p.ref(), PropertyLearning, boolValue(p.learning), IntValue(0))
	p.learning = false
}

// Appear completes a pending teleport using the current legacy incarnation.
// A generation-fenced command surface is separate from this legacy call.
func (w *World) Appear(ctx context.Context, id int32) error {
	return w.act(ctx, id, VerbAppear, func(s *state, p *player) {
		if p.teleporting {
			s.setBool(p.ref(), PropertyTeleporting, &p.teleporting, false)
			w.place(s, p)
			w.revalidateZone(s, p)
			if p.pendingRevive {
				w.revive(s, p)
			}
			p.send(SelfChanged{})
		}
		p.send(SelfChanged{})
		p.send(ActionIgnored{})
	})
}

func (w *World) revive(s *state, p *player) {
	p.pendingRevive = false
	s.setBool(p.ref(), PropertyDead, &p.dead, false)
	w.setHP(s, p, float64(p.maxHP())*respawnRestoreHP)
	w.broadcastToKnown(s, p, Revived{ObjectID: p.id})
}

const respawnRestoreHP = 0.7
