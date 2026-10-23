// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"time"
)

func (w *World) act(ctx context.Context, id int32, verb Verb, f func(s *state, p *player)) error {
	var err error
	if derr := w.doCommand(ctx, id, verb, func(s *state) {
		p, ok := s.players[id]
		if !ok {
			err = ErrNotPresent
			s.finish(Refused)
			return
		}
		f(s, p)
	}); derr != nil {
		return derr
	}
	return err
}

func (w *World) playerOnAction(s *state, p, q *player) {
	if p.target != q.id {
		w.setTarget(s, p, q.id)
		return
	}
	p.send(PositionObserved{ObjectID: q.id, Pos: q.pos, Heading: q.heading})
	p.send(ActionIgnored{})
}

func (w *World) sitDown(s *state, p *player) {
	now := time.Now()
	if p.sitting || p.attackingNow(now) || p.dead || p.tryingToSitOrStand(now) {
		return
	}
	s.setBool(p.ref(), PropertyPosture, &p.sitting, true)
	w.setIntention(s, p, order{intent: intentRest})
	w.broadcastToKnown(s, p, PostureChanged{ObjectID: p.id, Sitting: true, Pos: p.pos})
	p.lastSitStand = now
}

func (w *World) standUp(s *state, p *player, force bool) {
	now := time.Now()
	if !p.sitting || p.dead || (p.tryingToSitOrStand(now) && !force) {
		return
	}
	s.setBool(p.ref(), PropertyPosture, &p.sitting, false)
	w.setIntention(s, p, order{intent: intentIdle})
	w.broadcastToKnown(s, p, PostureChanged{ObjectID: p.id, Pos: p.pos})
	p.lastSitStand = now
}

func (w *World) setCP(s *state, p *player, cp float64) {
	if p.dead {
		return
	}
	maxCP := float64(p.stats.MaxCP())
	old := p.cp
	regen := cp < maxCP
	p.cp = min(max(cp, 0), maxCP)
	if old != p.cp {
		s.resourceChange(p.ref(), "cp", old, p.cp)
		w.markBroadcast(s, p, broadcastStatus)
	}
	if regen {
		w.startRegen(s, p)
	}
}

const (
	playerAttackRange = 40
	sitStandLock      = 2333 * time.Millisecond
	retargetBlocking  = 400 * time.Millisecond
)
