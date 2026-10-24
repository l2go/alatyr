// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"context"
	"errors"
	"slices"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/fixture"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func snapshot() bridge.Snapshot {
	fact := func(seq, kind uint64, domain string, fs ...wire.Field) wire.Record {
		return wire.Record{Type: wire.Fact, Fields: append([]wire.Field{wire.U(1, true, kind), wire.U(2, true, seq), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, domain), wire.U(6, true, 1)}, fs...)}
	}
	return bridge.Snapshot{GatewayEpoch: 1, Ready: true, FenceSeq: 4, Facts: []wire.Record{
		fact(1, 1, "objects", wire.S(64, true, 1), wire.U(19, true, 1), wire.Obj(25, true, []wire.Field{wire.S(1, true, 100), wire.S(5, true, 1)})),
		fact(2, 14, "inventory", wire.Items(24, true, []wire.Value{})),
		fact(3, 23, "skills", wire.U(31, true, 1), wire.Items(30, true, []wire.Value{})),
		fact(4, 21, "quests", wire.U(31, true, 1), wire.Items(29, true, []wire.Value{})),
	}}
}

func setup(t *testing.T) (*Session, *World, gateway.State, *fixture.World) {
	t.Helper()
	f, err := fixture.New(fixture.Config{})
	if err != nil {
		t.Fatal(err)
	}
	s, w, st, err := New(f, snapshot(), Config{Meaning: gatewaytest.Config(), Origin: time.Now()})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := s.Close(); err != nil {
			t.Error(err)
		}
	})
	return s, w, st, f
}

func TestForeignValuesReturnErrors(t *testing.T) {
	_, w, st, f := setup(t)
	for _, v := range []any{nil, "foreign", (*plan)(nil), &plan{}} {
		a := &gateway.Action{Value: v}
		for _, tc := range []struct {
			name string
			call func() error
		}{
			{"Start", func() error { return st.Start(a) }},
			{"Confirmed", func() error { _, err := st.Confirmed(a); return err }},
			{"Witness", func() error { _, err := st.Witness(a, gateway.Update{Value: packet{}}); return err }},
			{"CandidateValid", func() error { _, err := st.CandidateValid(a); return err }},
			{"Proof", func() error { _, err := st.Proof(a, 0); return err }},
		} {
			t.Run(tc.name, func(t *testing.T) {
				if err := tc.call(); !errors.Is(err, meaning.ErrValue) {
					t.Fatalf("error=%v", err)
				}
			})
		}
		if _, err := st.Apply(gateway.Update{Value: v}, 0); !errors.Is(err, meaning.ErrValue) {
			t.Fatal(err)
		}
		if err := w.Write(t.Context(), gateway.Step{Value: v}); !errors.Is(err, meaning.ErrValue) {
			t.Fatal(err)
		}
		if st.Validate(a, 0) != wire.ReasonInvalid {
			t.Fatal("foreign Validate accepted")
		}
	}
	if len(f.Writes()) != 0 {
		t.Fatal("foreign value wrote")
	}
}

func TestSnapshotRequiresPresentCompleteLists(t *testing.T) {
	for _, i := range []int{1, 2, 3} {
		t.Run([]string{"", "inventory", "skills", "quests"}[i], func(t *testing.T) {
			f, err := fixture.New(fixture.Config{})
			if err != nil {
				t.Fatal(err)
			}
			defer f.Close()
			snap := snapshot()
			snap.Facts = slices.Delete(snap.Facts, i, i+1)
			if _, _, _, err := New(f, snap, Config{Meaning: gatewaytest.Config(), Origin: time.Now()}); err == nil {
				t.Fatal("absent capture became known-empty")
			}
		})
	}
}

