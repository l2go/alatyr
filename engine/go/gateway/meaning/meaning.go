// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Namespace identifies the dictionary extension namespace.
const Namespace = "x.alatyr"

// Meaning is the fixed participant dictionary identifier.
const Meaning = "alatyr/1"

// WireVersion is the supported envelope version.
const WireVersion uint64 = 1

// Kind identifies a fixed fact kind and its semantic classification.
type Kind struct {
	// Code is the fixed fact code.
	Code uint64
	// Name is the neutral backend event name.
	Name string
	// Domain is the projection domain.
	Domain string
	// Family is the fact's semantic family.
	Family string
	// Parent is the outcome's parent classification, when applicable.
	Parent string
}

var kindRegistry = []Kind{
	{1, Appeared, "objects", "horizon", ""}, {2, Moved, "objects", "state", ""},
	{3, Stopped, "objects", "state", ""}, {4, Corrected, "objects", "state", ""},
	{5, Struck, "objects", "outcome", "harm"}, {6, Died, "objects", "outcome", "existence"},
	{7, Revived, "objects", "outcome", "existence"}, {8, Dropped, "objects", "outcome", "existence"},
	{9, Took, "objects", "outcome", "resource transfer"}, {10, Gone, "objects", "horizon", ""},
	{11, Spoke, "dialogue", "speech+proposal", "speech completion / possibility change"},
	{12, Shop, "trade", "proposal", "possibility change"}, {13, Offer, "trade", "proposal", "possibility change"},
	{14, Carried, "inventory", "state", ""}, {15, Inventory, "inventory", "state", ""},
	{16, Vitals, "self", "state", ""}, {17, Reward, "self", "outcome", "resource transfer"},
	{18, Notice, "dialogue", "speech", "speech completion"}, {19, History, "history", "state", ""},
	{20, Quest, "quests", "outcome", "obligation"}, {21, Quests, "quests", "state", ""},
	{22, Practice, "skills", "proposal", "possibility change"}, {23, Known, "skills", "state", ""},
	{24, Targeted, "objects", "state", ""},
}

// Verb identifies a requested action and its evidence class.
type Verb struct {
	// Code is the fixed requested action code.
	Code uint64
	// Name is its participant-facing name.
	Name string
	// Command is its backend command name.
	Command string
	// Class is its evidence class, or empty for classless actions.
	Class string
	// Waiting keeps delivery open for evidence.
	Waiting bool
}

var verbRegistry = []Verb{
	{1, "walk", Walk, "", false}, {2, "aim", Aim, "", false}, {3, "strike", Strike, "", false},
	{4, "take", Take, "take", true}, {5, "talk", Talk, "dialog", true}, {6, "choose", Say, "dialog", true},
	{7, "buy", Buy, "trade", true}, {8, "sell", Sell, "trade", true}, {9, "rise", Rise, "revive", true},
	{10, "focus", Focus, "", false}, {11, "engage", Engage, "", false},
	{12, "cast", Cast, "", true}, {13, "learn", Learn, "learn", true},
}

// Kinds returns an independent copy of the fixed fact table.
func Kinds() []Kind { return append([]Kind(nil), kindRegistry...) }

// Verbs returns an independent copy of the fixed verb table.
func Verbs() []Verb { return append([]Verb(nil), verbRegistry...) }

// KindByCode looks up a fact code without accepting extension kinds.
func KindByCode(code uint64) (Kind, bool) {
	for _, k := range kindRegistry {
		if k.Code == code {
			return k, true
		}
	}
	return Kind{}, false
}

// KindByName looks up a backend event name in the fixed dictionary.
func KindByName(name string) (Kind, bool) {
	for _, k := range kindRegistry {
		if k.Name == name {
			return k, true
		}
	}
	return Kind{}, false
}

// VerbByCode looks up a requested action code.
func VerbByCode(code uint64) (Verb, bool) {
	for _, v := range verbRegistry {
		if v.Code == code {
			return v, true
		}
	}
	return Verb{}, false
}

// VerbByCommand looks up a backend command name.
func VerbByCommand(name string) (Verb, bool) {
	for _, v := range verbRegistry {
		if v.Command == name {
			return v, true
		}
	}
	return Verb{}, false
}

