// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func command(op meaning.Operation, key uint64, ttl *uint64) (bridge.Command, error) {
	v, ok := meaning.VerbByCommand(op.Command.Kind)
	if !ok {
		return bridge.Command{}, wire.ErrUnsupported
	}
	c := bridge.Command{Key: key, Verb: v.Code, QueueTTLMS: ttl}
	m := op.Command
	if op.Selection {
		c.Verb = 2
	}
	switch c.Verb {
	case 1:
		c.Payload = []wire.Field{wire.At(11, true, m.X, m.Y, m.Z)}
	case 2, 3, 4, 5, 10:
		c.Payload = []wire.Field{wire.S(64, true, int64(m.Target))}
	case 6:
		c.Payload = []wire.Field{wire.S(64, true, int64(m.Target)), wire.Data(68, true, m.Text)}
	case 7, 8:
		c.Payload = []wire.Field{wire.S(14, true, int64(m.List)), wire.S(16, true, m.Count), wire.S(66, true, int64(m.Item))}
		if c.Verb == 8 {
			c.Payload = append(c.Payload, wire.S(64, true, int64(m.Target)))
		}
	case 9:
	case 12, 13:
		c.Payload = []wire.Field{wire.Obj(18, true, []wire.Field{wire.S(1, true, int64(m.Item)), wire.S(2, true, m.Count)})}
		if c.Verb == 12 {
			c.Payload = append(c.Payload, wire.S(64, true, int64(m.Target)), wire.U(19, true, boolValue(m.Force)))
		} else {
			c.Payload = append(c.Payload, wire.S(14, true, int64(m.List)))
		}
	default:
		return bridge.Command{}, wire.ErrUnsupported
	}
	return c, nil
}

func boolValue(v bool) uint64 {
	if v {
		return 1
	}
	return 0
}

// The bridge codec owns structural/profile validation. Meaning owns neutral
// event decoding; only raw IDs and saved-action extensions are translated here.
func event(r wire.Record) (meaning.Event, error) {
	codec, err := profile.NewProfileCodec()
	if err != nil {
		return meaning.Event{}, err
	}
	b, err := codec.Encode([]wire.Record{r})
	if err != nil {
		return meaning.Event{}, err
	}
	rs, err := codec.Decode(b)
	if err != nil {
		return meaning.Event{}, err
	}
	r = rs[0]
	if r.Type != wire.Fact {
		return meaning.Event{}, wire.ErrFrame
	}
	fs := make([]wire.Field, 0, len(r.Fields)+2)
	for _, f := range r.Fields {
		if f.Number < 64 {
			fs = append(fs, f)
		}
	}
	ref := func(id int64) []wire.Field { return []wire.Field{wire.S(1, true, id), wire.U(2, true, 1)} }
	fs = append(fs, wire.Obj(10, true, ref(profile.S(r, 64))), wire.Obj(16, true, ref(profile.S(r, 65))))
	d, err := meaning.EventValue(wire.Record{Type: wire.Fact, Fields: fs})
	if err != nil {
		return meaning.Event{}, err
	}
	e := d.Event
	// The audited bridge BuyList/SellList packets carry one complete goods
	// list, without the optional flag used by other list producers. Preserve an
	// explicit false flag; never infer completeness for a missing goods list.
	if (e.Kind == meaning.Shop || e.Kind == meaning.Offer) && profile.Has(r, 24) && !profile.Has(r, 31) {
		e.Complete = true
	}
	if opts, ok := wire.Find(r.Fields, 68); ok {
		e.Lines = make([]meaning.Line, 0, len(opts.Items))
		for _, option := range opts.Items {
			label, _ := wire.Find(option.Fields, 2)
			text, _ := wire.Find(label.Fields, 1)
			action, _ := wire.Find(option.Fields, 3)
			e.Lines = append(e.Lines, meaning.Line{Label: string(text.Data), Say: string(action.Data)})
		}
	}
	return e, nil
}
