// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"fmt"
	"math"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/stat"
)

type player struct {
	creature
	char                       Character
	stats                      *stat.Player
	stream                     *eventStream
	outbox                     []Event
	slow                       bool
	known                      map[int32]bool
	cp                         float64
	sitting                    bool
	lastSitStand               time.Time
	lastTarget                 int32
	lastTargetAt               time.Time
	memSkills                  []Skill
	zone                       catalog.ZoneState
	teleporting, pendingRevive bool
	lastFolk                   int32
	bypass, bypassPrefix       []string
	dialogue                   string
	dialogueHTML               string
	dialogueSpeaker            Ref
	affordances                []Affordance
	fence                      Fence
	itemRefs                   map[int32]Ref
	reuse                      map[int32]reuseStamp
	learning                   bool
	learningWrite              bool
}

// Event is a state notification, not an applied-effect receipt.
type Event interface{ event() }

// Moving announces an authoritative movement destination.
type Moving struct {
	ObjectID int32
	From, To Point
}

func (Moving) event() {}

// Stopped announces the end of movement at an authoritative position.
type Stopped struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}

func (Stopped) event() {}

// LeftWorld announces departure before the character is saved.
type LeftWorld struct{ ObjectID int32 }

func (LeftWorld) event() {}

// Self is a detached view of a player's current state.
type Self struct {
	Entity                              Ref
	Char                                Character
	RunSpeed, WalkSpeed                 int32
	Dead, Running, Sitting, Teleporting bool
	PAtk, PDef                          int32
	PDefKnown                           bool
}

// Entry supplies a ready character's initial self view and live notifications.
type Entry struct {
	Self         Self
	Events       <-chan Event
	Subscription Subscription
}

func (p *player) base() *creature { return &p.creature }
func (p *player) moveSpeed() float64 {
	if p.running {
		return float64(p.stats.RunSpeed())
	}
	return float64(p.stats.WalkSpeed())
}
func (p *player) isPlayer() bool     { return true }
func (p *player) collision() int32   { r, _ := p.stats.Collision(); return int32(r) }
func (p *player) level() int32       { return p.char.Level }
func (p *player) maxHP() int32       { return p.stats.MaxHP() }
func (p *player) maxMP() int32       { return p.stats.MaxMP() }
func (p *player) pAtk() float64      { return float64(p.stats.PAtk()) }
func (p *player) pDef() float64      { return float64(p.stats.PDef()) }
func (p *player) pAtkSpd() int32     { return p.stats.PAtkSpd() }
func (p *player) accuracy() int32    { return p.stats.Accuracy() }
func (p *player) evasion() int32     { return p.stats.Evasion() }
func (p *player) critical() int32    { return p.stats.Critical() }
func (p *player) attackRange() int32 { return playerAttackRange }
func (p *player) dex() int32         { return p.stats.DEX() }
func (p *player) tryingToSitOrStand(now time.Time) bool {
	return now.Before(p.lastSitStand.Add(sitStandLock))
}
func (p *player) randomDamage() float64 {
	if t, ok := p.stats.Worn[catalog.SlotRHand]; ok {
		return float64(t.RandomDamage) / 100
	}
	return 0.1
}
func (p *player) send(e Event) {
	if !p.slow {
		p.outbox = append(p.outbox, e)
	}
}
func (p *player) close(reason CloseReason) {
	if !p.slow {
		p.slow = true
		p.stream.close(reason)
	}
}
func (p *player) deliver(e Event) {
	if p.slow {
		return
	}
	e = cloneEvent(e)
	select {
	case p.stream.events <- e:
	default:
		p.close(CloseOverflow)
	}
}
func (w *World) statsOf(c Character) (*stat.Player, error) {
	if w.s == nil {
		return nil, ErrUninitialized
	}
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
	st.SkillFuncs = w.skillFuncs(c.Skills, st.Worn)
	return st, nil
}
func (w *World) selfOf(p *player) Self {
	c := p.char.Clone()
	c.X, c.Y, c.Z, c.Heading = p.pos.X, p.pos.Y, p.pos.Z, p.heading
	c.MaxHP, c.MaxMP, c.MaxCP = float64(p.stats.MaxHP()), float64(p.stats.MaxMP()), float64(p.stats.MaxCP())
	c.CurHP, c.CurMP, c.CurCP = p.hp, p.mp, p.cp
	return Self{Entity: p.ref(), Char: c, RunSpeed: p.stats.RunSpeed(), WalkSpeed: p.stats.WalkSpeed(), Dead: p.dead, Running: p.running, Sitting: p.sitting, Teleporting: p.teleporting, PAtk: p.stats.PAtk(), PDef: p.stats.PDef(), PDefKnown: p.stats.PDefKnown()}
}

