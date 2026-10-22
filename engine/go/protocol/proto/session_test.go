// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"math"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func fixture(t *testing.T) *Session {
	t.Helper()
	s, e := New(Config{Results: 5, Pending: 2, IntentBytes: 16, EvidenceMS: 100, QueueMS: 20, Classes: []string{"a", "b"}})
	if e != nil {
		t.Fatal(e)
	}
	if _, e = s.Take(1); e != nil {
		t.Fatal(e)
	}
	return s
}
func add(t *testing.T, s *Session, n uint64, class string, wait bool) {
	t.Helper()
	_, epoch := s.Controller()
	a := Attempt{N: n, Control: epoch, Verb: 9, Payload: []byte{byte(n)}}
	ad := s.Begin(1, true, true, a, 10)
	if !ad.Allocated || ad.Result != nil || ad.Close != nil {
		t.Fatalf("admission: %+v", ad)
	}
	if o, e := s.Prepare(n, 7, class, wait); e != nil || o != nil {
		t.Fatal(o, e)
	}
}
func TestReplayFloorAndPendingRetention(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	if e := s.Contact(1, 10); e != nil {
		t.Fatal(e)
	}
	if _, e := s.Sent(1, false); e != nil {
		t.Fatal(e)
	}
	for n := uint64(2); n <= 7; n++ {
		add(t, s, n, "", false)
		if _, e := s.Sent(n, true); e != nil {
			t.Fatal(e)
		}
	}
	_, epoch := s.Controller()
	old := s.Begin(1, true, true, Attempt{N: 2, Control: epoch, Verb: 9, Payload: []byte{2}}, 11)
	if old.Result == nil || old.Result.Stage != wire.Unrecoverable || old.Result.Reason != wire.TooOld {
		t.Fatalf("old %+v", old)
	}
	replay := s.Begin(1, true, true, Attempt{N: 1, Control: epoch, Verb: 9, Payload: []byte{1}}, 11)
	if !replay.Duplicate || replay.Allocated || replay.Result == nil || replay.Result.Stage != wire.Sent {
		t.Fatal(replay)
	}
	if len(s.entries) != s.config.Results || s.Floor() <= 2 {
		t.Fatal("cache is not bounded", s.Floor())
	}
	mismatch := s.Begin(1, true, true, Attempt{N: 1, Control: epoch, Verb: 9, Payload: []byte("different")}, 11)
	if mismatch.Close == nil || mismatch.Result.Reason != wire.DuplicateMismatch {
		t.Fatal(mismatch)
	}
}
func TestTaintSurvivesExpiryAndProjection(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	s.Contact(1, 10)
	s.Sent(1, false)
	if !s.Eligible(1, 11) || s.Eligible(1, 9) || s.Eligible(1, 110) {
		t.Fatal("window edges")
	}
	o := s.Expire(110)
	if len(o) != 1 || o[0].Stage != wire.Unknown {
		t.Fatal(o)
	}
	if _, e := s.Relocate(0); e != nil {
		t.Fatal(e)
	}
	add(t, s, 2, "a", true)
	s.Contact(2, 120)
	s.Sent(2, false)
	if s.Eligible(2, 121) {
		t.Fatal("expired producer was forgotten")
	}
	if _, e := s.Done(1, 121); e == nil {
		t.Fatal("late result changed")
	}
	if _, e := s.Done(2, 121); e == nil {
		t.Fatal("ambiguous evidence used")
	}
}
func TestBusyUsesResolvedEffectAndClassesAreIndependent(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	ad := s.Begin(1, true, true, Attempt{N: 2, Control: 1, Verb: 11}, 10)
	if !ad.Allocated {
		t.Fatal(ad)
	}
	out, e := s.Prepare(2, 5, "a", true)
	if e != nil || out == nil || out.Reason != wire.Busy || out.Requested != 11 || out.Effective != 5 {
		t.Fatal(out, e)
	}
	add(t, s, 3, "b", true)
	s.Contact(3, 10)
	s.Sent(3, false)
	if _, e := s.Done(3, 11); e != nil {
		t.Fatal(e)
	}
}
func TestControlFenceAndUnallocatedNumbers(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	grant, e := s.Take(2)
	if e != nil || grant.NextN != 2 {
		t.Fatal(grant, e)
	}
	late := s.Begin(1, true, true, Attempt{N: 2, Control: 1, Verb: 9}, 10)
	if late.Allocated || late.Result.Reason != wire.StaleControl || s.NextN() != 2 {
		t.Fatal(late)
	}
	out, e := s.CheckWrite(1, 11)
	if e != nil || out.Reason != wire.StaleControl || out.Stage != wire.Refused {
		t.Fatal(out, e)
	}
	ad := s.Begin(2, true, true, Attempt{N: 2, Control: grant.Epoch, Verb: 9}, 11)
	if !ad.Allocated {
		t.Fatal(ad)
	}
	s.Prepare(2, 5, "a", true)
	if out, e := s.CheckWrite(2, 11); e != nil || out != nil {
		t.Fatal(out, e)
	}
	s.Contact(2, 11)
	s.Take(1)
	out, e = s.CheckWrite(2, 12)
	if e != nil || out.Stage != wire.Unknown || out.Reason != wire.Interrupted || out.Delivery != wire.Partial {
		t.Fatal(out, e)
	}
	watch := s.Begin(9, false, true, Attempt{N: s.NextN(), Control: 1, Verb: 9}, 12)
	if watch.Allocated || watch.Result.Reason != wire.Watch {
		t.Fatal(watch)
	}
}
func TestQueueExpiryAndPartialFailure(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "", false)
	o, e := s.CheckWrite(1, 31)
	if e != nil || o.Reason != wire.QueueExpired {
		t.Fatal(o, e)
	}
	add(t, s, 2, "a", true)
	if e := s.Contact(2, 10); e != nil {
		t.Fatal(e)
	}
	o, e = s.CheckWrite(2, 110)
	if e != nil || o.Stage != wire.Unknown || o.Reason != wire.Timeout {
		t.Fatal(o, e)
	}
}
func TestCandidateFinalizesAtWindowEnd(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	s.Contact(1, 10)
	s.Sent(1, false)
	if _, e := s.Done(1, 110); e == nil {
		t.Fatal("deadline receipt counted")
	}
	out, e := s.CommitWitness(1, 30, 110)
	if e != nil || out.Stage != wire.Done {
		t.Fatal(out, e)
	}
	if got := s.Expire(110); len(got) != 0 {
		t.Fatal(got)
	}
	if _, e := s.Fail(1, wire.Timeout); e == nil {
		t.Fatal("final result regressed")
	}
}
func TestExhaustionAndOversizedReplay(t *testing.T) {
	s := fixture(t)
	future := s.Begin(1, true, true, Attempt{N: 2, Control: 1, Verb: 1}, 1)
	if future.Result == nil || future.Result.Reason != wire.OutOfOrder || future.Close == nil || s.NextN() != 1 || len(s.entries) != 0 {
		t.Fatal("future number allocated", future)
	}
	payload := make([]byte, 17)
	a := Attempt{N: 1, Control: 1, Verb: 1, Payload: payload}
	ad := s.Begin(1, true, true, a, 1)
	if ad.Result == nil || ad.Result.Reason != wire.Invalid {
		t.Fatal(ad)
	}
	ad = s.Begin(1, true, true, a, 1)
	if !ad.Duplicate || ad.Result.Reason != wire.Invalid {
		t.Fatal(ad)
	}
	if len(s.entries[0].Attempt.Payload) != 0 {
		t.Fatal("oversized bytes retained")
	}
	s.next = math.MaxUint64
	ad = s.Begin(1, true, true, Attempt{N: math.MaxUint64, Control: 1, Verb: 1}, 1)
	if ad.Close == nil || *ad.Close != wire.Internal {
		t.Fatal(ad)
	}
	s = fixture(t)
	s.control = math.MaxUint64
	if _, e := s.Take(2); e != ErrExhausted {
		t.Fatal(e)
	}
	s = fixture(t)
	s.projection = math.MaxUint64
	if _, e := s.Relocate(0); e != ErrExhausted {
		t.Fatal(e)
	}
}
func TestConnectionCannotRegainLostControlThroughCheckpoint(t *testing.T) {
	c, e := NewConnection(2)
	if e != nil {
		t.Fatal(e)
	}
	if e = c.Welcome(1, wire.Act); e != nil {
		t.Fatal(e)
	}
	c.Begin(1)
	if _, e = c.Allocate(); e == nil {
		t.Fatal("acted before ready")
	}
	c.Fact(1, 3)
	c.End(1, true)
	c.Grant(1, 4)
	c.Lose(2)
	c.Begin(1)
	c.End(1, true)
	if c.Acting {
		t.Fatal("lost grant restored by resync")
	}
	if _, e = c.Allocate(); e == nil {
		t.Fatal(e)
	}
	if ok, e := c.Fact(1, 2); e == nil || ok {
		t.Fatal("sequence reversed")
	}
	if ok, e := c.Fact(0, 9); e != nil || ok {
		t.Fatal("old epoch applied")
	}
	if _, e = c.Incomplete("d"); e != nil {
		t.Fatal(e)
	}
	c.Begin(1)
	c.End(1, true)
	if _, e = c.Incomplete("d"); e != wire.ErrUnsupported {
		t.Fatal("repair loop", e)
	}
	if CheckpointFailure(true) != wire.SlowConsumer || CheckpointFailure(false) != wire.ClosedWorld {
		t.Fatal("timeout causes merged")
	}
}

func TestConfiguredNegotiation(t *testing.T) {
	hello := wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 0), wire.U(2, true, 3), wire.Data(3, true, "d/1")}}
	v, m, e := Negotiate(hello, []uint64{1, 2}, []string{"d/1"})
	if e != nil || v != 2 || m != "d/1" {
		t.Fatal(v, m, e)
	}
	if _, _, e = Negotiate(hello, []uint64{4}, []string{"d/1"}); e != wire.ErrUnsupported {
		t.Fatal(e)
	}
	if _, _, e = Negotiate(hello, []uint64{1}, []string{"other/1"}); e != wire.ErrUnsupported {
		t.Fatal(e)
	}
}
