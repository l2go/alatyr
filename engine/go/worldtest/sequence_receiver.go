// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"sync"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

// SequenceReceiver releases real source publications at explicit test barriers.
// It adds no producer goroutine, no alternate world model and no raw protocol.
// Flush must run only inside synctest, after the gateway becomes quiescent.
type SequenceReceiver struct {
	gateway.World
	source  gateway.Receiver
	mu      sync.Mutex
	pending *gateway.Update
	notify  chan struct{}
	ended   bool
}

// NewSequenceReceiver schedules a directly connected world for sequence tests.
func NewSequenceReceiver(w gateway.World) *SequenceReceiver {
	return &SequenceReceiver{World: w, source: w.(gateway.Receiver), notify: make(chan struct{}, 1)}
}

// Notify signals only publications explicitly released by Flush.
func (r *SequenceReceiver) Notify() <-chan struct{} { return r.notify }

// Capacity preserves the underlying source's finite drain budget.
func (r *SequenceReceiver) Capacity() int { return r.source.Capacity() }

// Receive removes the one explicitly released publication.
func (r *SequenceReceiver) Receive() (gateway.Update, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.pending == nil {
		return gateway.Update{}, false
	}
	u := *r.pending
	r.pending = nil
	return u, true
}

// Flush releases one available publication in the source's own order.
func (r *SequenceReceiver) Flush() bool {
	synctest.Wait()
	r.mu.Lock()
	ended := r.ended
	pending := r.pending != nil
	r.mu.Unlock()
	if ended || pending {
		return false
	}
	u, ok := r.source.Receive()
	if !ok {
		return false
	}
	r.mu.Lock()
	r.pending = &u
	if p, ok := u.Value.(worldlink.Publication); ok && p.Err != nil {
		r.ended = true
	}
	r.mu.Unlock()
	r.notify <- struct{}{}
	return true
}
