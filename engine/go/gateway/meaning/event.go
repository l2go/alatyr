// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"io"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Option is a public handle and label; it contains no saved command text.
type Option struct {
	// ID is the numbered choice handle.
	ID uint64
	// Label is display text, not saved command text.
	Label string
}

// FactContext supplies trusted sequencing, references, proposals, and provenance.
type FactContext struct {
	// Seq is the nonzero fact sequence.
	Seq uint64
	// Epoch is the nonzero projection epoch.
	Epoch uint64
	// Ref is the subject's observed lifetime.
	Ref Ref
	// Target is the related object's observed lifetime.
	Target Ref
	// Dialog is the public saved-dialogue handle.
	Dialog uint64
	// Options contains public numbered labels only.
	Options []Option
	// Offers contains public saved-row handles in source order.
	Offers []uint64
	// Cause is an optional nonzero participant cause, set only by trusted composition.
	Cause *uint64
	// Origin is trusted text provenance, never supplied by an Intent.
	Origin wire.Provenance
}

// DecodedEvent is an owned fact value with public metadata, not a saved action.
type DecodedEvent struct {
	// Event is the independently owned DTO.
	Event Event
	// Context contains public fact metadata, not saved commands.
	Context FactContext
}

// EventRecord never serializes Line.Say. The gateway supplies numbered choices
// from its saved-action cache; a receiver needs a saved handle to act.
func EventRecord(ev Event, ctx FactContext) (wire.Record, error) {
	k, ok := KindByName(ev.Kind)
	if !ok {
		return wire.Record{}, wire.ErrUnsupported
	}
	if ctx.Seq == 0 || ctx.Epoch == 0 {
		return wire.Record{}, wire.ErrFrame
	}
	origin := ctx.Origin
	if origin == 0 {
		origin = wire.ProvenanceUnclassified
	}
	ctx.Ref.ID = ev.ID
	ctx.Target.ID = ev.Target
	f := []wire.Field{wire.U(1, true, k.Code), wire.U(2, true, ctx.Seq), wire.S(3, true, ev.MS), wire.U(4, true, ctx.Epoch), wire.Data(5, true, k.Domain), wire.U(6, true, 1), wire.Obj(10, true, refValue(ctx.Ref).Fields)}
	if ev.Role != "" {
		f = append(f, wire.Data(11, true, ev.Role))
	}
	if ev.Name != "" {
		f = append(f, textField(12, false, ev.Name, origin))
	}
	f = append(f, wire.At(13, true, ev.X, ev.Y, ev.Z), wire.At(14, true, ev.ToX, ev.ToY, ev.ToZ), wire.U(15, true, uint64(ev.Speed)), wire.Obj(16, true, refValue(ctx.Target).Fields), wire.S(17, true, int64(ev.Amount)), wire.U(18, true, boolean(ev.Hit)), wire.U(19, true, boolean(ev.Mine)))
	if ev.Text != "" {
		f = append(f, textField(20, false, ev.Text, origin))
	}
	if ev.Lines != nil {
		if len(ctx.Options) != len(ev.Lines) || ctx.Dialog == 0 {
			return wire.Record{}, wire.ErrFrame
		}
		items := make([]wire.Value, 0, len(ev.Lines))
		for i, line := range ev.Lines {
			option := ctx.Options[i]
			if option.ID == 0 || option.Label != line.Label {
				return wire.Record{}, wire.ErrFrame
			}
			items = append(items, wire.Value{Fields: []wire.Field{wire.U(1, true, option.ID), textField(2, true, line.Label, wire.ProvenanceAuthored)}})
		}
		f = append(f, wire.Items(21, true, items))
	}
	f = append(f, wire.S(22, true, int64(ev.List)))
	if ev.Coin != nil {
		f = append(f, wire.S(23, true, *ev.Coin))
	}
	if ev.Goods != nil {
		if (ev.Kind == Shop || ev.Kind == Offer || ev.Kind == Practice) && len(ctx.Offers) != len(ev.Goods) {
			return wire.Record{}, wire.ErrFrame
		}
		if ev.Kind == Practice {
			lessons := make([]wire.Value, 0, len(ev.Goods))
			extra := false
			for i, g := range ev.Goods {
				if ctx.Offers[i] == 0 {
					return wire.Record{}, wire.ErrFrame
				}
				lessons = append(lessons, wire.Value{Fields: []wire.Field{wire.U(1, true, ctx.Offers[i]), wire.S(2, true, int64(g.ID)), wire.S(3, true, g.Count), wire.S(4, true, int64(g.Object)), wire.S(5, true, g.Price)}})
				extra = extra || g.Name != "" || g.Change != 0
			}
			f = append(f, wire.Items(34, true, lessons))
			if extra {
				f = append(f, wire.Items(24, true, goodsValues(ev.Goods, nil, origin)))
			}
		} else {
			f = append(f, wire.Items(24, true, goodsValues(ev.Goods, ctx.Offers, origin)))
		}
	}
	if ev.Stats != nil {
		values := []*int64{ev.Stats.HP, ev.Stats.MaxHP, ev.Stats.MP, ev.Stats.MaxMP, ev.Stats.Level, ev.Stats.XP, ev.Stats.SP, ev.Stats.PAtk, ev.Stats.PDef}
		var fields []wire.Field
		for i, v := range values {
			if v != nil {
				fields = append(fields, wire.S(uint32(i+1), true, *v))
			}
		}
		f = append(f, wire.Obj(25, true, fields))
	}
	if ev.Category != "" {
		f = append(f, wire.Data(26, false, ev.Category))
	}
	f = append(f, wire.S(27, true, ev.CombatUntil))
	if ev.Entries != nil {
		items := make([]wire.Value, 0, len(ev.Entries))
		for _, e := range ev.Entries {
			items = append(items, wire.Value{Fields: []wire.Field{wire.S(1, true, e.MS), wire.Data(2, true, e.Category), textField(3, true, e.Text, origin)}})
		}
		f = append(f, wire.Items(28, false, items))
	}
	if ev.Quests != nil {
		items := make([]wire.Value, 0, len(ev.Quests))
		for _, q := range ev.Quests {
			items = append(items, wire.Value{Fields: []wire.Field{wire.S(1, true, int64(q.ID)), wire.S(2, true, int64(q.Step)), textField(3, true, q.Name, origin), wire.Data(4, true, q.State)}})
		}
		f = append(f, wire.Items(29, true, items))
	}
	if ev.Skills != nil {
		items := make([]wire.Value, 0, len(ev.Skills))
		for _, s := range ev.Skills {
			items = append(items, wire.Value{Fields: []wire.Field{wire.S(1, true, int64(s.ID)), wire.S(2, true, int64(s.Level)), wire.U(3, true, boolean(s.Passive)), wire.U(4, true, boolean(s.Disabled))}})
		}
		f = append(f, wire.Items(30, true, items))
	}
	f = append(f, wire.U(31, true, boolean(ev.Complete)))
	if ctx.Cause != nil {
		if *ctx.Cause == 0 {
			return wire.Record{}, wire.ErrFrame
		}
		f = append(f, wire.U(32, true, *ctx.Cause))
	}
	if ctx.Dialog != 0 {
		f = append(f, wire.U(33, true, ctx.Dialog))
	}
	return wire.Record{Type: wire.Fact, Fields: f}, nil
}
func goodsValues(goods []Good, offers []uint64, origin wire.Provenance) []wire.Value {
	out := make([]wire.Value, 0, len(goods))
	for i, g := range goods {
		fields := []wire.Field{wire.S(2, true, int64(g.Object)), wire.S(3, true, int64(g.ID)), wire.S(4, true, g.Count), wire.S(5, true, g.Price), wire.S(7, true, int64(g.Change))}
		if len(offers) > i && offers[i] != 0 {
			fields = append(fields, wire.U(1, true, offers[i]))
		}
		if g.Slot != nil {
			fields = append(fields, wire.S(8, true, *g.Slot))
		}
		if g.Name != "" {
			fields = append(fields, textField(6, false, g.Name, origin))
		}
		out = append(out, wire.Value{Fields: fields})
	}
	return out
}
func decodeGoods(items []wire.Value) []Good {
	out := make([]Good, 0, len(items))
	for _, v := range items {
		f := v.Fields
		var slot *int64
		if v, ok := wire.Find(f, 8); ok {
			slot = Number(v.S)
		}
		out = append(out, Good{Slot: slot, Object: int32(getS(f, 2)), ID: int32(getS(f, 3)), Count: getS(f, 4), Price: getS(f, 5), Name: stringText(f, 6), Change: int32(getS(f, 7))})
	}
	return out
}

