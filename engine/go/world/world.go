// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"sync/atomic"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
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
)

type Store interface {
	PutCharacter(context.Context, Character) error
}
type World struct {
	cat     *catalog.Catalog
	store   Store
	cmds    chan func(*state)
	stopped chan struct{}
	running atomic.Bool
	s       *state
}
type state struct {
	players map[int32]*player
	pending map[int32]*pendingSave
	moving  []int32
	ticks   int64
	timers  tasks
	seq     uint64
}

type pendingSave struct {
	char   Character
	saving bool
}

func New(cat *catalog.Catalog, store Store) *World {
	w := &World{cat: cat, store: store, cmds: make(chan func(*state)), stopped: make(chan struct{}), s: &state{players: map[int32]*player{}, pending: map[int32]*pendingSave{}}}
	w.s.after(time.Second/ticksPerSecond, w.tickMovement)
	return w
}
func (w *World) flush(s *state) {
	for _, p := range s.players {
		for _, e := range p.outbox {
			p.deliver(e)
		}
		p.outbox = nil
	}
}
