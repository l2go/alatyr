// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"math"
	"time"
	"unicode/utf8"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

const (
	maxPayloadBytes    = 4096
	maxReceiptBytes    = 8192
	maxEffects         = 64
	maxCheckpointBytes = 2 << 20
	maxRows            = 1024
)

var (
	errSource   = errors.New("worldbridge: incomplete source")
	errCommand  = errors.New("worldbridge: invalid command")
	errCapacity = errors.New("worldbridge: capacity exceeded")
)

// templates is the metadata this consumer needs, not a second catalog API.
type templates interface {
	Item(int32) (catalog.ItemTemplate, bool)
	Skill(int32, int32) (catalog.Skill, bool)
	CanTeach(int32, int32) bool
}

type translator struct{ catalog templates }

// fact is payload without a session-assigned sequence or epoch. Keeping those
// separate prevents translation from allocating session identity or renewing time.
type fact struct {
	kind   uint64
	domain string
	fields []wire.Field
}

func point(n uint32, p world.Point) wire.Field { return wire.At(n, true, p.X, p.Y, p.Z) }
func boolean(n uint32, b bool) wire.Field {
	if b {
		return wire.U(n, true, 1)
	}
	return wire.U(n, true, 0)
}

func text(n uint32, s string, origin wire.Provenance) (wire.Field, error) {
	if len(s) > maxPayloadBytes {
		return wire.Field{}, errCapacity
	}
	if !utf8.ValidString(s) {
		return wire.Field{}, errSource
	}
	return wire.Obj(n, false, []wire.Field{wire.Data(1, true, s), wire.U(2, true, uint64(origin))}), nil
}

// display uses checked truncation, like the world's integral stat views. It
// never clamps negative, nonfinite or unrepresentable state into a valid zero.
func display(v float64) (int64, error) {
	if math.IsNaN(v) || math.IsInf(v, 0) || v < 0 || v >= 0x1p63 {
		return 0, errSource
	}
	return int64(v), nil
}

func elapsed(at, origin world.Time) (int64, error) {
	if origin < 0 || at < origin {
		return 0, errSource
	}
	return int64((at - origin) / world.Time(time.Millisecond)), nil
}

func validRef(r world.Ref) bool { return r.ID > 0 && r.Gen != 0 }

// encode checks the frozen profile as well as its structural codec. The
// returned records own decoded storage and carry exact encoded-size metadata.
func encode(rs []wire.Record) ([]wire.Record, error) {
	c, err := profile.NewProfileCodec()
	if err != nil {
		return nil, err
	}
	b, err := c.Encode(rs)
	if err != nil {
		return nil, err
	}
	return c.Decode(b)
}

func factRecords(fs []fact, epoch, next uint64, at, origin world.Time) ([]wire.Record, uint64, error) {
	ms, err := elapsed(at, origin)
	if err != nil {
		return nil, next, err
	}
	if epoch == 0 || next == 0 || uint64(len(fs)) > math.MaxUint64-next {
		return nil, next, errCapacity
	}
	if len(fs) > maxRows {
		return nil, next, errCapacity
	}
	rs := make([]wire.Record, 0, len(fs))
	for _, f := range fs {
		if v, ok := wire.Find(f.fields, 64); ok && v.S <= 0 {
			return nil, 0, errSource
		}
		if v, ok := wire.Find(f.fields, 65); ok && v.S < 0 {
			return nil, 0, errSource
		}
		fields := []wire.Field{wire.U(1, true, f.kind), wire.U(2, true, next), wire.S(3, true, ms), wire.U(4, true, epoch), wire.Data(5, true, f.domain), boolean(6, true)}
		next++
		rs = append(rs, wire.Record{Type: wire.Fact, Fields: append(fields, f.fields...)})
	}
	// Encode records individually: a checkpoint can exceed one frame, but no
	// single observation may bypass the profile's frame bound.
	out := make([]wire.Record, 0, len(rs))
	for _, r := range rs {
		v, err := encode([]wire.Record{r})
		if err != nil {
			return nil, 0, err
		}
		out = append(out, v[0])
	}
	return out, next, nil
}
