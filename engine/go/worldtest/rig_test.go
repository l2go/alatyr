// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"context"
	"math/rand/v2"
	"path/filepath"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

type rig struct {
	t        *testing.T
	c        meaning.Config
	w        *world.World
	store    *memstore.Store
	port     *observedPort
	session  *backend.Session
	g        *gateway.Gateway
	p        *gatewaytest.Peer
	mu       sync.Mutex
	scope    *backend.World
	state    gateway.State
	handoffs []backend.Handoff
	hold     atomic.Bool
}

type observedPort struct {
	bridge.Port
	updates    chan bridge.Update
	stop, done chan struct{}
	once       sync.Once
	mu         sync.Mutex
	writes     []bridge.Command
	// filter is immutable and simulates observation loss, never a world mutation.
	filter func(bridge.Update) bridge.Update
}

func wrapPort(p bridge.Port, filter func(bridge.Update) bridge.Update) *observedPort {
	o := &observedPort{Port: p, updates: make(chan bridge.Update), stop: make(chan struct{}), done: make(chan struct{}), filter: filter}
	go func() {
		defer close(o.done)
		defer close(o.updates)
		for {
			select {
			case <-o.stop:
				return
			case u, ok := <-p.Updates():
				if !ok {
					return
				}
				if o.filter != nil {
					u = o.filter(u)
				}
				select {
				case o.updates <- u:
				case <-o.stop:
					return
				}
			}
		}
	}()
	return o
}
func (o *observedPort) Updates() <-chan bridge.Update { return o.updates }
func (o *observedPort) Write(ctx context.Context, c bridge.Command) (uint64, error) {
	o.mu.Lock()
	o.writes = append(o.writes, c)
	o.mu.Unlock()
	return o.Port.Write(ctx, c)
}
func (o *observedPort) Close() error {
	o.once.Do(func() { close(o.stop) })
	err := o.Port.Close()
	<-o.done
	return err
}
func (o *observedPort) commands() []bridge.Command {
	o.mu.Lock()
	defer o.mu.Unlock()
	return slices.Clone(o.writes)
}

type paced struct {
	*backend.World
	hold *atomic.Bool
}

func (p paced) Ready(gateway.Step, int64) bool { return !p.hold.Load() }

func readyCharacter() world.Character {
	return world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104,
		Items: []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 12, TemplateID: 1060, Count: 3, Slot: -1}}, Skills: []world.Skill{{ID: 3, Level: 1}}}
}
func newRig(t *testing.T, cat *catalog.Catalog, char world.Character, source meaning.Source, filter func(bridge.Update) bridge.Update, opts ...world.Option) *rig {
	policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
		return meaning.Capabilities{Talk: o.Role == "tutor" || o.Role == "provisioner", Pickup: o.Role == meaning.RoleDrop}
	}}
	return newRigWithPolicy(t, cat, char, source, filter, policy, opts...)
}

