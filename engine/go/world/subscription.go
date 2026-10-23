// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

// CloseReason identifies why an observation stream ended, not a gameplay outcome.
type CloseReason uint8

const (
	// CloseOverflow means a consumer exceeded the bounded event backlog.
	CloseOverflow CloseReason = iota + 1
	// CloseLeave means the observed character left the world.
	CloseLeave
	// CloseStop means Run ended.
	CloseStop
	// CloseReplaced means a new subscription superseded this stream.
	CloseReplaced
)

// Subscription carries detached notifications. End receives exactly one reason
// when Events closes, then closes itself. Both channels are owned by Run. There
// is no goroutine per subscription, and loss never removes or saves a character.
// The zero value is inactive. Resubscription and capture share one loop turn.
type Subscription struct {
	Events <-chan Event
	End    <-chan CloseReason
}

type eventStream struct {
	events chan Event
	end    chan CloseReason
	closed bool
}

func newEventStream() *eventStream {
	return &eventStream{events: make(chan Event, 64), end: make(chan CloseReason, 1)}
}

func (s *eventStream) subscription() Subscription {
	return Subscription{Events: s.events, End: s.end}
}

func (s *eventStream) close(reason CloseReason) {
	if s.closed {
		return
	}
	s.closed = true
	s.end <- reason
	close(s.events)
	close(s.end)
}
