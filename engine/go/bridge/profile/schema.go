// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package profile defines the bridge/1 registry over the shared wire codec.
package profile

import "github.com/l2go/l2jfree/engine/go/protocol/wire"

const (
	Open        uint64 = 256
	Opened      uint64 = 258
	Resume      uint64 = 260
	Close       uint64 = 262
	IntentError uint64 = 264
	Name               = "bridge/1"
	Namespace          = "x.l2jfree/bridge"
)

func Limits() wire.Limits {
	return wire.Limits{FrameBytes: 65536, StringBytes: 4096, ListItems: 1024, Depth: 3, Records: 1024, Fields: 128, IdentifierBytes: 64, WorldBytes: 256, ClientBytes: 128}
}
func req(s wire.Spec) wire.Spec      { s.Required = true; s.Essential = true; return s }
func positive(s wire.Spec) wire.Spec { s.Nonzero = true; return s }
func obj(s wire.Schema) wire.Spec    { return wire.Spec{Shape: wire.Object, Essential: true, Object: s} }
func list(s wire.Schema) wire.Spec {
	el := req(obj(s))
	return wire.Spec{Shape: wire.List, Essential: true, Element: &el}
}

// NeutralRegistry describes the frozen payloads without bridge extensions.
// A fresh registry is returned so callers cannot mutate another codec's schema.
func NeutralRegistry() wire.Registry {
	advertised := wire.Schema{}
	for i := uint32(1); i <= 34; i++ {
		advertised[i] = wire.Required(wire.UInt)
	}
	r := wire.Envelopes(advertised, Limits())
	i32 := wire.Essential(wire.Int)
	i32.Bits = 32
	u31 := wire.Essential(wire.UInt)
	u31.Bits = 31
	text := obj(wire.TextSchema())
	text.Essential = false
	position := wire.Essential(wire.Position)
	ref := obj(wire.Schema{1: req(i32), 2: wire.Required(wire.UInt)})
	option := wire.Schema{1: positive(wire.Required(wire.UInt)), 2: req(text)}
	good := wire.Schema{1: positive(wire.Essential(wire.UInt)), 2: i32, 3: req(i32), 4: wire.Required(wire.Int), 5: wire.Essential(wire.Int), 6: text, 7: i32}
	stats := wire.Schema{}
	for i := uint32(1); i <= 7; i++ {
		stats[i] = wire.Essential(wire.Int)
	}
	entry := wire.Schema{1: wire.Required(wire.Int), 2: wire.Required(wire.String), 3: req(text)}
	quest := wire.Schema{1: req(i32), 2: req(i32), 3: req(text), 4: wire.Required(wire.String)}
	skill := wire.Schema{1: req(i32), 2: req(i32), 3: wire.Required(wire.Bool), 4: wire.Required(wire.Bool)}
	use := wire.Schema{1: req(i32), 2: req(i32)}
	lesson := wire.Schema{1: positive(wire.Required(wire.UInt)), 2: req(i32), 3: wire.Required(wire.Int), 4: req(i32), 5: wire.Required(wire.Int)}
	entries := list(entry)
	entries.Essential = false
	payload := wire.Schema{10: ref, 11: wire.Essential(wire.String), 12: text, 13: position, 14: position, 15: u31, 16: ref, 17: i32, 18: wire.Essential(wire.Bool), 19: wire.Essential(wire.Bool), 20: text, 21: list(option), 22: i32, 23: wire.Essential(wire.Int), 24: list(good), 25: obj(stats), 26: wire.Optional(wire.String), 27: wire.Essential(wire.Int), 28: entries, 29: list(quest), 30: list(skill), 31: wire.Essential(wire.Bool), 32: positive(wire.Essential(wire.UInt)), 33: positive(wire.Essential(wire.UInt)), 34: list(lesson)}
	for n, s := range payload {
		r[wire.Fact][n] = s
	}
	for n, s := range (wire.Schema{10: ref, 11: position, 12: wire.Essential(wire.UInt), 13: wire.Essential(wire.UInt), 14: i32, 15: wire.Essential(wire.UInt), 16: wire.Essential(wire.Int), 17: wire.Essential(wire.Int), 18: obj(use), 19: wire.Essential(wire.Bool), 20: ref}) {
		r[wire.Intent][n] = s
	}
	return r
}

