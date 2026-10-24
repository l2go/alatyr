// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"context"
	"errors"
	"net"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/fixture"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type rig struct {
	t                          *testing.T
	c                          meaning.Config
	g                          *gateway.Gateway
	p                          *gatewaytest.Peer
	peers                      []*gatewaytest.Peer
	port                       bridge.Port
	f                          *fixture.World
	session                    *backend.Session
	origin                     time.Time
	seq, epoch                 uint64
	client                     bool
	mu                         sync.Mutex
	conn                       net.Conn
	workers                    sync.WaitGroup
	peerErrors                 chan error
	writes                     []fixture.Write
	handoffs                   []backend.Handoff
	source                     meaning.Source
	world                      gateway.World
	state                      gateway.State
	hold                       bool
	cached                     []bridge.Receipt
	blockDrain                 atomic.Bool
	drainEntered, drainRelease chan struct{}
	sendMu                     sync.Mutex
}

func initial() []meaning.Event {
	return []meaning.Event{
		{Kind: meaning.Appeared, ID: 1, Mine: true, Role: meaning.RolePlayer, Name: "Player"},
		{Kind: meaning.Appeared, ID: 2, Role: meaning.RoleUnknown, Name: "Speaker"},
		{Kind: meaning.Dropped, ID: 9, Role: meaning.RoleDrop, Name: "Item"},
		{Kind: meaning.Vitals, ID: 1, Stats: &meaning.Stats{HP: meaning.Number(100), MaxHP: meaning.Number(100), Level: meaning.Number(1)}},
		{Kind: meaning.Carried, Coin: meaning.Number(100), Goods: []meaning.Good{{Object: 40, ID: 30, Count: 10}}},
		{Kind: meaning.Quests, Complete: true, Quests: []meaning.QuestState{}},
		{Kind: meaning.Known, Complete: true, Skills: []meaning.Skill{{ID: 3, Level: 1}}},
	}
}

func rawFact(t testing.TB, e meaning.Event, seq, epoch uint64, extra ...wire.Field) wire.Record {
	t.Helper()
	ctx := meaning.FactContext{Seq: seq, Epoch: epoch, Ref: meaning.Ref{ID: e.ID, Gen: 1}, Target: meaning.Ref{ID: e.Target, Gen: 1}, Origin: wire.ProvenanceAuthored}
	if e.Lines != nil {
		ctx.Dialog = 1
		for i, l := range e.Lines {
			ctx.Options = append(ctx.Options, meaning.Option{ID: uint64(i + 1), Label: l.Label})
		}
	}
	if e.Goods != nil {
		for i := range e.Goods {
			ctx.Offers = append(ctx.Offers, uint64(i+1))
		}
	}
	r, err := meaning.EventRecord(e, ctx)
	if err != nil {
		t.Fatal(err)
	}
	r.Fields = slices.DeleteFunc(r.Fields, func(f wire.Field) bool {
		return f.Number == 10 || f.Number == 16 || f.Number == 21 || f.Number == 32 || f.Number == 33
	})
	r.Fields = append(r.Fields, wire.S(64, true, int64(e.ID)), wire.S(65, true, int64(e.Target)))
	if e.Lines != nil {
		var opts []wire.Value
		for i, l := range e.Lines {
			opts = append(opts, wire.Value{Fields: []wire.Field{wire.U(1, true, uint64(i+1)), wire.Obj(2, true, []wire.Field{wire.Data(1, true, l.Label), wire.U(2, true, uint64(wire.ProvenanceAuthored))}), wire.Data(3, true, l.Say)}})
		}
		r.Fields = append(r.Fields, wire.Items(68, true, opts))
	}
	if e.Kind == meaning.Spoke || e.Kind == meaning.Shop || e.Kind == meaning.Offer || e.Kind == meaning.Practice {
		r.Fields = append(r.Fields, wire.U(69, true, uint64(e.MS)))
	}
	r.Fields = append(r.Fields, extra...)
	return r
}

