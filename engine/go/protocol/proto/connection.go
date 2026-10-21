// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"math"

	"github.com/l2go/l2jfree/engine/go/protocol/wire"
)

// Connection is the receiving projection/control automaton. It owns no socket.
type Connection struct {
	Mode, Session, Projection, Control, NextN, LastSeq uint64
	Ready, Acting                                      bool
	welcome, checkpoint, closed                        bool
	hasGrant                                           bool
	repairs                                            map[string]bool
	maxDomains                                         int
}

func NewConnection(maxDomains int) (*Connection, error) {
	if maxDomains < 1 {
		return nil, wire.ErrConfig
	}
	return &Connection{maxDomains: maxDomains, repairs: map[string]bool{}}, nil
}
func (c *Connection) Welcome(session, mode uint64) error {
	if c.welcome || c.closed || session == 0 || (mode != wire.Act && mode != wire.Observe) {
		return ErrState
	}
	c.Session = session
	c.Mode = mode
	c.welcome = true
	return nil
}
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
func (c *Connection) End(epoch uint64, ready bool) error {
	if c.closed || !c.checkpoint || epoch != c.Projection {
		return ErrState
	}
	c.checkpoint = false
	c.Ready = ready
	c.Acting = ready && c.hasGrant && c.Mode == wire.Act
	return nil
}
func (c *Connection) Grant(epoch, next uint64) error {
	if c.closed || !c.Ready || c.Mode != wire.Act || epoch == 0 || epoch < c.Control || next == 0 {
		return ErrState
	}
	c.Control = epoch
	c.NextN = next
	c.hasGrant = true
	c.Acting = true
	return nil
}
func (c *Connection) Lose(epoch uint64) error {
	if c.closed || epoch == 0 || epoch < c.Control {
		return ErrState
	}
	c.Control = epoch
	c.hasGrant = false
	c.Acting = false
	return nil
}
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
	return wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, wire.Resync), wire.U(2, true, c.Projection)}}, nil
}
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

func (c *Connection) Close() { c.closed = true; c.Ready = false; c.Acting = false; c.hasGrant = false }

// CheckpointFailure keeps incomplete knowledge distinct from a slow transport.
func CheckpointFailure(transferBlocked bool) wire.CloseReason {
	if transferBlocked {
		return wire.SlowConsumer
	}
	return wire.ClosedWorld
}
