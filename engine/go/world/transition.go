// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"time"
)

// Ref identifies an incarnation, not a connection or relocation.
type Ref struct {
	ID  int32
	Gen uint64
}

// Time is elapsed monotonic world time in nanoseconds. It is not UTC.
type Time time.Duration

// IntentID is assigned by the world for legacy calls. Session numbering
// and replay protection are responsibilities of the host, not this module.
type IntentID struct {
	Owner Ref
	N     uint64
}

// Cause has exactly one source: a command or a named world rule.
// These records are for a trusted host. A recipient projection must redact
// causes and references outside that recipient's observable scene.
type Cause struct {
	Intent IntentID
	Rule   Rule
}

// Intent carries an optional deadline in the world's monotonic scale.
// Admission is part of the subsequent command surface. Legacy calls have no
// deadline and must not be mistaken for replay-safe intents. A zero Deadline
// means no deadline.
type Intent struct {
	ID       IntentID
	Verb     Verb
	Deadline Time
}

// Stage describes in-memory resolution. The zero value is Unknown.
type Stage uint8

const (
	// Unknown means the intent has no proven terminal outcome.
	Unknown Stage = iota
	// Done means the promised in-memory transition was committed.
	Done
	// Refused means the intent was rejected without execution.
	Refused
	// Expired means the deadline prevented execution.
	Expired
	// Canceled means the intent was canceled.
	Canceled
	// Yielded means the intent relinquished execution to another intent.
	Yielded
)

// Outcome describes a committed in-memory outcome, not storage durability.
// A zero Intent means there is no intent outcome; a nonzero Intent with Unknown
// means that particular intent has no proven terminal outcome.
type Outcome struct {
	Intent IntentID
	Stage  Stage
}

// Effect is a member of the closed outcome kernel. A group is ordered and must
// be applied in full or not at all; an unknown essential subject or direction
// makes the affected state incomplete.
type Effect interface{ effect() }

// ExistenceChanged records creation or removal of an incarnation.
type ExistenceChanged struct {
	Entity        Ref
	Before, After bool
}

func (ExistenceChanged) effect() {}

// CapabilityChanged records a skill level or an enabled capability.
type CapabilityChanged struct {
	Entity        Ref
	Capability    Capability
	ID            int32
	Before, After int32
}

func (CapabilityChanged) effect() {}

// ResourceLocation identifies where a resource resides. A zero entity with a
// named Place is a world source or sink, never an invented participant.
type ResourceLocation struct {
	Entity Ref
	// Item identifies the concrete inventory row for an item resource.
	Item  Ref
	Place Place
}

// Resource identifies a kind and, for items, an authored template.
type Resource struct {
	Kind       ResourceKind
	TemplateID int32
}

// ResourceTransferred records an exact integer quantity or a continuous amount.
// Integer balances use Quantity fields without floating-point conversion.
// Continuous balances use Amount and the non-Quantity balance fields instead.
type ResourceTransferred struct {
	Resource                                                                 Resource
	From, To                                                                 ResourceLocation
	Amount                                                                   float64
	Quantity                                                                 int64
	FromBefore, FromAfter, ToBefore, ToAfter                                 float64
	FromQuantityBefore, FromQuantityAfter, ToQuantityBefore, ToQuantityAfter int64
}

func (ResourceTransferred) effect() {}

// HarmApplied records applied harm, not a calculated or announced attack.
type HarmApplied struct {
	Source, Target Ref
	Resource       ResourceKind
	Before, After  float64
}

func (HarmApplied) effect() {}

// SanctionChanged records a world-imposed restriction.
type SanctionChanged struct {
	Entity        Ref
	Kind          SanctionKind
	Before, After bool
}

func (SanctionChanged) effect() {}

// ObligationChanged records an obligation's lifecycle state.
type ObligationChanged struct {
	Entity        Ref
	ID            uint64
	Before, After ObligationState
}

func (ObligationChanged) effect() {}

// SpeechCompleted records a complete authored utterance to its listener.
// Text is presentation, not an authorization or executable command.
type SpeechCompleted struct {
	Speaker, Listener Ref
	Text              string
}

func (SpeechCompleted) effect() {}

// PresenceChanged records the start or end of a presence channel.
// The current world does not implement media presence.
type PresenceChanged struct {
	Entity        Ref
	Before, After bool
}

func (PresenceChanged) effect() {}

