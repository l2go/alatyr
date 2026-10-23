// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"context"
	"errors"
	"math"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestRelocationBreaksProofAndWaitsForCheckpoint(t *testing.T) {
	checkpoint := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.handshake(1, 1)
		e.read()
		e.send(rawFact(2, 1, 2, wire.S(64, true, 7)))
		e.send(rawFact(3, 1, 4, wire.S(64, true, 7), wire.U(66, true, 4), wire.U(67, true, 1)))
		<-checkpoint
		e.send(cp(1, 1, 0), rawFact(4, 1, 1, wire.S(64, true, 7), wire.U(19, true, 1)), cp(2, 1, 1))
		e.send(sent(1, 1))
		e.close()
	})
	c := openTest(t, cfg)
	if _, err := c.Write(context.Background(), walk(1)); err != nil {
		t.Fatal(err)
	}
	unproven := nextUpdate(t, c)
	if len(unproven.Batch.Receipts) != 0 || profile.U(unproven.Batch.Records[0], 2) != 2 {
		t.Fatal("old proof survived relocation")
	}
	corrected := nextUpdate(t, c)
	if len(corrected.Batch.Records) != 1 || profile.U(corrected.Batch.Records[0], 66) != 4 {
		t.Fatal(corrected)
	}
	if n, err := c.Write(context.Background(), walk(2)); n != 0 || err == nil {
		t.Fatal("command before ready")
	}
	close(checkpoint)
	for i := 0; i < 4; i++ {
		nextUpdate(t, c)
	}
	c.Close()
	wait()
}
func TestProofExpiryKeepsOriginalReceiveTime(t *testing.T) {
	base := time.Now()
	var elapsed atomic.Int64
	release := make(chan struct{})
	gotFact := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.handshake(1, 1)
		e.read()
		e.send(rawFact(2, 1, 2))
		e.send(wire.Record{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 99)}})
		e.read()
		close(gotFact)
		<-release
		e.send(sent(1, 1))
		e.close()
	})
	cfg.Now = func() time.Time { return base.Add(time.Duration(elapsed.Load())) }
	c := openTest(t, cfg)
	if _, err := c.Write(context.Background(), walk(1)); err != nil {
		t.Fatal(err)
	}
	<-gotFact
	elapsed.Store(int64(6 * time.Second))
	u := nextUpdate(t, c)
	if len(u.Batch.Receipts) != 0 || len(u.Batch.ReceivedAt) != 1 || !u.Batch.ReceivedAt[0].Equal(base) {
		t.Fatal("proof expiry retimed fact")
	}
	close(release)
	late := nextUpdate(t, c)
	if len(late.Batch.Records) != 0 || len(late.Batch.Receipts) != 1 {
		t.Fatal("late receipt fabricated witness")
	}
	c.Close()
	wait()
}
func TestLateLiveDataBehindOpeningBarrier(t *testing.T) {
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.read()
		e.send(cp(1, 1, 0), rawFact(1, 1, 1, wire.S(64, true, 7), wire.U(19, true, 1)), cp(2, 1, 1), rawFact(2, 1, 2))
		e.send(opened(1))
		e.close()
	})
	c := openTest(t, cfg)
	u := nextUpdate(t, c)
	if len(u.Batch.Records) != 1 || profile.U(u.Batch.Records[0], 2) != 2 || u.Batch.Snapshot {
		t.Fatal("late live data not fenced", u)
	}
	c.Close()
	wait()
}
func TestHeartbeatUsesExpectedNonceAndServiceBudget(t *testing.T) {
	base := time.Now()
	var elapsed atomic.Int64
	ready := make(chan struct{})
	done := make(chan struct{})
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.handshake(1, 1)
		ping := e.read()
		if ping.Type != wire.Ping || profile.U(ping, 1) != 1 {
			t.Error("missing unique ping")
		}
		e.send(wire.Record{Type: wire.Pong, Fields: ping.Fields})
		close(ready)
		<-done
		e.close()
	})
	cfg.Now = func() time.Time { return base.Add(time.Duration(elapsed.Load())) }
	c := openTest(t, cfg)
	elapsed.Store(int64(16 * time.Second))
	<-ready
	close(done)
	c.Close()
	wait()
}
func TestServiceFloodFailsClosed(t *testing.T) {
	cfg, _, wait := testConfig(t, func(e endpoint) {
		e.handshake(1, 1)
		var rs []wire.Record
		for i := 0; i < 5; i++ {
			rs = append(rs, wire.Record{Type: wire.Pong, Fields: []wire.Field{wire.U(1, true, uint64(i+1))}})
		}
		e.send(rs...)
		e.close()
	})
	c := openTest(t, cfg)
	for u := range c.Updates() {
		if u.Kind == LiveBatch {
			t.Fatal("service data applied")
		}
	}
	c.Close()
	wait()
}
func TestOpenRefusalAndInvalidLimits(t *testing.T) {
	for _, mode := range []string{"refused", "limits", "incomplete", "wrong epoch"} {
		t.Run(mode, func(t *testing.T) {
			cfg, _, wait := testConfig(t, func(e endpoint) {
				e.read()
				if mode == "refused" {
					e.send(wire.Record{Type: profile.Opened, Fields: []wire.Field{wire.U(1, true, 2), wire.U(5, true, 11)}})
					return
				}
				end := cp(2, 1, 1)
				if mode == "incomplete" {
					end.Fields = append(end.Fields, wire.Items(4, true, []wire.Value{{Data: []byte("objects")}}))
				}
				e.send(cp(1, 1, 0), rawFact(1, 1, 1, wire.S(64, true, 7), wire.U(19, true, 1)), end)
				r := opened(1)
				if mode == "wrong epoch" {
					r.Fields[3] = wire.U(4, true, 2)
				}
				if mode == "limits" {
					v := r.Fields[5].Value
					for i := range v.Fields {
						if v.Fields[i].Number == 1 {
							v.Fields[i].Value.U = 123
						}
					}
					r.Fields[5].Value = v
					raw, _ := wire.New(profile.Limits(), profile.Registry())
					raw.Write(e.conn, []wire.Record{r})
				} else {
					e.send(r)
				}
			})
			c, _, err := Open(context.Background(), cfg, Identity{"guest", "Guest"})
			if err == nil || c != nil {
				t.Fatal("invalid opening published client")
			}
			typed, ok := errors.AsType[*Error](err)
			if !ok {
				t.Fatal("untyped error")
			}
			if mode == "refused" && (typed.Kind != OpenRefused || typed.Code != 11) {
				t.Fatal(err)
			}
			wait()
		})
	}
}
func TestBoundedOwnerAndSequenceValidation(t *testing.T) {
	c := &Client{cfg: Config{Now: time.Now}, codec: mustCodec(t)}
	o := &owner{c: c, phase: phaseLive, epoch: 1, next: 1, seq: 4, self: 7, entries: map[uint64]*entry{}}
	if o.validateFrame([]wire.Record{rawFact(4, 1, 2)}) == nil {
		t.Fatal("repeated sequence")
	}
	if o.validateFrame([]wire.Record{rawFact(5, 2, 2)}) == nil {
		t.Fatal("stale transport epoch")
	}
	if o.validateFrame([]wire.Record{rawFact(5, 1, 4, wire.S(64, true, 9), wire.U(66, true, 1), wire.U(67, true, 1))}) == nil {
		t.Fatal("foreign own teleport")
	}
	wrong := rawFact(5, 1, 25)
	if !errors.Is(o.validateFrame([]wire.Record{wrong}), wire.ErrUnsupported) {
		t.Fatal("unknown kind")
	}
	o.inCheckpoint = true
	o.checkpointBytes = 2097151
	r := rawFact(5, 1, 2)
	r.EncodedBytes = 2
	if errorKind(o.validateFrame([]wire.Record{r})) != Capacity {
		t.Fatal("snapshot cap")
	}
	o.queueBytes = 262144
	if o.enqueue(Batch{Records: []wire.Record{r}}, 1) {
		t.Fatal("backlog cap")
	}
	o.phase = phaseDetached
	op := operation{ctx: context.Background(), command: walk(1), reply: make(chan response, 1)}
	o.write(op)
	if (<-op.reply).n != 0 {
		t.Fatal("detached registration")
	}
	o.phase = phaseLive
	o.ready = true
	o.next = 0
	o.write(op)
	if (<-op.reply).err == nil {
		t.Fatal("number wrap")
	}
	o.next = 1
	for n := uint64(1); n <= 8; n++ {
		o.entries[n] = &entry{command: walk(n), n: n}
	}
	op.command = walk(100)
	o.write(op)
	if errorKind((<-op.reply).err) != Capacity {
		t.Fatal("pending cap")
	}
}
func mustCodec(t *testing.T) *profile.Codec {
	t.Helper()
	c, err := profile.NewProfileCodec()
	if err != nil {
		t.Fatal(err)
	}
	return c
}
func TestReceiptCacheConflictAndNoInventedFacts(t *testing.T) {
	o := &owner{c: &Client{cfg: Config{Now: time.Now}}, phase: phaseLive, epoch: 1, entries: map[uint64]*entry{1: {command: walk(10), n: 1, epoch: 1}}}
	first := sent(1, 1)
	retained := first
	o.entries[1].result = &retained
	if err := o.validateFrame([]wire.Record{first}); err != nil {
		t.Fatal(err)
	}
	reordered := sent(1, 1)
	reordered.Fields[0], reordered.Fields[1] = reordered.Fields[1], reordered.Fields[0]
	if err := o.validateFrame([]wire.Record{reordered}); err != nil {
		t.Fatal("field order changed duplicate meaning", err)
	}
	other := sent(1, 1)
	other.Fields[1] = wire.U(2, true, 4)
	other.Fields[2] = wire.U(3, true, 17)
	if o.validateFrame([]wire.Record{other}) == nil {
		t.Fatal("cached result rewritten")
	}
	o.entries[1].result = nil
	if o.validateFrame([]wire.Record{first, other}) == nil {
		t.Fatal("conflicting results in one frame")
	}
	if err := o.apply(event{records: []wire.Record{first}, at: time.Now()}); err != nil {
		t.Fatal(err)
	}
	if len(o.queue) != 1 || len(o.queue[0].u.Batch.Records) != 0 || len(o.queue[0].u.Batch.Receipts) != 1 {
		t.Fatal("receipt fabricated facts")
	}
}
func TestConcurrentCloseChurn(t *testing.T) {
	for i := 0; i < 6; i++ {
		cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1); e.close() })
		c := openTest(t, cfg)
		var wg sync.WaitGroup
		for j := 0; j < 4; j++ {
			wg.Add(1)
			go func() { defer wg.Done(); c.Close() }()
		}
		wg.Wait()
		wait()
	}
}
func TestErrorsNeverExposeWorldOrIdentity(t *testing.T) {
	for kind := ConfigError; kind <= RemoteCloseUnconfirmed; kind++ {
		s := (&Error{Kind: kind, Code: math.MaxUint64}).Error()
		for _, secret := range []string{"Guest", "guest", "saved_action", "127.0.0.1"} {
			if strings.Contains(s, secret) {
				t.Fatal("sensitive error detail")
			}
		}
	}
}

