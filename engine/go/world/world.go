// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	crand "crypto/rand"
	"errors"
	"math/rand/v2"
	"sync/atomic"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

func (w *World) Run(ctx context.Context) error {
	if !w.running.CompareAndSwap(false, true) {
		return ErrRunning
	}
	defer close(w.stopped)
	defer func() {
		for _, p := range w.s.players {
			p.close()
		}
	}()
	s := w.s
	timer := time.NewTimer(time.Hour)
	defer timer.Stop()
	for {
		wait, pending := s.runDue()
		w.flush(s)
		if !pending {
			wait = time.Hour
		}
		timer.Reset(wait)
		select {
		case <-ctx.Done():
			return nil
		case cmd := <-w.cmds:
			cmd(s)
			w.flush(s)
		case <-timer.C:
		}
	}
}

func (w *World) do(ctx context.Context, f func(*state)) error {
	done := make(chan struct{})
	select {
	case w.cmds <- func(s *state) { f(s); w.flush(s); close(done) }:
	case <-w.stopped:
		return ErrStopped
	case <-ctx.Done():
		return ctx.Err()
	}
	// Once accepted, the bounded command completes even if its caller cancels.
	<-done
	return nil
}

var (
	ErrStopped    = errors.New("world: stopped")
	ErrRunning    = errors.New("world: Run already called")
	ErrNotPresent = errors.New("world: character not present")
	ErrPresent    = errors.New("world: character already present or awaiting persistence")
	ErrMove       = errors.New("world: invalid movement")
	ErrSaving     = errors.New("world: save already in progress")
	ErrCombat     = errors.New("world: combat prevents logout")
)

type Store interface {
	PutCharacter(context.Context, Character) error
}
type World struct {
	cat           *catalog.Catalog
	store         Store
	cmds          chan func(*state)
	stopped       chan struct{}
	running       atomic.Bool
	s             *state
	items         *itemlog.Log
	started       time.Time
	experience    []int64
	announcements []string
	ground        Ground
}
type state struct {
	players                                                         map[int32]*player
	pending                                                         map[int32]*pendingSave
	moving                                                          []int32
	ticks                                                           int64
	timers                                                          tasks
	seq                                                             uint64
	npcs                                                            map[int32]*npc
	ground                                                          map[int32]*groundItem
	regions                                                         map[regionKey]*region
	thinkers, animators, followers, broadcastQueue, revalidateQueue []int32
	stance                                                          map[int32]time.Time
	stanceOrder, regenOrder                                         []int32
	decay                                                           []decayEntry
	rng                                                             *rand.Rand
	ids                                                             *ids
	reserved                                                        map[int32]bool
}

type pendingSave struct {
	char   Character
	saving bool
}

func New(cat *catalog.Catalog, store Store, options ...Option) *World {
	w := &World{cat: cat, store: store, cmds: make(chan func(*state)), stopped: make(chan struct{}), s: &state{players: map[int32]*player{}, pending: map[int32]*pendingSave{}}}
	w.started = time.Now()
	w.items = itemlog.Discard
	w.ground = NoGround{}
	var seed [32]byte
	crand.Read(seed[:])
	w.s.rng = rand.New(rand.NewChaCha8(seed))
	w.s.npcs = map[int32]*npc{}
	w.s.ground = map[int32]*groundItem{}
	w.s.regions = map[regionKey]*region{}
	w.s.stance = map[int32]time.Time{}
	w.s.reserved = map[int32]bool{}
	for _, option := range options {
		option(w)
	}
	var used []int32
	for id := range w.s.reserved {
		used = append(used, id)
	}
	w.s.ids = newIDs(used)
	if cat != nil {
		w.spawnAll(w.s)
	}
	w.startManagers(w.s)
	w.startGameClock(w.s)
	w.s.after(decayPeriod, w.tickDecay)
	return w
}
func (w *World) flush(s *state) {
	for _, p := range s.players {
		for _, e := range p.outbox {
			if _, ok := e.(SelfInfo); ok {
				e = SelfInfo{Self: w.selfOf(p)}
			}
			p.deliver(e)
		}
		p.outbox = nil
	}
}
