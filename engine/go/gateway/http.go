// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// HTTPConfig admits a configured loopback origin, or explicit hosts and origins
// behind a proxy with explicitly trusted source IPs. Forwarded headers are never authentication
// from a remote peer. LocalAddress may use port zero with http.LocalAddrContextKey.
type HTTPConfig struct {
	Proxy                       bool
	LocalIdentity, LocalAddress string
	Origins, Hosts              []string
	// ProxyPeers contains numeric trusted source IPs. Empty preserves the
	// historical IPv4 loopback default. Forwarded headers never select the peer.
	ProxyPeers                   []string
	IdentityHeader, SchemeHeader string
}

// HTTP serves bounded binary WebSocket participant connections.
// Gateway and Config must be initialized before use and never mutated afterwards.
type HTTP struct {
	Gateway *Gateway
	Config  HTTPConfig
}

func authority(raw, scheme string) (string, bool) {
	if raw == "" || strings.ContainsAny(raw, "@/\\?#, \t\r\n") || strings.Contains(raw, "%") {
		return "", false
	}
	parsed, err := url.Parse(scheme + "://" + raw)
	if err != nil || parsed.Host != raw || parsed.User != nil {
		return "", false
	}
	host := strings.ToLower(parsed.Hostname())
	if ip := net.ParseIP(host); ip != nil {
		host = ip.String()
	}
	if host == "" {
		return "", false
	}
	for _, r := range host {
		if r > 127 {
			return "", false
		}
	}
	port := parsed.Port()
	if port == "" {
		if strings.HasSuffix(raw, ":") {
			return "", false
		}
		if scheme == "https" {
			port = "443"
		} else {
			port = "80"
		}
	}
	n, err := strconv.Atoi(port)
	if err != nil || n < 1 || n > 65535 {
		return "", false
	}
	if strings.Contains(host, ":") && net.ParseIP(host) == nil {
		return "", false
	}
	return net.JoinHostPort(host, strconv.Itoa(n)), true
}
func origin(raw string) (string, bool) {
	if strings.Contains(raw, "#") {
		return "", false
	}
	u, err := url.Parse(raw)
	if err != nil || u.User != nil || u.Path != "" || u.RawQuery != "" || u.ForceQuery || u.Fragment != "" || u.Opaque != "" || (u.Scheme != "http" && u.Scheme != "https") {
		return "", false
	}
	a, ok := authority(u.Host, u.Scheme)
	if !ok {
		return "", false
	}
	return u.Scheme + "://" + a, true
}
func (h HTTP) allowed(r *http.Request) bool {
	origins := r.Header.Values("Origin")
	if len(origins) != 1 {
		return false
	}
	o, ok := origin(origins[0])
	if !ok {
		return false
	}
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	if h.Config.Proxy {
		values := r.Header.Values(h.Config.SchemeHeader)
		if len(values) != 1 || (values[0] != "http" && values[0] != "https") {
			return false
		}
		scheme = values[0]
	}
	a, ok := authority(r.Host, scheme)
	if !ok {
		return false
	}
	if !h.Config.Proxy {
		configuredHost, configuredPort, err := net.SplitHostPort(h.Config.LocalAddress)
		if err != nil || net.ParseIP(configuredHost) == nil || !net.ParseIP(configuredHost).IsLoopback() {
			return false
		}
		address := h.Config.LocalAddress
		if configuredPort == "0" {
			local, ok := r.Context().Value(http.LocalAddrContextKey).(net.Addr)
			if !ok {
				return false
			}
			address = local.String()
		}
		expected, valid := authority(address, scheme)
		if !valid || a != expected {
			return false
		}
		expectedOrigin, valid := origin(scheme + "://" + address)
		return valid && o == expectedOrigin
	}
	goodO, goodH := false, false
	for _, v := range h.Config.Origins {
		v, valid := origin(v)
		if valid && v == o && strings.HasPrefix(v, scheme+"://") {
			goodO = true
		}
	}
	for _, v := range h.Config.Hosts {
		v, valid := authority(v, scheme)
		if valid && v == a {
			goodH = true
		}
	}
	return goodO && goodH
}

