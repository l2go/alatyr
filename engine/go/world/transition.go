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

// IntentID is assigned by the world for commands. Session numbering
// and replay protection are responsibilities of the host, not this module.
// Enter uses a zero Owner because its incarnation does not exist at admission.
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
// Execute admits the deadline on the owning loop. Legacy calls have no
// deadline and must not be mistaken for replay-safe intents. A zero Deadline
// means no deadline.
type Intent struct {
	ID       IntentID
	Verb     Verb
	Deadline Time
}

// Stage describes in-memory resolution. The zero value is StageUnknown.
type Stage uint8

const (
	// StageUnknown means the intent has no proven terminal outcome.
	StageUnknown Stage = iota
	// StageDone means the promised in-memory transition was committed.
	StageDone
	// StageRefused means the intent was rejected without execution.
	StageRefused
	// StageExpired means the deadline prevented execution.
	StageExpired
	// StageCanceled means the intent was canceled.
	StageCanceled
	// StageYielded means the intent relinquished execution to another intent.
	StageYielded
)

// Outcome describes a committed in-memory outcome, not storage durability.
// A zero Intent means there is no intent outcome; a nonzero Intent with StageUnknown
// means that particular intent has no proven terminal outcome.
// Reason is nonzero only for a source-authored pre-execution refusal.
type Outcome struct {
	Intent IntentID
	Stage  Stage
	Reason Reason
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

// Amount is a closed, comparable resource quantity. The zero value is exact
// integer zero. ResourceItem, ResourceSP and ResourceXP use integers;
// ResourceHP, ResourceMP and ResourceCP use continuous amounts.
type Amount struct {
	continuous bool
	integer    int64
	real       float64
}

// IntegerAmount constructs an exact quantity without floating-point conversion.
func IntegerAmount(n int64) Amount { return Amount{integer: n} }

// ContinuousAmount constructs a continuous quantity, including continuous zero.
func ContinuousAmount(n float64) Amount { return Amount{continuous: true, real: n} }

// Integer returns the exact quantity and whether the amount is integer.
func (a Amount) Integer() (int64, bool) { return a.integer, !a.continuous }

// Continuous returns the quantity and whether the amount is continuous.
func (a Amount) Continuous() (float64, bool) { return a.real, a.continuous }

// Balance records resource amounts before and after a transition. The amounts
// have the same kind as the transfer. A world source or sink has a zero balance
// of that kind; it is not a measured participant balance.
type Balance struct {
	Before, After Amount
}

// ResourceTransferred records one quantity and balances in the resource's
// numeric kind. Consumers can inspect that kind without inferring populated
// fields or converting exact integer balances to floating point.
type ResourceTransferred struct {
	Resource               Resource
	From, To               ResourceLocation
	Amount                 Amount
	FromBalance, ToBalance Balance
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
// A zero Subject identifies the world itself, for example its game clock.
type Fact struct {
	Subject       Ref
	Property      Property
	Before, After Value
}

// Value is a closed, comparable property value. Its zero value is integer zero.
// Property determines the expected kind; mismatched kinds are not converted.
type Value struct {
	kind     valueKind
	integer  int64
	ref      Ref
	label    string
	point    Point
	time     Time
	unsigned uint64
}

type valueKind uint8

const (
	integerValue valueKind = iota
	refValue
	labelValue
	pointValue
	timeValue
	unsignedValue
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

// UintValue constructs a monotonic identity without a signed conversion.
func UintValue(n uint64) Value { return Value{kind: unsignedValue, unsigned: n} }

// Unsigned returns the identity and whether the value is unsigned.
func (v Value) Unsigned() (uint64, bool) { return v.unsigned, v.kind == unsignedValue }

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
// Only the command that opens Intent may fix Outcome, with that unchanged ID.
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
// identity, target, parameters, costs, issue time and expiry are world-assigned.
// Rendering text is not an authorization to execute a choice.
type Affordance struct {
	ID      uint64
	Verb    Verb
	Target  Ref
	Params  []Parameter
	Cost    []Cost
	Issued  Time
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

func (s *state) source(cause Cause, f func()) Commit {
	if s.commitSeq == ^uint64(0) {
		s.err = ErrExhausted
		return Commit{}
	}
	previous := s.batch
	if previous != nil {
		old := previous.cause
		previous.cause = cause
		defer func() { previous.cause = old }()
		f()
		return Commit{}
	}
	s.batch = &commitBatch{cause: cause}
	f()
	b := s.batch
	s.batch = previous
	if len(b.groups) == 0 && b.outcome.Intent == (IntentID{}) {
		return Commit{}
	}
	s.commitSeq++
	c := Commit{Sequence: s.commitSeq, Time: s.now(), Intent: b.intent, Outcome: b.outcome, Groups: b.groups}
	if s.observe != nil {
		s.observe(cloneCommit(c))
	}
	return c
}
func (s *state) command(owner Ref, verb Verb, f func()) {
	s.commandUntil(owner, verb, 0, f)
}

func (s *state) commandUntil(owner Ref, verb Verb, deadline Time, f func()) Commit {
	if s.commandSeq == ^uint64(0) {
		s.err = ErrExhausted
		return Commit{}
	}
	s.commandSeq++
	return s.source(Cause{Intent: IntentID{Owner: owner, N: s.commandSeq}}, func() {
		s.batch.intent = Intent{ID: s.cause().Intent, Verb: verb, Deadline: deadline}
		if deadline > 0 && s.now() >= deadline {
			s.finish(StageExpired)
		} else {
			f()
		}
		if s.batch.outcome.Intent == (IntentID{}) {
			s.finish(StageUnknown)
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
	s.resolve(stage, "")
}

func (s *state) resolve(stage Stage, reason Reason) {
	if s.batch == nil || s.batch.intent.ID.N == 0 || s.batch.cause.Intent != s.batch.intent.ID || s.batch.outcome.Intent != (IntentID{}) {
		return
	}
	s.batch.outcome = Outcome{Intent: s.batch.intent.ID, Stage: stage, Reason: reason}
}
func cloneCommit(c Commit) Commit {
	c.Groups = slices.Clone(c.Groups)
	for i := range c.Groups {
		c.Groups[i].Effects = slices.Clone(c.Groups[i].Effects)
		c.Groups[i].Facts = slices.Clone(c.Groups[i].Facts)
	}
	return c
}
func (s *state) itemTransfer(p *player, item Ref, resource Resource, from ResourceLocation, quantity, before, after int64) {
	s.group([]Effect{ResourceTransferred{Resource: resource, From: from, To: ResourceLocation{Entity: p.ref(), Item: item, Place: PlaceInventory}, Amount: IntegerAmount(quantity),
		ToBalance: Balance{Before: IntegerAmount(before), After: IntegerAmount(after)}}})
}
func (s *state) resourceChange(entity Ref, kind ResourceKind, before, after float64) {
	if before == after {
		return
	}
	e := ResourceTransferred{Resource: Resource{Kind: kind}, Amount: ContinuousAmount(after - before),
		From: ResourceLocation{Place: PlaceWorld}, To: ResourceLocation{Entity: entity, Place: PlaceCharacter},
		FromBalance: Balance{Before: ContinuousAmount(0), After: ContinuousAmount(0)},
		ToBalance:   Balance{Before: ContinuousAmount(before), After: ContinuousAmount(after)}}
	if after < before {
		e.Amount = ContinuousAmount(before - after)
		e.From, e.To = e.To, e.From
		e.FromBalance, e.ToBalance = e.ToBalance, e.FromBalance
	}
	s.group([]Effect{e})
}
