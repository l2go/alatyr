// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"
)

type handshakeConn struct {
	*memoryConn
	started, release, done chan struct{}
	once                   sync.Once
	deadline               time.Time
	closeErr               error
	writes                 atomic.Int32
}

func (c *handshakeConn) SetWriteDeadline(d time.Time) error {
	c.deadline = d
	return c.deadlineErr
}
func (c *handshakeConn) Write(b []byte) (int, error) {
	c.writes.Add(1)
	if c.started == nil {
		return c.memoryConn.Write(b)
	}
	close(c.started)
	timer := time.NewTimer(time.Until(c.deadline))
	defer timer.Stop()
	select {
	case <-c.release:
		return len(b), nil
	case <-c.done:
		return 0, net.ErrClosed
	case <-timer.C:
		return 0, context.DeadlineExceeded
	}
}
func (c *handshakeConn) Close() error {
	c.once.Do(func() { close(c.done) })
	return c.closeErr
}

type handshakeWriter struct {
	*httptest.ResponseRecorder
	conn               *handshakeConn
	hijacking, release chan struct{}
	bufferSize         int
}

func (w handshakeWriter) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	if w.hijacking != nil {
		close(w.hijacking)
		<-w.release
	}
	return w.conn, bufio.NewReadWriter(bufio.NewReader(w.conn), bufio.NewWriterSize(w.conn, w.bufferSize)), nil
}

func handshakeRequest() *http.Request {
	r := httptest.NewRequest("GET", "http://127.0.0.1:8080/wire", nil)
	r.Header.Set("Origin", "http://127.0.0.1:8080")
	r.Header.Set("Connection", "Upgrade")
	r.Header.Set("Upgrade", "websocket")
	r.Header.Set("Sec-WebSocket-Version", "13")
	r.Header.Set("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
	return r
}

func TestCloseJoinsInFlightHTTPHandshake(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cfg := testConfig(t)
		cfg.Shutdown = time.Millisecond
		cfg.Write = time.Second
		g := newTestGateway(t, cfg)
		conn := &handshakeConn{memoryConn: &memoryConn{Reader: bytes.NewReader(nil)},
			started: make(chan struct{}), release: make(chan struct{}), done: make(chan struct{})}
		h := HTTP{Gateway: g, Config: HTTPConfig{LocalIdentity: "one", LocalAddress: "127.0.0.1:8080"}}
		handlerDone := make(chan struct{})
		go func() {
			defer close(handlerDone)
			h.ServeHTTP(handshakeWriter{ResponseRecorder: httptest.NewRecorder(), conn: conn}, handshakeRequest())
		}()
		<-conn.started
		if err := g.Close(); err != nil {
			t.Fatalf("Close = %v, want completed join", err)
		}
		select {
		case <-handlerDone:
		default:
			t.Fatal("Close returned before HTTP owner ended")
		}
		select {
		case <-conn.done:
		default:
			t.Fatal("Close returned with a live hijacked socket")
		}
	})
}

func TestCloseTracksLateHijackHandoff(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cfg := testConfig(t)
		cfg.Shutdown = time.Millisecond
		g := newTestGateway(t, cfg)
		conn := &handshakeConn{memoryConn: &memoryConn{Reader: bytes.NewReader(nil)}, done: make(chan struct{})}
		writer := handshakeWriter{ResponseRecorder: httptest.NewRecorder(), conn: conn,
			hijacking: make(chan struct{}), release: make(chan struct{})}
		h := HTTP{Gateway: g, Config: HTTPConfig{LocalIdentity: "one", LocalAddress: "127.0.0.1:8080"}}
		handlerDone := make(chan struct{})
		go func() { defer close(handlerDone); h.ServeHTTP(writer, handshakeRequest()) }()
		<-writer.hijacking
		if err := g.Close(); !errors.Is(err, context.DeadlineExceeded) {
			t.Fatalf("Close = %v, want deadline for tracked Hijack owner", err)
		}
		close(writer.release)
		<-handlerDone
		if err := g.Close(); err != nil {
			t.Fatal(err)
		}
		if conn.writes.Load() != 0 {
			t.Fatal("late handoff wrote a handshake after shutdown")
		}
		select {
		case <-conn.done:
		default:
			t.Fatal("late handoff did not close socket")
		}
	})
}

func TestFailedUpgradeRetainsSocketCloseError(t *testing.T) {
	for _, failure := range []string{"deadline", "write", "flush"} {
		t.Run(failure, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				g, err := New(testConfig(t))
				if err != nil {
					t.Fatal(err)
				}
				closeErr := errors.New("synthetic socket close failure")
				ioErr := errors.New("synthetic handshake failure")
				conn := &handshakeConn{memoryConn: &memoryConn{Reader: bytes.NewReader(nil)},
					done: make(chan struct{}), closeErr: closeErr}
				writer := handshakeWriter{ResponseRecorder: httptest.NewRecorder(), conn: conn}
				switch failure {
				case "deadline":
					conn.deadlineErr = ioErr
				case "write":
					conn.writeErr = ioErr
					writer.bufferSize = 1
				case "flush":
					conn.writeErr = ioErr
				}
				h := HTTP{Gateway: g, Config: HTTPConfig{LocalIdentity: "one", LocalAddress: "127.0.0.1:8080"}}
				h.ServeHTTP(writer, handshakeRequest())
				if err := g.Close(); !errors.Is(err, closeErr) || errors.Is(err, ioErr) {
					t.Fatalf("Close = %v, want only socket cleanup error %v", err, closeErr)
				}
				if strings.Contains(writer.Body.String(), closeErr.Error()) || strings.Contains(writer.Body.String(), ioErr.Error()) {
					t.Fatal("HTTP response exposed internal error")
				}
			})
		})
	}
}

func TestMalformedUpgradeDoesNotPoisonClose(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		g := newTestGateway(t, testConfig(t))
		h := HTTP{Gateway: g, Config: HTTPConfig{LocalIdentity: "one", LocalAddress: "127.0.0.1:8080"}}
		r := handshakeRequest()
		r.Header.Del("Sec-WebSocket-Key")
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		if w.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
		}
		if err := g.Close(); err != nil {
			t.Fatalf("Close = %v after malformed input, want nil", err)
		}
	})
}