func cp(epoch uint64, phase wire.CheckpointPhase, ready bool) wire.Record {
	v := uint64(0)
	if ready {
		v = 1
	}
	return wire.Record{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(phase)), wire.U(2, true, epoch), wire.U(3, true, v)}}
}

func (r *rig) snapshot() bridge.Snapshot {
	s := bridge.Snapshot{GatewayEpoch: r.epoch, Ready: true}
	for _, e := range initial() {
		r.seq++
		s.Facts = append(s.Facts, rawFact(r.t, e, r.seq, r.epoch))
	}
	s.FenceSeq = r.seq
	return s
}

func newRig(t *testing.T, client bool, source meaning.Source) *rig {
	t.Helper()
	r := &rig{t: t, c: gatewaytest.Config(), origin: time.Now(), epoch: 1, client: client, source: source, peerErrors: make(chan error, 8)}
	r.drainEntered = make(chan struct{}, 1)
	r.drainRelease = make(chan struct{}, 1)
	f, err := fixture.New(fixture.Config{})
	if err != nil {
		t.Fatal(err)
	}
	r.f = f
	snapshot := r.snapshot()
	r.port = f
	if client {
		cfg := bridge.Config{Secret: [32]byte{1}, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			a, b := net.Pipe()
			r.mu.Lock()
			r.conn = b
			r.mu.Unlock()
			r.workers.Add(1)
			go r.serve(b)
			return a, nil
		}}
		c, s, err := bridge.Open(t.Context(), cfg, bridge.Identity{Account: "guest", Character: "Player"})
		if err != nil {
			t.Fatal(err)
		}
		r.port = c
		snapshot = s
	}
	policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
		return meaning.Capabilities{Talk: o.Ref.ID == 2, Pickup: o.Role == meaning.RoleDrop}
	}}
	session, w, st, err := backend.New(r.port, snapshot, backend.Config{Meaning: r.c, Policy: policy, Origin: r.origin, Source: func(e meaning.Event) meaning.Source { return r.source }, Handoff: func(h backend.Handoff) { r.mu.Lock(); r.handoffs = append(r.handoffs, h); r.mu.Unlock() }})
	if err != nil {
		t.Fatal(err)
	}
	r.session = session
	r.world = w
	r.state = st
	t.Cleanup(func() {
		if err := r.session.Close(); err != nil {
			t.Error(err)
		}
		r.mu.Lock()
		if r.conn != nil {
			if err := r.conn.Close(); err != nil {
				t.Error(err)
			}
		}
		r.mu.Unlock()
		r.workers.Wait()
		select {
		case err := <-r.peerErrors:
			t.Error(err)
		default:
		}
	})
	r.g = gatewaytest.New(t, r.c, func(ctx context.Context, id string) (gateway.World, gateway.State, error) {
		return paced{World: r.world, rig: r}, r.state, nil
	}, r.origin)
	r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true, func(tr gateway.Transport) gateway.Transport {
		return &drainTransport{Transport: tr, rig: r, done: make(chan struct{})}
	})
	r.peers = append(r.peers, r.p)
	r.sync()
	return r
}

func (r *rig) sync() {
	r.t.Helper()
	synctest.Wait()
	r.p.Drain()
	synctest.Wait()
	r.p.Drain()
	synctest.Wait()
}
func (r *rig) advance(d time.Duration) { time.Sleep(d); r.sync() }

