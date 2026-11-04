// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

func (s *game) notifications() <-chan struct{} {
	if r, ok := s.world.(Receiver); ok {
		return r.Notify()
	}
	return nil
}
func (s *game) updates() <-chan Update {
	if _, ok := s.world.(Receiver); ok {
		return nil
	}
	return s.world.Updates()
}
func (s *game) receiveWorld() {
	if r, ok := s.world.(Receiver); ok {
		if u, ok := r.Receive(); ok {
			s.observe(u)
		}
	}
}

// drainWorld lets a finite source burst fix its outcomes before a deadline is
// evaluated. The bound covers the entire initially queued mailbox. New state
// traffic cannot postpone expiry forever by continuously replenishing it.
func (s *game) drainWorld() bool {
	r, ok := s.world.(Receiver)
	if !ok {
		return true
	}
	for range r.Capacity() {
		u, ok := r.Receive()
		if !ok {
			return true
		}
		if !s.observe(u) {
			return false
		}
	}
	return !s.closed
}
