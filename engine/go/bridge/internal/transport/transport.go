// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package transport adds deadlines to the shared codec without another framer.
package transport

import (
	"context"
	"errors"
	"io"
	"net"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Conn reads bridge/1 frames from one connection under two read deadlines:
// a caller bound and a per-frame limit that starts with the first byte.
type Conn struct {
	raw          net.Conn
	codec        *profile.Codec
	mu           sync.Mutex
	bound, frame time.Time
}

// New wraps c and decodes its frames with p.
func New(c net.Conn, p *profile.Codec) *Conn { return &Conn{raw: c, codec: p} }
func earlier(a, b time.Time) time.Time {
	if a.IsZero() || !b.IsZero() && b.Before(a) {
		return b
	}
	return a
}

// Bound sets the caller's read deadline; the zero time removes it.
func (c *Conn) Bound(t time.Time) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.bound = t
	c.raw.SetReadDeadline(earlier(c.bound, c.frame))
}

// Close closes the connection.
func (c *Conn) Close() error { return c.raw.Close() }

type reader struct {
	c       *Conn
	started bool
	err     error
	bytes   int
}

func (r *reader) Read(b []byte) (int, error) {
	if !r.started && len(b) > 1 {
		b = b[:1]
	}
	n, err := r.c.raw.Read(b)
	r.bytes += n
	if n > 0 && !r.started {
		r.started = true
		r.c.mu.Lock()
		r.c.frame = time.Now().Add(5 * time.Second)
		r.c.raw.SetReadDeadline(earlier(r.c.frame, r.c.bound))
		r.c.mu.Unlock()
	}
	if err != nil {
		r.err = err
	}
	return n, err
}

// Read reads one frame and returns its records and the bytes consumed. Once
// the first byte arrives, the rest of the frame must arrive within five
// seconds. A connection that ends inside a frame reports wire.ErrFrame.
func (c *Conn) Read() ([]wire.Record, int, error) {
	c.mu.Lock()
	c.frame = time.Time{}
	c.raw.SetReadDeadline(c.bound)
	c.mu.Unlock()
	rd := &reader{c: c}
	rs, err := c.codec.Read(rd)
	if rd.err != nil && err != nil {
		if errors.Is(rd.err, io.EOF) && rd.bytes > 0 {
			return nil, rd.bytes, wire.ErrFrame
		}
		return nil, rd.bytes, rd.err
	}
	return rs, rd.bytes, err
}

// Write has exactly one caller, the session writer. Cancellation closes the
// transport through the owner; a partially written frame is never retried.
func (c *Conn) Write(ctx context.Context, rs []wire.Record) error {
	deadline := time.Now().Add(5 * time.Second)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		deadline = d
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := c.raw.SetWriteDeadline(deadline); err != nil {
		return err
	}
	return c.codec.Write(c.raw, rs)
}

var _ io.Reader = (*reader)(nil)
