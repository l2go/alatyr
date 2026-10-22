// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"fmt"
	"math"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world/stat"
)

type player struct {
	creature
	char   Character
	stats  *stat.Player
	out    chan Event
	outbox []Event
	slow   bool
}
type Event interface{ event() }
type Moving struct {
	ObjectID int32
	From, To Point
}

func (Moving) event() {}

type Stopped struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}

func (Stopped) event() {}

type LeftWorld struct{ ObjectID int32 }

func (LeftWorld) event() {}

type Self struct {
	Char                Character
	RunSpeed, WalkSpeed int32
}
type Entry struct {
	Self   Self
	Events <-chan Event
}

func (p *player) base() *creature    { return &p.creature }
func (p *player) moveSpeed() float64 { return float64(p.stats.RunSpeed()) }
func (p *player) send(e Event) {
	if !p.slow {
		p.outbox = append(p.outbox, e)
	}
}
func (p *player) close() {
	if !p.slow {
		p.slow = true
		close(p.out)
	}
}
func (p *player) deliver(e Event) {
	if p.slow {
		return
	}
	select {
	case p.out <- e:
	default:
		p.close()
	}
}
func (w *World) statsOf(c Character) (*stat.Player, error) {
	if w.cat == nil {
		return nil, fmt.Errorf("world: missing catalog")
	}
	t, ok := w.cat.Player(c.ClassID)
	if !ok {
		return nil, fmt.Errorf("world: no template of class %d", c.ClassID)
	}
	var items []stat.WornItem
	for _, it := range c.Items {
		if _, ok := w.cat.Item(it.TemplateID); !ok {
			return nil, fmt.Errorf("world: unknown item template %d", it.TemplateID)
		}
		if it.Equipped() {
			items = append(items, stat.WornItem{TemplateID: it.TemplateID, Slot: it.Slot})
		}
	}
	worn, funcs := stat.Worn(w.cat, items)
	st := &stat.Player{Template: t, Level: c.Level, Female: c.Female, Worn: worn, Funcs: funcs}
	for _, k := range c.Skills {
		st.SkillFuncs = append(st.SkillFuncs, w.cat.PassiveFuncs(k.ID, k.Level)...)
	}
	return st, nil
}
func (w *World) selfOf(p *player) Self {
	c := p.char.Clone()
	c.X, c.Y, c.Z, c.Heading = p.pos.X, p.pos.Y, p.pos.Z, p.heading
	c.MaxHP, c.MaxMP, c.MaxCP = float64(p.stats.MaxHP()), float64(p.stats.MaxMP()), float64(p.stats.MaxCP())
	return Self{Char: c, RunSpeed: p.stats.RunSpeed(), WalkSpeed: p.stats.WalkSpeed()}
}

// Enter loads a ready character at its stored position. It grants no items,
// skills or quests and does not select a start point.
func (w *World) Enter(ctx context.Context, c Character) (Entry, error) {
	c = c.Clone()
	if c.ObjectID <= 0 || c.Level < 1 {
		return Entry{}, fmt.Errorf("world: invalid ready character")
	}
	for _, v := range []float64{c.CurHP, c.CurMP, c.CurCP} {
		if math.IsNaN(v) || math.IsInf(v, 0) || v < 0 {
			return Entry{}, fmt.Errorf("world: invalid vitals")
		}
	}
	st, err := w.statsOf(c)
	if err != nil {
		return Entry{}, err
	}
	if w.store == nil {
		return Entry{}, fmt.Errorf("world: missing store")
	}
	var entry Entry
	var result error
	err = w.do(ctx, func(s *state) {
		if _, ok := s.players[c.ObjectID]; ok {
			result = ErrPresent
			return
		}
		if _, ok := s.pending[c.ObjectID]; ok {
			result = ErrPresent
			return
		}
		p := &player{creature: creature{id: c.ObjectID, pos: Point{c.X, c.Y, c.Z}, heading: c.Heading}, char: c, stats: st, out: make(chan Event, 64)}
		s.players[c.ObjectID] = p
		entry = Entry{Self: w.selfOf(p), Events: p.out}
	})
	if err != nil {
		return Entry{}, err
	}
	return entry, result
}
func (w *World) Snapshot(ctx context.Context, id int32) (Self, error) {
	var self Self
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[id]
		if !ok {
			result = ErrNotPresent
			return
		}
		self = w.selfOf(p)
	})
	if err != nil {
		return Self{}, err
	}
	return self, result
}

// MoveTo uses server positions and a straight walk without a terrain provider.
// The destination is a world position, not a client click or a ground height.
func (w *World) MoveTo(ctx context.Context, id int32, to Point) error {
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[id]
		if !ok {
			result = ErrNotPresent
			return
		}
		dx, dy := float64(to.X)-float64(p.pos.X), float64(to.Y)-float64(p.pos.Y)
		if math.Hypot(dx, dy) > 9900 || p.char.CurHP <= 0 || p.moveSpeed() <= 0 {
			result = ErrMove
			return
		}
		from := p.pos
		w.moveTo(s, p, to, 0)
		if p.move != nil {
			p.send(Moving{ObjectID: id, From: from, To: to})
		}
	})
	if err != nil {
		return err
	}
	return result
}

// Leave removes the character and closes events before saving on the caller.
// A failed save leaves a detached pending snapshot; another Leave retries it.
func (w *World) Leave(ctx context.Context, id int32) error {
	var c Character
	var pending *pendingSave
	var result error
	err := w.do(ctx, func(s *state) {
		if p, ok := s.players[id]; ok {
			c = w.selfOf(p).Char
			c.LastAccess = time.Now().UnixMilli()
			for _, e := range p.outbox {
				p.deliver(e)
			}
			p.outbox = nil
			p.deliver(LeftWorld{ObjectID: id})
			p.close()
			delete(s.players, id)
			s.moving = slices.DeleteFunc(s.moving, func(moving int32) bool { return moving == id })
			s.pending[id] = &pendingSave{char: c.Clone()}
		}
		pending = s.pending[id]
		if pending == nil {
			return
		}
		if pending.saving {
			result = ErrSaving
			return
		}
		pending.saving = true
		c = pending.char.Clone()
	})
	if err != nil {
		return err
	}
	if result != nil {
		return result
	}
	if pending == nil {
		return nil
	}
	if err = w.store.PutCharacter(ctx, c); err != nil {
		// Restore retryability even when the store observed caller cancellation.
		_ = w.do(context.Background(), func(s *state) { pending.saving = false })
		return fmt.Errorf("world: save character: %w", err)
	}
	return w.do(context.Background(), func(s *state) {
		if s.pending[id] == pending {
			delete(s.pending, id)
		}
	})
}
