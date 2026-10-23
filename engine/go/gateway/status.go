// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
)

// Usage is bounded operational accounting, never participant or display data.
type Usage struct{ Objects, Rows, Windows, ProjectionBytes int }

// Status reports one serialized session accounting sample, not world content.
type Status struct {
	Usage                                     Usage
	Connections, Results, Pending, QueueBytes int
	NextN, Floor, Epoch                       uint64
}
type accounting interface{ Usage() Usage }

// Status asks the session loop for current accounting within ctx's deadline.
func (g *Gateway) Status(ctx context.Context, identity string) (Status, error) {
	g.mu.Lock()
	s := g.games[identity]
	if s != nil && s.closed {
		s = nil
	}
	g.mu.Unlock()
	if s == nil {
		return Status{}, ErrAuth
	}
	reply := make(chan Status, 1)
	select {
	case s.input <- input{status: reply}:
	case <-ctx.Done():
		return Status{}, ctx.Err()
	case <-s.done:
		return Status{}, ErrAuth
	case <-g.stop:
		return Status{}, context.Canceled
	}
	select {
	case out := <-reply:
		return out, nil
	case <-ctx.Done():
		return Status{}, ctx.Err()
	case <-s.done:
		return Status{}, ErrAuth
	case <-g.stop:
		return Status{}, context.Canceled
	}
}
func (s *game) status() Status {
	out := Status{Results: len(s.attempts.Snapshot()), NextN: s.attempts.NextN(), Floor: s.attempts.Floor(), Epoch: s.attempts.Projection()}
	for n := range s.actions {
		if e, ok := s.attempts.Lookup(n); ok && !e.Terminal {
			out.Pending++
		}
	}
	conns := s.connections()
	out.Connections = len(conns)
	for _, c := range conns {
		c.mu.Lock()
		out.QueueBytes += c.bytes + c.active
		c.mu.Unlock()
	}
	if model, ok := s.state.(accounting); ok {
		out.Usage = model.Usage()
	}
	return out
}
