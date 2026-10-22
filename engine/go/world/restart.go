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
	r.players--
	for id := range p.known {
		w.playerForgetObject(s, p, id)
		if q, ok := s.players[id]; ok {
			w.playerForgetObject(s, q, p.id)
		}
	}
	w.setTarget(s, p, 0)
	w.stopFollow(s, p)
	p.ai = ai{}
}

func (w *World) Appear(ctx context.Context, id int32) error {
	return w.act(ctx, id, func(s *state, p *player) {
		if p.teleporting {
			p.teleporting = false
			w.place(s, p)
			w.revalidateZone(s, p)
			if p.pendingRevive {
				w.revive(s, p)
			}
			p.send(SelfInfo{})
		}
		p.send(SelfInfo{})
		p.send(ActionFailed{})
	})
}

func (w *World) revive(s *state, p *player) {
	p.pendingRevive = false
	p.dead = false
	w.setHP(s, p, float64(p.maxHP())*respawnRestoreHP)
	w.broadcastToKnown(s, p, Revived{ObjectID: p.id})
}
