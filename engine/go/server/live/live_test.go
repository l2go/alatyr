// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"archive/zip"
	"bytes"
	"context"
	"io"
	"io/fs"
	"net"
	"os"
	"strings"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/server/goworld"
)

type recorder struct {
	mu   sync.Mutex
	logs []Entry
	view View
}

func (r *recorder) watch(u Update) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if u.Log != nil {
		r.logs = append(r.logs, *u.Log)
	}
	if u.View != nil {
		r.view = *u.View
	}
}

func (r *recorder) wait(t *testing.T, what string, ok func(*recorder) bool) {
	t.Helper()
	deadline := time.Now().Add(20 * time.Second)
	for time.Now().Before(deadline) {
		r.mu.Lock()
		done := ok(r)
		r.mu.Unlock()
		if done {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	t.Fatalf("no %s; log %v", what, r.logs)
}

func startWorld(t *testing.T) *World {
	t.Helper()
	w, err := Start(os.DirFS("../../../../world"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := w.Close(); err != nil {
			t.Error(err)
		}
	})
	return w
}

// The visitor talks to the tutor of the authored village and gets Done,
// proved by the page the tutor speaks for that attempt.
func TestVisitorTalkIsDone(t *testing.T) {
	w := startWorld(t)
	var r recorder
	s, err := w.Join(t.Context(), Visitor, r.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	var tutor int32
	r.wait(t, "tutor in view", func(r *recorder) bool {
		for _, o := range r.view.Objects {
			if o.Role == "tutor" {
				tutor = o.ID
			}
		}
		return r.view.Ready && tutor != 0
	})
	if err := s.Do(Action{Verb: meaning.Talk, Target: tutor}); err != nil {
		t.Fatal(err)
	}
	r.wait(t, "Done for the talk", func(r *recorder) bool {
		for _, e := range r.logs {
			if e.Kind == "result" && e.N == 1 && e.Stage == "Done" {
				return true
			}
		}
		return false
	})
	r.mu.Lock()
	defer r.mu.Unlock()
	proved := false
	for _, e := range r.logs {
		proved = proved || e.Kind == "fact" && e.Cause == 1 && strings.Contains(e.Text, "speaks")
	}
	if !proved || r.view.Dialog == nil || len(r.view.Dialog.Lines) == 0 {
		t.Fatalf("Done without the page that proves it: %v", r.logs)
	}
}

// The agent plays on its own next to the visitor; the world answers it with
// outcomes, and no attempt ends Unknown for any reason but missing proof.
func TestAgentPlaysAlongsideTheVisitor(t *testing.T) {
	w := startWorld(t)
	var visitor, agent recorder
	vs, err := w.Join(t.Context(), Visitor, visitor.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer vs.Close()
	as, err := w.Join(t.Context(), Agent, agent.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer as.Close()
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- RunAgent(ctx, as, 100*time.Millisecond) }()
	agent.wait(t, "four outcomes", func(r *recorder) bool {
		n := 0
		for _, e := range r.logs {
			if e.Kind == "result" {
				n++
			}
		}
		return n >= 4
	})
	cancel()
	if err := <-done; err != context.Canceled {
		t.Fatalf("agent: %v", err)
	}
	agent.mu.Lock()
	defer agent.mu.Unlock()
	for _, e := range agent.logs {
		if e.Kind == "result" && e.Stage == "Unknown" && e.Reason != "no proof in time" {
			t.Fatalf("agent attempt ended %s: %v", e.Text, agent.logs)
		}
	}
	visitor.wait(t, "the agent in the visitor's view", func(r *recorder) bool {
		for _, o := range r.view.Objects {
			if o.Name == "Wanderer" {
				return true
			}
		}
		return false
	})
}

func TestAgentTalkSurvivesDelayedObserverReply(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w := startWorld(t)
		vs, err := w.Join(t.Context(), Visitor, nil)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			if err := vs.Close(); err != nil {
				t.Error(err)
			}
		})
		synctest.Wait()
		if !vs.View().Ready {
			t.Fatal("visitor checkpoint did not complete")
		}
		// Use a 400-unit approach covered by the original completion window.
		character := w.chars[Agent]
		character.X, character.Y = -71057, 257883-400
		w.chars[Agent] = character
		// The world captures Observe before the opening caller resumes. Force
		// that gap, and a fractional-millisecond capture, without a real sleep.
		time.Sleep(1500 * time.Microsecond)
		calls := 0
		w.config.Now = func() time.Time {
			calls++
			if calls == 2 {
				time.Sleep(3 * time.Millisecond)
			}
			return time.Now()
		}
		var agent recorder
		as, err := w.Join(t.Context(), Agent, agent.watch)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			if err := as.Close(); err != nil {
				t.Error(err)
			}
		})
		ctx, cancel := context.WithCancel(t.Context())
		done := make(chan error, 1)
		go func() { done <- RunAgent(ctx, as, 100*time.Millisecond) }()
		t.Cleanup(func() { cancel(); <-done })
		for range 60 {
			time.Sleep(100 * time.Millisecond)
			synctest.Wait()
			if err := as.Err(); err != nil {
				t.Fatalf("observer reply delay closed the agent: %v", err)
			}
			agent.mu.Lock()
			results, doneTalk := 0, false
			var uncertain *Entry
			for _, e := range agent.logs {
				if e.Kind != "result" {
					continue
				}
				results++
				if e.Stage == "Unknown" {
					entry := e
					uncertain = &entry
				}
				doneTalk = doneTalk || e.N == 1 && e.Stage == "Done"
			}
			agent.mu.Unlock()
			if uncertain != nil {
				t.Fatalf("uncertain agent outcome: %+v", *uncertain)
			}
			if doneTalk && results >= 4 {
				return
			}
		}
		t.Fatal("delayed talk did not complete within its original window")
	})
}

// The browser passes the rendered world as a zip archive; it loads exactly as
// the directory does.
func TestWorldStartsFromArchive(t *testing.T) {
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	if err := zw.AddFS(os.DirFS("../../../../world")); err != nil {
		t.Fatal(err)
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	zr, err := zip.NewReader(bytes.NewReader(buf.Bytes()), int64(buf.Len()))
	if err != nil {
		t.Fatal(err)
	}
	var archive fs.FS = zr
	w, err := Start(archive)
	if err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
}

// Dial reaches a gateway over TCP the way a server's stream endpoint takes
// participants: a bearer token line, then alatyr/1 frames.
func TestDialReachesAGatewayOverTCP(t *testing.T) {
	w := startWorld(t)
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	const token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	go func() {
		c, err := ln.Accept()
		if err != nil {
			return
		}
		line := make([]byte, len(token)+1)
		if _, err := io.ReadFull(c, line); err != nil || string(line) != token+"\n" {
			c.Close()
			return
		}
		_ = w.gateway.Bind(context.Background(), Visitor, &gateway.Stream{Conn: c, Limit: goworld.Limits(2).Wire.FrameBytes})
	}()
	var r recorder
	s, err := Dial(t.Context(), ln.Addr().String(), token, true, r.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	r.wait(t, "a ready view", func(r *recorder) bool { return r.view.Ready && len(r.view.Objects) > 0 })
}