// Config holds explicit dictionary and storage limits. No deployment defaults
// are supplied. Deployments may change proposal lifetime without
// changing any wire meaning, while preserving all other invalidation rules.
type Config struct {
	// Wire bounds structural encoding and decoding, before semantic planning.
	Wire wire.Limits
	// Advertised contains Welcome limit fields 1 through 34 in wire order.
	// State checks aggregate fact bytes against fields 26 and 30 (indices 25 and 29).
	Advertised [34]uint64
	// Attempts supplies protocol admission bounds; Pending also bounds learn plans.
	Attempts proto.Config
	// ProposalMS is the lifetime from original backend receipt, never snapshot time.
	ProposalMS int64
	// WindowBytes bounds both serialized proposal bytes and retained action text.
	WindowBytes int
	// Windows bounds the number of retained proposal windows.
	Windows int
	// Objects bounds currently observed reference lifetimes and actor projections.
	Objects int
	// HistoryEntries bounds the retained tail of a history report.
	HistoryEntries int
	// HistoryBytes bounds the encoded retained history report.
	HistoryBytes int
}

// LimitsFields encodes all 34 explicitly supplied advertised bounds.
func (c Config) LimitsFields() []wire.Field {
	out := make([]wire.Field, 0, len(c.Advertised))
	for i, x := range c.Advertised {
		out = append(out, wire.U(uint32(i+1), true, x))
	}
	return out
}

// Registry returns an independently owned schema for alatyr/1.
func Registry(c Config) wire.Registry {
	limits := wire.Schema{}
	for i := range c.Advertised {
		s := wire.Essential(wire.UInt)
		s.Required = true
		limits[uint32(i+1)] = s
	}
	r := wire.Envelopes(limits, c.Wire)
	obj := func(schema wire.Schema) wire.Spec { s := wire.Essential(wire.Object); s.Object = schema; return s }
	list := func(schema wire.Schema) wire.Spec {
		el := wire.Required(wire.Object)
		el.Object = schema
		s := wire.Essential(wire.List)
		s.Element = &el
		return s
	}
	i32 := wire.Essential(wire.Int)
	i32.Bits = 32
	u31 := wire.Essential(wire.UInt)
	u31.Bits = 31
	text := obj(wire.TextSchema())
	text.Essential = false
	req := func(s wire.Spec) wire.Spec { s.Required = true; s.Essential = true; return s }
	positive := func(s wire.Spec) wire.Spec { s.Nonzero = true; return s }
	ref := obj(wire.Schema{1: req(i32), 2: wire.Required(wire.UInt)})
	position := wire.Essential(wire.Position)
	option := wire.Schema{1: positive(wire.Required(wire.UInt)), 2: req(text)}
	good := wire.Schema{1: positive(wire.Essential(wire.UInt)), 2: i32, 3: req(i32), 4: wire.Required(wire.Int), 5: wire.Essential(wire.Int), 6: text, 7: i32}
	stats := wire.Schema{}
	for n := uint32(1); n <= 7; n++ {
		stats[n] = wire.Essential(wire.Int)
	}
	entry := wire.Schema{1: wire.Required(wire.Int), 2: wire.Required(wire.String), 3: req(text)}
	quest := wire.Schema{1: req(i32), 2: req(i32), 3: req(text), 4: wire.Required(wire.String)}
	skill := wire.Schema{1: req(i32), 2: req(i32), 3: wire.Required(wire.Bool), 4: wire.Required(wire.Bool)}
	use := wire.Schema{1: req(i32), 2: req(i32)}
	lesson := wire.Schema{1: positive(wire.Required(wire.UInt)), 2: req(i32), 3: wire.Required(wire.Int), 4: req(i32), 5: wire.Required(wire.Int)}
	entries := list(entry)
	entries.Essential = false
	payload := wire.Schema{
		10: ref, 11: wire.Essential(wire.String), 12: text, 13: position, 14: position, 15: u31, 16: ref, 17: i32,
		18: wire.Essential(wire.Bool), 19: wire.Essential(wire.Bool), 20: text, 21: list(option), 22: i32, 23: wire.Essential(wire.Int),
		24: list(good), 25: obj(stats), 26: wire.Optional(wire.String), 27: wire.Essential(wire.Int), 28: entries,
		29: list(quest), 30: list(skill), 31: wire.Essential(wire.Bool), 32: positive(wire.Essential(wire.UInt)), 33: positive(wire.Essential(wire.UInt)), 34: list(lesson),
	}
	for n, s := range payload {
		r[wire.Fact][n] = s
	}
	commands := wire.Schema{
		10: ref, 11: position, 12: wire.Essential(wire.UInt), 13: wire.Essential(wire.UInt), 14: i32, 15: wire.Essential(wire.UInt),
		16: wire.Essential(wire.Int), 17: wire.Essential(wire.Int), 18: obj(use), 19: wire.Essential(wire.Bool), 20: ref,
	}
	for n, s := range commands {
		r[wire.Intent][n] = s
	}
	return r
}