func (r *rig) serve(conn net.Conn) {
	defer r.workers.Done()
	defer conn.Close()
	codec, err := profile.NewProfileCodec()
	if err != nil {
		r.peerErrors <- err
		return
	}
	rs, err := codec.Read(conn)
	if err != nil {
		return
	}
	if len(rs) != 1 || (rs[0].Type != profile.Open && rs[0].Type != profile.Resume) {
		r.peerErrors <- errors.New("unexpected raw handshake")
		return
	}
	epoch := r.epoch
	// Snapshot facts use the shared capture, without producing live outcomes.
	startSeq := r.seq - uint64(len(initial()))
	facts := []wire.Record{cp(epoch, wire.CheckpointBegin, false)}
	for i, e := range initial() {
		facts = append(facts, rawFact(r.t, e, startSeq+uint64(i+1), epoch))
	}
	facts = append(facts, cp(epoch, wire.CheckpointEnd, true))
	if err = r.rawWrite(conn, facts); err != nil {
		return
	}
	for _, v := range r.cached {
		if err = r.rawWrite(conn, []wire.Record{result(v)}); err != nil {
			return
		}
	}
	var limits []wire.Field
	for n, v := range profile.AdvertisedLimits() {
		limits = append(limits, wire.U(n, true, v))
	}
	opened := wire.Record{Type: profile.Opened, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 7), wire.Data(3, true, "test-world"), wire.U(4, true, epoch), wire.U(5, true, 0), wire.Obj(6, true, limits), wire.U(7, true, 1), wire.Data(8, true, profile.Name)}}
	if err = r.rawWrite(conn, []wire.Record{opened}); err != nil {
		return
	}
	for {
		rs, err = codec.Read(conn)
		if err != nil {
			return
		}
		for _, rec := range rs {
			switch rec.Type {
			case wire.Intent:
				cmd := bridge.Command{Key: profile.U(rec, 1), Verb: profile.U(rec, 2), Payload: slices.DeleteFunc(slices.Clone(rec.Fields), func(f wire.Field) bool { return f.Number <= 4 })}
				r.mu.Lock()
				r.writes = append(r.writes, fixture.Write{Command: cmd, N: profile.U(rec, 1)})
				r.mu.Unlock()
			case profile.Close:
				if err = r.rawWrite(conn, []wire.Record{{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0)}}}); err != nil {
					r.peerErrors <- err
				}
				return
			case wire.Ping:
				if err = r.rawWrite(conn, []wire.Record{{Type: wire.Pong, Fields: rec.Fields}}); err != nil {
					return
				}
			default:
				r.peerErrors <- errors.New("unexpected raw record")
				return
			}
		}
	}
}

