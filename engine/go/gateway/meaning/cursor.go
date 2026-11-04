// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"math"
	"slices"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// View is a detached authoritative capture. Events describe current state,
// never historical outcomes. Incarnations name the source's visible lifetimes.
type View struct {
	// Events contains the current typed facts in checkpoint order.
	Events []Input
	// Incarnations identifies each visible source incarnation.
	Incarnations map[int32]uint64
	// Ready certifies every required source domain.
	Ready bool
	// Domains names incomplete source domains.
	Domains []string
}

type reference struct {
	source uint64
	ref    Ref
}
type proposalKey struct {
	kind string
	list int32
}
type savedProposal struct {
	token   uint64
	context FactContext
}

// Cursor retains only protocol reference generations and saved proposal
// handles. The authoritative view stays with the source; captures and planning
// environments are temporary values, never an independently updated scene.
// The gateway session owns it; methods must be serialized.
type Cursor struct {
	config     Config
	next       uint64
	refs       map[int32]reference
	proposals  *Proposals
	saved      map[proposalKey]savedProposal
	savedOrder []proposalKey
}

// NewCursor creates the protocol metadata for one direct observer.
func NewCursor(c Config) (*Cursor, error) {
	if c.Objects < 1 || c.Attempts.Pending < 1 {
		return nil, ErrLimit
	}
	if _, err := NewCodec(c); err != nil {
		return nil, err
	}
	if _, err := proto.New(c.Attempts); err != nil {
		return nil, err
	}
	p, err := NewProposals(c)
	if err != nil {
		return nil, err
	}
	return &Cursor{config: c, refs: map[int32]reference{}, proposals: p, saved: map[proposalKey]savedProposal{}}, nil
}

func (c *Cursor) ref(id int32) Ref {
	if r, ok := c.refs[id]; ok {
		return r.ref
	}
	return Ref{ID: id}
}
func (c *Cursor) enter(id int32, source uint64) error {
	if id <= 0 || source == 0 {
		return wire.ErrFrame
	}
	if r, ok := c.refs[id]; ok && r.source == source {
		return nil
	}
	if len(c.refs) >= c.config.Objects && c.refs[id].source == 0 || c.next == math.MaxUint64 {
		return ErrLimit
	}
	c.next++
	c.refs[id] = reference{source: source, ref: Ref{ID: id, Gen: c.next}}
	return nil
}

// Reset retires spatial references and proposal authority without reusing IDs.
func (c *Cursor) Reset() { clear(c.refs); c.proposals.Invalidate(); clear(c.saved); c.savedOrder = nil }

// Record translates one typed source observation into a participant fact.
// source is the appearance's incarnation; zero is valid for other event kinds.
func (c *Cursor) Record(in Input, source uint64, now int64) (wire.Record, error) {
	e := in.Event
	if e.Kind == Appeared || e.Kind == Dropped {
		if err := c.enter(e.ID, source); err != nil {
			return wire.Record{}, err
		}
	}
	ctx := FactContext{Seq: 1, Epoch: 1, Ref: c.ref(e.ID), Target: c.ref(e.Target)}
	if e.Kind == Quest {
		ctx.Ref.Gen = 0
	}
	// Keep the dictionary's existing provenance on ordinary facts. Saved
	// proposals carry authored provenance through Proposals.Capture.

	if e.Kind == Spoke || e.Kind == Shop || e.Kind == Offer || e.Kind == Practice {
		key := proposalKey{e.Kind, e.List}
		saved, ok := c.saved[key]
		handles := c.proposals.Handles(now)
		live := saved.context.Dialog == 0 && len(saved.context.Offers) == 0
		if saved.context.Dialog != 0 {
			live = handles[saved.context.Dialog]
		}
		if len(saved.context.Offers) > 0 {
			live = handles[saved.context.Offers[0]]
		}
		if !ok || saved.token != in.Token || saved.context.Ref != ctx.Ref || !live {
			born := in.ReceivedAt
			if in.ProposalBorn != nil {
				born = *in.ProposalBorn
			}
			if born < 0 || born > now {
				return wire.Record{}, wire.ErrFrame
			}
			var err error
			if now-born >= c.config.ProposalMS {
				if e.Kind == Spoke {
					e.Lines = []Line{}
				} else {
					e.Goods = []Good{}
				}
			}
			ctx, err = c.proposals.capture(e, ctx.Ref, e.Complete || e.Kind == Spoke, born, now)
			if err != nil {
				return wire.Record{}, err
			}
			c.saveProposal(key, savedProposal{token: in.Token, context: ctx})
		} else {
			ctx = saved.context
		}
	}
	record, err := EventRecord(e, ctx)
	if err != nil {
		return wire.Record{}, err
	}
	if e.Kind == Gone {
		delete(c.refs, e.ID)
	}
	if e.Kind == Took {
		delete(c.refs, e.Target)
	}
	if e.Kind == Died && e.Mine {
		c.proposals.Invalidate()
		clear(c.saved)
		c.savedOrder = nil
	}
	for _, g := range e.Goods {
		if e.Kind == Inventory && (g.Change == 3 || g.Count == 0) {
			c.proposals.RemoveSaleObject(g.Object)
		}
	}
	return record, nil
}

