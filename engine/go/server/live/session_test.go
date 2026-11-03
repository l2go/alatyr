// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

// Ordering, numbering and lifecycle of a participant: intents leave in number
// order from any number of callers, a rejoined participant continues its
// numbering, a checkpoint replaces the picture, lost control stops acting,
// and updates reach watch one at a time.

import (
	"errors"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/server/goworld"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func waitActing(t *testing.T, s *Session) {
	t.Helper()
	end := time.NewTimer(time.Second * 5)
	defer end.Stop()
	tick := time.NewTicker(time.Millisecond)
	defer tick.Stop()
	for {
		s.mu.Lock()
		ok := s.ready && s.control != 0
		s.mu.Unlock()
		if ok {
			return
		}
		select {
		case <-end.C:
			t.Fatal("not ready")
		case <-tick.C:
		}
	}
}
func TestConcurrentActionsLeaveInNumberOrder(t *testing.T) {
	codec, err := meaning.NewCodec(goworld.Limits(1))
	if err != nil {
		t.Fatal(err)
	}
	a, b := net.Pipe()
	defer a.Close()
	defer b.Close()
	entered, release := make(chan struct{}), make(chan struct{})
	s := &Session{codec: codec, stream: &gateway.Stream{Conn: a, Limit: 8192}, ready: true, control: 1, pending: map[uint64]string{}, watch: func(u Update) {
		if u.Log != nil && u.Log.Kind == "intent" && u.Log.N == 1 {
			close(entered)
			<-release
		}
	}}
	nums := make(chan []uint64, 1)
	go func() {
		var ns []uint64
		stream := &gateway.Stream{Conn: b, Limit: 8192}
		for range 2 {
			raw, e := stream.Read(time.Now().Add(time.Second * 5))
			if e != nil {
				nums <- ns
				return
			}
			rs, _ := codec.Decode(raw)
			for _, r := range rs {
				ns = append(ns, fieldU(r, 1))
			}
		}
		nums <- ns
	}()
	done1, done2 := make(chan error, 1), make(chan error, 1)
	go func() { done1 <- s.Do(Action{Verb: meaning.Walk, X: 1, Y: 1}) }()
	<-entered
	// The second action waits for the first to be written.
	go func() { done2 <- s.Do(Action{Verb: meaning.Walk, X: 2, Y: 2}) }()
	time.Sleep(50 * time.Millisecond)
	close(release)
	if e := <-done1; e != nil {
		t.Fatal(e)
	}
	if e := <-done2; e != nil {
		t.Fatal(e)
	}
	got := <-nums
	if len(got) != 2 || got[0] != 1 || got[1] != 2 {
		t.Fatalf("wire attempts %v; want [1 2]", got)
	}
}
func TestRejoinContinuesTheAttemptNumbers(t *testing.T) {
	w := startWorld(t)
	var r recorder
	s, err := w.Join(t.Context(), Visitor, r.watch)
	if err != nil {
		t.Fatal(err)
	}
	waitActing(t, s)
	if err = s.Do(Action{Verb: meaning.Walk, X: -71057, Y: 257803}); err != nil {
		t.Fatal(err)
	}
	r.wait(t, "result", func(r *recorder) bool {
		for _, e := range r.logs {
			if e.Kind == "result" {
				return true
			}
		}
		return false
	})
	if err = s.Close(); err != nil {
		t.Fatal(err)
	}
	var r2 recorder
	s2, err := w.Join(t.Context(), Visitor, r2.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer s2.Close()
	waitActing(t, s2)
	if err = s2.Do(Action{Verb: meaning.Walk, X: -71056, Y: 257803}); err != nil {
		t.Fatal(err)
	}
	r2.wait(t, "result", func(r *recorder) bool {
		for _, e := range r.logs {
			if e.Kind == "result" {
				return true
			}
		}
		return false
	})
	r2.mu.Lock()
	defer r2.mu.Unlock()
	for _, e := range r2.logs {
		if e.Kind == "intent" && e.N != 2 {
			t.Fatalf("rejoined intent N=%d, want 2; logs=%v", e.N, r2.logs)
		}
	}
}

func TestCheckpointReplacesTheView(t *testing.T) {
	s := &Session{ready: true, control: 1, objects: map[int32]*tracked{99: {Object: Object{ID: 99}}}, pending: map[uint64]string{}}
	err := s.consume([]wire.Record{{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointBegin)), wire.U(2, true, 2), wire.U(3, true, 0)}}})
	if err != nil {
		t.Fatal(err)
	}
	if s.ready {
		t.Error("checkpoint Begin leaves ready=true")
	}
	s.consume([]wire.Record{{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointEnd)), wire.U(2, true, 2), wire.U(3, true, 1)}}})
	if len(s.View().Objects) != 0 {
		t.Error("object absent from replacement checkpoint survives in view")
	}
}
func TestLostControlStopsActing(t *testing.T) {
	s := &Session{ready: true, control: 1, objects: map[int32]*tracked{}, pending: map[uint64]string{}}
	err := s.consume([]wire.Record{{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlLost)), wire.U(2, true, 2)}}})
	if err != nil {
		t.Fatal(err)
	}
	if s.control != 0 {
		t.Fatalf("lost control still has acting epoch %d", s.control)
	}
}
func TestPingIsAnswered(t *testing.T) {
	codec, _ := meaning.NewCodec(goworld.Limits(1))
	a, b := net.Pipe()
	defer b.Close()
	peer := &gateway.Stream{Conn: b, Limit: 8192}
	result := make(chan error, 1)
	go func() {
		if _, e := peer.Read(time.Now().Add(time.Second)); e != nil {
			result <- e
			return
		}
		raw, _ := codec.Encode([]wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 123)}}})
		if e := peer.Write(raw, time.Now().Add(time.Second)); e != nil {
			result <- e
			return
		}
		raw, e := peer.Read(time.Now().Add(time.Second))
		if e == nil {
			var rs []wire.Record
			rs, e = codec.Decode(raw)
			if e == nil && (len(rs) != 1 || rs[0].Type != wire.Pong || fieldU(rs[0], 1) != 123) {
				e = errors.New("wrong pong")
			}
		}
		result <- e
	}()
	s, e := newSession(codec, &gateway.Stream{Conn: a, Limit: 8192}, wire.ParticipantPerson, nil)
	if e != nil {
		t.Fatal(e)
	}
	defer s.Close()
	if e = <-result; e != nil {
		t.Fatal(e)
	}
}

