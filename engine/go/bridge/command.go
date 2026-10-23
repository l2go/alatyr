// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// measured bounds a caller-owned tree before encoding or copying it. It also
// bounds metadata for empty objects/lists, not just the payload byte lengths.
func measured(fs []wire.Field, depth int) (int, bool) {
	if depth > 3 || len(fs) > 128 {
		return 0, false
	}
	n := 0
	for _, f := range fs {
		n += 16
		v := f.Value
		if len(v.Data) > 4096 || len(v.Items) > 1024 {
			return 0, false
		}
		n += len(v.Data)
		if v.Fields != nil {
			x, ok := measured(v.Fields, depth+1)
			if !ok {
				return 0, false
			}
			n += x
		}
		for _, it := range v.Items {
			if len(it.Data) > 4096 {
				return 0, false
			}
			n += 16 + len(it.Data)
			x, ok := measured(it.Fields, depth+1)
			if !ok {
				return 0, false
			}
			n += x
		}
		if n > 65536 {
			return 0, false
		}
	}
	return n, true
}
func commandRecord(c Command, n, epoch, ttl uint64) wire.Record {
	fs := []wire.Field{wire.U(1, true, n), wire.U(2, true, c.Verb), wire.U(3, true, epoch), wire.U(4, true, ttl)}
	return wire.Record{Type: wire.Intent, Fields: append(fs, c.Payload...)}
}
func validateCommand(c Command) error {
	if c.Key == 0 {
		return failure(StateError)
	}
	if _, ok := measured(c.Payload, 0); !ok {
		return failure(Capacity)
	}
	if c.QueueTTLMS != nil && *c.QueueTTLMS > 5000 {
		return failure(ConfigError)
	}
	allowed := map[uint32]bool{}
	required := []uint32{}
	switch c.Verb {
	case 1:
		required = []uint32{11}
	case 2, 3, 4, 5, 10:
		required = []uint32{64}
	case 6:
		required = []uint32{64, 68}
	case 7:
		required = []uint32{14, 16, 66}
		allowed[17] = true
	case 8:
		required = []uint32{14, 16, 64, 66}
		allowed[17] = true
	case 9:
	case 12:
		required = []uint32{64, 18}
		allowed[19] = true
	case 13:
		required = []uint32{14, 18}
	default:
		return failure(StateError)
	}
	for _, n := range required {
		allowed[n] = true
		if _, ok := wire.Find(c.Payload, n); !ok {
			return failure(StateError)
		}
	}
	seen := map[uint32]bool{}
	for _, f := range c.Payload {
		if !allowed[f.Number] || seen[f.Number] {
			return failure(StateError)
		}
		seen[f.Number] = true
	}
	for _, n := range []uint32{64, 66, 16} {
		if v, ok := wire.Find(c.Payload, n); ok && v.S <= 0 {
			return failure(StateError)
		}
	}
	if v, ok := wire.Find(c.Payload, 14); ok {
		if c.Verb == 13 {
			if v.S != 0 && v.S != 1 && v.S != 2 && v.S != 4 && v.S != 6 {
				return failure(StateError)
			}
		} else if v.S <= 0 {
			return failure(StateError)
		}
	}
	if v, ok := wire.Find(c.Payload, 18); ok {
		id, _ := wire.Find(v.Fields, 1)
		level, _ := wire.Find(v.Fields, 2)
		if id.S < 1 || id.S > 32000 || level.S <= 0 || c.Verb == 13 && level.S > 1000 {
			return failure(StateError)
		}
	}
	if v, ok := wire.Find(c.Payload, 68); ok && len(v.Data) == 0 {
		return failure(StateError)
	}
	return nil
}
func checkReceipt(r Receipt, c Command) error {
	if r.Requested != c.Verb || r.Effective != c.Verb {
		return wire.ErrFrame
	}
	if r.Stage != wire.StageDone {
		return nil
	}
	switch c.Verb {
	case 1, 3, 9, 12:
		return wire.ErrFrame
	}
	// Match the command's captured identifiers where the raw request carries them.
	var itemTotal int64
	coins := 0
	targets := 0
	skills := 0
	for _, e := range r.Effects {
		k, _ := wire.Find(e.Fields, 1)
		delta, _ := wire.Find(e.Fields, 4)
		switch k.U {
		case 1:
			item, _ := wire.Find(e.Fields, 2)
			object, _ := wire.Find(e.Fields, 3)
			wanted, _ := wire.Find(c.Payload, 66)
			if (c.Verb == 7 || c.Verb == 8) && item.S != wanted.S {
				return wire.ErrFrame
			}
			if c.Verb == 8 {
				target, _ := wire.Find(c.Payload, 64)
				if object.S != target.S || delta.S >= 0 {
					return wire.ErrFrame
				}
			}
			if (c.Verb == 7 || c.Verb == 4) && delta.S <= 0 {
				return wire.ErrFrame
			}
			if delta.S > 0 && itemTotal > 1<<63-1-delta.S || delta.S < 0 && itemTotal < (-1<<63)-delta.S {
				return wire.ErrFrame
			}
			itemTotal += delta.S
		case 2:
			coins++
			if c.Verb == 7 && delta.S >= 0 || c.Verb == 8 && delta.S <= 0 {
				return wire.ErrFrame
			}
		case 3:
			skills++
			if c.Verb == 13 {
				use, _ := wire.Find(c.Payload, 18)
				id, _ := wire.Find(use.Fields, 1)
				level, _ := wire.Find(use.Fields, 2)
				got, _ := wire.Find(e.Fields, 5)
				old, _ := wire.Find(e.Fields, 6)
				next, _ := wire.Find(e.Fields, 7)
				if id.S != got.S || next.S != level.S || old.S >= level.S {
					return wire.ErrFrame
				}
			}
		case 5:
			targets++
			target, _ := wire.Find(c.Payload, 64)
			got, _ := wire.Find(e.Fields, 8)
			if target.S != got.S {
				return wire.ErrFrame
			}
		}
		switch c.Verb {
		case 7, 8:
			if k.U != 1 && k.U != 2 {
				return wire.ErrFrame
			}
		case 4:
			if k.U != 1 && k.U != 5 {
				return wire.ErrFrame
			}
		case 5, 6:
			return wire.ErrFrame
		case 2, 10:
			if k.U != 5 {
				return wire.ErrFrame
			}
		case 13:
			if k.U != 1 && k.U != 2 && k.U != 3 && k.U != 4 {
				return wire.ErrFrame
			}
		}
	}
	count, _ := wire.Find(c.Payload, 16)
	if c.Verb == 7 && (coins != 1 || itemTotal != count.S) || c.Verb == 8 && (coins != 1 || itemTotal != -count.S) || c.Verb == 4 && (itemTotal <= 0 || targets > 1) || (c.Verb == 2 || c.Verb == 10) && targets != 1 || c.Verb == 13 && skills != 1 {
		return wire.ErrFrame
	}
	return nil
}
