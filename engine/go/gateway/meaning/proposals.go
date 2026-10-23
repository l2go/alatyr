// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"math"
	"slices"
	"strings"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type proposalWindow struct {
	id       uint64
	kind     string
	list     int32
	npc      Ref
	created  int64
	complete bool
	lines    []Line
	goods    []Good
	offers   []uint64
}

// Proposals is the bounded saved-action store. Saved command text must never
// be included in diagnostics or participant payloads. Public labels and handles
// are encoded separately by EventRecord.
type Proposals struct {
	config  Config
	next    uint64
	windows []proposalWindow
}

// NewProposals constructs an actor-owned saved-action store with explicit positive bounds.
func NewProposals(c Config) (*Proposals, error) {
	if c.Windows < 1 || c.WindowBytes < 1 || c.ProposalMS <= 0 || c.Wire.ListItems < 1 {
		return nil, ErrLimit
	}
	return &Proposals{config: c}, nil
}

// Invalidate retires all proposals without reusing their handles.
func (p *Proposals) Invalidate() { clear(p.windows); p.windows = nil }

func (p *Proposals) expire(now int64) {
	p.windows = slices.DeleteFunc(p.windows, func(w proposalWindow) bool { return now < w.created || now-w.created >= p.config.ProposalMS })
}

// Capture copies a bounded proposal at its original backend receive time.
// sourceComplete must describe audited completeness, not participant-supplied data.
func (p *Proposals) Capture(ev Event, npc Ref, sourceComplete bool, now int64) (FactContext, error) {
	if ev.Kind != Spoke && ev.Kind != Shop && ev.Kind != Offer && ev.Kind != Practice {
		return FactContext{}, wire.ErrFrame
	}
	if now < 0 || len(ev.Lines) > p.config.Wire.ListItems || len(ev.Goods) > p.config.Wire.ListItems {
		return FactContext{}, ErrLimit
	}
	count := uint64(1)
	if ev.Kind != Spoke {
		count += uint64(len(ev.Goods))
	}
	if count > math.MaxUint64-p.next {
		return FactContext{}, ErrLimit
	}
	w := proposalWindow{id: p.next + 1, kind: ev.Kind, list: ev.List, npc: npc, created: now, complete: sourceComplete}
	ctx := FactContext{Seq: 1, Epoch: 1, Ref: npc, Origin: wire.ProvenanceAuthored}
	used := len(ev.Text)
	for _, s := range []string{ev.Text, ev.Name} {
		if len(s) > p.config.Wire.StringBytes {
			return FactContext{}, ErrLimit
		}
	}
	if ev.Kind == Spoke {
		ctx.Dialog = w.id
		if ev.Lines != nil {
			ctx.Options = make([]Option, 0, len(ev.Lines))
			w.lines = make([]Line, 0, len(ev.Lines))
		}
		for i, line := range ev.Lines {
			if line.Say == "" || len(line.Label) > p.config.Wire.StringBytes || len(line.Say) > p.config.Wire.StringBytes {
				return FactContext{}, ErrLimit
			}
			used += len(line.Label) + len(line.Say)
			w.lines = append(w.lines, Line{Label: strings.Clone(line.Label), Say: strings.Clone(line.Say)})
			ctx.Options = append(ctx.Options, Option{uint64(i + 1), line.Label})
		}
	} else {
		if ev.Goods != nil {
			w.goods = make([]Good, 0, len(ev.Goods))
			w.offers = make([]uint64, 0, len(ev.Goods))
			ctx.Offers = make([]uint64, 0, len(ev.Goods))
		}
		for i, g := range ev.Goods {
			if len(g.Name) > p.config.Wire.StringBytes {
				return FactContext{}, ErrLimit
			}
			used += len(g.Name)
			g.Name = strings.Clone(g.Name)
			w.goods = append(w.goods, g)
			id := w.id + uint64(i+1)
			w.offers = append(w.offers, id)
			ctx.Offers = append(ctx.Offers, id)
		}
	}
	// Account both serialized public data and the additional captured action text.
	record, err := EventRecord(ev, ctx)
	if err != nil {
		return FactContext{}, err
	}
	codec, err := NewCodec(p.config)
	if err != nil {
		return FactContext{}, err
	}
	encoded, err := codec.Encode([]wire.Record{record})
	if err != nil {
		return FactContext{}, err
	}
	if len(encoded) > p.config.WindowBytes || used > p.config.WindowBytes-len(encoded) {
		return FactContext{}, ErrLimit
	}
	p.expire(now)
	p.windows = slices.DeleteFunc(p.windows, func(old proposalWindow) bool {
		return old.kind == w.kind && (w.kind == Spoke || old.list == w.list)
	})
	if len(p.windows) >= p.config.Windows {
		p.windows = slices.Delete(p.windows, 0, 1)
	}
	p.next += count
	p.windows = append(p.windows, w)
	return ctx, nil
}
func (p *Proposals) choose(dialog, option uint64, now int64) (Command, Ref, wire.Reason) {
	p.expire(now)
	for _, w := range p.windows {
		if w.kind != Spoke || w.id != dialog {
			continue
		}
		if !w.complete || option == 0 || option > uint64(len(w.lines)) {
			break
		}
		return Command{Kind: Say, Text: w.lines[option-1].Say}, w.npc, wire.ReasonNone
	}
	return Command{}, Ref{}, wire.ReasonStaleOffer
}
func (p *Proposals) trade(kind string, list int32, offer uint64, count, price int64, now int64) (Command, wire.Reason) {
	p.expire(now)
	if count < 1 || price < 0 || price != 0 && count > math.MaxInt64/price {
		return Command{}, wire.ReasonInvalid
	}
	for _, w := range p.windows {
		if w.kind != kind || w.list != list || !w.complete {
			continue
		}
		for i, id := range w.offers {
			if id != offer {
				continue
			}
			g := w.goods[i]
			if g.Price != price || g.Count >= 0 && count > g.Count {
				return Command{}, wire.ReasonStaleOffer
			}
			command := Command{Kind: Buy, List: list, Item: g.ID, Count: count}
			if kind == Offer {
				command.Kind = Sell
				command.Target = g.Object
			}
			return command, wire.ReasonNone
		}
	}
	return Command{}, wire.ReasonStaleOffer
}
func (p *Proposals) learn(list int32, offer uint64, now int64) (Command, wire.Reason) {
	p.expire(now)
	for _, w := range p.windows {
		if w.kind != Practice || w.list != list || !w.complete {
			continue
		}
		for i, id := range w.offers {
			if id == offer {
				g := w.goods[i]
				if g.ID < 1 || g.Count < 1 || g.Count > math.MaxInt32 || g.Object < int32(g.Count) || g.Price < 0 {
					return Command{}, wire.ReasonStaleOffer
				}
				return Command{Kind: Learn, List: list, Item: g.ID, Count: g.Count}, wire.ReasonNone
			}
		}
	}
	return Command{}, wire.ReasonStaleOffer
}

// RemoveSaleObject invalidates any cached sale row for an inventory removal.
func (p *Proposals) RemoveSaleObject(id int32) {
	p.windows = slices.DeleteFunc(p.windows, func(w proposalWindow) bool {
		if w.kind != Offer {
			return false
		}
		for _, g := range w.goods {
			if g.Object == id {
				return true
			}
		}
		return false
	})
}

// Choose resolves only a currently saved proposal. It never parses input text.
func (p *Proposals) Choose(dialog, option uint64, now int64) (Command, Ref, wire.Reason) {
	return p.choose(dialog, option, now)
}

// Handles exposes only currently live public handles for projection pruning.
func (p *Proposals) Handles(now int64) map[uint64]bool {
	p.expire(now)
	out := map[uint64]bool{}
	for _, w := range p.windows {
		out[w.id] = true
		for _, id := range w.offers {
			out[id] = true
		}
	}
	return out
}
