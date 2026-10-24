// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package fixture

import (
	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Profile validation stays in bridge. These captured-command checks mirror
// the Port producer's obligation; a script cannot claim an unrelated done.
func checkReceipt(r bridge.Receipt, c bridge.Command) error {
	if r.Requested != c.Verb || r.Effective != c.Verb {
		return wire.ErrFrame
	}
	fs := []wire.Field{wire.U(1, true, r.N), wire.U(2, true, uint64(r.Stage)), wire.U(3, true, uint64(r.Reason)), wire.U(5, true, r.Requested), wire.U(6, true, r.Effective), wire.U(7, true, uint64(r.Delivery))}
	if r.Text != nil {
		fs = append(fs, wire.Field{Number: 4, Value: *r.Text})
	}
	if r.EffectsPresent {
		fs = append(fs, wire.Items(66, true, r.Effects))
	}
	if r.BridgeReason != nil {
		fs = append(fs, wire.Data(64, true, r.Namespace), wire.U(65, true, *r.BridgeReason))
	}
	codec, err := profile.NewProfileCodec()
	if err != nil {
		return err
	}
	if _, err = codec.Encode([]wire.Record{{Type: wire.Result, Fields: fs}}); err != nil {
		return err
	}
	if r.Stage != wire.StageDone {
		return nil
	}
	switch c.Verb {
	case 1, 3, 9, 12:
		return wire.ErrFrame
	}
	total := int64(0)
	coins, targets, skills := 0, 0, 0
	for _, v := range r.Effects {
		kind, _ := wire.Find(v.Fields, 1)
		delta, _ := wire.Find(v.Fields, 4)
		switch kind.U {
		case 1:
			id, _ := wire.Find(v.Fields, 2)
			object, _ := wire.Find(v.Fields, 3)
			wanted, _ := wire.Find(c.Payload, 66)
			target, _ := wire.Find(c.Payload, 64)
			if (c.Verb == 7 || c.Verb == 8) && id.S != wanted.S || c.Verb == 8 && (object.S != target.S || delta.S >= 0) || (c.Verb == 7 || c.Verb == 4) && delta.S <= 0 {
				return wire.ErrFrame
			}
			if delta.S > 0 && total > 1<<63-1-delta.S || delta.S < 0 && total < (-1<<63)-delta.S {
				return wire.ErrFrame
			}
			total += delta.S
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
				got, _ := wire.Find(v.Fields, 5)
				old, _ := wire.Find(v.Fields, 6)
				next, _ := wire.Find(v.Fields, 7)
				if id.S != got.S || next.S != level.S || old.S >= level.S {
					return wire.ErrFrame
				}
			}
		case 5:
			targets++
			target, _ := wire.Find(c.Payload, 64)
			got, _ := wire.Find(v.Fields, 8)
			if target.S != got.S {
				return wire.ErrFrame
			}
		}
		switch c.Verb {
		case 7, 8:
			if kind.U != 1 && kind.U != 2 {
				return wire.ErrFrame
			}
		case 4:
			if kind.U != 1 && kind.U != 5 {
				return wire.ErrFrame
			}
		case 5, 6:
			return wire.ErrFrame
		case 2, 10:
			if kind.U != 5 {
				return wire.ErrFrame
			}
		case 13:
			if kind.U < 1 || kind.U > 4 {
				return wire.ErrFrame
			}
		}
	}
	count, _ := wire.Find(c.Payload, 16)
	if c.Verb == 7 && (coins != 1 || total != count.S) || c.Verb == 8 && (coins != 1 || total != -count.S) || c.Verb == 4 && (total <= 0 || targets > 1) || (c.Verb == 2 || c.Verb == 10) && targets != 1 || c.Verb == 13 && skills != 1 {
		return wire.ErrFrame
	}
	return nil
}