// Capture translates a fresh world checkpoint. Reconciliation happens before
// records are built, so every reference uses the same authoritative horizon.
// No captured event or spatial value is retained by the cursor.
func (c *Cursor) Capture(view View, now int64) ([]wire.Record, error) {
	for id, r := range c.refs {
		if view.Incarnations[id] != r.source {
			delete(c.refs, id)
		}
	}
	for _, in := range view.Events {
		e := in.Event
		if e.Kind == Appeared || e.Kind == Dropped {
			if err := c.enter(e.ID, view.Incarnations[e.ID]); err != nil {
				return nil, err
			}
		}
	}
	out := make([]wire.Record, 0, len(view.Events))
	for _, in := range view.Events {
		r, err := c.Record(in, view.Incarnations[in.Event.ID], now)
		if err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, nil
}

// Environment builds an ephemeral planning view using only references already
// exposed by this cursor. A newly visible source object cannot acquire protocol
// authority until its appearance or checkpoint has been sent.
func (c *Cursor) Environment(view View, now int64, classify func(Observed) Capabilities) Environment {
	obs, _ := NewObservations(c.config.Objects)
	env := Environment{Observed: obs, Proposals: c.proposals, Known: map[int32]Skill{}, Inventory: map[int32]Good{}, Now: now, Classify: classify}
	for _, in := range view.Events {
		e := in.Event
		switch e.Kind {
		case Appeared, Dropped:
			r, ok := c.refs[e.ID]
			if !ok || r.source != view.Incarnations[e.ID] {
				continue
			}
			obs.objects[e.ID] = Observed{Ref: r.ref, Role: e.Role, X: e.X, Y: e.Y, Z: e.Z}
			if e.Mine {
				env.Self = e.ID
				env.Alive = true
			}
		case Died:
			if o, ok := obs.objects[e.ID]; ok {
				o.Dead = true
				obs.objects[e.ID] = o
			}
			if e.ID == env.Self {
				env.Dead = true
				env.Alive = false
			}
		case Known:
			for _, k := range e.Skills {
				env.Known[k.ID] = k
			}
		case Carried:
			for _, g := range e.Goods {
				env.Inventory[g.Object] = g
			}
		}
	}
	return env
}

// References returns the currently published lifetimes in ID order.
func (c *Cursor) References() []Ref {
	out := make([]Ref, 0, len(c.refs))
	for _, r := range c.refs {
		out = append(out, r.ref)
	}
	slices.SortFunc(out, func(a, b Ref) int { return cmp32(a.ID, b.ID) })
	return out
}

// Source returns the source incarnation behind a currently published reference.
func (c *Cursor) Source(ref Ref) (uint64, bool) {
	r, ok := c.refs[ref.ID]
	return r.source, ok && ref.Gen != 0 && r.ref == ref
}

func (c *Cursor) saveProposal(key proposalKey, value savedProposal) {
	for i, old := range c.savedOrder {
		if old == key {
			c.savedOrder = slices.Delete(c.savedOrder, i, i+1)
			break
		}
	}
	if len(c.savedOrder) >= c.config.Windows {
		delete(c.saved, c.savedOrder[0])
		c.savedOrder = slices.Delete(c.savedOrder, 0, 1)
	}
	c.saved[key] = value
	c.savedOrder = append(c.savedOrder, key)
}
