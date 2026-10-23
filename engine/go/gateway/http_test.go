// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"bufio"
	"errors"
	"io"
	"net"
	"net/http/httptest"
	"testing"
	"time"
)

func TestOriginHostBoundary(t *testing.T) {
	local := HTTP{Config: HTTPConfig{LocalAddress: "127.0.0.1:8080"}}
	for _, test := range []struct {
		host, origin string
		want         bool
	}{{"127.0.0.1:8080", "http://127.0.0.1:8080", true}, {"evil.test:8080", "http://evil.test:8080", false}, {"127.0.0.1:8080", "null", false}, {"127.0.0.1:8080", "", false}, {"127.0.0.1:8080", "http://127.0.0.1:8080/", false}, {"127.0.0.1:8080", "http://x@127.0.0.1:8080", false}, {"127.0.0.1:8080", "http://127.0.0.1:8080,http://127.0.0.1:8080", false}, {"127.0.0.1:8080", "https://127.0.0.1:8080", false}} {
		r := httptest.NewRequest("GET", "http://"+test.host+"/wire", nil)
		r.Header.Set("Origin", test.origin)
		if got := local.allowed(r); got != test.want {
			t.Errorf("%+v got %v", test, got)
		}
	}
	proxy := HTTP{Config: HTTPConfig{Proxy: true, Origins: []string{"https://PLAY.Example"}, Hosts: []string{"play.example:443"}, SchemeHeader: "X-Forwarded-Proto"}}
	r := httptest.NewRequest("GET", "http://play.example/wire", nil)
	r.Header.Set("Origin", "https://play.example:443")
	r.Header.Set("X-Forwarded-Proto", "https")
	if !proxy.allowed(r) {
		t.Fatal("canonical origin rejected")
	}
	r.Header.Add("Origin", "https://play.example")
	if proxy.allowed(r) {
		t.Fatal("multiple origins allowed")
	}
	for _, bad := range []string{"https://play.example:0", "https://play.example:65536", "https://play.example:", "https://play.example?x", "https://play.example#x", "https://play.example#", "https://play.example/", "https://play.example@other", "https://\u00e9.example"} {
		if _, ok := origin(bad); ok {
			t.Error(bad)
		}
	}
}
func TestStreamAndWebSocketBounds(t *testing.T) {
	for _, raw := range [][]byte{{0x81, 0}, {0x81, 0x80, 4}, {0}, {255, 255, 255, 255, 255, 255, 255, 255, 255, 2}} {
		a, b := net.Pipe()
		done := make(chan error, 1)
		go func() {
			_, err := b.Write(raw)
			done <- errors.Join(err, b.Close())
		}()
		s := Stream{a, 65536}
		if _, err := s.Read(time.Now().Add(time.Second)); err == nil {
			t.Fatal(raw)
		}
		if err := a.Close(); err != nil {
			t.Fatal(err)
		}
		// A rejected prefix can close the reader before the remaining bytes drain.
		if err := <-done; err != nil && !errors.Is(err, io.ErrClosedPipe) {
			t.Fatal(err)
		}
	}
	for _, test := range []struct {
		head []byte
		ok   bool
	}{{[]byte{0x82, 0x81, 1, 2, 3, 4, 8}, true}, {[]byte{0x82, 1, 9}, false}, {[]byte{0x81, 0x81, 1, 2, 3, 4, 8}, false}, {[]byte{0x82, 0xfe, 0, 126}, false}} {
		a, b := net.Pipe()
		done := make(chan error, 1)
		go func() {
			_, err := b.Write(test.head)
			done <- errors.Join(err, b.Close())
		}()
		s := socket{conn: a, r: bufio.NewReader(a), limit: 16}
		body, err := s.Read(time.Now().Add(time.Second))
		if (err == nil) != test.ok {
			t.Fatalf("%x: %x %v", test.head, body, err)
		}
		if err := a.Close(); err != nil {
			t.Fatal(err)
		}
		if err := <-done; err != nil && !errors.Is(err, io.ErrClosedPipe) {
			t.Fatal(err)
		}
	}
}
