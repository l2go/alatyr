// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package profile

import (
	"bytes"
	"io"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Codec is the shared wire codec restricted to the bridge/1 profile. Every
// decoded record is also checked against the profile's field rules.
type Codec struct{ wire *wire.Codec }

// NewProfileCodec returns a codec built from Limits and Registry.
func NewProfileCodec() (*Codec, error) {
	w, err := wire.New(Limits(), Registry())
	if err != nil {
		return nil, err
	}
	return &Codec{w}, nil
}

// Decode decodes one frame. If any record fails the profile, it returns an
// error and no records.
func (c *Codec) Decode(b []byte) ([]wire.Record, error) {
	r, err := c.wire.Decode(b)
	return validate(r, err)
}

// Read reads and decodes one frame like Decode.
func (c *Codec) Read(r io.Reader) ([]wire.Record, error) {
	v, err := c.wire.Read(r)
	return validate(v, err)
}

// Encode encodes one frame and verifies that it decodes under the profile.
func (c *Codec) Encode(r []wire.Record) ([]byte, error) {
	b, err := c.wire.Encode(r)
	if err != nil {
		return nil, err
	}
	if _, err = c.Decode(b); err != nil {
		return nil, err
	}
	return b, nil
}

// Write verifies one frame like Encode and writes it to w.
func (c *Codec) Write(w io.Writer, r []wire.Record) error {
	if _, err := c.Encode(r); err != nil {
		return err
	}
	return c.wire.Write(w, r)
}

// U returns the unsigned value of field n of r, or zero if it is absent.
func U(r wire.Record, n uint32) uint64 { v, _ := wire.Find(r.Fields, n); return v.U }

// S returns the signed value of field n of r, or zero if it is absent.
func S(r wire.Record, n uint32) int64 { v, _ := wire.Find(r.Fields, n); return v.S }

// Has reports whether r has field n.
func Has(r wire.Record, n uint32) bool { _, b := wire.Find(r.Fields, n); return b }

// Data returns the bytes of field n of r, or nil if it is absent.
func Data(r wire.Record, n uint32) []byte { v, _ := wire.Find(r.Fields, n); return v.Data }

func validate(rs []wire.Record, err error) ([]wire.Record, error) {
	if err != nil {
		return nil, err
	}
	for _, r := range rs {
		if err := check(r); err != nil {
			return nil, err
		}
	}
	return rs, nil
}

// check follows the endpoint's order, after the neutral wire validation.
func check(r wire.Record) error {
	frame, unsupported := wire.ErrFrame, wire.ErrUnsupported
	u := func(n uint32) uint64 { return U(r, n) }
	has := func(n uint32) bool { return Has(r, n) }
	data := func(n uint32) []byte { return Data(r, n) }
	switch r.Type {
	case Open:
		if u(1) > u(2) || len(data(4)) != 32 || len(data(5)) == 0 || len(data(6)) == 0 || bytes.IndexByte(data(5), 0) >= 0 || bytes.IndexByte(data(6), 0) >= 0 || u(7) != 1 {
			return frame
		}
		if u(1) > 1 || u(2) < 1 || string(data(3)) != Name {
			return unsupported
		}
	case Opened:
		if u(5) > 21 {
			return unsupported
		}
		switch u(1) {
		case 1:
			for _, n := range []uint32{2, 3, 4, 6, 7, 8} {
				if !has(n) {
					return frame
				}
			}
			if u(5) != 0 || u(7) != 1 || string(data(8)) != Name || len(data(3)) > 256 {
				return frame
			}
			limits, _ := wire.Find(r.Fields, 6)
			for n, want := range AdvertisedLimits() {
				v, ok := wire.Find(limits.Fields, n)
				if !ok || v.U != want {
					return frame
				}
			}
		case 2:
			if u(5) == 0 {
				return frame
			}
			for _, n := range []uint32{2, 3, 4, 6, 7, 8} {
				if has(n) {
					return frame
				}
			}
		default:
			return unsupported
		}
	case Resume:
		if u(1) == 0 || u(2) == 0 || len(data(3)) != 32 || string(data(4)) != Name {
			return frame
		}
	case Close:
		if u(3) != 0 {
			return frame
		}
	case IntentError:
		if u(2) != 22 && u(2) != 256 {
			return unsupported
		}
	case wire.Result:
		pair := has(65)
		if has(64) != pair || has(64) && string(data(64)) != Namespace {
			return frame
		}
		stage := wire.Stage(u(2))
		reason := u(3)
		if (stage == wire.StageSent || stage == wire.StageDone) && u(7) != 2 {
			return frame
		}
		if stage == wire.StageUnrecoverable && (!pair || u(65) != 21) {
			return frame
		}
		if pair {
			code := u(65)
			if code > 21 {
				return unsupported
			}
			if code == 0 || code == 11 || code == 12 || code == 13 || code == 19 || !profilePair(code, reason) {
				return frame
			}
		}
		if has(66) != (stage == wire.StageDone) {
			return frame
		}
		effects, _ := wire.Find(r.Fields, 66)
		for _, v := range effects.Items {
			if err := checkEffect(v.Fields); err != nil {
				return err
			}
		}
	case wire.Fact:
		if has(10) || has(16) || has(32) {
			return frame
		}
		generation, own := has(66), has(67)
		if generation != own || own && u(67) != 1 || generation && u(66) == 0 {
			return frame
		}
		if has(21) && has(68) {
			return frame
		}
		args, _ := wire.Find(r.Fields, 72)
		for _, v := range args.Items {
			if err := checkMessage(v.Fields); err != nil {
				return err
			}
		}
	}
	return nil
}
func profilePair(code, core uint64) bool {
	if code >= 1 && code <= 5 || code >= 14 && code <= 18 {
		return core == 16
	}
	switch code {
	case 6:
		return core == 10 || core == 6
	case 7:
		return core == 1
	case 8:
		return core == 7
	case 9, 10:
		return core == 16
	case 20:
		return core == 24
	case 21:
		return core == 20
	}
	return false
}
func exact(fields []wire.Field, allowed []uint32) error {
	for _, f := range fields {
		ok := false
		for _, n := range allowed {
			ok = ok || f.Number == n
		}
		if !ok {
			return wire.ErrFrame
		}
	}
	for _, n := range allowed {
		if _, ok := wire.Find(fields, n); !ok {
			return wire.ErrFrame
		}
	}
	return nil
}
func checkEffect(fields []wire.Field) error {
	kind, _ := wire.Find(fields, 1)
	var allowed []uint32
	switch kind.U {
	case 1:
		allowed = []uint32{1, 2, 3, 4}
	case 2:
		allowed = []uint32{1, 4}
	case 3:
		allowed = []uint32{1, 5, 6, 7}
	case 4:
		allowed = []uint32{1, 4, 6, 7}
	case 5:
		allowed = []uint32{1, 8}
	default:
		return wire.ErrUnsupported
	}
	if err := exact(fields, allowed); err != nil {
		return err
	}
	delta, _ := wire.Find(fields, 4)
	old, _ := wire.Find(fields, 6)
	next, _ := wire.Find(fields, 7)
	target, _ := wire.Find(fields, 8)
	if (kind.U == 1 || kind.U == 2 || kind.U == 4) && delta.S == 0 {
		return wire.ErrFrame
	}
	if (kind.U == 3 || kind.U == 4) && (old.S < 0 || next.S < 0 || old.S == next.S) {
		return wire.ErrFrame
	}
	if kind.U == 5 && target.S < 0 {
		return wire.ErrFrame
	}
	return nil
}
func checkMessage(fields []wire.Field) error {
	typ, _ := wire.Find(fields, 1)
	var allowed []uint32
	switch typ.U {
	case 0:
		allowed = []uint32{1, 2}
	case 1, 2, 3, 5, 6:
		allowed = []uint32{1, 3}
	case 4:
		allowed = []uint32{1, 3, 4}
	case 7:
		allowed = []uint32{1, 3, 4, 5}
	default:
		return wire.ErrUnsupported
	}
	return exact(fields, allowed)
}
