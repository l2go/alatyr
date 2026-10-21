// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

// Package wire implements bounded, extensible binary frames.
package wire

import "errors"

const (
	Ping       uint64 = 1
	Hello      uint64 = 2
	Pong       uint64 = 3
	Welcome    uint64 = 4
	Checkpoint uint64 = 6
	Fact       uint64 = 8
	Intent     uint64 = 10
	Result     uint64 = 12
	Control    uint64 = 14
	Bye        uint64 = 16
)

type Stage uint64

const (
	Sent Stage = iota + 1
	Done
	Refused
	Unknown
	Unrecoverable
)

type Reason uint64

const (
	None Reason = iota
	NotReady
	Watch
	StaleControl
	StaleTarget
	StaleOffer
	QueueExpired
	Rate
	Busy
	UnknownEssential
	Invalid
	NoAction
	NotAlive
	SkillUnknown
	SkillPassive
	SkillDisabled
	World
	Timeout
	WorldClosed
	Interrupted
	TooOld
	OutOfOrder
	DuplicateMismatch
	TargetUnconfirmed
	BackendError
)

type Delivery uint64

const (
	NoDelivery Delivery = iota
	Local
	Backend
	Partial
)

type CloseReason uint64

const (
	Normal CloseReason = iota
	Unsupported
	FrameError
	SlowConsumer
	Full
	Auth
	Origin
	Internal
	ClosedWorld
)

const (
	Person       uint64 = 1
	Bot          uint64 = 2
	Agent        uint64 = 3
	Act          uint64 = 1
	Observe      uint64 = 2
	Begin        uint64 = 1
	End          uint64 = 2
	TakeControl  uint64 = 1
	Granted      uint64 = 2
	Lost         uint64 = 3
	Resync       uint64 = 4
	Authored     uint64 = 1
	System       uint64 = 2
	Player       uint64 = 3
	Unclassified uint64 = 4
)

var (
	ErrFrame       = errors.New("invalid frame")
	ErrUnsupported = errors.New("unsupported essential meaning")
	ErrConfig      = errors.New("invalid limits or schema")
)

type missingRequired struct{ depth int }

func (e *missingRequired) Error() string { return "missing required field" }

type Type uint8

const (
	Unsigned Type = iota
	Signed
	LengthDelimited
	Fixed64
)

type Shape uint8

const (
	UInt Shape = iota
	Int
	Bool
	String
	Identifier
	Bytes
	Object
	List
	Position
	Language
	Fixed
)

// Limits are supplied by a profile. The format itself has no deployment defaults.
type Limits struct {
	FrameBytes, StringBytes, ListItems, Depth, Records, Fields int
	IdentifierBytes, WorldBytes, ClientBytes                   int
}

type Spec struct {
	Shape               Shape
	Essential, Required bool
	Nonzero             bool
	Bits                int
	MaxBytes            int
	Enum                []uint64
	Object              Schema
	Element             *Spec
}
type Schema map[uint32]Spec
type Registry map[uint64]Schema

// Value preserves field presence through the containing Fields slice.
// Empty lists and empty objects remain present even when their slice is empty.
type Value struct {
	U      uint64
	S      int64
	Data   []byte
	Fields []Field
	Items  []Value
	XYZ    [3]int32
	Fixed  [8]byte
}
type Field struct {
	Number    uint32
	Essential bool
	Type      Type
	Value     Value
}
type Record struct {
	Type        uint64
	Fields      []Field
	Unsupported bool
	// EncodedBytes is receive-side metadata, including skipped fields. It is not
	// serialized, and lets admission bound a record before retaining its payload.
	EncodedBytes int
	// UnsupportedHash identifies an opaque unsupported request without retaining
	// skipped extension buffers. This local metadata is never serialized.
	UnsupportedHash [32]byte
}

func Find(fields []Field, n uint32) (Value, bool) {
	for _, f := range fields {
		if f.Number == n {
			return f.Value, true
		}
	}
	return Value{}, false
}
func U(n uint32, e bool, x uint64) Field { return Field{n, e, Unsigned, Value{U: x}} }
func S(n uint32, e bool, x int64) Field  { return Field{n, e, Signed, Value{S: x}} }
func Data(n uint32, e bool, x string) Field {
	return Field{n, e, LengthDelimited, Value{Data: []byte(x)}}
}
func Obj(n uint32, e bool, x []Field) Field   { return Field{n, e, LengthDelimited, Value{Fields: x}} }
func Items(n uint32, e bool, x []Value) Field { return Field{n, e, LengthDelimited, Value{Items: x}} }
func At(n uint32, e bool, x, y, z int32) Field {
	return Field{n, e, LengthDelimited, Value{XYZ: [3]int32{x, y, z}}}
}

func Required(shape Shape) Spec  { return Spec{Shape: shape, Essential: true, Required: true} }
func Essential(shape Shape) Spec { return Spec{Shape: shape, Essential: true} }
func Optional(shape Shape) Spec  { return Spec{Shape: shape} }

func TextSchema() Schema {
	return Schema{1: Required(String), 2: {Shape: UInt, Essential: true, Required: true, Enum: []uint64{Authored, System, Player, Unclassified}}, 3: Optional(Language)}
}

// Envelopes leaves profile payloads and advertised limits to the caller.
func Envelopes(advertised Schema, bounds Limits) Registry {
	enum := func(values ...uint64) Spec { s := Required(UInt); s.Enum = values; return s }
	text := Optional(Object)
	text.Object = TextSchema()
	ident := Required(Identifier)
	world, client := ident, Optional(String)
	world.MaxBytes = bounds.WorldBytes
	client.MaxBytes = bounds.ClientBytes
	limits := Required(Object)
	limits.Object = advertised
	domains := Essential(List)
	el := Required(Identifier)
	domains.Element = &el
	return Registry{
		Ping: {1: Required(UInt)}, Pong: {1: Required(UInt)},
		Hello:      {1: Required(UInt), 2: Required(UInt), 3: ident, 4: enum(Person, Bot, Agent), 5: enum(Act, Observe), 6: client},
		Welcome:    {1: Required(UInt), 2: ident, 3: world, 4: Required(UInt), 5: enum(Act, Observe), 6: Required(UInt), 7: limits},
		Checkpoint: {1: enum(Begin, End), 2: Required(UInt), 3: Required(Bool), 4: domains},
		Fact:       {1: Required(UInt), 2: Required(UInt), 3: Required(Int), 4: Required(UInt), 5: ident, 6: Required(Bool)},
		Intent:     {1: Required(UInt), 2: Required(UInt), 3: Required(UInt), 4: Essential(UInt)},
		Result:     {1: Required(UInt), 2: enum(1, 2, 3, 4, 5), 3: enum(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24), 4: text, 5: Required(UInt), 6: Required(UInt), 7: enum(0, 1, 2, 3)},
		Control:    {1: enum(TakeControl, Granted, Lost, Resync), 2: Essential(UInt), 3: Essential(UInt)},
		Bye:        {1: enum(0, 1, 2, 3, 4, 5, 6, 7, 8), 2: text},
	}
}

// UnsupportedRecord preserves only the validated numeric envelope kind, so a
// transport can close an unsupported close record without a recursive reply.
type UnsupportedRecord struct{ Type uint64 }

func (e *UnsupportedRecord) Error() string { return ErrUnsupported.Error() }
func (e *UnsupportedRecord) Unwrap() error { return ErrUnsupported }
