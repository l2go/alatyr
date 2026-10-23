// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"slices"
	"strings"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

type prepared struct {
	command bridge.Command
	request world.Request
	refusal world.Reason
	cost    int64
}

// normalize bounds shape before encoding caller-owned values. The shared codec
// validates types and ranges and supplies an independent canonical copy.
func normalize(c bridge.Command) (bridge.Command, error) {
	if c.Key == 0 || len(c.Payload) > 5 || c.QueueTTLMS != nil && *c.QueueTTLMS > 5000 {
		return bridge.Command{}, errCommand
	}
	var required, optional []uint32
	switch c.Verb {
	case 1:
		required = []uint32{11}
	case 2, 3, 4, 5, 10:
		required = []uint32{64}
	case 6:
		required = []uint32{64, 68}
	case 7:
		required = []uint32{14, 16, 66}
		optional = []uint32{17}
	case 8:
		required = []uint32{14, 16, 64, 66}
		optional = []uint32{17}
	case 9:
	case 12:
		required = []uint32{64, 18}
		optional = []uint32{19}
	case 13:
		required = []uint32{14, 18}
	default:
		return bridge.Command{}, errCommand
	}
	seen := map[uint32]bool{}
	for _, f := range c.Payload {
		if seen[f.Number] || !slices.Contains(required, f.Number) && !slices.Contains(optional, f.Number) {
			return bridge.Command{}, errCommand
		}
		seen[f.Number] = true
		v := f.Value
		if len(v.Items) > 0 || len(v.Data) > maxPayloadBytes || len(v.Fields) > 2 {
			return bridge.Command{}, errCapacity
		}
		if f.Number != 18 && len(v.Fields) != 0 || f.Number != 68 && len(v.Data) != 0 {
			return bridge.Command{}, errCommand
		}
		if f.Number == 18 {
			if len(v.Fields) != 2 {
				return bridge.Command{}, errCommand
			}
			for _, sf := range v.Fields {
				if len(sf.Value.Fields) != 0 || len(sf.Value.Items) != 0 || len(sf.Value.Data) != 0 {
					return bridge.Command{}, errCommand
				}
			}
		}
	}
	for _, n := range required {
		if !seen[n] {
			return bridge.Command{}, errCommand
		}
	}
	r := wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, c.Verb), wire.U(3, true, 1), wire.U(4, true, 0)}}
	r.Fields = append(r.Fields, c.Payload...)
	rs, err := encode([]wire.Record{r})
	if err != nil {
		return bridge.Command{}, errCommand
	}
	if rs[0].EncodedBytes > maxPayloadBytes {
		return bridge.Command{}, errCapacity
	}
	c.Payload = rs[0].Fields[4:] // Codec preserves field order, including the envelope.
	if c.QueueTTLMS != nil {
		ttl := *c.QueueTTLMS
		c.QueueTTLMS = &ttl
	}
	for _, n := range []uint32{64, 66, 16} {
		if v, ok := wire.Find(c.Payload, n); ok && v.S <= 0 {
			return bridge.Command{}, errCommand
		}
	}
	if v, ok := wire.Find(c.Payload, 68); ok && (len(v.Data) == 0 || strings.ContainsRune(string(v.Data), 0)) {
		return bridge.Command{}, errCommand
	}
	if v, ok := wire.Find(c.Payload, 18); ok {
		id, _ := wire.Find(v.Fields, 1)
		level, _ := wire.Find(v.Fields, 2)
		if id.S < 1 || id.S > 32000 || level.S < 1 || level.S > 1<<31-1 || c.Verb == 13 && level.S > 1000 {
			return bridge.Command{}, errCommand
		}
	}
	if v, ok := wire.Find(c.Payload, 14); ok {
		if c.Verb == 13 {
			if v.S != 0 && v.S != 1 && v.S != 2 && v.S != 4 && v.S != 6 {
				return bridge.Command{}, errCommand
			}
		} else if v.S <= 0 {
			return bridge.Command{}, errCommand
		}
	}
	return c, nil
}

