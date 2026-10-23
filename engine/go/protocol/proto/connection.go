// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"math"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Connection tracks the participant side of one connection: the welcome, the
// projection checkpoints, control grants and attempt numbers. It owns no socket.
type Connection struct {
	Mode, Session, Projection, Control, NextN, LastSeq uint64
	Ready, Acting                                      bool
	welcome, checkpoint, closed                        bool
	hasGrant                                           bool
	repairs                                            map[string]bool
	maxDomains                                         int
}

// NewConnection returns a Connection that may request repair of at most
// maxDomains projection domains.
func NewConnection(maxDomains int) (*Connection, error) {
	if maxDomains < 1 {
		return nil, wire.ErrConfig
	}
	return &Connection{maxDomains: maxDomains, repairs: map[string]bool{}}, nil
}

// Welcome records the world's acceptance: the session and the granted Mode.
func (c *Connection) Welcome(session, mode uint64) error {
	if c.welcome || c.closed || session == 0 || (wire.Mode(mode) != wire.ModeAct && wire.Mode(mode) != wire.ModeObserve) {
		return ErrState
	}
	c.Session = session
	c.Mode = mode
	c.welcome = true
	return nil
}

// Begin starts a checkpoint of projection epoch. Acting stops until End.
func (c *Connection) Begin(epoch uint64) error {
	if !c.welcome || c.closed || epoch == 0 || epoch < c.Projection {
		return ErrState
	}
	c.Projection = epoch
	c.Ready = false
	c.Acting = false
	c.checkpoint = true
	return nil
}

// End finishes the checkpoint of epoch and records whether the projection is ready.
func (c *Connection) End(epoch uint64, ready bool) error {
	if c.closed || !c.checkpoint || epoch != c.Projection {
		return ErrState
	}
	c.checkpoint = false
	c.Ready = ready
	c.Acting = ready && c.hasGrant && wire.Mode(c.Mode) == wire.ModeAct
	return nil
}

// Grant records a control grant with control epoch and the next attempt number.
func (c *Connection) Grant(epoch, next uint64) error {
	if c.closed || !c.Ready || wire.Mode(c.Mode) != wire.ModeAct || epoch == 0 || epoch < c.Control || next == 0 {
		return ErrState
	}
	c.Control = epoch
	c.NextN = next
	c.hasGrant = true
	c.Acting = true
	return nil
}

// Lose records that control moved away at control epoch.
func (c *Connection) Lose(epoch uint64) error {
	if c.closed || epoch == 0 || epoch < c.Control {
		return ErrState
	}
	c.Control = epoch
	c.hasGrant = false
	c.Acting = false
	return nil
}

// Fact checks a fact of projection epoch with sequence seq. It reports false
// with a nil error for any epoch other than the current projection, older or
// newer; the caller drops such a fact. ErrState reports a closed or unwelcomed
// connection, no projection, or a sequence that is zero or not above LastSeq.
func (c *Connection) Fact(epoch, seq uint64) (bool, error) {
	if c.closed || !c.welcome || c.Projection == 0 {
		return false, ErrState
	}
	if epoch != c.Projection {
		return false, nil
	}
	if seq == 0 || seq <= c.LastSeq {
		return false, ErrState
	}
	c.LastSeq = seq
	return true, nil
}

// Incomplete requests one repair per domain; a repeat closes as unsupported.
func (c *Connection) Incomplete(domain string) (wire.Record, error) {
	if c.closed || domain == "" || c.Projection == 0 {
		return wire.Record{}, ErrState
	}
	c.Ready = false
	c.Acting = false
	if c.repairs[domain] || len(c.repairs) >= c.maxDomains {
		c.closed = true
		return wire.Record{}, wire.ErrUnsupported
	}
	c.repairs[domain] = true
	return wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlResync)), wire.U(2, true, c.Projection)}}, nil
}

// Allocate returns the next attempt number while the connection may act.
// When the number space is exhausted it closes the connection and returns
// ErrExhausted.
func (c *Connection) Allocate() (uint64, error) {
	if !c.Acting || !c.Ready || c.closed {
		return 0, ErrState
	}
	if c.NextN == math.MaxUint64 {
		c.closed = true
		return 0, ErrExhausted
	}
	n := c.NextN
	c.NextN++
	return n, nil
}

// Close closes the connection; it can no longer act.
func (c *Connection) Close() {
	c.closed = true
	c.Ready = false
	c.Acting = false
	c.hasGrant = false
}

// CheckpointFailure keeps incomplete knowledge distinct from a slow transport.
func CheckpointFailure(transferBlocked bool) wire.CloseReason {
	if transferBlocked {
		return wire.CloseSlowConsumer
	}
	return wire.CloseWorldClosed
}
