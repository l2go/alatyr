// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"context"
	"errors"
	"math/rand/v2"
	"path/filepath"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

type rig struct {
	handoffs []worldlink.Handoff
	t        *testing.T
	c        meaning.Config
	w        *world.World
	store    *memstore.Store
	port     *observedPort
	session  *worldlink.Session
	g        *gateway.Gateway
	p        *gatewaytest.Peer
	mu       sync.Mutex
	scope    gateway.World
	state    gateway.State
	hold     atomic.Bool
	stop     func()
}
type command struct {
	Verb, Key uint64
	Payload   []wire.Field
	Value     meaning.Command
}
type observedPort struct {
	gateway.World
	Port          interface{ Owner() world.Ref }
	source        gateway.Receiver
	mu            sync.Mutex
	writes        []command
	filter        func(gateway.Update) gateway.Update
	ignoreStopped atomic.Bool
}

func (o *observedPort) Notify() <-chan struct{} { return o.source.Notify() }
func (o *observedPort) Capacity() int           { return o.source.Capacity() }
func (o *observedPort) Receive() (gateway.Update, bool) {
	u, ok := o.source.Receive()
	if ok && o.filter != nil {
		u = o.filter(u)
	}
	return u, ok
}
func (o *observedPort) Write(ctx context.Context, step gateway.Step) error {
	c, err := worldlink.CommandValue(step)
	if err != nil {
		return err
	}
	verb, _ := meaning.VerbByCommand(c.Kind)
	payload := []wire.Field{}
	if c.Kind == meaning.Walk {
		payload = append(payload, wire.At(11, true, c.X, c.Y, c.Z))
	}
	o.mu.Lock()
	o.writes = append(o.writes, command{Verb: verb.Code, Key: step.N, Value: c, Payload: payload})
	o.mu.Unlock()
	return o.World.Write(ctx, step)
}
func (o *observedPort) Close() error {
	err := o.World.Close()
	if o.ignoreStopped.Load() && errors.Is(err, world.ErrStopped) {
		return nil
	}
	return err
}
func (o *observedPort) commands() []command {
	o.mu.Lock()
	defer o.mu.Unlock()
	return slices.Clone(o.writes)
}

type paced struct {
	*observedPort
	hold *atomic.Bool
}

func (p paced) Ready(gateway.Step, int64) bool { return !p.hold.Load() }

func readyCharacter() world.Character {
	return world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104,
		Items: []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 12, TemplateID: 1060, Count: 3, Slot: -1}}, Skills: []world.Skill{{ID: 3, Level: 1}}}
}
func newRig(t *testing.T, cat *catalog.Catalog, char world.Character, source meaning.Source, filter func(gateway.Update) gateway.Update, opts ...world.Option) *rig {
	policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
		return meaning.Capabilities{Talk: o.Role == "tutor" || o.Role == "provisioner", Pickup: o.Role == meaning.RoleDrop}
	}}
	return newRigWithPolicy(t, cat, char, source, filter, policy, opts...)
}
func newRigWithPolicy(t *testing.T, cat *catalog.Catalog, char world.Character, source meaning.Source, filter func(gateway.Update) gateway.Update, policy meaning.Policy, opts ...world.Option) *rig {
	return newRigWithPort(t, cat, char, source, filter, policy, nil, opts...)
}
func newRigWithPort(t *testing.T, cat *catalog.Catalog, char world.Character, source meaning.Source, filter func(gateway.Update) gateway.Update, policy meaning.Policy, decorate func(gateway.World) gateway.World, opts ...world.Option) *rig {
	t.Helper()
	r := &rig{t: t, c: gatewaytest.Config(), store: memstore.New()}
	if err := r.store.PutCharacter(t.Context(), char); err != nil {
		t.Fatal(err)
	}
	ids := []int32{char.ObjectID}
	for _, it := range char.Items {
		ids = append(ids, it.ObjectID)
	}
	opts = append([]world.Option{world.WithReservedIDs(ids), world.WithRandomSource(rand.NewPCG(1, 2)), world.WithInterest(r.c.Objects - 1)}, opts...)
	r.w = world.New(cat, r.store, opts...)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- r.w.Run(ctx) }()
	r.stop = sync.OnceFunc(func() {
		if r.port != nil {
			r.port.ignoreStopped.Store(true)
		}
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	})
	t.Cleanup(r.stop)
	origin := time.Now()
	var err error
	r.session, r.state, err = worldlink.Open(t.Context(), worldlink.Config{World: r.w, Catalog: cat, Meaning: r.c, Policy: policy, Origin: origin, Handoff: func(h worldlink.Handoff) { r.mu.Lock(); r.handoffs = append(r.handoffs, h); r.mu.Unlock() }, Load: func(context.Context, worldlink.Identity) (world.Character, error) { return char.Clone(), nil }}, worldlink.Identity{Account: char.Account, Character: char.Name})
	if err != nil {
		t.Fatal(err)
	}
	r.port = &observedPort{World: r.session, Port: r.session, source: r.session, filter: filter}
	r.scope = paced{observedPort: r.port, hold: &r.hold}
	if decorate != nil {
		r.scope = decorate(r.scope)
	}
	t.Cleanup(func() {
		if err := r.port.Close(); err != nil {
			t.Error(err)
		}
	})
	r.g = gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) {
		r.mu.Lock()
		defer r.mu.Unlock()
		return r.scope, r.state, nil
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
	return meaning.Source{Dialogue: true, Pickup: true, Learning: true, Return: true, ItemUse: true, Trade: true}
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
	raw := r.port.Port
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

func (r *rig) reconnect() {
	r.t.Helper()
	r.p.Close()
	r.sync()
	r.p = gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeAct, true)
	r.sync()
}

func participantCommands(commands []command) int {
	n := 0
	for _, c := range commands {
		if c.Key != 0 {
			n++
		}
	}
	return n
}