// EventValue consumes a structurally validated dictionary record. Proposal
// commands are deliberately absent until a bounded proposal store resolves them.
func EventValue(r wire.Record) (DecodedEvent, error) {
	if r.Type != wire.Fact || r.Unsupported {
		return DecodedEvent{}, wire.ErrUnsupported
	}
	f := r.Fields
	k, ok := KindByCode(getU(f, 1))
	if !ok {
		return DecodedEvent{}, wire.ErrUnsupported
	}
	if getString(f, 5) != k.Domain || getU(f, 6) != 1 {
		return DecodedEvent{}, wire.ErrFrame
	}
	ctx := FactContext{Seq: getU(f, 2), Epoch: getU(f, 4), Ref: getRef(f, 10), Target: getRef(f, 16), Dialog: getU(f, 33)}
	e := Event{Kind: k.Name, MS: getS(f, 3), ID: ctx.Ref.ID, Target: ctx.Target.ID, Role: getString(f, 11), Name: stringText(f, 12), Speed: int32(getU(f, 15)), Amount: int32(getS(f, 17)), Hit: getU(f, 18) == 1, Mine: getU(f, 19) == 1, Text: stringText(f, 20), List: int32(getS(f, 22)), Category: getString(f, 26), CombatUntil: getS(f, 27), Complete: getU(f, 31) == 1}
	if v, yes := wire.Find(f, 13); yes {
		e.X, e.Y, e.Z = v.XYZ[0], v.XYZ[1], v.XYZ[2]
	}
	if v, yes := wire.Find(f, 14); yes {
		e.ToX, e.ToY, e.ToZ = v.XYZ[0], v.XYZ[1], v.XYZ[2]
	}
	if v, yes := wire.Find(f, 21); yes {
		e.Lines = make([]Line, 0, len(v.Items))
		ctx.Options = make([]Option, 0, len(v.Items))
		for _, item := range v.Items {
			label := stringText(item.Fields, 2)
			e.Lines = append(e.Lines, Line{Label: label})
			ctx.Options = append(ctx.Options, Option{getU(item.Fields, 1), label})
		}
	}
	if v, yes := wire.Find(f, 23); yes {
		e.Coin = Number(v.S)
	}
	if v, yes := wire.Find(f, 24); yes {
		e.Goods = decodeGoods(v.Items)
		ctx.Offers = make([]uint64, 0, len(v.Items))
		for _, item := range v.Items {
			ctx.Offers = append(ctx.Offers, getU(item.Fields, 1))
		}
	}
	if v, yes := wire.Find(f, 25); yes {
		e.Stats = &Stats{}
		dest := []**int64{&e.Stats.HP, &e.Stats.MaxHP, &e.Stats.MP, &e.Stats.MaxMP, &e.Stats.Level, &e.Stats.XP, &e.Stats.SP, &e.Stats.PAtk, &e.Stats.PDef}
		for i, p := range dest {
			if val, present := wire.Find(v.Fields, uint32(i+1)); present {
				*p = Number(val.S)
			}
		}
	}
	if v, yes := wire.Find(f, 28); yes {
		e.Entries = make([]Entry, 0, len(v.Items))
		for _, item := range v.Items {
			e.Entries = append(e.Entries, Entry{MS: getS(item.Fields, 1), Category: getString(item.Fields, 2), Text: stringText(item.Fields, 3)})
		}
	}
	if v, yes := wire.Find(f, 29); yes {
		e.Quests = make([]QuestState, 0, len(v.Items))
		for _, item := range v.Items {
			e.Quests = append(e.Quests, QuestState{ID: int32(getS(item.Fields, 1)), Step: int32(getS(item.Fields, 2)), Name: stringText(item.Fields, 3), State: getString(item.Fields, 4)})
		}
	}
	if v, yes := wire.Find(f, 30); yes {
		e.Skills = make([]Skill, 0, len(v.Items))
		for _, item := range v.Items {
			e.Skills = append(e.Skills, Skill{ID: int32(getS(item.Fields, 1)), Level: int32(getS(item.Fields, 2)), Passive: getU(item.Fields, 3) == 1, Disabled: getU(item.Fields, 4) == 1})
		}
	}
	if v, yes := wire.Find(f, 32); yes {
		ctx.Cause = &v.U
	}
	if v, yes := wire.Find(f, 34); yes {
		lessons := make([]Good, 0, len(v.Items))
		offers := make([]uint64, 0, len(v.Items))
		for _, item := range v.Items {
			lessons = append(lessons, Good{ID: int32(getS(item.Fields, 2)), Count: getS(item.Fields, 3), Object: int32(getS(item.Fields, 4)), Price: getS(item.Fields, 5)})
			offers = append(offers, getU(item.Fields, 1))
		}
		if e.Goods != nil {
			if len(e.Goods) != len(lessons) {
				return DecodedEvent{}, wire.ErrFrame
			}
			for i, g := range e.Goods {
				base := lessons[i]
				if g.ID != base.ID || g.Count != base.Count || g.Object != base.Object || g.Price != base.Price {
					return DecodedEvent{}, wire.ErrFrame
				}
				lessons[i].Name = g.Name
				lessons[i].Change = g.Change
			}
		}
		e.Goods = lessons
		ctx.Offers = offers
	}
	return DecodedEvent{e, ctx}, nil
}

