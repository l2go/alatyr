// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"sync"
)

// Publication is an ordered observation or a source-authored outcome. Resync
// asks the consumer to capture a new checkpoint before reading more events.
type Publication struct {
	// Event is an immutable source observation.
	Event Event
	// Commit carries the exact outcome subscribed at admission.
	Commit Commit
	// Resync requires a fresh source checkpoint before further observations.
	Resync bool
}

// Mailbox is the single bounded queue of a direct observer. Run publishes;
// one consumer calls Next. It owns no goroutines and never blocks Run.
// State updates coalesce only within an uninterrupted run of the same property;
// an intervening event is an ordering barrier. Outcomes survive event overflow.
type Mailbox struct {
	mu                          sync.Mutex
	notify                      chan struct{}
	queue                       []Publication
	watched                     map[IntentID]bool
	events, limit, outcomeLimit int
	resync, closed              bool
	discontinuous               bool
}

func newMailbox(limit, outcomes int) *Mailbox {
	return &Mailbox{notify: make(chan struct{}, 1), limit: limit, outcomeLimit: outcomes, watched: map[IntentID]bool{}}
}

// Notify is a level-triggered notification. Read Next until it reports empty.
func (m *Mailbox) Notify() <-chan struct{} { return m.notify }
func (m *Mailbox) wake() {
	select {
	case m.notify <- struct{}{}:
	default:
	}
}

// Next removes one ordered publication. False means empty; Closed distinguishes EOF.
func (m *Mailbox) Next() (Publication, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.resync {
		m.resync = false
		m.wake()
		return Publication{Resync: true}, true
	}
	if len(m.queue) == 0 {
		return Publication{}, false
	}
	p := m.queue[0]
	m.queue[0] = Publication{}
	m.queue = m.queue[1:]
	if p.Event != nil {
		m.events--
	}
	if p.Commit.Outcome.Intent.N != 0 {
		delete(m.watched, p.Commit.Outcome.Intent)
	}
	if len(m.queue) > 0 || m.closed {
		m.wake()
	}
	return p, true
}

// Closed reports that Run has ended this observation stream.
func (m *Mailbox) Closed() bool { m.mu.Lock(); defer m.mu.Unlock(); return m.closed }

// Drained reports stable EOF: the publisher has closed and no tail remains.
// Unlike a separate Next/Closed pair, it cannot skip publications appended
// between the consumer's empty read and the publisher's final close.
func (m *Mailbox) Drained() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.closed && len(m.queue) == 0 && !m.resync
}

func (m *Mailbox) close() { m.mu.Lock(); defer m.mu.Unlock(); m.closed = true; m.wake() }

// Stop releases an observer's queued publications and wakes its consumer.
// It does not depart or save the character; the session owner handles that.
func (m *Mailbox) Stop() {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.closed = true
	clear(m.queue)
	m.queue = nil
	clear(m.watched)
	m.events = 0
	m.resync = false
	m.discontinuous = false
	m.wake()
}

func (m *Mailbox) publish(e Event) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed || m.resync {
		return
	}
	key := coalescingKey(e)
	if key != (eventKey{}) {
		for i := len(m.queue) - 1; i >= 0; i-- {
			old := coalescingKey(m.queue[i].Event)
			if old == (eventKey{}) {
				break
			}
			if old == key {
				m.queue = append(m.queue[:i], m.queue[i+1:]...)
				m.events--
				break
			}
		}
	}
	if m.events >= m.limit {
		m.dropEvents()
		m.discontinuous = true
		m.resync = true
	} else {
		m.queue = append(m.queue, Publication{Event: e})
		m.events++
	}
	m.wake()
}
func (m *Mailbox) watch(id IntentID) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, exists := m.watched[id]; m.closed || id.N == 0 || exists || len(m.watched) >= m.outcomeLimit {
		return false
	}
	m.watched[id] = true
	return true
}