func prepare(c bridge.Command, s world.Scene, now world.Time) (prepared, error) {
	c, err := normalize(c)
	if err != nil {
		return prepared{}, err
	}
	p := prepared{command: c, request: world.Request{Owner: s.Self.Entity}}
	if !validRef(p.request.Owner) || now < 0 {
		return prepared{}, errSource
	}
	ttl := uint64(5000) // The shared raw port's default queue budget.
	if c.QueueTTLMS != nil {
		ttl = *c.QueueTTLMS
	}
	delta := world.Time(ttl) * world.Time(time.Millisecond)
	if now > world.Time(1<<63-1)-delta || now+delta == 0 {
		return prepared{}, errCapacity
	}
	p.request.Deadline = now + delta
	switch c.Verb {
	case 1:
		p.request.Verb = world.VerbMove
		v, _ := wire.Find(c.Payload, 11)
		p.request.Position = world.Point{X: v.XYZ[0], Y: v.XYZ[1], Z: v.XYZ[2]}
	case 2, 10:
		p.request.Verb = world.VerbSelect
	case 3:
		p.request.Verb = world.VerbAttack
	case 4:
		p.request.Verb = world.VerbPickup
	case 5:
		p.request.Verb = world.VerbTalk
	case 6:
		p.request.Verb = world.VerbChoose
	case 12:
		p.request.Verb = world.VerbCast
	case 13:
		p.request.Verb = world.VerbLearn
	case 7, 8, 9:
		p.refusal = world.ReasonUnavailable
		return p, nil
	}
	if v, ok := wire.Find(c.Payload, 64); ok {
		id := int32(v.S)
		if id == s.Self.Entity.ID {
			p.request.Target = s.Self.Entity
		}
		for _, o := range s.Objects {
			if o.Entity.ID == id {
				if p.request.Target != (world.Ref{}) {
					return prepared{}, errSource
				}
				p.request.Target = o.Entity
			}
		}
		if !validRef(p.request.Target) {
			p.refusal = world.ReasonStale
			return p, nil
		}
	}
	if v, ok := wire.Find(c.Payload, 18); ok {
		id, _ := wire.Find(v.Fields, 1)
		level, _ := wire.Find(v.Fields, 2)
		p.request.Skill = world.Skill{ID: int32(id.S), Level: int32(level.S)}
	}
	if v, ok := wire.Find(c.Payload, 19); ok {
		p.request.Ctrl = v.U != 0
	}
	if c.Verb == 6 {
		_, opts, err := choices(s.Dialogue)
		if err != nil {
			return prepared{}, err
		}
		action, _ := wire.Find(c.Payload, 68)
		found := false
		for _, o := range opts {
			bound, _ := wire.Find(o.Fields, 3)
			if string(bound.Data) == string(action.Data) {
				found = true
			}
		}
		if found {
			for _, a := range s.Dialogue.Affordances {
				if a.Verb == world.VerbChoose && a.Target == p.request.Target && a.Issued <= now && a.Expires > now {
					if p.request.AffordanceID != 0 {
						return prepared{}, errSource
					}
					p.request.AffordanceID = a.ID
				}
			}
		}
		if p.request.AffordanceID == 0 {
			p.refusal = world.ReasonStale
		}
	}
	if c.Verb == 13 {
		typ, _ := wire.Find(c.Payload, 14)
		if typ.S != 0 {
			p.refusal = world.ReasonUnavailable
			return p, nil
		}
		for _, a := range s.Dialogue.Affordances {
			id, level, ok := lesson(a)
			if !ok || a.Expires <= now || a.Issued > now || id != p.request.Skill.ID || level != p.request.Skill.Level {
				continue
			}
			if p.request.AffordanceID != 0 {
				return prepared{}, errSource
			}
			p.request.AffordanceID = a.ID
			p.request.Target = a.Target
			p.cost = a.Cost[0].Quantity
		}
		if p.request.AffordanceID == 0 || !validRef(p.request.Target) {
			p.refusal = world.ReasonStale
		}
	}
	return p, nil
}