// Codec adds dictionary-kind handling without making the neutral decoder depend
// on a world or on session values. It still returns no partially validated frame.
type Codec struct{ wire *wire.Codec }

// NewCodec constructs an immutable codec using explicit structural limits.
func NewCodec(config Config) (*Codec, error) {
	c, err := wire.New(config.Wire, Registry(config))
	if err != nil {
		return nil, err
	}
	return &Codec{c}, nil
}

// Decode validates an entire bounded frame and returns no partial records on error.
func (c *Codec) Decode(b []byte) ([]wire.Record, error) {
	r, err := c.wire.Decode(b)
	if err != nil {
		return nil, err
	}
	return c.validate(r, false)
}
func (c *Codec) validate(r []wire.Record, writing bool) ([]wire.Record, error) {
	out := make([]wire.Record, 0, len(r))
	for _, item := range r {
		if item.Type == wire.Fact {
			if k, ok := KindByCode(getU(item.Fields, 1)); ok {
				if k.Domain != getString(item.Fields, 5) || getU(item.Fields, 6) != 1 {
					return nil, wire.ErrFrame
				}
				if !item.Unsupported && k.Name == Practice && has(item.Fields, 24) && has(item.Fields, 34) {
					if _, err := EventValue(item); err != nil {
						return nil, err
					}
				}
			} else {
				if writing {
					return nil, wire.ErrUnsupported
				}
				if getU(item.Fields, 6) == 0 {
					continue
				}
				item.Unsupported = true
			}
		}
		out = append(out, item)
	}
	return out, nil
}

// Read reads one bounded stream frame and returns no partial records on error.
func (c *Codec) Read(r io.Reader) ([]wire.Record, error) {
	records, err := c.wire.Read(r)
	if err != nil {
		return nil, err
	}
	return c.validate(records, false)
}

// Encode validates records and their dictionary identity before encoding.
func (c *Codec) Encode(records []wire.Record) ([]byte, error) {
	checked, err := c.validate(records, true)
	if err != nil {
		return nil, err
	}
	return c.wire.Encode(checked)
}

// Write validates and writes a bounded stream frame, reporting write failures.
func (c *Codec) Write(w io.Writer, records []wire.Record) error {
	checked, err := c.validate(records, true)
	if err != nil {
		return err
	}
	return c.wire.Write(w, checked)
}