func TestUpdatesAreDeliveredOneAtATime(t *testing.T) {
	codec, _ := meaning.NewCodec(goworld.Limits(1))
	a, b := net.Pipe()
	defer a.Close()
	defer b.Close()
	entered, release := make(chan struct{}), make(chan struct{})
	var active atomic.Int32
	var overlap atomic.Bool
	s := &Session{codec: codec, stream: &gateway.Stream{Conn: a, Limit: 8192}, ready: true, epoch: 1, control: 1, objects: map[int32]*tracked{}, pending: map[uint64]string{}}
	s.watch = func(u Update) {
		if active.Add(1) > 1 {
			overlap.Store(true)
		}
		defer active.Add(-1)
		if u.Log != nil && u.Log.Kind == "intent" {
			close(entered)
			<-release
		}
	}
	go func() { (&gateway.Stream{Conn: b, Limit: 8192}).Read(time.Now().Add(time.Second)) }()
	sent := make(chan error, 1)
	go func() { sent <- s.Do(Action{Verb: meaning.Walk, X: 1, Y: 1}) }()
	<-entered
	// The reader's update must wait for the intent's watch to return.
	consumed := make(chan error, 1)
	go func() {
		consumed <- s.consume([]wire.Record{{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointEnd)), wire.U(2, true, 1), wire.U(3, true, 1)}}})
	}()
	time.Sleep(50 * time.Millisecond)
	close(release)
	if err := <-sent; err != nil {
		t.Fatal(err)
	}
	if err := <-consumed; err != nil {
		t.Fatal(err)
	}
	if overlap.Load() {
		t.Fatal("updates overlapped")
	}
}
func TestFailedWriteStopsActing(t *testing.T) {
	codec, _ := meaning.NewCodec(goworld.Limits(1))
	a, b := net.Pipe()
	defer b.Close()
	a.Close()
	s := &Session{codec: codec, stream: &gateway.Stream{Conn: a, Limit: 8192}, done: make(chan struct{}), ready: true, control: 1, objects: map[int32]*tracked{}, pending: map[uint64]string{}}
	if err := s.Do(Action{Verb: meaning.Walk, X: 1, Y: 1}); err == nil {
		t.Fatal("expected write error")
	}
	if s.ready || s.Pending() != 0 {
		t.Fatalf("failed write leaves ready=%v pending=%d", s.ready, s.Pending())
	}
}
func TestWorldCloseCanRepeat(t *testing.T) {
	w, err := Start(os.DirFS("../../../../world"))
	if err != nil {
		t.Fatal(err)
	}
	if err = w.Close(); err != nil {
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() { done <- w.Close() }()
	select {
	case <-done:
	case <-time.After(100 * time.Millisecond):
		w.done <- nil
		<-done
		t.Fatal("second World.Close blocks forever draining the single-use done value")
	}
}
func TestMovingViewHasSpeed(t *testing.T) {
	w := startWorld(t)
	var r recorder
	s, err := w.Join(t.Context(), Visitor, r.watch)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	waitActing(t, s)
	if err = s.Do(Action{Verb: meaning.Walk, X: -69600, Y: 257550}); err != nil {
		t.Fatal(err)
	}
	r.wait(t, "movement", func(r *recorder) bool { return r.view.Self.ToX != r.view.Self.X || r.view.Self.ToY != r.view.Self.Y })
	v := s.View()
	if v.Self.Speed <= 0 {
		t.Fatalf("moving self has speed=%d, from=(%d,%d) to=(%d,%d); position() will never animate it", v.Self.Speed, v.Self.X, v.Self.Y, v.Self.ToX, v.Self.ToY)
	}
}
func TestConcurrentActionsWithoutWatchLeaveInOrder(t *testing.T) {
	codec, _ := meaning.NewCodec(goworld.Limits(1))
	a, b := net.Pipe()
	defer a.Close()
	defer b.Close()
	s := &Session{codec: codec, stream: &gateway.Stream{Conn: a, Limit: 8192}, ready: true, control: 1, pending: map[uint64]string{}}
	const count = 128
	read := make(chan []uint64, 1)
	go func() {
		var ns []uint64
		peer := &gateway.Stream{Conn: b, Limit: 8192}
		for range count {
			raw, e := peer.Read(time.Now().Add(5 * time.Second))
			if e != nil {
				break
			}
			rs, _ := codec.Decode(raw)
			ns = append(ns, fieldU(rs[0], 1))
		}
		read <- ns
	}()
	var wg sync.WaitGroup
	start := make(chan struct{})
	for i := range count {
		wg.Go(func() {
			<-start
			if err := s.Do(Action{Verb: meaning.Walk, X: int32(i), Y: 1}); err != nil {
				t.Error(err)
			}
		})
	}
	close(start)
	wg.Wait()
	got := <-read
	for i, n := range got {
		if n != uint64(i+1) {
			t.Fatalf("nil-watch wire attempts %v, want 1..%d in order", got, count)
		}
	}
}
func TestWatchNeedsNoLocking(t *testing.T) {
	codec, _ := meaning.NewCodec(goworld.Limits(1))
	a, b := net.Pipe()
	defer a.Close()
	defer b.Close()
	var calls int
	s := &Session{codec: codec, stream: &gateway.Stream{Conn: a, Limit: 8192}, ready: true, epoch: 1, control: 1, objects: map[int32]*tracked{}, pending: map[uint64]string{}, watch: func(Update) { calls++ }}
	const count = 128
	done := make(chan struct{})
	go func() {
		defer close(done)
		peer := &gateway.Stream{Conn: b, Limit: 8192}
		for range count {
			if _, err := peer.Read(time.Now().Add(5 * time.Second)); err != nil {
				return
			}
		}
	}()
	var wg sync.WaitGroup
	wg.Go(func() {
		for range count {
			s.consume([]wire.Record{{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(wire.CheckpointEnd)), wire.U(2, true, 1), wire.U(3, true, 1)}}})
		}
	})
	for range count {
		if err := s.Do(Action{Verb: meaning.Walk, X: 1, Y: 1}); err != nil {
			t.Fatal(err)
		}
	}
	wg.Wait()
	<-done
}
