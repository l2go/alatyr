// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"bufio"
	"bytes"
	"errors"
	"io"
	"net"
	"testing"
	"time"
)

type memoryConn struct {
	*bytes.Reader
	deadlineErr error
	writeErr    error
	short       bool
}

func (c *memoryConn) Write(b []byte) (int, error) {
	if c.writeErr != nil {
		return 0, c.writeErr
	}
	if c.short {
		return 0, nil
	}
	return len(b), nil
}
func (*memoryConn) Close() error                       { return nil }
func (*memoryConn) LocalAddr() net.Addr                { return &net.TCPAddr{} }
func (*memoryConn) RemoteAddr() net.Addr               { return &net.TCPAddr{} }
func (c *memoryConn) SetDeadline(time.Time) error      { return c.deadlineErr }
func (c *memoryConn) SetReadDeadline(time.Time) error  { return c.deadlineErr }
func (c *memoryConn) SetWriteDeadline(time.Time) error { return c.deadlineErr }

func TestTransportPropagatesDeadlineAndWriteErrors(t *testing.T) {
	want := errors.New("synthetic transport failure")
	for _, kind := range []string{"stream", "websocket"} {
		for _, failure := range []string{"deadline", "write", "short"} {
			t.Run(kind+"/"+failure, func(t *testing.T) {
				c := &memoryConn{Reader: bytes.NewReader(nil)}
				switch failure {
				case "deadline":
					c.deadlineErr = want
				case "write":
					c.writeErr = want
				case "short":
					c.short = true
				}
				var transport Transport = &Stream{c, 16}
				if kind == "websocket" {
					transport = &socket{conn: c, r: bufio.NewReader(c), limit: 16}
				}
				err := transport.Write([]byte("data"), time.Time{})
				expected := want
				if failure == "short" {
					expected = io.ErrShortWrite
				}
				if !errors.Is(err, expected) {
					t.Fatalf("Write = %v, want %v", err, expected)
				}
				if failure == "deadline" {
					if _, err := transport.Read(time.Time{}); !errors.Is(err, want) {
						t.Fatalf("Read = %v, want %v", err, want)
					}
				}
			})
		}
	}
}

func FuzzStreamRead(f *testing.F) {
	for _, seed := range [][]byte{{1, 42}, {3, 42}, {0x81, 0}, {0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 2}, {0}} {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, b []byte) {
		s := Stream{&memoryConn{Reader: bytes.NewReader(b)}, 4096}
		got, err := s.Read(time.Time{})
		if err != nil && got != nil {
			t.Fatalf("failed Read returned %d bytes", len(got))
		}
		if len(got) > s.Limit {
			t.Fatalf("frame bytes = %d, limit %d", len(got), s.Limit)
		}
		if err == nil {
			if len(got) == 0 {
				t.Fatal("empty successful frame")
			}
			if err := s.Write(got, time.Time{}); err != nil {
				t.Fatal(err)
			}
		}
	})
}

func FuzzWebSocketRead(f *testing.F) {
	for _, seed := range [][]byte{
		{0x82, 0x81, 1, 2, 3, 4, 8},
		{0x02, 0x81, 1, 2, 3, 4, 8, 0x80, 0x81, 1, 2, 3, 4, 9},
		{0x82, 1, 9}, {0x82, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff},
	} {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, b []byte) {
		c := &memoryConn{Reader: bytes.NewReader(b)}
		s := socket{conn: c, r: bufio.NewReader(c), limit: 4096}
		got, err := s.Read(time.Time{})
		if err != nil && got != nil {
			t.Fatalf("failed Read returned %d bytes", len(got))
		}
		if len(got) > s.limit {
			t.Fatalf("frame bytes = %d, limit %d", len(got), s.limit)
		}
		if err == nil {
			if len(got) == 0 {
				t.Fatal("empty successful frame")
			}
			if err := s.Write(got, time.Time{}); err != nil {
				t.Fatal(err)
			}
		}
	})
}
