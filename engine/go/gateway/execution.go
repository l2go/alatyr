// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"slices"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func (s *game) result(o wire.Record) {
	s.broadcast(o)
	if f := s.gate.cfg.Diagnostic; f != nil {
		summary := Summary{AtMS: s.gate.now(), Session: s.id, Epoch: s.attempts.Projection(), Record: wire.Result, N: uintField(o, 1), Stage: uintField(o, 2), Reason: uintField(o, 3), Requested: uintField(o, 5), Effective: uintField(o, 6)}
		if entry, ok := s.attempts.Lookup(summary.N); ok {
			summary.ElapsedMS = summary.AtMS - entry.Queued
		}
		f(summary)
	}
}
func (s *game) service(ctx context.Context) {
	if s.closed {
		return
	}
	// System service has no user admission dependency. An acknowledgement must
	// progress while a relocation checkpoint is deliberately not ready.
	if len(s.services) > 0 && (s.pacer == nil || s.pacer.Ready(s.services[0], s.gate.now())) {
		step := s.services[0]
		s.services[0] = Step{}
		s.services = s.services[1:]
		if s.write(ctx, step) != nil {
			s.fail(wire.CloseWorldClosed)
		}
		return
	}
	select {
	case in := <-s.input:
		s.process(in)
		return
	default:
	}
	select {
	case update, ok := <-s.world.Updates():
		if !ok {
			s.fail(wire.CloseWorldClosed)
			return
		}
		s.observe(update)
		return
	default:
	}
	if s.stop {
		owner, _ := s.attempts.Controller()
		if owner != 0 {
			s.stop = false
			return
		}
		step := s.state.Stop()
		if step == nil {
			s.stop = false
			return
		}
		if s.pacer == nil || s.pacer.Ready(*step, s.gate.now()) {
			s.stop = false
			if s.write(ctx, *step) != nil {
				s.fail(wire.CloseWorldClosed)
			}
			return
		}
		// Keep the stop pending without delaying local actions or refusals.
	}
	ns := make([]uint64, 0, len(s.actions))
	for n := range s.actions {
		ns = append(ns, n)
	}
	slices.Sort(ns)
	for _, n := range ns {
		ex := s.actions[n]
		entry, ok := s.attempts.Lookup(n)
		if !ok || entry.Terminal {
			delete(s.actions, n)
			continue
		}
		if entry.Sent {
			continue
		}
		if o, err := s.attempts.CheckWrite(n, s.gate.now()); err != nil {
			delete(s.actions, n)
			continue
		} else if o != nil {
			s.result(o.Record())
			delete(s.actions, n)
			continue
		}
		if reason := s.state.Validate(ex.action, s.gate.now()); reason != wire.ReasonNone {
			o, err := s.attempts.Refuse(n, reason)
			if err != nil {
				s.fail(wire.CloseInternal)
				return
			}
			s.result(o.Record())
			delete(s.actions, n)
			continue
		}
		if ex.waiting {
			if s.state.Confirmed(ex.action) {
				ex.waiting = false
				ex.index++
			} else if s.gate.now()-ex.helperAt >= s.gate.cfg.TargetWait.Milliseconds() {
				o, err := s.attempts.Fail(n, wire.ReasonTargetUnconfirmed)
				if err != nil {
					s.fail(wire.CloseInternal)
					return
				}
				s.result(o.Record())
				delete(s.actions, n)
				continue
			} else {
				continue
			}
		}
		if ex.action.Local {
			for other, pending := range s.actions {
				entry, _ := s.attempts.Lookup(other)
				selectionPlan := false
				for _, step := range pending.action.Steps {
					selectionPlan = selectionPlan || step.Await
				}
				if other != n && !entry.Sent && (pending.waiting || selectionPlan) {
					if o, e := s.attempts.Fail(other, wire.ReasonInterrupted); e == nil {
						s.result(o.Record())
					}
					delete(s.actions, other)
				}
			}
			o, err := s.attempts.Sent(n, true)
			if err != nil {
				s.fail(wire.CloseInternal)
				return
			}
			s.result(o.Record())
			delete(s.actions, n)
			continue
		}
		if ex.index >= len(ex.action.Steps) {
			o, err := s.attempts.Sent(n, false)
			if err != nil {
				s.fail(wire.CloseInternal)
				return
			}
			s.result(o.Record())
			continue
		}
		if ex.index == 0 && !ex.waiting {
			s.state.Start(ex.action)
		}
		step := ex.action.Steps[ex.index]
		if step.Await && s.state.Confirmed(ex.action) {
			ex.index++
			continue
		}
		// Confirmation is current world state, not a permanent receipt. Recheck
		// even when a previously confirmed prelude was skipped on an earlier
		// loop turn; a later selection fact may have changed the server target.
		requiresConfirmation := false
		for _, prior := range ex.action.Steps[:ex.index] {
			requiresConfirmation = requiresConfirmation || prior.Await
		}
		if requiresConfirmation && !s.state.Confirmed(ex.action) {
			o, err := s.attempts.Refuse(n, wire.ReasonTargetUnconfirmed)
			if err != nil {
				s.fail(wire.CloseInternal)
				return
			}
			s.result(o.Record())
			delete(s.actions, n)
			continue
		}
		if s.pacer != nil && !s.pacer.Ready(step, s.gate.now()) {
			return
		}
		// A competing producer invalidates correlation, including selection helpers
		// and saved actions capable of producing more than one class of event.
		for _, class := range step.Interferes {
			for other, x := range s.actions {
				if other == n || x.action.Class != class {
					continue
				}
				e, _ := s.attempts.Lookup(other)
				if e.Contact && !e.Terminal {
					if err := s.attempts.Taint(class); err != nil {
						s.fail(wire.CloseInternal)
						return
					}
				}
			}
		}
		if err := s.attempts.Contact(n, s.gate.now()); err != nil {
			s.fail(wire.CloseInternal)
			return
		}
		if err := s.write(ctx, step); err != nil {
			o, err := s.attempts.Fail(n, wire.ReasonBackendError)
			if err != nil {
				s.fail(wire.CloseInternal)
				return
			}
			s.result(o.Record())
			delete(s.actions, n)
			continue
		}
		if step.Await {
			ex.waiting = true
			ex.helperAt = s.gate.now()
		} else {
			ex.index++
			if ex.index == len(ex.action.Steps) {
				o, err := s.attempts.Sent(n, false)
				if err != nil {
					s.fail(wire.CloseInternal)
					return
				}
				s.result(o.Record())
			}
		}
		// Yield between packets. A control/epoch/input change is seen before the
		// next step, rather than locking a multi-packet action across its IO.
		return
	}
}
func (s *game) write(ctx context.Context, step Step) error {
	ctx, cancel := context.WithTimeout(ctx, s.gate.cfg.Write)
	defer cancel()
	return s.world.Write(ctx, step)
}
func (s *game) observe(update Update) bool {
	s.expire()
	if update.Relocate {
		var survivor uint64
		for n, ex := range s.actions {
			if ex.action.SurviveRelocation {
				ex.hasCandidate = false
				survivor = n
			}
		}
		outcomes, err := s.attempts.Relocate(survivor)
		if err != nil {
			s.fail(wire.CloseInternal)
			return false
		}
		for _, o := range outcomes {
			s.result(o.Record())
		}
		s.state.Invalidate()
	}
	if len(update.Service) > s.gate.cfg.Teleports-len(s.services) {
		s.fail(wire.CloseInternal)
		return false
	}
	s.services = append(s.services, update.Service...)
	observation, err := s.state.Apply(update, s.gate.now())
	if err != nil {
		s.fail(wire.CloseInternal)
		return false
	}
	if update.Relocate {
		for _, c := range s.connections() {
			if c.hello {
				s.checkpoint(c, nil, true)
			}
		}
	}
	for i := range observation.Facts {
		r := observation.Facts[i]
		// Only the typed dictionary/backend contract can establish an outcome.
		for n, ex := range s.actions {
			entry, ok := s.attempts.Lookup(n)
			if !ok || entry.Terminal || !s.attempts.Eligible(n, s.gate.now()) {
				continue
			}
			switch s.state.Witness(ex.action, update) {
			case Match:
				if ex.action.AfterCheckpoint {
					ex.candidate = s.gate.now()
					ex.hasCandidate = true
					continue
				}
				o, e := s.attempts.Done(n, s.gate.now())
				if e == nil {
					set(&r, 32, wire.U(32, true, n))
					s.result(o.Record())
					delete(s.actions, n)
				}
			case Candidate:
				ex.candidate = s.gate.now()
				ex.hasCandidate = true
			}
		}
		if !update.Relocate {
			s.fact(r)
		}
	}
	for _, c := range s.connections() {
		if !c.hello || c.ready {
			continue
		}
		snapshot, err := s.state.Snapshot(s.gate.now())
		if err != nil {
			s.fail(wire.CloseInternal)
			return false
		}
		if snapshot.Ready && c.awaitComplete {
			c.awaitComplete = false
			c.capturedReady = true
			end := checkpoint(false, s.attempts.Projection(), true, nil)
			raw, err := s.gate.cfg.Codec.Encode([]wire.Record{end})
			if err != nil {
				s.fail(wire.CloseInternal)
				return false
			}
			c.mu.Lock()
			if len(raw) > s.gate.cfg.OutBytes-c.bytes-c.active {
				c.mu.Unlock()
				s.drop(c, wire.CloseSlowConsumer)
				continue
			}
			c.queue = append(c.queue, outgoing{raw: raw, fence: c.generation})
			c.bytes += len(raw)
			c.mu.Unlock()
			c.wake()
		} else if snapshot.Ready && !c.transfer {
			s.checkpoint(c, nil, false)
		}
	}

	return true
}
func (s *game) expire() {
	now := s.gate.now()
	for n, ex := range s.actions {
		e, ok := s.attempts.Lookup(n)
		if !ok || e.Terminal {
			delete(s.actions, n)
			continue
		}
		if ex.action.FullWindow && e.Contact && now >= e.Deadline && ex.hasCandidate && s.state.CandidateValid(ex.action) {
			// Commit at the original deadline even when a scheduler tick arrives late.
			o, err := s.attempts.CommitWitness(n, ex.candidate, e.Deadline)
			if err == nil {
				r, err := s.state.Proof(ex.action, now)
				if err != nil {
					s.fail(wire.CloseInternal)
					return
				}
				set(&r, 32, wire.U(32, true, n))
				s.fact(r)
				s.result(o.Record())
				delete(s.actions, n)
			}
		}
	}
	for _, o := range s.attempts.Expire(now) {
		s.result(o.Record())
		delete(s.actions, o.N)
	}
	for _, c := range s.connections() {
		if !c.hello {
			if now-c.lastPong >= s.gate.cfg.Hello.Milliseconds() {
				s.drop(c, wire.CloseFrameError)
			}
			continue
		}
		if (c.transfer || !c.ready) && now-c.incompleteAt >= s.gate.cfg.Checkpoint.Milliseconds() {
			reason := wire.CloseSlowConsumer
			if !c.ready {
				snapshot, err := s.state.Snapshot(now)
				if err != nil {
					s.fail(wire.CloseInternal)
					return
				}
				if !snapshot.Ready {
					reason = wire.CloseWorldClosed
				}
			}
			s.drop(c, reason)
			continue
		}
		if c.pendingPing && now-c.lastPong >= s.gate.cfg.Pong.Milliseconds() {
			s.drop(c, wire.CloseNormal)
			continue
		}
		if now-c.lastPing >= s.gate.cfg.Ping.Milliseconds() {
			if !c.pendingPing {
				c.nonce++
				if c.nonce == 0 {
					s.fail(wire.CloseInternal)
					return
				}
				c.pendingPing = true
				c.lastPong = now
			}
			c.lastPing = now
			s.send(c, record(wire.Ping, wire.U(1, true, c.nonce)))
		}
	}
}

func (s *game) commitReadyWitnesses() {
	owner, _ := s.attempts.Controller()
	ready := false
	for _, c := range s.connections() {
		if c.id == owner && c.ready {
			ready = true
		}
	}
	if !ready {
		return
	}
	for n, ex := range s.actions {
		if !ex.action.AfterCheckpoint || !ex.hasCandidate || !s.state.CandidateValid(ex.action) {
			continue
		}
		o, err := s.attempts.Done(n, s.gate.now())
		if err != nil {
			continue
		}
		r, err := s.state.Proof(ex.action, s.gate.now())
		if err != nil {
			s.fail(wire.CloseInternal)
			return
		}
		set(&r, 32, wire.U(32, true, n))
		s.fact(r)
		s.result(o.Record())
		delete(s.actions, n)
	}
}