func TestConfirmedResumeCanRecoverAgain(t *testing.T) {
	cfg, dials, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1) }, func(e endpoint) { e.read(); e.send(cp(1, 2, 0)) }, func(e endpoint) { e.handshake(3, 2); e.close() })
	c := openTest(t, cfg)
	nextUpdate(t, c)
	if _, err := c.Resume(context.Background()); err == nil {
		t.Fatal("partial checkpoint returned success")
	}
	if u := nextUpdate(t, c); u.Kind != Detached {
		t.Fatal(u)
	}
	rec, err := c.Resume(context.Background())
	if err != nil || rec.Snapshot.GatewayEpoch != 3 {
		t.Fatal("confirmed epoch lost", rec, err)
	}
	c.Close()
	wait()
	if dials.Load() != 3 {
		t.Fatal("extra resume")
	}
}

func TestResumeDialFailureDoesNotGuessOrRefreshEpoch(t *testing.T) {
	cfg, _, wait := testConfig(t, func(e endpoint) { e.handshake(1, 1) }, func(e endpoint) { e.handshake(2, 2); e.close() })
	dial := cfg.DialContext
	var attempts atomic.Int32
	cfg.DialContext = func(ctx context.Context, network, address string) (net.Conn, error) {
		if attempts.Add(1) == 2 {
			return nil, errors.New("unavailable")
		}
		return dial(ctx, network, address)
	}
	c := openTest(t, cfg)
	nextUpdate(t, c)
	if _, err := c.Resume(context.Background()); err == nil {
		t.Fatal("dial failure ignored")
	}
	rec, err := c.Resume(context.Background())
	if err != nil || rec.Snapshot.GatewayEpoch != 2 {
		t.Fatal("unsent resume consumed epoch", rec, err)
	}
	c.Close()
	wait()
}

