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

// Run owns mutable gameplay until cancellation or identity exhaustion.
// Shutdown closes notifications but does not save characters implicitly.
func (w *World) Run(ctx context.Context) error {
	if w.s == nil {
		return ErrUninitialized
	}
	if !w.running.CompareAndSwap(false, true) {
		return ErrRunning
	}
	defer close(w.stopped)
	defer func() {
		for _, p := range w.s.players {
			p.close(CloseStop)
		}
	}()
	s := w.s
	timer := time.NewTimer(time.Hour)
	defer timer.Stop()
	for {
		if s.err != nil {
			return s.err
		}
		wait, pending := s.runDue()
		if s.err != nil {
			return s.err
		}
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
	return w.submit(ctx, func(s *state) { s.rule(RuleHost, func() { f(s) }) })
}

func (w *World) submit(ctx context.Context, f func(*state)) error {
	if w.s == nil {
		return ErrUninitialized
	}
	done := make(chan struct{})
	var result error
	select {
	case w.cmds <- func(s *state) {
		f(s)
		w.flush(s)
		result = s.err
		close(done)
	}:
	case <-w.stopped:
		return ErrStopped
	case <-ctx.Done():
		return ctx.Err()
	}
	// Once accepted, the bounded command completes even if its caller cancels.
	<-done
	return result
}

func (w *World) doCommand(ctx context.Context, id int32, verb Verb, f func(*state)) error {
	return w.submit(ctx, func(s *state) { s.command(s.ref(id), verb, func() { f(s) }) })
}

var (
	// ErrStopped reports a command submitted after Run ended.
	ErrStopped = errors.New("world: stopped")
	// ErrRunning reports a second call to Run.
	ErrRunning = errors.New("world: run already called")
	// ErrNotPresent reports a missing live character.
	ErrNotPresent = errors.New("world: character not present")
	// ErrPresent reports an identity in use or awaiting persistence.
	ErrPresent = errors.New("world: character already present or awaiting persistence")
	// ErrMove reports movement rejected before execution.
	ErrMove = errors.New("world: invalid movement")
	// ErrSaving reports an overlapping persistence operation.
	ErrSaving = errors.New("world: save already in progress")
	// ErrCombat reports voluntary logout during combat stance.
	ErrCombat = errors.New("world: combat prevents logout")
	// ErrExhausted reports exhaustion of a monotonic identity or sequence.
	ErrExhausted = errors.New("world: identity exhausted")
	// ErrUninitialized reports use of a World not constructed by New.
	ErrUninitialized = errors.New("world: uninitialized")
)

// Store persists detached characters. The host owns retries and transactions.
// Durable implementations also provide PutLearning(context.Context, Character)
// to atomically persist a captured SP balance and its learned skills.
type Store interface {
	PutCharacter(context.Context, Character) error
}

// World is a single-owner gameplay simulation. Construct it with New.
// The zero value returns ErrUninitialized instead of starting a loop.
type World struct {
	cat             *catalog.Catalog
	store           Store
	cmds            chan func(*state)
	stopped         chan struct{}
	running         atomic.Bool
	s               *state
	items           *itemlog.Log
	started         time.Time
	experience      []int64
	announcements   []string
	ground          Ground
	durability      func(Durability)
	idFirst, idLast int32
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
	started                                                         time.Time
	generation                                                      uint64
	err                                                             error
	commandSeq, commitSeq                                           uint64
	affordanceSeq                                                   uint64
	eventSeq                                                        uint64
	batch                                                           *commitBatch
	observe                                                         func(Commit)
}

type pendingSave struct {
	owner  Ref
	char   Character
	saving bool
}

// New constructs a world and applies options before spawning. It starts no
// goroutines; the host calls Run and owns its cancellation.
func New(cat *catalog.Catalog, store Store, options ...Option) *World {
	w := &World{cat: cat, store: store, cmds: make(chan func(*state)), stopped: make(chan struct{}), s: &state{players: map[int32]*player{}, pending: map[int32]*pendingSave{}}}
	w.started = time.Now()
	w.s.started = w.started
	w.items = itemlog.Discard
	w.ground = NoGround{}
	var seed [32]byte
	// crypto/rand.Read fills the slice or terminates the process in modern Go.
	_, _ = crand.Read(seed[:])
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
	if w.idFirst > 0 {
		w.s.ids.last, w.s.ids.limit = w.idFirst-1, w.idLast
	}
	if cat != nil {
		w.s.rule(RuleSpawn, func() { w.spawnAll(w.s) })
	}
	w.startManagers(w.s)
	w.startGameClock(w.s)
	w.s.afterRule(decayPeriod, RuleDecay, w.tickDecay)
	return w
}
func (w *World) flush(s *state) {
	for _, p := range s.players {
		for _, e := range p.outbox {
			if _, ok := e.(SelfChanged); ok {
				e = SelfChanged{Self: w.selfOf(p)}
			}
			p.deliver(s, e)
		}
		p.outbox = nil
	}
}
