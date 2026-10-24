// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package fixture

import (
	"context"
	"errors"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestReceiptPresenceAndCapturedTarget(t *testing.T) {
	for _, tc := range []struct {
		name    string
		verb    uint64
		present bool
		target  int64
		valid   bool
	}{
		{"talk-absent", 5, false, 0, false},
		{"talk-empty", 5, true, 0, true},
		{"target-absent", 2, false, 0, false},
		{"target-empty", 2, true, 0, false},
		{"target-match", 2, true, 7, true},
		{"target-mismatch", 2, true, 8, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			w, err := New(Config{})
			if err != nil {
				t.Fatal(err)
			}
			defer w.Close()
			n, err := w.Write(t.Context(), bridge.Command{Key: 1, Verb: tc.verb, Payload: []wire.Field{wire.S(64, true, 7)}})
			if err != nil {
				t.Fatal(err)
			}
			r := bridge.Receipt{N: n, Key: 1, GatewayEpoch: 1, Stage: wire.StageDone, Requested: tc.verb, Effective: tc.verb, Delivery: wire.DeliveryBackend, EffectsPresent: tc.present, ObservedAt: time.Now()}
			if tc.target != 0 {
				r.Effects = []wire.Value{{Fields: []wire.Field{wire.U(1, true, 5), wire.S(8, true, tc.target)}}}
			}
			err = w.Emit(t.Context(), bridge.Update{Kind: bridge.LiveBatch, Batch: bridge.Batch{Receipts: []bridge.Receipt{r}}})
			if (err == nil) != tc.valid {
				t.Fatalf("Emit error=%v, want valid=%v", err, tc.valid)
			}
		})
	}
}

func TestBoundedChurnAndOwnedLog(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, err := New(Config{WriteBytes: 4096})
		if err != nil {
			t.Fatal(err)
		}
		defer w.Close()
		for n := uint64(1); n <= 100; n++ {
			if _, err := w.Write(t.Context(), bridge.Command{Key: n, Verb: 9}); err != nil {
				t.Fatal(err)
			}
			time.Sleep(5 * time.Second)
			count, bytes := w.RetainedWrites()
			if count > 64 || bytes > 4096 || len(w.entries) > 72 {
				t.Fatalf("retained %d/%d/%d", count, bytes, len(w.entries))
			}
		}
		log := w.Writes()
		log[0].Command.Key = 0
		if w.Writes()[0].Command.Key == 0 {
			t.Fatal("write log aliases retained command")
		}
		if _, err := w.Write(t.Context(), bridge.Command{Key: 100, Verb: 9}); err == nil {
			t.Fatal("reused key accepted")
		}
	})
}

func TestCloseUnblocksQueuedEmit(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		w, err := New(Config{Queue: 1})
		if err != nil {
			t.Fatal(err)
		}
		if err := w.Emit(t.Context(), bridge.Update{Kind: bridge.LiveBatch}); err != nil {
			t.Fatal(err)
		}
		done := make(chan error, 1)
		go func() { done <- w.Emit(context.Background(), bridge.Update{Kind: bridge.LiveBatch}) }()
		synctest.Wait()
		if err := w.Close(); err != nil {
			t.Fatal(err)
		}
		var state *bridge.Error
		if err := <-done; !errors.As(err, &state) || state.Kind != bridge.StateError {
			t.Fatalf("Emit error=%v, want closed state", err)
		}
		for range w.Updates() {
		}
	})
}