func TestCompletedCacheNeverEvictsPending(t *testing.T) {
	o := &owner{c: &Client{cfg: Config{Now: time.Now}}, phase: phaseLive, epoch: 1, entries: map[uint64]*entry{1000: {command: walk(1000), n: 1000, epoch: 1}}}
	for n := uint64(1); n <= 65; n++ {
		o.entries[n] = &entry{command: walk(n), n: n, epoch: 1}
		if err := o.apply(event{records: []wire.Record{sent(n, 1)}, at: time.Now()}); err != nil {
			t.Fatal(err)
		}
	}
	if len(o.completed) != 64 || len(o.entries) != 65 || o.entries[1] != nil || o.entries[1000] == nil || o.entries[1000].result != nil {
		t.Fatal("cache evicted a pending request")
	}
}

func TestProcessSessionCapBeforeDial(t *testing.T) {
	if sessions.Load() != 0 {
		t.Fatal("session leaked from a previous client")
	}
	sessions.Add(64)
	defer sessions.Add(-64)
	called := false
	cfg := Config{Secret: [32]byte{1}, DialContext: func(context.Context, string, string) (net.Conn, error) {
		called = true
		return nil, errors.New("must not dial")
	}}
	_, _, err := Open(context.Background(), cfg, Identity{"guest", "Guest"})
	if typed, ok := errors.AsType[*Error](err); !ok || typed.Kind != Capacity || called || sessions.Load() != 64 {
		t.Fatal("session cap after allocation", err)
	}
}

