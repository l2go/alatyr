// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import "github.com/l2go/alatyr/engine/go/protocol/wire"

func (s *game) trace(c *connection, r wire.Record, bytes int, incoming bool) {
	f := s.gate.cfg.Diagnostic
	if f == nil {
		return
	}
	_, epoch := s.attempts.Controller()
	summary := Summary{AtMS: s.gate.now(), Session: s.id, Connection: c.id, Epoch: s.attempts.Projection(), Control: epoch, Record: r.Type, Bytes: uint64(bytes)}
	switch r.Type {
	case wire.Intent:
		summary.N = uintField(r, 1)
		summary.Requested = uintField(r, 2)
	case wire.Result:
		summary.N = uintField(r, 1)
		summary.Stage = uintField(r, 2)
		summary.Reason = uintField(r, 3)
		summary.Requested = uintField(r, 5)
		summary.Effective = uintField(r, 6)
	case wire.Fact:
		summary.Kind = uintField(r, 1)
		summary.Seq = uintField(r, 2)
		summary.Epoch = uintField(r, 4)
	case wire.Hello, wire.Welcome, wire.Control:
		if !s.gate.cfg.Debug {
			return
		}
		summary.Debug = true
		if r.Type == wire.Hello {
			summary.WireMin = uintField(r, 1)
			summary.WireMax = uintField(r, 2)
			summary.Kind = uintField(r, 4)
			summary.Mode = uintField(r, 5)
			_, summary.Redacted = wire.Find(r.Fields, 6)
		}
		if r.Type == wire.Welcome {
			summary.Mode = uintField(r, 5)
			summary.WireMin = uintField(r, 1)
			summary.WireMax = uintField(r, 1)
		}
	case wire.Bye:
		summary.Reason = uintField(r, 1)
	default:
		if incoming {
			return
		}
	}
	if summary.N != 0 {
		if entry, ok := s.attempts.Lookup(summary.N); ok {
			summary.ElapsedMS = summary.AtMS - entry.Queued
		}
	}
	f(summary)
}
