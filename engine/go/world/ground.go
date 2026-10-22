// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
	"math"
	"slices"
	"time"
)

const (
	lootProtection = 15 * time.Second
	autoDestroy    = 600 * time.Second
)

type groundItem struct {
	id, templateID      int32
	count               int64
	pos                 Point
	owner               int32
	ownerUntil, dropped time.Time
}

func (w *World) dropOnGround(s *state, n *npc, killer *player, templateID int32, count int64) {
	if count <= 0 {
		return
	}
	g := &groundItem{id: s.newID(), templateID: templateID, count: count,
		pos:   Point{offsetCoordinate(n.pos.X, int32(s.rng.IntN(141))-70), offsetCoordinate(n.pos.Y, int32(s.rng.IntN(141))-70), offsetCoordinate(max(n.pos.Z, killer.pos.Z), 20)},
		owner: killer.id, ownerUntil: time.Now().Add(lootProtection), dropped: time.Now()}
	if g.id == 0 {
		return
	}
	g.pos = w.onGround(g.pos)
	s.ground[g.id] = g
	w.logItem(itemlog.Create, "Loot", g.id, templateID, count, killer, n.tmpl.Name)
	w.showGround(s, g, n.id)
	s.after(autoDestroy, func(s *state) {
		if s.ground[g.id] == g {
			w.removeFromGround(s, g)
			w.items.Write(itemlog.Entry{Op: itemlog.Delete, Process: "Decay", ObjectID: g.id, Item: w.itemName(g.templateID), Player: itemlog.Null, Ref: itemlog.Null})
		}
	})
}
func offsetCoordinate(base, delta int32) int32 {
	return int32(min(max(int64(base)+int64(delta), math.MinInt32), math.MaxInt32))
}
func (w *World) itemName(id int32) string { t, _ := w.cat.Item(id); return t.Name }
func (w *World) showGround(s *state, g *groundItem, dropper int32) {
	t, _ := w.cat.Item(g.templateID)
	for _, p := range s.players {
		if !p.teleporting && distance3D(p.pos, g.pos) <= playerWatch(len(p.known)) {
			p.known[g.id] = true
			p.send(ItemDropped{ObjectID: g.id, TemplateID: g.templateID, Pos: g.pos, Count: g.count, Stackable: t.Stackable(), DropperID: dropper})
		}
	}
}
func (w *World) giveItem(s *state, p *player, n *npc, templateID int32, count int64) {
	id := s.newID()
	if id == 0 {
		return
	}
	_ = w.addItem(s, p, Item{ObjectID: id, TemplateID: templateID, Count: count, Slot: -1}, "Loot", n.tmpl.Name, false)
}
func (w *World) load(p *player) int64 {
	var total int64
	for _, it := range p.char.Items {
		t, _ := w.cat.Item(it.TemplateID)
		if t.Weight > 0 && it.Count > (math.MaxInt64-total)/int64(t.Weight) {
			return math.MaxInt64
		}
		total += int64(max(t.Weight, 0)) * it.Count
	}
	return total
}
func (w *World) canAdd(p *player, it Item) bool {
	t, ok := w.cat.Item(it.TemplateID)
	if !ok || it.Count <= 0 {
		return false
	}
	if !t.Stackable() && it.Count != 1 {
		return false
	}
	if t.Stackable() {
		for _, held := range p.char.Items {
			if held.TemplateID == it.TemplateID && !held.Equipped() && it.Count > math.MaxInt64-held.Count {
				return false
			}
		}
	}
	return true
}
func (w *World) addItem(s *state, p *player, it Item, process, ref string, existed bool) error {
	if !w.canAdd(p, it) {
		return errors.New("world: invalid inventory quantity")
	}
	t, _ := w.cat.Item(it.TemplateID)
	i := -1
	change := "added"
	if t.Stackable() {
		i = slices.IndexFunc(p.char.Items, func(o Item) bool { return o.TemplateID == it.TemplateID && !o.Equipped() })
	}
	if i >= 0 {
		p.char.Items[i].Count += it.Count
		change = "modified"
		w.logItem(itemlog.Change, process, p.char.Items[i].ObjectID, it.TemplateID, p.char.Items[i].Count, p, ref)
		if existed {
			w.logItem(itemlog.Delete, process, it.ObjectID, it.TemplateID, 0, p, ref)
		}
	} else {
		p.char.Items = append(p.char.Items, it)
		i = len(p.char.Items) - 1
		op := itemlog.Create
		if existed {
			op = itemlog.Change
		}
		w.logItem(op, process, it.ObjectID, it.TemplateID, it.Count, p, ref)
	}
	p.send(InventoryChanged{Changes: []ItemChange{{Kind: change, Item: p.char.Items[i]}}, Load: w.load(p)})
	return nil
}
func (w *World) doPickup(s *state, p *player, item int32) {
	g, ok := s.ground[item]
	if !ok || p.dead || p.teleporting || g.owner != 0 && g.owner != p.id && time.Now().Before(g.ownerUntil) {
		p.send(RequestIgnored{})
		return
	}
	it := Item{ObjectID: g.id, TemplateID: g.templateID, Count: g.count, Slot: -1}
	if !w.canAdd(p, it) {
		p.send(RequestIgnored{})
		return
	}
	w.broadcastToKnown(s, p, ItemPicked{PlayerID: p.id, ObjectID: g.id, Pos: g.pos})
	w.removeFromGround(s, g)
	_ = w.addItem(s, p, it, "Pickup", itemlog.Null, true)
}
func (w *World) Pickup(ctx context.Context, id, item int32) error {
	return w.act(ctx, id, func(s *state, p *player) {
		if p.dead || p.teleporting || !p.known[item] {
			p.send(RequestIgnored{})
			return
		}
		w.setIntention(s, p, order{intent: intentPickUp, object: item})
	})
}

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
