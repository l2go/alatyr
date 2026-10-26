// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"errors"
	"net"
	"path/filepath"
	"reflect"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func authoredCatalog(t testing.TB) *catalog.Catalog {
	t.Helper()
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	return cat
}
func sessionCharacter() world.Character {
	return world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
}
func startPortWorld(t *testing.T, cat *catalog.Catalog, store world.Store, opts ...world.Option) (Config, *world.World) {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	opts = append(opts, world.WithReservedIDs([]int32{1}))
	w := world.New(cat, store, opts...)
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	t.Cleanup(func() {
		cancel()
		if err := <-done; err != nil {
			t.Errorf("Run: %v", err)
		}
	})
	return Config{World: w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) { return sessionCharacter(), nil }}, w
}
func openPort(t *testing.T, cfg Config) (*Client, bridge.Snapshot) {
	t.Helper()
	c, snap, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := c.Close(); err != nil {
			t.Errorf("Close: %v", err)
		}
	})
	return c, snap
}
func nextPortUpdate(t *testing.T, p bridge.Port) bridge.Update {
	t.Helper()
	u, ok := <-p.Updates()
	if !ok {
		t.Fatal("unexpected closed Updates")
	}
	return u
}
func awaitReceipt(t *testing.T, p bridge.Port, key uint64) bridge.Receipt {
	t.Helper()
	for range 100 {
		u := nextPortUpdate(t, p)
		if u.Kind != bridge.LiveBatch {
			t.Fatalf("update kind: got %v, want LiveBatch", u.Kind)
		}
		if len(u.Batch.Records) != len(u.Batch.ReceivedAt) {
			t.Fatal("record timestamps differ in length")
		}
		for _, r := range u.Batch.Receipts {
			if r.Key == key {
				return r
			}
		}
	}
	t.Fatal("receipt missing within bounded updates")
	return bridge.Receipt{}
}
func portWalk(key uint64) bridge.Command {
	return bridge.Command{Key: key, Verb: 1, Payload: []wire.Field{wire.At(11, true, -71027, 257883, -3104)}}
}

type contractSession struct {
	port     bridge.Port
	snapshot bridge.Snapshot
	detach   func()
}

// runPortContract is one capability-neutral contract applied to both concrete
// ports. The network endpoint is synthetic; no Java server or content is run.
func runPortContract(t *testing.T, open func(*testing.T) contractSession) {
	t.Helper()
	t.Run("admission_order_and_close", func(t *testing.T) {
		synctest.Test(t, func(t *testing.T) {
			f := open(t)
			p := f.port
			if !f.snapshot.Ready || f.snapshot.GatewayEpoch != 1 || len(f.snapshot.Incomplete) != 0 {
				t.Fatal("opening checkpoint not ready")
			}
			if _, err := p.Resume(t.Context()); err == nil {
				t.Fatal("Resume admitted live session")
			}
			if n, err := p.Write(t.Context(), bridge.Command{}); err == nil || n != 0 {
				t.Fatalf("invalid command: n=%d err=%v", n, err)
			}
			ctx, cancel := context.WithCancel(t.Context())
			cancel()
			if n, err := p.Write(ctx, portWalk(100)); err == nil || n != 0 {
				t.Fatalf("canceled admission: n=%d err=%v", n, err)
			}
			n, err := p.Write(t.Context(), portWalk(7))
			if err != nil || n != 1 {
				t.Fatalf("Write: n=%d err=%v, want 1/nil", n, err)
			}
			r := awaitReceipt(t, p, 7)
			if r.N != n || r.GatewayEpoch != 1 || r.Stage != wire.StageSent || r.Replay || r.EffectsPresent {
				t.Fatalf("walk receipt: %+v", r)
			}
			if n, err := p.Write(t.Context(), portWalk(7)); err == nil || n != 0 {
				t.Fatal("duplicate key admitted")
			}
			if err := p.Close(); err != nil {
				t.Fatal(err)
			}
			if err := p.Close(); err != nil {
				t.Fatal(err)
			}
			closed := 0
			for u := range p.Updates() {
				if u.Kind != bridge.Closed {
					t.Fatalf("after Close: got %v, want Closed", u.Kind)
				}
				closed++
			}
			if closed != 1 {
				t.Fatalf("Closed count: got %d, want 1", closed)
			}
			if _, err := p.Write(t.Context(), portWalk(8)); err == nil {
				t.Fatal("Write after Close")
			}
		})
	})
	t.Run("same_session_recovery", func(t *testing.T) {
		synctest.Test(t, func(t *testing.T) {
			f := open(t)
			p := f.port
			if _, err := p.Write(t.Context(), portWalk(11)); err != nil {
				t.Fatal(err)
			}
			original := awaitReceipt(t, p, 11)
			f.detach()
			for {
				if nextPortUpdate(t, p).Kind == bridge.Detached {
					break
				}
			}
			recovery, err := p.Resume(t.Context())
			if err != nil {
				t.Fatal(err)
			}
			if !recovery.Snapshot.Ready || recovery.Snapshot.GatewayEpoch != 2 || recovery.Snapshot.FenceSeq <= f.snapshot.FenceSeq {
				t.Fatal("recovery checkpoint not fresh")
			}
			if len(recovery.Receipts) != 1 {
				t.Fatalf("replay count: got %d, want 1", len(recovery.Receipts))
			}
			r := recovery.Receipts[0]
			if !r.Replay || r.Key != original.Key || r.N != original.N || r.GatewayEpoch != original.GatewayEpoch || r.Stage != original.Stage {
				t.Fatalf("replayed receipt changed identity: %+v", r)
			}
			if _, err := p.Write(t.Context(), portWalk(11)); err == nil {
				t.Fatal("recovery forgot key")
			}
			if n, err := p.Write(t.Context(), portWalk(12)); err != nil || n != 2 {
				t.Fatalf("post-recovery attempt: %d/%v", n, err)
			}
			if r := awaitReceipt(t, p, 12); r.GatewayEpoch != 2 || r.Replay {
				t.Fatal("new receipt in old epoch")
			}
		})
	})
}