// Fact records a source-authored observation, not an outcome inferred from a
// refresh notification. Ordinary state (position, posture, target, zone) is
// separate from the kernel effects used to account for resources and harm.
type Fact struct {
	Subject       Ref
	Property      Property
	Before, After Value
}

// Value is a closed, comparable property value. Its zero value is integer zero.
// Property determines the expected kind; mismatched kinds are not converted.
type Value struct {
	kind    valueKind
	integer int64
	ref     Ref
	label   string
	point   Point
	time    Time
}

type valueKind uint8

const (
	integerValue valueKind = iota
	refValue
	labelValue
	pointValue
	timeValue
)

// IntValue constructs an exact integer value, including boolean facts as 0/1.
func IntValue(n int64) Value { return Value{integer: n} }

// RefValue constructs an incarnation reference value.
func RefValue(r Ref) Value { return Value{kind: refValue, ref: r} }

// LabelValue constructs a world-defined label, not executable text.
func LabelValue(s string) Value { return Value{kind: labelValue, label: s} }

// PointValue constructs an authoritative world position.
func PointValue(p Point) Value { return Value{kind: pointValue, point: p} }

// TimeValue constructs a monotonic world-time value.
func TimeValue(t Time) Value { return Value{kind: timeValue, time: t} }

// Integer returns the exact integer and whether the value is an integer.
func (v Value) Integer() (int64, bool) { return v.integer, v.kind == integerValue }

// Ref returns the reference and whether the value is a reference.
func (v Value) Ref() (Ref, bool) { return v.ref, v.kind == refValue }

// Label returns the label and whether the value is a label.
func (v Value) Label() (string, bool) { return v.label, v.kind == labelValue }

// Point returns the position and whether the value is a position.
func (v Value) Point() (Point, bool) { return v.point, v.kind == pointValue }

// Time returns the world time and whether the value is a time.
func (v Value) Time() (Time, bool) { return v.time, v.kind == timeValue }

func boolValue(b bool) Value {
	if b {
		return IntValue(1)
	}
	return IntValue(0)
}

// setBool keeps the recorded before value coupled to the assigned state.
func (s *state) setBool(entity Ref, property Property, field *bool, next bool) {
	before := *field
	*field = next
	s.change(entity, property, boolValue(before), boolValue(next))
}

func (s *state) setAttacking(c *creature, next int32) {
	before := c.attacking
	c.attacking = next
	s.change(c.ref(), PropertyAttacking, boolValue(before != 0), boolValue(next != 0))
}

// EffectGroup is an ordered, indivisible set of effects and source observations.
type EffectGroup struct {
	Cause   Cause
	Effects []Effect
	Facts   []Fact
}

// Commit is one publication from the owning loop. Each group has its own
// source cause. Sequence establishes order even when world timestamps coincide.
// A zero Outcome.Intent means there is no intent outcome. Intent describes the
// originating operation when this commit fixes its outcome; ambient groups may
// carry a prior IntentID as their cause without fixing that outcome again.
type Commit struct {
	Sequence uint64
	Time     Time
	Intent   Intent
	Outcome  Outcome
	Groups   []EffectGroup
}

// Durability is a separate signal for an external write. It cannot rewrite a
// committed Commit. Err describes only the write, not an in-memory rollback.
type Durability struct {
	Intent IntentID
	Time   Time
	Err    error
}

// Affordance is the common proposal form for dialogue and learning. Its
// identity, target, parameters, costs and expiry are assigned by the world.
// Rendering text is not an authorization to execute a choice.
type Affordance struct {
	ID      uint64
	Verb    Verb
	Target  Ref
	Params  []Parameter
	Cost    []Cost
	Expires Time
}

// Parameter is one named, world-provided affordance parameter.
type Parameter struct {
	Name  string
	Value Value
}

// Cost is an exact resource quantity required by an affordance.
type Cost struct {
	Resource Resource
	Quantity int64
}

type commitBatch struct {
	cause   Cause
	intent  Intent
	groups  []EffectGroup
	outcome Outcome
}