// Enter loads a ready character at its stored position. It grants no items,
// skills or quests and does not select a start point.
func (w *World) Enter(ctx context.Context, c Character) (Entry, error) {
	c = c.Clone()
	if err := validateReady(c); err != nil {
		return Entry{}, err
	}
	st, err := w.statsOf(c)
	if err != nil {
		return Entry{}, err
	}
	if st.MaxHP() < 1 || st.MaxMP() < 0 || st.MaxCP() < 0 || st.PAtk() < 0 || st.PDef() < 1 || st.PAtkSpd() < 1 {
		return Entry{}, fmt.Errorf("world: invalid computed stats")
	}
	if w.store == nil {
		return Entry{}, fmt.Errorf("world: missing store")
	}
	var entry Entry
	var result error
	err = w.doCommand(ctx, c.ObjectID, VerbEnter, func(s *state) {
		seen, validationErr := s.entryIDs(c)
		if validationErr != nil {
			result = validationErr
			s.finish(StageRefused)
			return
		}
		for id := range seen {
			s.reserved[id] = true
			s.ids.reserve(id)
		}
		p, allocationErr := s.enterPlayer(c, st)
		if allocationErr != nil {
			result = allocationErr
			return
		}
		s.players[c.ObjectID] = p
		s.group([]Effect{ExistenceChanged{Entity: p.ref(), After: true}})
		s.finish(StageDone)
		for _, message := range w.announcements {
			p.send(Announcement{Text: message})
		}
		w.place(s, p)
		w.revalidateZone(s, p)
		if p.hp < float64(p.maxHP()) || p.mp < float64(p.maxMP()) || p.cp < float64(st.MaxCP()) {
			w.startRegen(s, p)
		}
		entry = Entry{Self: w.selfOf(p), Events: p.stream.events, Subscription: p.stream.subscription()}
	})
	if err != nil {
		return Entry{}, err
	}
	return entry, result
}

func validateReady(c Character) error {
	if c.ObjectID <= 0 || c.Level < 1 || c.Level > 1000 || c.Exp < 0 || c.SP < 0 || c.Heading < 0 || c.Heading > 65535 {
		return fmt.Errorf("world: invalid ready character")
	}
	for _, v := range []float64{c.CurHP, c.CurMP, c.CurCP} {
		if math.IsNaN(v) || math.IsInf(v, 0) || v < 0 {
			return fmt.Errorf("world: invalid vitals")
		}
	}
	return nil
}

func (s *state) enterPlayer(c Character, st *stat.Player) (*player, error) {
	generation := s.incarnation()
	if generation == 0 {
		return nil, ErrExhausted
	}
	p := &player{creature: creature{id: c.ObjectID, generation: generation, pos: Point{c.X, c.Y, c.Z}, heading: c.Heading,
		running: true, hp: min(c.CurHP, float64(st.MaxHP())), mp: min(c.CurMP, float64(st.MaxMP())), dead: c.CurHP <= 0,
		listeners: map[int32]bool{}, regen: time.Now()}, char: c, stats: st, stream: newEventStream(), known: map[int32]bool{}, cp: min(c.CurCP, float64(st.MaxCP()))}
	p.itemRefs = make(map[int32]Ref, len(c.Items))
	for _, it := range c.Items {
		gen := s.incarnation()
		if gen == 0 {
			return nil, ErrExhausted
		}
		p.itemRefs[it.ObjectID] = Ref{ID: it.ObjectID, Gen: gen}
	}
	return p, nil
}

