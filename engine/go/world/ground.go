// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

func (w *World) removeFromGround(s *state, g *groundItem) {
	delete(s.ground, g.id)
	for _, p := range s.players {
		if p.known[g.id] {
			delete(p.known, g.id)
			p.send(ObjectGone{ObjectID: g.id})
			w.notifyForget(s, p, g.id)
		}
	}
}

func (w *World) logItem(op, process string, objectID, templateID int32, count int64, p *player, ref string) {
	t, _ := w.cat.Item(templateID)
	w.items.Write(itemlog.Entry{
		Op: op, Process: process, ObjectID: objectID, Item: t.Name, Count: count,
		Player: p.char.Name, Ref: ref, EtcType: t.EtcType,
	})
}

func (w *World) logRef(s *state, object int32) string {
	if n, ok := s.npcs[object]; ok {
		return n.tmpl.Name
	}
	if q, ok := s.players[object]; ok {
		return "player " + q.char.Name
	}
	return itemlog.Null
}