func TestReceiptBeforeWriteReturn(t *testing.T) {
	var f *fixture.World
	var err error
	f, err = fixture.New(fixture.Config{OnWrite: func(ctx context.Context, c bridge.Command, n uint64) error {
		return f.Emit(ctx, bridge.Update{Kind: bridge.LiveBatch, Batch: bridge.Batch{Receipts: []bridge.Receipt{{N: n, Key: c.Key, GatewayEpoch: 1, Stage: wire.StageSent, Requested: c.Verb, Effective: c.Verb, Delivery: wire.DeliveryBackend, ObservedAt: time.Now()}}}})
	}})
	if err != nil {
		t.Fatal(err)
	}
	s, w, st, err := New(f, snapshot(), Config{Meaning: gatewaytest.Config(), Origin: time.Now()})
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	r := wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 42), wire.U(2, true, 1), wire.At(11, true, 1, 2, 3)}}
	a, reason := st.Plan(r, 0)
	if reason != wire.ReasonNone {
		t.Fatal(reason)
	}
	step := a.Steps[0]
	step.N = 42
	step.Role = gateway.StepEffect
	step.DeadlineMS = 5000
	if err = w.Write(t.Context(), step); err != nil {
		t.Fatal(err)
	}
	u := <-w.Updates()
	if _, err = st.Apply(u, 100); err != nil {
		t.Fatal(err)
	}
	if len(w.pending) != 0 {
		t.Fatal("receipt lost early key binding")
	}
}

func TestExpiredBindingsDoNotExhaustCapacity(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s, w, st, _ := setup(t)
		defer s.Close()
		write := func(n uint64) error {
			a, reason := st.Plan(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, n), wire.U(2, true, 1), wire.At(11, true, 1, 2, 3)}}, int64(n))
			if reason != wire.ReasonNone {
				t.Fatal(reason)
			}
			step := a.Steps[0]
			step.N = n
			step.Role = gateway.StepEffect
			step.DeadlineMS = 5000
			return w.Write(t.Context(), step)
		}
		for n := uint64(1); n <= 8; n++ {
			if err := write(n); err != nil {
				t.Fatal(err)
			}
		}
		if err := write(9); err == nil {
			t.Fatal("ninth pending write accepted")
		}
		time.Sleep(5 * time.Second)
		if err := write(9); err != nil {
			t.Fatal(err)
		}
	})
}

func TestDetachExpiryAndCloseJoin(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s, w, _, f := setup(t)
		if err := w.Detach(); err != nil {
			t.Fatal(err)
		}
		if _, _, err := s.Resume(t.Context()); err == nil {
			t.Fatal("unscripted Resume accepted")
		}
		if err := s.Close(); err != nil {
			t.Fatal(err)
		}
		if _, err := f.Write(t.Context(), bridge.Command{Key: 1, Verb: 9}); err == nil {
			t.Fatal("failed Resume left raw port open")
		}
		s, w, _, f = setup(t)
		if err := w.Detach(); err != nil {
			t.Fatal(err)
		}
		time.Sleep(30 * time.Second)
		synctest.Wait()
		if _, _, err := s.Resume(t.Context()); err == nil {
			t.Fatal("expired Resume accepted")
		}
		var wg sync.WaitGroup
		for range 4 {
			wg.Go(func() {
				if err := s.Close(); err != nil {
					t.Error(err)
				}
			})
		}
		wg.Wait()
		if _, err := f.Write(t.Context(), bridge.Command{Key: 1, Verb: 9}); err == nil {
			t.Fatal("expiry left raw port open")
		}
	})
}

func FuzzRawEvent(f *testing.F) {
	codec, err := profile.NewProfileCodec()
	if err != nil {
		f.Fatal(err)
	}
	b, err := codec.Encode(snapshot().Facts[:1])
	if err != nil {
		f.Fatal(err)
	}
	f.Add(b)
	f.Fuzz(func(t *testing.T, b []byte) {
		rs, err := codec.Decode(b)
		if err != nil {
			return
		}
		for _, r := range rs {
			if r.Type == wire.Fact {
				_, err := event(r)
				if err != nil && !errors.Is(err, wire.ErrFrame) && !errors.Is(err, wire.ErrUnsupported) {
					t.Fatal(err)
				}
			}
		}
	})
}