func TestNewGenerationInvalidatesOlderCheckpoint(t *testing.T) {
	o := &owner{c: &Client{cfg: Config{Now: time.Now}}, phase: phaseLive, epoch: 1, self: 7, g: 4, ready: true, entries: map[uint64]*entry{}}
	apply := func(rs ...wire.Record) {
		t.Helper()
		if err := o.validateFrame(rs); err != nil {
			t.Fatal(err)
		}
		if err := o.apply(event{records: rs, at: time.Now()}); err != nil {
			t.Fatal(err)
		}
	}
	apply(cp(1, 1, 0))
	apply(rawFact(1, 1, 4, wire.S(64, true, 7), wire.U(66, true, 5), wire.U(67, true, 1)))
	apply(cp(2, 1, 1))
	if o.ready || o.g != 5 || o.epoch != 1 {
		t.Fatal("stale checkpoint admitted actions or changed transport epoch")
	}
	size := len(o.queue)
	apply(rawFact(2, 1, 4, wire.S(64, true, 7), wire.U(66, true, 5), wire.U(67, true, 1)))
	if len(o.queue) != size {
		t.Fatal("duplicate generation applied twice")
	}
	apply(rawFact(3, 1, 4, wire.S(64, true, 7), wire.U(66, true, 4), wire.U(67, true, 1)))
	if o.g != 5 {
		t.Fatal("generation went backwards")
	}
	apply(cp(1, 1, 0), cp(2, 1, 1))
	if !o.ready {
		t.Fatal("fresh checkpoint did not restore admission")
	}
}