func (c *creature) ref() Ref   { return Ref{ID: c.id, Gen: c.generation} }
func (g *groundItem) ref() Ref { return Ref{ID: g.id, Gen: g.generation} }
func (s *state) ref(id int32) Ref {
	if a, ok := s.actor(id); ok {
		return a.base().ref()
	}
	if g, ok := s.ground[id]; ok {
		return g.ref()
	}
	for _, p := range s.players {
		if r := p.itemRefs[id]; r != (Ref{}) {
			return r
		}
	}
	return Ref{}
}
func (s *state) incarnation() uint64 {
	if s.generation == ^uint64(0) {
		s.err = ErrExhausted
		return 0
	}
	s.generation++
	return s.generation
}
func (s *state) now() Time { return Time(time.Since(s.started)) }

func (s *state) at(t time.Time) Time {
	if t.IsZero() {
		return 0
	}
	return Time(t.Sub(s.started))
}

func (s *state) source(cause Cause, f func()) {
	if s.commitSeq == ^uint64(0) {
		s.err = ErrExhausted
		return
	}
	previous := s.batch
	if previous != nil {
		old := previous.cause
		previous.cause = cause
		defer func() { previous.cause = old }()
		f()
		return
	}
	s.batch = &commitBatch{cause: cause}
	f()
	b := s.batch
	s.batch = previous
	if len(b.groups) == 0 && b.outcome.Intent == (IntentID{}) {
		return
	}
	s.commitSeq++
	t := Commit{Sequence: s.commitSeq, Time: s.now(), Intent: b.intent, Outcome: b.outcome, Groups: b.groups}
	if s.observe != nil {
		s.observe(cloneCommit(t))
	}
}
func (s *state) command(owner Ref, verb Verb, f func()) {
	if s.commandSeq == ^uint64(0) {
		s.err = ErrExhausted
		return
	}
	s.commandSeq++
	s.source(Cause{Intent: IntentID{Owner: owner, N: s.commandSeq}}, func() {
		s.batch.intent = Intent{ID: s.cause().Intent, Verb: verb}
		f()
		if s.batch.outcome.Intent == (IntentID{}) {
			s.finish(Unknown)
		}
	})
}
func (s *state) rule(name Rule, f func()) { s.source(Cause{Rule: name}, f) }
func (s *state) cause() Cause {
	if s.batch != nil {
		return s.batch.cause
	}
	return Cause{Rule: RuleHost}
}
func (s *state) group(effects []Effect, changes ...Fact) {
	if len(effects) == 0 && len(changes) == 0 {
		return
	}
	if s.batch == nil {
		s.rule(RuleHost, func() { s.group(effects, changes...) })
		return
	}
	s.batch.groups = append(s.batch.groups, EffectGroup{Cause: s.batch.cause, Effects: slices.Clone(effects), Facts: slices.Clone(changes)})
}
func (s *state) change(entity Ref, property Property, before, after Value) {
	if before != after {
		s.group(nil, Fact{Subject: entity, Property: property, Before: before, After: after})
	}
}
func (s *state) finish(stage Stage) {
	if s.batch != nil && s.batch.cause.Intent.N != 0 && s.batch.outcome.Intent == (IntentID{}) {
		s.batch.intent.ID = s.batch.cause.Intent
		s.batch.outcome = Outcome{Intent: s.batch.cause.Intent, Stage: stage}
	}
}
func cloneCommit(t Commit) Commit {
	t.Groups = slices.Clone(t.Groups)
	for i := range t.Groups {
		t.Groups[i].Effects = slices.Clone(t.Groups[i].Effects)
		t.Groups[i].Facts = slices.Clone(t.Groups[i].Facts)
	}
	return t
}
func (s *state) itemTransfer(p *player, item Ref, resource Resource, from ResourceLocation, quantity, before, after int64) {
	s.group([]Effect{ResourceTransferred{Resource: resource, From: from, To: ResourceLocation{Entity: p.ref(), Item: item, Place: PlaceInventory}, Quantity: quantity,
		ToQuantityBefore: before, ToQuantityAfter: after}})
}
func (s *state) resourceChange(entity Ref, kind ResourceKind, before, after float64) {
	if before == after {
		return
	}
	e := ResourceTransferred{Resource: Resource{Kind: kind}, Amount: after - before,
		From: ResourceLocation{Place: PlaceWorld}, To: ResourceLocation{Entity: entity, Place: PlaceCharacter}, ToBefore: before, ToAfter: after}
	if after < before {
		e.Amount = before - after
		e.From, e.To = e.To, e.From
		e.FromBefore, e.FromAfter, e.ToBefore, e.ToAfter = before, after, 0, 0
	}
	s.group([]Effect{e})
}