func TestPortContract(t *testing.T) {
	cat := authoredCatalog(t)
	for _, implementation := range []string{"world", "bridge"} {
		t.Run(implementation, func(t *testing.T) {
			open := func(t *testing.T) contractSession {
				if implementation == "bridge" {
					return networkContractPort(t)
				}
				cfg, w := startPortWorld(t, cat, memstore.New())
				c, snap := openPort(t, cfg)
				return contractSession{c, snap, func() {
					if _, _, err := w.Subscribe(t.Context(), c.Owner()); err != nil {
						t.Fatal(err)
					}
				}}
			}
			runPortContract(t, open)
		})
	}
}

// networkContractPort speaks the frozen bridge profile through net.Pipe. Its
// two connections belong to the test and are joined during cleanup.
func networkContractPort(t *testing.T) contractSession {
	t.Helper()
	var dials atomic.Int32
	var workers sync.WaitGroup
	detach := make(chan struct{})
	codec, err := profile.NewProfileCodec()
	if err != nil {
		t.Fatal(err)
	}
	cfg := bridge.Config{Secret: [32]byte{1}, DialContext: func(context.Context, string, string) (net.Conn, error) {
		epoch := uint64(dials.Add(1))
		a, b := net.Pipe()
		workers.Add(1)
		go func() {
			defer workers.Done()
			defer b.Close()
			finished := make(chan struct{})
			defer close(finished)
			read := func() wire.Record {
				rs, err := codec.Read(b)
				if err != nil {
					return wire.Record{}
				}
				return rs[0]
			}
			send := func(rs ...wire.Record) {
				if err := codec.Write(b, rs); err != nil {
					t.Error(err)
				}
			}
			read()
			seq := uint64(1)
			if epoch == 2 {
				seq = 3
			}
			fact := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, seq), wire.S(3, true, 0), wire.U(4, true, epoch), wire.Data(5, true, "objects"), wire.U(6, true, 1), wire.S(64, true, 7), wire.U(19, true, 1)}}
			send(checkpointRecord(wire.CheckpointBegin, epoch, false), fact, checkpointRecord(wire.CheckpointEnd, epoch, true))
			if epoch == 2 {
				send(receiptRecord(bridge.Receipt{N: 1, Requested: 1, Effective: 1, Stage: wire.StageSent, Reason: wire.ReasonNone, Delivery: wire.DeliveryBackend}))
			}
			var limits []wire.Field
			for n, v := range profile.AdvertisedLimits() {
				limits = append(limits, wire.U(n, true, v))
			}
			send(wire.Record{Type: profile.Opened, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 7), wire.Data(3, true, "synthetic"), wire.U(4, true, epoch), wire.U(5, true, 0), wire.Obj(6, true, limits), wire.U(7, true, 1), wire.Data(8, true, profile.Name)}})
			for {
				r := read()
				if r.Type == 0 {
					return
				} // connection lost after the scripted detach
				if r.Type == profile.Close {
					send(wire.Record{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0)}})
					return
				}
				if r.Type == wire.Ping {
					send(wire.Record{Type: wire.Pong, Fields: r.Fields})
					continue
				}
				if r.Type != wire.Intent {
					t.Errorf("request: got %d, want Intent", r.Type)
					return
				}
				n := profile.U(r, 1)
				send(receiptRecord(bridge.Receipt{N: n, Requested: 1, Effective: 1, Stage: wire.StageSent, Reason: wire.ReasonNone, Delivery: wire.DeliveryBackend}))
				if epoch == 1 {
					// Close and detach race through a dedicated owner. The reader
					// can still accept a Close while the test retains the port.
					workers.Add(1)
					go func() {
						defer workers.Done()
						select {
						case <-detach:
							b.Close()
						case <-finished:
						}
					}()
				}
			}
		}()
		return a, nil
	}}
	c, snap, err := bridge.Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := c.Close(); err != nil {
			t.Error(err)
		}
		select {
		case <-detach:
		default:
			close(detach)
		}
		workers.Wait()
	})
	return contractSession{c, snap, func() { close(detach) }}
}

