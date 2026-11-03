// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"context"
	"errors"
	"io/fs"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/server/goworld"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

// Identities of the two participants a World admits.
const (
	Visitor = "visitor"
	Agent   = "agent"
)

// World is the authored world and its gateway running in this process.
type World struct {
	world   *world.World
	gateway *gateway.Gateway
	cat     *catalog.Catalog
	config  backend.Config
	codec   *meaning.Codec
	chars   map[string]world.Character
	cancel  context.CancelFunc
	done    chan error

	mu    sync.Mutex
	ports map[string]opened

	closeOnce sync.Once
	closeErr  error
}

type opened struct {
	port    *worldbridge.Client
	session *backend.Session
}

// Start loads the rendered world from fsys and starts it with its gateway.
// The caller must Close the World.
func Start(fsys fs.FS) (*World, error) {
	cat, err := catalog.LoadFS(fsys)
	if err != nil {
		return nil, err
	}
	h := &World{cat: cat, chars: map[string]world.Character{Visitor: visitor(), Agent: agent()}, done: make(chan error, 1), ports: map[string]opened{}}
	var reserved []int32
	for _, c := range h.chars {
		reserved = append(reserved, c.ObjectID)
		for _, it := range c.Items {
			reserved = append(reserved, it.ObjectID)
		}
	}
	h.world = world.New(cat, memstore.New(), world.WithReservedIDs(reserved), world.WithInterest(goworld.Limits(2).Objects-1))
	life, cancel := context.WithCancel(context.Background())
	h.cancel = cancel
	go func() { h.done <- h.world.Run(life) }()
	origin := time.Now()
	mc := goworld.Limits(len(h.chars))
	h.config = goworld.BackendConfig(mc, origin)
	if h.codec, err = meaning.NewCodec(mc); err != nil {
		return nil, errors.Join(err, h.stopWorld())
	}
	policy := goworld.GatewayPolicy(mc)
	policy.Codec, policy.Factory, policy.Origin, policy.World = h.codec, h.open, origin, cat.ID()
	policy.Identities = map[string]gateway.Identity{Visitor: {Act: true}, Agent: {Act: true}}
	policy.Shutdown = 5 * time.Second
	if h.gateway, err = gateway.New(policy); err != nil {
		return nil, errors.Join(err, h.stopWorld())
	}
	return h, nil
}

// open is the gateway's factory: it opens the identity's character in the
// world through the same composition as the server. A reopened identity first
// departs its previous incarnation, as the server's host does.
func (h *World) open(ctx context.Context, id string) (gateway.World, gateway.State, error) {
	c, ok := h.chars[id]
	if !ok {
		return nil, nil, gateway.ErrAuth
	}
	h.mu.Lock()
	previous, exists := h.ports[id]
	delete(h.ports, id)
	h.mu.Unlock()
	if exists {
		if err := previous.session.Close(); err != nil {
			return nil, nil, err
		}
	}
	identity := bridge.Identity{Account: c.Account, Character: c.Name}
	p, s, w, state, err := goworld.Open(ctx, h.world, h.cat, identity, func(context.Context, bridge.Identity) (world.Character, error) {
		return c.Clone(), nil
	}, h.config, nil)
	if err != nil {
		if p != nil {
			err = errors.Join(err, p.Close())
		}
		return nil, nil, err
	}
	h.mu.Lock()
	h.ports[id] = opened{p, s}
	h.mu.Unlock()
	return w, state, nil
}

// Join connects a participant to its identity over an in-memory stream.
// watch receives every update, one at a time and in order, from the
// session's reader or from a Do; it must not block and must not call the
// Session. The caller must Close the Session.
func (h *World) Join(ctx context.Context, identity string, watch func(Update)) (*Session, error) {
	stream, err := h.gateway.Pipe(ctx, identity)
	if err != nil {
		return nil, err
	}
	kind := participantPerson
	if identity == Agent {
		kind = participantAgent
	}
	return newSession(h.codec, stream, kind, watch)
}

// Close stops the gateway, departs the participants and stops the world.
// Every call returns the result of the first.
func (h *World) Close() error {
	h.closeOnce.Do(func() {
		err := h.gateway.Close()
		h.mu.Lock()
		ports := h.ports
		h.ports = map[string]opened{}
		h.mu.Unlock()
		for _, o := range ports {
			err = errors.Join(err, o.session.Close())
		}
		h.closeErr = errors.Join(err, h.stopWorld())
	})
	return h.closeErr
}

func (h *World) stopWorld() error {
	h.cancel()
	return <-h.done
}

// visitor stands by the village tutor, the agent at the edge of the meadow.
func visitor() world.Character {
	return world.Character{ObjectID: 1, Account: Visitor, Name: "Visitor", ClassID: 0, BaseClass: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257803, Z: -3104,
		Items:  []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 12, TemplateID: 1060, Count: 3, Slot: -1}},
		Skills: []world.Skill{{ID: 3, Level: 1}}}
}

func agent() world.Character {
	return world.Character{ObjectID: 2, Account: Agent, Name: "Wanderer", ClassID: 0, BaseClass: 0, Level: 2, SP: 100, CurHP: 100, CurMP: 50, CurCP: 10, X: -70500, Y: 257400, Z: -3104,
		Items:  []world.Item{{ObjectID: 20, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 21, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 22, TemplateID: 1060, Count: 3, Slot: -1}},
		Skills: []world.Skill{{ID: 3, Level: 1}}}
}