// ServeHTTP checks host, origin, and trusted identity before reserving admission.
// A failed upgrade releases only its connection and an otherwise unused new world.
func (h HTTP) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !h.allowed(r) {
		http.Error(w, "origin refused", http.StatusForbidden)
		return
	}
	identity := h.Config.LocalIdentity
	if h.Config.Proxy {
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		peers := h.Config.ProxyPeers
		if len(peers) == 0 {
			peers = []string{"127.0.0.1"}
		}
		trusted := false
		if err == nil {
			peer := net.ParseIP(host)
			for _, allowed := range peers {
				ip := net.ParseIP(allowed)
				if peer != nil && ip != nil && peer.Equal(ip) {
					trusted = true
					break
				}
			}
		}
		if !trusted {
			http.Error(w, "participant unavailable", http.StatusUnauthorized)
			return
		}
		values := r.Header.Values(h.Config.IdentityHeader)
		if len(values) != 1 {
			http.Error(w, "participant unavailable", http.StatusUnauthorized)
			return
		}
		identity = values[0]
	}
	if len(identity) == 0 || len(identity) > 128 {
		http.Error(w, "participant unavailable", http.StatusUnauthorized)
		return
	}
	// Reserve before upgrade/Welcome; failed upgrades release their reservation.
	pending := &deferredTransport{ready: make(chan struct{}), done: make(chan struct{}), closeError: h.Gateway.closeError}
	c, _, err := h.Gateway.bind(r.Context(), identity, pending, true)
	if err != nil {
		status := http.StatusUnauthorized
		if errors.Is(err, ErrFull) {
			status = http.StatusServiceUnavailable
		}
		http.Error(w, "participant unavailable", status)
		return
	}
	defer h.Gateway.wg.Done()
	err = upgrade(w, r, h.Gateway.cfg.FrameBytes, h.Gateway.cfg.Write, pending.attach)
	close(pending.ready)
	if err != nil {
		// Cleanup belongs to this HTTP owner even if the connection loop already
		// closed its reservation. Preserve only Close errors, not malformed input.
		h.Gateway.closeError(pending.Close())
		c.close()
		select {
		case c.game.input <- input{abort: true}:
		case <-c.game.done:
		case <-h.Gateway.stop:
		}
		http.Error(w, "invalid websocket upgrade", http.StatusBadRequest)
		return
	}
	h.Gateway.mu.Lock()
	c.game.provisional = false
	h.Gateway.mu.Unlock()
}

// A reservation cannot start its Hello clock before the HTTP upgrade finishes.
type deferredTransport struct {
	done       chan struct{}
	once       sync.Once
	mu         sync.Mutex
	ready      chan struct{}
	transport  Transport
	closed     bool
	closeError func(error)
}

func (d *deferredTransport) attach(t Transport) bool {
	d.mu.Lock()
	d.transport = t
	closed := d.closed
	d.mu.Unlock()
	if closed {
		d.closeError(t.Close())
	}
	return !closed
}