// Forget releases a retired observer's outstanding outcome subscription.
// The owner calls it only after the gateway retired that attempt. It removes
// an undelivered terminal result as well as a future subscription.
func (m *Mailbox) Forget(id IntentID) {
	if id.N == 0 {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.watched, id)
	kept := m.queue[:0]
	for _, p := range m.queue {
		if p.Commit.Outcome.Intent != id {
			kept = append(kept, p)
		}
	}
	clear(m.queue[len(kept):])
	m.queue = kept
}
func (m *Mailbox) outcome(c Commit) {
	if c.Outcome.Intent.N == 0 || c.Outcome.Stage == StageUnknown {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed || !m.watched[c.Outcome.Intent] {
		return
	}
	m.watched[c.Outcome.Intent] = false
	m.queue = append(m.queue, Publication{Commit: cloneCommit(c)})
	m.wake()
}
func (m *Mailbox) dropEvents() bool {
	kept := m.queue[:0]
	gap := m.discontinuous
	for _, p := range m.queue {
		switch p.Event.(type) {
		case ObjectGone, ItemPicked:
			gap = true
		}
		if p.Event == nil {
			kept = append(kept, p)
		}
	}
	clear(m.queue[len(kept):])
	m.queue = kept
	m.events = 0
	return gap
}
func (m *Mailbox) fence() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	gap := m.dropEvents()
	m.resync = false
	m.discontinuous = false
	if len(m.queue) > 0 || m.closed {
		m.wake()
	}
	return gap
}

type eventKey struct {
	id       int32
	property uint8
}

func coalescingKey(value Event) eventKey {
	switch v := value.(type) {
	case Moving:
		return eventKey{v.ObjectID, 1}
	case Following:
		return eventKey{v.ObjectID, 1}
	case Stopped:
		return eventKey{v.ObjectID, 1}
	case PositionObserved:
		return eventKey{v.ObjectID, 1}
	case TargetChosen:
		return eventKey{v.ObjectID, 2}
	case TargetCleared:
		return eventKey{v.ObjectID, 2}
	case Vitals:
		return eventKey{v.ObjectID, 3}
	case PostureChanged:
		return eventKey{v.ObjectID, 4}
	}
	return eventKey{}
}

// Observe installs the direct observer and captures its first checkpoint in
// one loop turn. limit is the host's event capacity, derived from visibility.
// It replaces the legacy subscription without departing the incarnation.
func (w *World) Observe(ctx context.Context, owner Ref, limit, outcomes int) (Scene, *Mailbox, error) {
	if limit < 1 || outcomes < 1 {
		return Scene{}, nil, ErrUninitialized
	}
	var scene Scene
	var box *Mailbox
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[owner.ID]
		if !ok || p.ref() != owner {
			result = ErrNotPresent
			return
		}
		w.flush(s)
		if p.mailbox != nil {
			p.mailbox.close()
		}
		if p.stream != nil {
			p.stream.close(CloseReplaced)
			p.stream = nil
		}
		p.slow = false
		box = newMailbox(limit, outcomes)
		p.mailbox = box
		scene = w.capture(s, p)
	})
	if err != nil {
		return Scene{}, nil, err
	}
	return scene, box, result
}

// CaptureObserver atomically captures and retires queued state through its
// fence. Outcomes are retained independently of a checkpoint, never replayed
// as newly executed commands. The caller checkpoints every attached connection.
func (w *World) CaptureObserver(ctx context.Context, owner Ref, box *Mailbox) (Scene, error) {
	if box == nil {
		return Scene{}, ErrUninitialized
	}
	var scene Scene
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[owner.ID]
		if !ok || p.ref() != owner || p.mailbox != box {
			result = ErrNotPresent
			return
		}
		w.flush(s)
		scene = w.capture(s, p)
		gap := box.fence()
		scene.Begin.Discontinuous, scene.End.Discontinuous = gap, gap
	})
	if err != nil {
		return Scene{}, err
	}
	return scene, result
}
