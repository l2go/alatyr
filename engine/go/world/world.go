// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"time"
)

func (w *World) Run(ctx context.Context) error {
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
	case <-ctx.Done():
		return ctx.Err()
	}
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
