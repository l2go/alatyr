// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package transport

import (
	"context"
	"errors"
	"net"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestFirstByteDeadline(t *testing.T) {
	a, b := net.Pipe()
	defer b.Close()
	defer a.Close()
	codec, err := profile.NewProfileCodec()
	if err != nil {
		t.Fatal(err)
	}
	c := New(a, codec)
	done := make(chan error, 1)
	go func() { _, _, err := c.Read(); done <- err }()
	// Idle has no five-second frame deadline. The shorter opening bound is separate.
	c.mu.Lock()
	if !c.frame.IsZero() {
		t.Error("frame started without a byte")
	}
	c.mu.Unlock()
	if _, err = b.Write([]byte{128}); err != nil {
		t.Fatal(err)
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
	if err = <-done; err == nil {
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
		rs, err := codec.Read(b)
		if err == nil && (len(rs) != 1 || profile.U(rs[0], 1) != 123) {
			err = errors.New("short-write output")
		}
		done <- err
	}()
	if err := c.Write(context.Background(), []wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 123)}}}); err != nil {
		t.Fatal(err)
	}
	if err := <-done; err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := c.Write(ctx, []wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 1)}}}); err == nil {
		t.Fatal("canceled write")
	}
}