func (d *deferredTransport) get(deadline time.Time) (Transport, error) {
	timer := time.NewTimer(time.Until(deadline))
	defer timer.Stop()
	select {
	case <-d.ready:
	case <-d.done:
		return nil, io.EOF
	case <-timer.C:
		return nil, ErrFrame
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.closed || d.transport == nil {
		return nil, io.EOF
	}
	return d.transport, nil
}
func (d *deferredTransport) Read(t time.Time) ([]byte, error) {
	s, e := d.get(t)
	if e != nil {
		return nil, e
	}
	return s.Read(t)
}
func (d *deferredTransport) Write(b []byte, t time.Time) error {
	s, e := d.get(t)
	if e != nil {
		return e
	}
	return s.Write(b, t)
}
func (d *deferredTransport) Close() error {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.closed {
		if d.transport != nil {
			return d.transport.Close()
		}
		return nil
	}
	d.closed = true
	d.once.Do(func() { close(d.done) })
	if d.transport != nil {
		return d.transport.Close()
	}
	return nil
}

type socket struct {
	conn      net.Conn
	r         *bufio.Reader
	mu        sync.Mutex
	limit     int
	closeOnce sync.Once
	closeErr  error
}

func upgrade(w http.ResponseWriter, r *http.Request, limit int, timeout time.Duration, attach func(Transport) bool) error {
	hasToken := func(value, want string) bool {
		for _, v := range strings.Split(value, ",") {
			if strings.EqualFold(strings.TrimSpace(v), want) {
				return true
			}
		}
		return false
	}
	key := r.Header.Values("Sec-WebSocket-Key")
	if r.Method != "GET" || !hasToken(r.Header.Get("Connection"), "upgrade") || !strings.EqualFold(r.Header.Get("Upgrade"), "websocket") || r.Header.Get("Sec-WebSocket-Version") != "13" || len(key) != 1 {
		return ErrFrame
	}
	raw, err := base64.StdEncoding.DecodeString(key[0])
	if err != nil || len(raw) != 16 {
		return ErrFrame
	}
	hj, ok := w.(http.Hijacker)
	if !ok {
		return ErrFrame
	}
	conn, rw, err := hj.Hijack()
	if err != nil {
		return err
	}
	// Transfer ownership before any handshake I/O. The reader and writer still
	// wait for ready, but shutdown can now close the hijacked socket immediately.
	s := &socket{conn: conn, r: rw.Reader, limit: limit}
	if !attach(s) {
		return net.ErrClosed
	}
	sum := sha1.Sum([]byte(key[0] + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
	accept := base64.StdEncoding.EncodeToString(sum[:])
	err = conn.SetWriteDeadline(time.Now().Add(timeout))
	if err != nil {
		return err
	}
	if _, err = rw.WriteString("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n"); err == nil {
		err = rw.Flush()
	}
	if err != nil {
		return err
	}
	return nil
}
func (s *socket) Read(deadline time.Time) ([]byte, error) {
	if s.limit < 1 {
		return nil, ErrFrame
	}
	if err := s.conn.SetReadDeadline(deadline); err != nil {
		return nil, err
	}
	var body []byte
	fragment := false
	for {
		var head [2]byte
		if _, err := io.ReadFull(s.r, head[:]); err != nil {
			return nil, err
		}
		fin := head[0]&128 != 0
		op := head[0] & 15
		if head[0]&112 != 0 || head[1]&128 == 0 {
			return nil, ErrFrame
		}
		size := uint64(head[1] & 127)
		if size == 126 {
			var ext [2]byte
			if _, err := io.ReadFull(s.r, ext[:]); err != nil {
				return nil, err
			}
			size = uint64(binary.BigEndian.Uint16(ext[:]))
			if size < 126 {
				return nil, ErrFrame
			}
		} else if size == 127 {
			var ext [8]byte
			if _, err := io.ReadFull(s.r, ext[:]); err != nil {
				return nil, err
			}
			size = binary.BigEndian.Uint64(ext[:])
			if size < 65536 {
				return nil, ErrFrame
			}
		}
		if op >= 8 {
			if !fin || size > 125 {
				return nil, ErrFrame
			}
		} else if size > uint64(s.limit-len(body)) {
			return nil, ErrFrame
		}
		if op != 0 && op != 2 && op != 8 && op != 9 && op != 10 {
			return nil, ErrFrame
		}
		if op == 0 && !fragment || op == 2 && fragment {
			return nil, ErrFrame
		}
		var mask [4]byte
		if _, err := io.ReadFull(s.r, mask[:]); err != nil {
			return nil, err
		}
		b := make([]byte, int(size))
		if _, err := io.ReadFull(s.r, b); err != nil {
			return nil, err
		}
		for i := range b {
			b[i] ^= mask[i%4]
		}
		switch op {
		case 8:
			return nil, io.EOF
		case 9:
			if err := s.write(10, b, deadline); err != nil {
				return nil, err
			}
			continue
		case 10:
			continue
		}
		body = append(body, b...)
		if fin {
			if len(body) == 0 {
				return nil, ErrFrame
			}
			return body, nil
		}
		fragment = true
	}
}
func (s *socket) write(op byte, b []byte, deadline time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if err := s.conn.SetWriteDeadline(deadline); err != nil {
		return err
	}
	header := []byte{128 | op}
	n := len(b)
	if n < 126 {
		header = append(header, byte(n))
	} else if n <= 65535 {
		header = append(header, 126, byte(n>>8), byte(n))
	} else {
		header = append(header, 127)
		var size [8]byte
		binary.BigEndian.PutUint64(size[:], uint64(n))
		header = append(header, size[:]...)
	}
	if err := writeFull(s.conn, header); err != nil {
		return err
	}
	return writeFull(s.conn, b)
}
func (s *socket) Write(b []byte, t time.Time) error {
	if len(b) > s.limit || len(b) == 0 {
		return ErrFrame
	}
	return s.write(2, b, t)
}
func (s *socket) Close() error {
	s.closeOnce.Do(func() { s.closeErr = s.conn.Close() })
	return s.closeErr
}
