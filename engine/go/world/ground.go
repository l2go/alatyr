// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"math"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

const (
	lootProtection = 15 * time.Second
	autoDestroy    = 600 * time.Second
)

type groundItem struct {
	id, templateID      int32
	generation          uint64
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
	g.generation = s.incarnation()
	if g.generation == 0 {
		return
	}
	g.pos = w.onGround(g.pos)
	s.ground[g.id] = g
	s.group([]Effect{ExistenceChanged{Entity: g.ref(), After: true}, ResourceTransferred{Resource: Resource{Kind: ResourceItem, TemplateID: templateID}, From: ResourceLocation{Place: PlaceReward}, To: ResourceLocation{Entity: g.ref(), Place: PlaceGround}, Amount: IntegerAmount(count), ToBalance: Balance{After: IntegerAmount(count)}}}, Fact{Subject: g.ref(), Property: PropertyPosition, After: PointValue(g.pos)})
	w.logItem(itemlog.Create, "Loot", g.id, templateID, count, killer, n.tmpl.Name)
	w.showGround(s, g, n.id)
	s.afterRule(autoDestroy, RuleItemDecay, func(s *state) {
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
			if !p.known[g.id] {
				s.change(p.ref(), PropertyKnownObject, RefValue(Ref{}), RefValue(g.ref()))
			}
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
	if err := w.addItem(s, p, Item{ObjectID: id, TemplateID: templateID, Count: count, Slot: -1}, "Loot", n.tmpl.Name, false); err != nil {
		// A reward that cannot fit the inventory is not granted. No resource
		// transfer is recorded, and the allocated ID remains reserved.
		p.send(ActionIgnored{})
	}
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
	w.insertItem(s, p, it, process, ref, existed)
	return s.err
}

// insertItem is used only after canAdd succeeds in the same owning-loop turn.
func (w *World) insertItem(s *state, p *player, it Item, process, ref string, existed bool) {
	t, _ := w.cat.Item(it.TemplateID)
	i := -1
	change := "added"
	before := int64(0)
	if t.Stackable() {
		i = slices.IndexFunc(p.char.Items, func(o Item) bool { return o.TemplateID == it.TemplateID && !o.Equipped() })
	}
	if i >= 0 {
		before = p.char.Items[i].Count
		p.char.Items[i].Count += it.Count
		change = "modified"
		w.logItem(itemlog.Change, process, p.char.Items[i].ObjectID, it.TemplateID, p.char.Items[i].Count, p, ref)
		if existed {
			w.logItem(itemlog.Delete, process, it.ObjectID, it.TemplateID, 0, p, ref)
		}
	} else {
		if p.itemRefs == nil {
			p.itemRefs = map[int32]Ref{}
		}
		if !existed {
			gen := s.incarnation()
			if gen == 0 {
				return
			}
			p.itemRefs[it.ObjectID] = Ref{ID: it.ObjectID, Gen: gen}
		}
		p.char.Items = append(p.char.Items, it)
		i = len(p.char.Items) - 1
		op := itemlog.Create
		if existed {
			op = itemlog.Change
		}
		w.logItem(op, process, it.ObjectID, it.TemplateID, it.Count, p, ref)
	}
	if !existed {
		s.itemTransfer(p, p.itemRefs[p.char.Items[i].ObjectID], Resource{Kind: ResourceItem, TemplateID: it.TemplateID}, ResourceLocation{Place: PlaceReward}, it.Count, before, p.char.Items[i].Count)
	}
	p.send(InventoryChanged{Changes: []ItemChange{{Kind: change, Item: p.char.Items[i]}}, Load: w.load(p)})
}
func (w *World) doPickup(s *state, p *player, item int32) {
	g, ok := s.ground[item]
	if !ok || p.dead || p.teleporting || g.owner != 0 && g.owner != p.id && time.Now().Before(g.ownerUntil) {
		p.send(ActionIgnored{})
		return
	}
	it := Item{ObjectID: g.id, TemplateID: g.templateID, Count: g.count, Slot: -1}
	if !w.canAdd(p, it) {
		p.send(ActionIgnored{})
		return
	}
	w.broadcastToKnown(s, p, ItemPicked{PlayerID: p.id, ObjectID: g.id, Pos: g.pos})
	before := int64(0)
	tmpl, _ := w.cat.Item(it.TemplateID)
	if tmpl.Stackable() {
		for _, held := range p.char.Items {
			if held.TemplateID == it.TemplateID && !held.Equipped() {
				before = held.Count
				break
			}
		}
	}
	w.eraseGround(s, g)
	if before == 0 {
		if p.itemRefs == nil {
			p.itemRefs = map[int32]Ref{}
		}
		p.itemRefs[it.ObjectID] = g.ref()
	}
	w.insertItem(s, p, it, "Pickup", itemlog.Null, true)
	after := before + it.Count
	row := slices.IndexFunc(p.char.Items, func(held Item) bool { return held.ObjectID == it.ObjectID })
	if before != 0 {
		row = slices.IndexFunc(p.char.Items, func(held Item) bool { return held.TemplateID == it.TemplateID && !held.Equipped() })
	}
	effects := []Effect{ResourceTransferred{Resource: Resource{Kind: ResourceItem, TemplateID: it.TemplateID}, From: ResourceLocation{Entity: g.ref(), Place: PlaceGround}, To: ResourceLocation{Entity: p.ref(), Item: p.itemRefs[p.char.Items[row].ObjectID], Place: PlaceInventory}, Amount: IntegerAmount(it.Count), FromBalance: Balance{Before: IntegerAmount(it.Count)}, ToBalance: Balance{Before: IntegerAmount(before), After: IntegerAmount(after)}}}
	if before != 0 {
		effects = append(effects, ExistenceChanged{Entity: g.ref(), Before: true})
	}
	s.group(effects)
}

// Pickup starts an intention to collect a known ground item.
func (w *World) Pickup(ctx context.Context, id, item int32) error {
	return w.act(ctx, id, VerbPickup, func(s *state, p *player) {
		if p.dead || p.teleporting || !p.known[item] {
			p.send(ActionIgnored{})
			return
		}
		w.setIntention(s, p, order{intent: intentPickUp, object: item})
	})
}

func (w *World) removeFromGround(s *state, g *groundItem) {
	s.group([]Effect{ResourceTransferred{Resource: Resource{Kind: ResourceItem, TemplateID: g.templateID}, From: ResourceLocation{Entity: g.ref(), Place: PlaceGround}, To: ResourceLocation{Place: PlaceDecay}, Amount: IntegerAmount(g.count), FromBalance: Balance{Before: IntegerAmount(g.count)}}, ExistenceChanged{Entity: g.ref(), Before: true}})
	w.eraseGround(s, g)
}
func (w *World) eraseGround(s *state, g *groundItem) {
	delete(s.ground, g.id)
	for _, p := range s.players {
		if p.known[g.id] {
			s.change(p.ref(), PropertyKnownObject, RefValue(g.ref()), RefValue(Ref{}))
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