func TestWorldPortMoveCloseAndSave(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := memstore.New()
		cfg, w := startPortWorld(t, cat, store)
		c, _ := openPort(t, cfg)
		ctx, cancel := context.WithCancel(t.Context())
		cancel()
		if _, err := c.Write(ctx, portWalk(99)); err == nil {
			t.Fatal("canceled Write")
		}
		if _, err := c.Write(t.Context(), portWalk(1)); err != nil {
			t.Fatal(err)
		}
		awaitReceipt(t, c, 1)
		// Virtual time advances to a movement observation, without a sleep or
		// wall-clock poll. The world owns the timer producing this record.
		for {
			u := nextPortUpdate(t, c)
			arrived := false
			for _, r := range u.Batch.Records {
				if r.Type == wire.Fact && profile.U(r, 1) == 3 {
					arrived = true
				}
			}
			if arrived {
				break
			}
		}
		if err := c.Close(); err != nil {
			t.Fatal(err)
		}
		chars, err := store.Characters(t.Context(), "example")
		if err != nil || len(chars) != 1 || chars[0].X != -71027 {
			t.Fatalf("saved movement: %+v/%v", chars, err)
		}
		if _, err := w.Capture(t.Context(), c.Owner()); !errors.Is(err, world.ErrNotPresent) {
			t.Fatalf("after close capture: %v", err)
		}
	})
}

func TestWorldPortZeroAndIdentity(t *testing.T) {
	var zero Client
	if zero.Owner() != (world.Ref{}) || zero.Close() != nil {
		t.Fatal("zero client not inactive")
	}
	if _, err := zero.Write(t.Context(), portWalk(1)); err == nil {
		t.Fatal("zero Write")
	}
	if _, err := zero.Resume(t.Context()); err == nil {
		t.Fatal("zero Resume")
	}
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		for _, id := range []bridge.Identity{{Account: "other", Character: "Walker"}, {Account: "example", Character: "Other"}, {Account: "", Character: "Walker"}} {
			if c, _, err := Open(t.Context(), cfg, id); err == nil || c != nil {
				t.Fatal("identity mismatch accepted")
			}
		}
		if _, err := w.Capture(t.Context(), world.Ref{ID: 1, Gen: 1}); !errors.Is(err, world.ErrNotPresent) {
			t.Fatal("identity mismatch entered world")
		}
	})
}

func TestReceiptCopiesDoNotAliasJournal(t *testing.T) {
	r := bridge.Receipt{Effects: []wire.Value{{Fields: []wire.Field{wire.Data(1, true, "original")}}}, BridgeReason: new(uint64(20)), Text: &wire.Value{Data: []byte("notice")}}
	original := cloneReceipt(r)
	copied := cloneReceipt(r)
	copied.Effects[0].Fields[0].Value.Data[0] = 'X'
	*copied.BridgeReason = 0
	copied.Text.Data[0] = 'X'
	if !reflect.DeepEqual(r, original) {
		t.Fatal("receipt copy aliases original")
	}
}

func TestPortReceiptsAreTypedEvidence(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, _ := startPortWorld(t, cat, memstore.New())
		port, snapshot := openPort(t, cfg)
		var target int64
		for _, r := range snapshot.Facts {
			if r.Type == wire.Fact && profile.U(r, 1) == 1 && profile.S(r, 64) != 1 {
				target = profile.S(r, 64)
				break
			}
		}
		if target == 0 {
			t.Fatal("authored target absent")
		}
		if _, err := port.Write(t.Context(), bridge.Command{Key: 1, Verb: 2, Payload: []wire.Field{wire.S(64, true, target)}}); err != nil {
			t.Fatal(err)
		}
		update := nextPortUpdate(t, port)
		if update.Kind != bridge.LiveBatch || len(update.Batch.Receipts) != 1 || len(update.Batch.Records) != 0 || len(update.Batch.ReceivedAt) != 0 {
			t.Fatalf("receipt transport: %+v, want typed evidence before observations", update)
		}
		if update.Batch.Receipts[0].Stage != wire.StageDone {
			t.Fatalf("selection receipt: %+v", update.Batch.Receipts[0])
		}
	})
}