// AdvertisedLimits is the exact accepted Opened.limits object.
func AdvertisedLimits() map[uint32]uint64 {
	return map[uint32]uint64{1: 65536, 2: 4096, 3: 1024, 4: 3, 5: 4096, 6: 64, 7: 8, 8: 262144, 14: 8, 15: 1024, 16: 128, 17: 5000, 20: 5000, 21: 5000, 22: 10000, 23: 15000, 24: 30000, 26: 2097152, 27: 65536, 28: 4, 29: 2, 30: 2097152}
}

func Registry() wire.Registry {
	r := NeutralRegistry()
	i32 := wire.Essential(wire.Int)
	i32.Bits = 32
	saved := wire.Required(wire.String)
	saved.MaxBytes = 4096
	options := list(wire.Schema{1: positive(wire.Required(wire.UInt)), 2: req(obj(wire.TextSchema())), 3: saved})
	args := list(wire.Schema{1: wire.Required(wire.UInt), 2: wire.Essential(wire.String), 3: wire.Essential(wire.Int), 4: wire.Essential(wire.Int), 5: wire.Essential(wire.Int)})
	for n, s := range (wire.Schema{64: i32, 65: i32, 66: positive(wire.Essential(wire.UInt)), 67: wire.Essential(wire.Bool), 68: options, 69: wire.Essential(wire.UInt), 70: wire.Essential(wire.Bool), 71: wire.Essential(wire.UInt), 72: args, 73: i32, 74: wire.Essential(wire.Int)}) {
		r[wire.Fact][n] = s
	}
	saved.Required = false
	r[wire.Intent][64] = i32
	r[wire.Intent][66] = i32
	r[wire.Intent][68] = saved
	r[wire.Result][64] = wire.Essential(wire.String)
	r[wire.Result][65] = wire.Essential(wire.UInt)
	r[wire.Result][66] = list(wire.Schema{1: wire.Required(wire.UInt), 2: i32, 3: i32, 4: wire.Essential(wire.Int), 5: i32, 6: wire.Essential(wire.Int), 7: wire.Essential(wire.Int), 8: i32})
	account := wire.Required(wire.String)
	account.MaxBytes = 128
	r[Open] = wire.Schema{1: wire.Required(wire.UInt), 2: wire.Required(wire.UInt), 3: wire.Required(wire.String), 4: wire.Required(wire.Bytes), 5: account, 6: account, 7: wire.Required(wire.UInt)}
	limits := wire.Schema{}
	for n := range AdvertisedLimits() {
		limits[n] = wire.Required(wire.UInt)
	}
	world := wire.Essential(wire.String)
	world.MaxBytes = 256
	r[Opened] = wire.Schema{1: wire.Required(wire.UInt), 2: wire.Essential(wire.UInt), 3: world, 4: wire.Essential(wire.UInt), 5: wire.Required(wire.UInt), 6: obj(limits), 7: wire.Essential(wire.UInt), 8: wire.Essential(wire.String)}
	r[Resume] = wire.Schema{1: wire.Required(wire.UInt), 2: wire.Required(wire.UInt), 3: wire.Required(wire.Bytes), 4: wire.Required(wire.String)}
	text := obj(wire.TextSchema())
	text.Essential = false
	r[Close] = wire.Schema{1: wire.Required(wire.UInt), 2: wire.Required(wire.UInt), 3: wire.Required(wire.UInt), 4: text}
	r[IntentError] = wire.Schema{1: wire.Required(wire.UInt), 2: wire.Required(wire.UInt), 3: text}
	return r
}