func (r *rig) log() []fixture.Write {
	r.sync()
	if !r.client {
		return r.f.Writes()
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	return slices.Clone(r.writes)
}

func (r *rig) send(batch bridge.Batch) {
	r.t.Helper()
	if !r.client {
		if err := r.f.Emit(r.t.Context(), bridge.Update{Kind: bridge.LiveBatch, Batch: batch}); err != nil {
			r.t.Fatal(err)
		}
	} else {
		rs := slices.Clone(batch.Records)
		for _, v := range batch.Receipts {
			rs = append(rs, result(v))
		}
		r.mu.Lock()
		conn := r.conn
		r.mu.Unlock()
		if err := r.rawWrite(conn, rs); err != nil {
			r.t.Fatal(err)
		}
	}
	r.sync()
}

func result(v bridge.Receipt) wire.Record {
	fs := []wire.Field{wire.U(1, true, v.N), wire.U(2, true, uint64(v.Stage)), wire.U(3, true, uint64(v.Reason)), wire.U(5, true, v.Requested), wire.U(6, true, v.Effective), wire.U(7, true, uint64(v.Delivery))}
	if v.BridgeReason != nil {
		fs = append(fs, wire.Data(64, true, profile.Namespace), wire.U(65, true, *v.BridgeReason))
	}
	if v.EffectsPresent {
		fs = append(fs, wire.Items(66, true, v.Effects))
	}
	return wire.Record{Type: wire.Result, Fields: fs}
}

func (r *rig) fact(e meaning.Event, extra ...wire.Field) wire.Record {
	r.seq++
	return rawFact(r.t, e, r.seq, r.epoch, extra...)
}
func (r *rig) emit(e meaning.Event, extra ...wire.Field) {
	r.send(bridge.Batch{Records: []wire.Record{r.fact(e, extra...)}, ReceivedAt: []time.Time{time.Now()}})
}

func (r *rig) receipt(x fixture.Write, stage wire.Stage, reason wire.Reason, effects ...wire.Value) bridge.Receipt {
	delivery := wire.DeliveryBackend
	if stage == wire.StageRefused {
		delivery = wire.DeliveryNone
	}
	return bridge.Receipt{N: x.N, Key: x.Command.Key, GatewayEpoch: r.epoch, Requested: x.Command.Verb, Effective: x.Command.Verb, Stage: stage, Reason: reason, Delivery: delivery, ObservedAt: time.Now(), EffectsPresent: stage == wire.StageDone, Effects: effects}
}
func (r *rig) reply(x fixture.Write, stage wire.Stage, reason wire.Reason, e meaning.Event, effects ...wire.Value) {
	b := bridge.Batch{Receipts: []bridge.Receipt{r.receipt(x, stage, reason, effects...)}}
	if e.Kind != "" {
		b.Records = []wire.Record{r.fact(e)}
		b.ReceivedAt = []time.Time{time.Now()}
	}
	r.send(b)
}

func (r *rig) intent(n, verb uint64, payload ...wire.Field) wire.Record {
	return wire.Record{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, r.p.Control)}, payload...)}
}
func (r *rig) ref(id int32) wire.Field {
	r.t.Helper()
	for i := len(r.p.Records) - 1; i >= 0; i-- {
		rec := r.p.Records[i]
		if rec.Type == wire.Fact {
			f, ok := wire.Find(rec.Fields, 10)
			if ok {
				v, _ := wire.Find(f.Fields, 1)
				if v.S == int64(id) {
					return wire.Obj(10, true, f.Fields)
				}
			}
		}
	}
	r.t.Fatalf("missing ref %d", id)
	return wire.Field{}
}
func targetEffect(id int32) wire.Value {
	return wire.Value{Fields: []wire.Field{wire.U(1, true, 5), wire.S(8, true, int64(id))}}
}
func itemEffect() wire.Value {
	return wire.Value{Fields: []wire.Field{wire.U(1, true, 1), wire.S(2, true, 30), wire.S(3, true, 40), wire.S(4, true, 1)}}
}
func skillEffect() wire.Value {
	return wire.Value{Fields: []wire.Field{wire.U(1, true, 3), wire.S(5, true, 3), wire.S(6, true, 1), wire.S(7, true, 2)}}
}

type paced struct {
	gateway.World
	rig *rig
}

func (w paced) Ready(step gateway.Step, now int64) bool { return !w.rig.hold }
func (w paced) Detach() error {
	v, ok := w.World.(gateway.Detacher)
	if !ok {
		return wire.ErrConfig
	}
	return v.Detach()
}

func (r *rig) rawWrite(conn net.Conn, rs []wire.Record) error {
	r.sendMu.Lock()
	defer r.sendMu.Unlock()
	codec, err := profile.NewProfileCodec()
	if err != nil {
		return err
	}
	return codec.Write(conn, rs)
}

type drainTransport struct {
	gateway.Transport
	rig  *rig
	done chan struct{}
	once sync.Once
}

func (d *drainTransport) Write(b []byte, deadline time.Time) error {
	codec, err := meaning.NewCodec(d.rig.c)
	if err != nil {
		return err
	}
	rs, err := codec.Decode(b)
	if err != nil {
		return err
	}
	if d.rig.blockDrain.Load() && len(rs) == 1 && rs[0].Type == wire.Checkpoint && gatewaytest.U(rs[0], 1) == uint64(wire.CheckpointEnd) && gatewaytest.U(rs[0], 2) > 1 && gatewaytest.U(rs[0], 3) == 1 {
		d.rig.drainEntered <- struct{}{}
		timer := time.NewTimer(time.Until(deadline))
		defer timer.Stop()
		select {
		case <-d.rig.drainRelease:
		case <-d.done:
			return errors.New("drain closed")
		case <-timer.C:
			return context.DeadlineExceeded
		}
	}
	return d.Transport.Write(b, deadline)
}
func (d *drainTransport) Close() error {
	d.once.Do(func() { close(d.done) })
	return d.Transport.Close()
}
