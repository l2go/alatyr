// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package transport

import (
	"context"
	"errors"
	"github.com/l2go/l2jfree/engine/go/bridge/profile"
	"github.com/l2go/l2jfree/engine/go/protocol/wire"
	"net"
	"testing"
	"time"
)

func TestFirstByteDeadline(t *testing.T) {
	a, b := net.Pipe()
	defer b.Close()
	defer a.Close()
	codec, e := profile.NewProfileCodec()
	if e != nil {
		t.Fatal(e)
	}
	c := New(a, codec)
	done := make(chan error, 1)
	go func() { _, _, e := c.Read(); done <- e }()
	// Idle has no five-second frame deadline. The shorter opening bound is separate.
	c.mu.Lock()
	if !c.frame.IsZero() {
		t.Error("frame started without a byte")
	}
	c.mu.Unlock()
	if _, e = b.Write([]byte{128}); e != nil {
		t.Fatal(e)
	}
	deadline := time.Now().Add(time.Second)
	for {
		c.mu.Lock()
		frame := c.frame
		c.mu.Unlock()
		if !frame.IsZero() {
			if time.Until(frame) < 4*time.Second {
				t.Fatal("transfer budget")
			}
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("prefix did not start deadline")
		}
		time.Sleep(time.Millisecond)
	}
	c.Bound(time.Now().Add(15 * time.Millisecond))
	if e = <-done; e == nil {
		t.Fatal("partial prefix accepted")
	}
}

type shortConn struct {
	net.Conn
	limit int
}

func (c shortConn) Write(b []byte) (int, error) {
	if len(b) > c.limit {
		b = b[:c.limit]
	}
	return c.Conn.Write(b)
}
func TestShortWritesAndCancellation(t *testing.T) {
	a, b := net.Pipe()
	defer b.Close()
	defer a.Close()
	codec, _ := profile.NewProfileCodec()
	c := New(shortConn{a, 1}, codec)
	done := make(chan error, 1)
	go func() {
		rs, e := codec.Read(b)
		if e == nil && (len(rs) != 1 || profile.U(rs[0], 1) != 123) {
			e = errors.New("short-write output")
		}
		done <- e
	}()
	if e := c.Write(context.Background(), []wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 123)}}}); e != nil {
		t.Fatal(e)
	}
	if e := <-done; e != nil {
		t.Fatal(e)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if e := c.Write(ctx, []wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 1)}}}); e == nil {
		t.Fatal("canceled write")
	}
}