func newRigWithPolicy(t *testing.T, cat *catalog.Catalog, char world.Character, source meaning.Source, filter func(bridge.Update) bridge.Update, policy meaning.Policy, opts ...world.Option) *rig {
	t.Helper()
	r := &rig{t: t, c: gatewaytest.Config(), store: memstore.New()}
	if err := r.store.PutCharacter(t.Context(), char); err != nil {
		t.Fatal(err)
	}
	ids := []int32{char.ObjectID}
	for _, it := range char.Items {
		ids = append(ids, it.ObjectID)
	}
	opts = append([]world.Option{world.WithReservedIDs(ids), world.WithRandomSource(rand.NewPCG(1, 2))}, opts...)
	r.w = world.New(cat, r.store, opts...)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- r.w.Run(ctx) }()
	t.Cleanup(func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	})
	origin := time.Now()
	raw, snap, err := worldbridge.Open(t.Context(), worldbridge.Config{World: r.w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) { return char.Clone(), nil }}, bridge.Identity{Account: char.Account, Character: char.Name})
	if err != nil {
		t.Fatal(err)
	}
	r.port = wrapPort(raw, filter)
	t.Cleanup(func() {
		if err := r.port.Close(); err != nil {
			t.Error(err)
		}
	})
	r.session, r.scope, r.state, err = backend.New(r.port, snap, backend.Config{Meaning: r.c, Policy: policy, Origin: origin, Source: func(meaning.Event) meaning.Source { return source }, Handoff: func(h backend.Handoff) { r.mu.Lock(); r.handoffs = append(r.handoffs, h); r.mu.Unlock() }})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := r.session.Close(); err != nil {
			t.Error(err)
		}
	})
	r.g = gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) {
		r.mu.Lock()
		defer r.mu.Unlock()
		return paced{World: r.scope, hold: &r.hold}, r.state, nil
	}, origin)
	r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
	r.sync()
	return r
}
func runWorld(t *testing.T, f func(*testing.T, *catalog.Catalog)) {
	t.Helper()
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	synctest.Test(t, func(t *testing.T) { f(t, cat) })
}
func allSources() meaning.Source {
	return meaning.Source{Dialogue: true, Pickup: true, Learning: true, Return: true}
}
func (r *rig) sync() {
	r.t.Helper()
	synctest.Wait()
	r.p.Drain()
	synctest.Wait()
	r.p.Drain()
	synctest.Wait()
}
func (r *rig) advance(d time.Duration) { time.Sleep(d); r.sync() } // Virtual time inside synctest only.
func (r *rig) intent(n, verb uint64, fields ...wire.Field) wire.Record {
	return wire.Record{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, r.p.Control)}, fields...)}
}
func (r *rig) ref(id int32, field uint32) wire.Field {
	r.t.Helper()
	for i := len(r.p.Records) - 1; i >= 0; i-- {
		rec := r.p.Records[i]
		if rec.Type != wire.Fact {
			continue
		}
		f, ok := wire.Find(rec.Fields, 10)
		if !ok {
			continue
		}
		n, _ := wire.Find(f.Fields, 1)
		if n.S == int64(id) {
			return wire.Obj(field, true, f.Fields)
		}
	}
	r.t.Fatalf("missing observed id %d", id)
	return wire.Field{}
}
func (r *rig) actor(template int32) world.Ref {
	r.t.Helper()
	raw := r.port.Port.(*worldbridge.Client)
	scene, err := r.w.Capture(r.t.Context(), raw.Owner())
	if err != nil {
		r.t.Fatal(err)
	}
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == template {
			return o.Entity
		}
	}
	r.t.Fatalf("authored actor template %d not visible", template)
	return world.Ref{}
}
func (r *rig) expect(n uint64, stage wire.Stage, reason wire.Reason, delivery wire.Delivery, causes int) {
	r.t.Helper()
	res := r.p.Result(n, stage)
	r.sync()
	if gatewaytest.U(res, 3) != uint64(reason) || gatewaytest.U(res, 7) != uint64(delivery) {
		r.t.Fatalf("result %d: got %+v, want %v/%v/%v", n, res, stage, reason, delivery)
	}
	terminal, causal := 0, 0
	for _, rec := range r.p.Records {
		if rec.Type == wire.Result && gatewaytest.U(rec, 1) == n && (gatewaytest.U(rec, 2) != uint64(wire.StageSent) || stage == wire.StageSent) {
			terminal++
		}
		if rec.Type == wire.Fact && gatewaytest.U(rec, 32) == n {
			causal++
		}
	}
	if terminal != 1 || causal != causes {
		r.t.Fatalf("attempt %d: terminal/causal got %d/%d, want 1/%d", n, terminal, causal, causes)
	}
}
func (r *rig) choose(n uint64, index int) {
	r.t.Helper()
	spoke := r.p.Fact(11)
	d, err := meaning.EventValue(spoke)
	if err != nil {
		r.t.Fatal(err)
	}
	if index >= len(d.Context.Options) {
		r.t.Fatalf("choice %d absent", index)
	}
	r.p.Send(r.intent(n, 6, wire.U(12, true, d.Context.Dialog), wire.U(13, true, d.Context.Options[index].ID)))
}