func (s *state) entryIDs(c Character) (map[int32]bool, error) {
	if _, ok := s.players[c.ObjectID]; ok {
		return nil, ErrPresent
	}
	if _, ok := s.pending[c.ObjectID]; ok {
		return nil, ErrPresent
	}
	if _, ok := s.actor(c.ObjectID); ok {
		return nil, ErrPresent
	}
	seen := map[int32]bool{c.ObjectID: true}
	for _, it := range c.Items {
		if it.ObjectID <= 0 || it.Count <= 0 || seen[it.ObjectID] {
			return nil, fmt.Errorf("world: invalid ready item %d", it.ObjectID)
		}
		if _, ok := s.actor(it.ObjectID); ok {
			return nil, ErrPresent
		}
		if _, ok := s.ground[it.ObjectID]; ok {
			return nil, ErrPresent
		}
		for _, other := range s.players {
			for _, held := range other.char.Items {
				if held.ObjectID == it.ObjectID {
					return nil, ErrPresent
				}
			}
		}
		seen[it.ObjectID] = true
	}
	for _, pending := range s.pending {
		if seen[pending.char.ObjectID] {
			return nil, ErrPresent
		}
		for _, it := range pending.char.Items {
			if seen[it.ObjectID] {
				return nil, ErrPresent
			}
		}
	}
	if _, ok := s.ground[c.ObjectID]; ok {
		return nil, ErrPresent
	}
	for _, other := range s.players {
		for _, held := range other.char.Items {
			if held.ObjectID == c.ObjectID {
				return nil, ErrPresent
			}
		}
	}
	return seen, nil
}

// Snapshot returns detached self state without replaying effects.
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
	err := w.doCommand(ctx, id, VerbMove, func(s *state) {
		p, ok := s.players[id]
		if !ok {
			result = ErrNotPresent
			s.finish(StageRefused)
			return
		}
		if !w.validMove(p, to) {
			result = ErrMove
			s.finish(StageRefused)
			return
		}
		if w.ground != nil {
			to.X, to.Y, to.Z = w.ground.Walk(p.pos.X, p.pos.Y, p.pos.Z, to.X, to.Y, to.Z)
		}
		w.setIntention(s, p, order{intent: intentMoveTo, pos: to})
	})
	if err != nil {
		return err
	}
	return result
}

// Leave removes the character and closes events before saving on the caller.
// A failed save leaves a detached pending snapshot; another Leave retries it.
func (w *World) Leave(ctx context.Context, id int32) error {
	return w.leave(ctx, id, false)
}
func (w *World) leave(ctx context.Context, id int32, voluntary bool) error {
	var c Character
	var pending *pendingSave
	var result error
	err := w.doCommand(ctx, id, VerbLeave, func(s *state) {
		if p, ok := s.players[id]; ok {
			if voluntary && time.Now().Before(s.stance[id]) {
				result = ErrCombat
				s.finish(StageRefused)
				return
			}
			if p.learningWrite {
				result = ErrSaving
				s.finish(StageRefused)
				return
			}
			c = w.selfOf(p).Char
			p.attackSeq++
			p.castSeq++
			s.setAttacking(p.base(), 0)
			s.setBool(p.ref(), PropertyCasting, &p.casting, false)
			s.change(p.ref(), PropertyMovement, boolValue(p.move != nil), IntValue(0))
			p.move = nil
			p.regenOn = false
			if !p.teleporting {
				w.decayPlayer(s, p)
			}
			p.teleporting = true
			s.change(p.ref(), PropertyCombatUntil, TimeValue(s.at(s.stance[id])), TimeValue(0))
			delete(s.stance, id)
			c.LastAccess = time.Now().UnixMilli()
			for _, e := range p.outbox {
				p.deliver(e)
			}
			p.outbox = nil
			p.deliver(LeftWorld{ObjectID: id})
			p.close(CloseLeave)
			s.group([]Effect{ExistenceChanged{Entity: p.ref(), Before: true}})
			s.finish(StageDone)
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
			s.finish(StageRefused)
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
		clearErr := w.do(context.Background(), func(s *state) { pending.saving = false })
		return fmt.Errorf("world: save character: %w", errors.Join(err, clearErr))
	}
	return w.do(context.Background(), func(s *state) {
		if s.pending[id] == pending {
			delete(s.pending, id)
		}
	})
}
