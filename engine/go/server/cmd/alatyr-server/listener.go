// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"net"
	"sync"
)

// boundedListener caps HTTP connections before allocating handler goroutines.
// It rejects excess connections immediately and releases a slot once per Close.
type boundedListener struct {
	net.Listener
	slots chan struct{}
}

func (l *boundedListener) Accept() (net.Conn, error) {
	for {
		c, err := l.Listener.Accept()
		if err != nil {
			return nil, err
		}
		select {
		case l.slots <- struct{}{}:
			return &boundedConn{Conn: c, release: func() { <-l.slots }}, nil
		default:
			// A refused connection has no owner and carries no application data.
			if err := c.Close(); err != nil {
				return nil, err
			}
		}
	}
}

type boundedConn struct {
	net.Conn
	once    sync.Once
	release func()
	err     error
}

func (c *boundedConn) Close() error {
	c.once.Do(func() { c.err = c.Conn.Close(); c.release() })
	return c.err
}