// Ref pairs a backend ID with a monotonically allocated observation lifetime.
type Ref struct {
	// ID is the backend object identifier.
	ID int32
	// Gen is the observation lifetime; zero supplies no live reference.
	Gen uint64
}

func refValue(r Ref) wire.Value {
	return wire.Value{Fields: []wire.Field{wire.S(1, true, int64(r.ID)), wire.U(2, true, r.Gen)}}
}
func getU(f []wire.Field, n uint32) uint64      { v, _ := wire.Find(f, n); return v.U }
func getS(f []wire.Field, n uint32) int64       { v, _ := wire.Find(f, n); return v.S }
func getString(f []wire.Field, n uint32) string { v, _ := wire.Find(f, n); return string(v.Data) }
func getRef(f []wire.Field, n uint32) Ref {
	v, _ := wire.Find(f, n)
	return Ref{int32(getS(v.Fields, 1)), getU(v.Fields, 2)}
}
func textValue(s string, origin wire.Provenance) wire.Value {
	return wire.Value{Fields: []wire.Field{wire.Data(1, true, s), wire.U(2, true, uint64(origin))}}
}
func textField(n uint32, e bool, s string, origin wire.Provenance) wire.Field {
	return wire.Obj(n, e, textValue(s, origin).Fields)
}
func stringText(f []wire.Field, n uint32) string {
	v, _ := wire.Find(f, n)
	return getString(v.Fields, 1)
}
func boolean(v bool) uint64 {
	if v {
		return 1
	}
	return 0
}

// Effect is a resolved action, not a backend delivery receipt.
type Effect struct {
	// Requested is the original participant verb code.
	Requested uint64
	// Effective is the permission-resolved verb code.
	Effective uint64
	// Class is the resolved evidence class.
	Class string
	// Waiting requires evidence after delivery.
	Waiting bool
	// ConfirmSelection requires current selection before the effect write.
	ConfirmSelection bool
}

// Resolve runs before class reservation. It changes permission, not packet bytes.
func Resolve(verb uint64, capability Capabilities, force bool) (Effect, wire.Reason) {
	v, ok := VerbByCode(verb)
	if !ok {
		return Effect{}, wire.ReasonNoAction
	}
	e := Effect{Requested: verb, Effective: verb, Class: v.Class, Waiting: v.Waiting}
	hostile := capability.Strike
	peaceful := capability.Talk
	switch v.Command {
	case Engage:
		if !hostile && !peaceful {
			return Effect{}, wire.ReasonNoAction
		}
		e.ConfirmSelection = true
		if peaceful && !force {
			e.Effective = 5
			e.Class = "dialog"
			e.Waiting = true
		} else {
			e.Effective = 3
			e.Class = ""
			e.Waiting = false
		}
	case Strike:
		if !hostile && !(peaceful && force) {
			return Effect{}, wire.ReasonNoAction
		}
	case Talk:
		if !peaceful {
			return Effect{}, wire.ReasonNoAction
		}
	case Take:
		if !capability.Pickup {
			return Effect{}, wire.ReasonNoAction
		}
	case Cast:
		if !capability.Cast {
			return Effect{}, wire.ReasonNoAction
		}
		e.ConfirmSelection = true
	}
	return e, wire.ReasonNone
}
