// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"encoding/binary"
	"io"
	"net"
	"time"
)

// Stream uses the protocol's sole length prefix. The limit is checked before
// allocation, including overflow and noncanonical prefixes.
type Stream struct {
	Conn  net.Conn
	Limit int
}

// Read returns one bounded, canonically length-prefixed frame body.
func (s *Stream) Read(deadline time.Time) ([]byte, error) {
	if s.Limit < 1 {
		return nil, ErrFrame
	}
	if err := s.Conn.SetReadDeadline(deadline); err != nil {
		return nil, err
	}
	var n uint64
	for i := 0; i < 10; i++ {
		var b [1]byte
		if _, err := io.ReadFull(s.Conn, b[:]); err != nil {
			return nil, err
		}
		if i == 9 && b[0] > 1 {
			return nil, ErrFrame
		}
		n |= uint64(b[0]&127) << uint(7*i)
		if b[0]&128 == 0 {
			if (i > 0 && b[0] == 0) || n == 0 || n > uint64(s.Limit) {
				return nil, ErrFrame
			}
			raw := make([]byte, int(n))
			if _, err := io.ReadFull(s.Conn, raw); err != nil {
				return nil, err
			}
			return raw, nil
		}
	}
	return nil, ErrFrame
}
func writeFull(w io.Writer, b []byte) error {
	for len(b) > 0 {
		n, err := w.Write(b)
		if n < 0 || n > len(b) {
			return io.ErrShortWrite
		}
		b = b[n:]
		if err != nil {
			return err
		}
		if n == 0 {
			return io.ErrShortWrite
		}
	}
	return nil
}

// Write sends one bounded frame body with the protocol's length prefix.
func (s *Stream) Write(b []byte, deadline time.Time) error {
	if len(b) == 0 || len(b) > s.Limit {
		return ErrFrame
	}
	if err := s.Conn.SetWriteDeadline(deadline); err != nil {
		return err
	}
	var prefix [10]byte
	n := binary.PutUvarint(prefix[:], uint64(len(b)))
	if err := writeFull(s.Conn, prefix[:n]); err != nil {
		return err
	}
	return writeFull(s.Conn, b)
}

// Close releases the connection and unblocks Read and Write.
func (s *Stream) Close() error { return s.Conn.Close() }
