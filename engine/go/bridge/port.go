// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import "context"

// Port is an opened raw world session. Its constructor supplies the initial
// ready Snapshot separately; opening is not a Port method. Commands, updates
// and recovery use the same raw contract as Client. Receipts are evidence,
// not participant outcomes; admission and outcome policy belong to the caller.
type Port interface {
	// Updates returns live batches and lifecycle changes in arrival order.
	// The final update is Closed, followed by closure of the channel.
	// An update not yet received when the session closes may be discarded in favor of Closed.
	Updates() <-chan Update

	// Write hands off a command and returns its attempt number. Only one Write
	// may be in progress. An error after handoff does not prove zero effects.
	Write(context.Context, Command) (uint64, error)

	// Resume restores the same detached session with a fresh checkpoint and
	// retained receipts. It does not execute the retained commands again.
	// Only one Resume may be in progress.
	Resume(context.Context) (Recovery, error)

	// Close stops the session and waits for its owner to finish. Repeated calls
	// return the same result; an unconfirmed remote closure remains an error.
	Close() error
}

var _ Port = (*Client)(nil)
