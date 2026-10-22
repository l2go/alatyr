// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"bytes"
	"cmp"
	"crypto/sha256"
	"errors"
	"math"
	"slices"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Errors returned by Session and Connection.
var (
	// ErrState reports an operation that is not valid in the current state.
	ErrState = errors.New("invalid state transition")
	// ErrExhausted reports a counter that reached its maximum; the session is closed.
	ErrExhausted = errors.New("counter exhausted")
)

// Config bounds a Session. Results is the number of retained outcomes, Pending
// the number of unfinished attempts, IntentBytes the largest retained payload;
// EvidenceMS is how long the world may take to witness an effect after contact,
// QueueMS the default time an attempt may wait before contact. Classes names
// the exclusive attempt classes.
type Config struct {
	Results, Pending, IntentBytes int
	EvidenceMS, QueueMS           int64
	Classes                       []string
}

// Outcome is the result of attempt N.
type Outcome struct {
	N                    uint64
	Stage                wire.Stage
	Reason               wire.Reason
	Requested, Effective uint64
	Delivery             wire.Delivery
}

// Record returns o as a Result record.
func (o Outcome) Record() wire.Record {
	return wire.Record{Type: wire.Result, Fields: []wire.Field{
		wire.U(1, true, o.N), wire.U(2, true, uint64(o.Stage)), wire.U(3, true, uint64(o.Reason)), wire.U(5, true, o.Requested), wire.U(6, true, o.Effective), wire.U(7, true, uint64(o.Delivery)),
	}}
}

// Attempt is one participant request: number N under control epoch Control,
// verb Verb and its payload. QueueMS, when set, overrides Config.QueueMS.
type Attempt struct {
	N, Control, Verb uint64
	Payload          []byte
	QueueMS          *int64
	EncodedBytes     int
}

// Entry is the session's record of one attempt, as returned by Lookup.
type Entry struct {
	Attempt                          Attempt
	Owner, Projection, Effective     uint64
	Queued, Deadline                 int64
	Class                            string
	Waiting, Contact, Sent, Terminal bool
	Result                           *Outcome
	Oversized                        bool
	Digest                           [32]byte
}
type classState struct {
	open    uint64
	tainted bool
}

// Session holds the attempt state of one world session: control epochs,
// attempt numbers, the projection epoch and retained outcomes. The caller
// serializes calls; a Session starts no goroutines or timers.
type Session struct {
	config                                       Config
	next, floor, control, controller, projection uint64
	entries                                      []Entry
	classes                                      map[string]classState
	closed                                       bool
}

// Admission is the answer to Begin or Reserve. Allocated reports that the
// attempt took a new number; Duplicate that it repeats a known attempt. Result,
// when set, is the outcome to send; Close, when set, closes the connection.
type Admission struct {
	Allocated, Duplicate bool
	Result               *Outcome
	Close                *wire.CloseReason
}

// Grant is a control grant: the control epoch and the next attempt number.
type Grant struct{ Epoch, NextN uint64 }

// New returns a Session for c, or wire.ErrConfig when c is not valid.
func New(c Config) (*Session, error) {
	if c.Results < 1 || c.Pending < 1 || c.Pending >= c.Results-1 || c.IntentBytes < 1 || c.EvidenceMS <= 0 || c.QueueMS < 0 {
		return nil, wire.ErrConfig
	}
	classes := make(map[string]classState, len(c.Classes))
	for _, name := range c.Classes {
		if name == "" {
			return nil, wire.ErrConfig
		}
		if _, exists := classes[name]; exists {
			return nil, wire.ErrConfig
		}
		classes[name] = classState{}
	}
	c.Classes = slices.Clone(c.Classes)
	return &Session{config: c, next: 1, floor: 1, projection: 1, classes: classes}, nil
}

// NextN returns the next attempt number the controller may use.
func (s *Session) NextN() uint64 { return s.next }

// Floor returns the lowest attempt number whose outcome is still retained.
func (s *Session) Floor() uint64 { return s.floor }

// Projection returns the current projection epoch.
func (s *Session) Projection() uint64 { return s.projection }

// Controller returns the owner holding control, 0 if none, and the control epoch.
func (s *Session) Controller() (owner, epoch uint64) { return s.controller, s.control }

func (s *Session) bump(v *uint64) error {
	if *v == math.MaxUint64 {
		s.closed = true
		return ErrExhausted
	}
	*v++
	return nil
}

// Take gives control to owner and returns the grant. A new owner starts a new
// control epoch; the current owner gets its existing grant again.
func (s *Session) Take(owner uint64) (Grant, error) {
	if s.closed || owner == 0 {
		return Grant{}, ErrState
	}
	if s.controller != owner {
		if err := s.bump(&s.control); err != nil {
			return Grant{}, err
		}
		s.controller = owner
	}
	return Grant{s.control, s.next}, nil
}

// Lose takes control away from owner and starts a new control epoch. It does
// nothing when owner does not hold control.
func (s *Session) Lose(owner uint64) error {
	if s.closed {
		return ErrState
	}
	if s.controller != owner || owner == 0 {
		return nil
	}
	if err := s.bump(&s.control); err != nil {
		return err
	}
	s.controller = 0
	return nil
}
func (s *Session) index(n uint64) int {
	for i := range s.entries {
		if s.entries[i].Attempt.N == n {
			return i
		}
	}
	return -1
}

// Snapshot returns the retained outcomes in attempt order.
func (s *Session) Snapshot() []Outcome {
	var out []Outcome
	for _, e := range s.entries {
		if e.Result != nil {
			out = append(out, *e.Result)
		}
	}
	slices.SortFunc(out, func(a, b Outcome) int { return cmp.Compare(a.N, b.N) })
	return out
}

// Lookup returns a copy of the entry for attempt n and whether it exists.
func (s *Session) Lookup(n uint64) (Entry, bool) {
	i := s.index(n)
	if i < 0 {
		return Entry{}, false
	}
	e := s.entries[i]
	e.Attempt.Payload = bytes.Clone(e.Attempt.Payload)
	if e.Attempt.QueueMS != nil {
		v := *e.Attempt.QueueMS
		e.Attempt.QueueMS = &v
	}
	if e.Result != nil {
		v := *e.Result
		e.Result = &v
	}
	return e, true
}
func rejection(a Attempt, r wire.Reason) *Outcome {
	return &Outcome{N: a.N, Stage: wire.StageRefused, Reason: r, Requested: a.Verb}
}
func terminalClose(r wire.CloseReason) *wire.CloseReason { return &r }
func same(a, b Attempt) bool {
	return a.N == b.N && a.Control == b.Control && a.Verb == b.Verb && bytes.Equal(a.Payload, b.Payload) && ((a.QueueMS == nil && b.QueueMS == nil) || (a.QueueMS != nil && b.QueueMS != nil && *a.QueueMS == *b.QueueMS))
}

// Begin allocates only numbers from the current controller. Mode/control errors
// are connection-local and cannot consume another connection's next number.
func (s *Session) Begin(owner uint64, act, ready bool, a Attempt, now int64) Admission {
	return s.begin(owner, act, ready, a, now, true)
}

// Reserve performs only number/control/cache admission. The transport caller
// then charges its token bucket before readiness and payload/TTL validation.
func (s *Session) Reserve(owner uint64, act, ready bool, a Attempt, now int64) Admission {
	return s.begin(owner, act, ready, a, now, false)
}
func (s *Session) begin(owner uint64, act, ready bool, a Attempt, now int64, validate bool) Admission {
	if s.closed {
		return Admission{Close: terminalClose(wire.CloseWorldClosed)}
	}
	if a.N == 0 {
		return Admission{Close: terminalClose(wire.CloseFrameError)}
	}
	if i := s.index(a.N); i >= 0 {
		e := s.entries[i]
		candidate := a
		if e.Oversized {
			candidate.Payload = nil
		}
		if !same(e.Attempt, candidate) || (e.Oversized && e.Digest != sha256.Sum256(a.Payload)) {
			return Admission{Result: rejection(a, wire.ReasonDuplicateMismatch), Close: terminalClose(wire.CloseFrameError)}
		}
		var result *Outcome
		if e.Result != nil {
			v := *e.Result
			result = &v
		}
		return Admission{Duplicate: true, Result: result}
	}
	if a.N < s.floor {
		return Admission{Result: &Outcome{N: a.N, Stage: wire.StageUnrecoverable, Reason: wire.ReasonTooOld, Requested: a.Verb}}
	}
	if !act {
		return Admission{Result: rejection(a, wire.ReasonWatch)}
	}
	if s.controller == 0 && !ready {
		return Admission{Result: rejection(a, wire.ReasonNotReady)}
	}
	if owner == 0 || owner != s.controller || a.Control != s.control {
		return Admission{Result: rejection(a, wire.ReasonStaleControl)}
	}
	if a.N != s.next {
		return Admission{Result: rejection(a, wire.ReasonOutOfOrder), Close: terminalClose(wire.CloseFrameError)}
	}
	if s.next == math.MaxUint64 {
		s.closed = true
		return Admission{Close: terminalClose(wire.CloseInternal)}
	}
	if len(s.entries) >= s.config.Results {
		old := -1
		for i, e := range s.entries {
			if e.Terminal && (old < 0 || e.Attempt.N < s.entries[old].Attempt.N) {
				old = i
			}
		}
		if old < 0 {
			s.closed = true
			return Admission{Close: terminalClose(wire.CloseInternal)}
		}
		if v := s.entries[old].Attempt.N + 1; v > s.floor {
			s.floor = v
		}
		s.entries = slices.Delete(s.entries, old, old+1)
	}
	s.next++
	// Store only bounded request bytes. An oversized rejection is never executable;
	// retain a digest-sized identity instead of retaining its source buffer.
	if len(a.Payload) > s.config.IntentBytes {
		// An oversized input cannot originate from the bounded participant encoder.
		// Do not reserve an unbounded cache entry or leave a reusable allocated n.
		digest := sha256.Sum256(a.Payload)
		bounded := a
		bounded.Payload = nil
		if a.QueueMS != nil {
			v := *a.QueueMS
			bounded.QueueMS = &v
		}
		s.entries = append(s.entries, Entry{Attempt: bounded, Owner: owner, Projection: s.projection, Terminal: validate, Result: nil, Oversized: true, Digest: digest})
		if !validate {
			return Admission{Allocated: true}
		}
		s.entries[len(s.entries)-1].Result = rejection(a, wire.ReasonInvalid)
		return Admission{Allocated: true, Result: rejection(a, wire.ReasonInvalid)}
	}
	a.Payload = bytes.Clone(a.Payload)
	if a.QueueMS != nil {
		v := *a.QueueMS
		a.QueueMS = &v
	}
	s.entries = append(s.entries, Entry{Attempt: a, Owner: owner, Projection: s.projection, Queued: now})
	if validate && (a.EncodedBytes < 0 || a.EncodedBytes > s.config.IntentBytes) {
		o, _ := s.Refuse(a.N, wire.ReasonInvalid)
		return Admission{Allocated: true, Result: &o}
	}
	if validate && !ready {
		o, _ := s.Refuse(a.N, wire.ReasonNotReady)
		return Admission{Allocated: true, Result: &o}
	}
	if validate && a.QueueMS != nil && (*a.QueueMS < 0 || *a.QueueMS > s.config.QueueMS) {
		o, _ := s.Refuse(a.N, wire.ReasonInvalid)
		return Admission{Allocated: true, Result: &o}
	}
	return Admission{Allocated: true}
}

func (s *Session) finish(i int, o Outcome) Outcome {
	e := &s.entries[i]
	e.Result = &o
	e.Terminal = true
	if e.Class != "" {
		state := s.classes[e.Class]
		if state.open == e.Attempt.N {
			state.open = 0
		}
		if o.Stage == wire.StageUnknown {
			state.tainted = true
		}
		s.classes[e.Class] = state
	}
	return o
}

// Refuse finishes attempt n as refused. After contact with the world it
// finishes the attempt as unknown instead, through Fail.
func (s *Session) Refuse(n uint64, reason wire.Reason) (Outcome, error) {
	if reason < wire.ReasonNotReady || reason > wire.ReasonBackendError {
		return Outcome{}, ErrState
	}
	i := s.index(n)
	if i < 0 || s.entries[i].Terminal {
		return Outcome{}, ErrState
	}
	e := s.entries[i]
	if e.Contact {
		return s.Fail(n, reason)
	}
	o := *rejection(e.Attempt, reason)
	o.Effective = e.Effective
	return s.finish(i, o), nil
}

// Fail finishes attempt n as unknown after contact with the world. Before
// contact it refuses the attempt instead, through Refuse.
func (s *Session) Fail(n uint64, reason wire.Reason) (Outcome, error) {
	i := s.index(n)
	if i < 0 || s.entries[i].Terminal {
		return Outcome{}, ErrState
	}
	e := s.entries[i]
	if !e.Contact {
		return s.Refuse(n, reason)
	}
	delivery := wire.DeliveryPartial
	if e.Sent {
		delivery = wire.DeliveryBackend
	}
	switch reason {
	case wire.ReasonTimeout, wire.ReasonWorldClosed, wire.ReasonTargetUnconfirmed, wire.ReasonBackendError:
	default:
		reason = wire.ReasonInterrupted
	}
	return s.finish(i, Outcome{n, wire.StageUnknown, reason, e.Attempt.Verb, e.Effective, delivery}), nil
}

// Prepare stores the already-resolved effect. Class names come only from config.
func (s *Session) Prepare(n, effective uint64, class string, waiting bool) (*Outcome, error) {
	i := s.index(n)
	if i < 0 || s.entries[i].Terminal || s.entries[i].Effective != 0 || effective == 0 {
		return nil, ErrState
	}
	s.entries[i].Effective = effective
	if class != "" {
		state, known := s.classes[class]
		if !known {
			return nil, ErrState
		}
		if state.open != 0 {
			o, err := s.Refuse(n, wire.ReasonBusy)
			return &o, err
		}
	}
	live := 0
	for _, e := range s.entries {
		if !e.Terminal {
			live++
		}
	}
	if live > s.config.Pending {
		o, err := s.Refuse(n, wire.ReasonBusy)
		return &o, err
	}
	e := &s.entries[i]
	e.Effective = effective
	e.Class = class
	e.Waiting = waiting
	if class != "" {
		v := s.classes[class]
		v.open = n
		s.classes[class] = v
	}
	return nil, nil
}

// CheckWrite must be called immediately before each actual backend write.
// Reference/proposal validation is a caller-supplied dictionary responsibility.
func (s *Session) CheckWrite(n uint64, now int64) (*Outcome, error) {
	i := s.index(n)
	if i < 0 || s.closed || s.entries[i].Terminal || s.entries[i].Effective == 0 {
		return nil, ErrState
	}
	e := s.entries[i]
	reason := wire.ReasonNone
	if e.Owner != s.controller || e.Attempt.Control != s.control {
		reason = wire.ReasonStaleControl
	} else if e.Projection != s.projection {
		reason = wire.ReasonInterrupted
	} else if e.Contact && now >= e.Deadline {
		reason = wire.ReasonTimeout
	} else if !e.Contact {
		ttl := s.config.QueueMS
		if e.Attempt.QueueMS != nil {
			ttl = *e.Attempt.QueueMS
		}
		if now < e.Queued || now-e.Queued > ttl {
			reason = wire.ReasonQueueExpired
		}
	}
	if reason != wire.ReasonNone {
		o, err := s.Refuse(n, reason)
		return &o, err
	}
	return nil, nil
}

// Contact records that attempt n reached the world at now and starts its
// evidence window of Config.EvidenceMS.
func (s *Session) Contact(n uint64, now int64) error {
	i := s.index(n)
	if i < 0 || s.entries[i].Terminal || s.entries[i].Effective == 0 {
		return ErrState
	}
	e := &s.entries[i]
	if !e.Contact {
		if now < 0 || now > math.MaxInt64-s.config.EvidenceMS {
			s.closed = true
			return ErrExhausted
		}
		e.Contact = true
		e.Deadline = now + s.config.EvidenceMS
	}
	return nil
}

// Sent records that attempt n was delivered, locally or to the world, and
// returns its sent outcome. An attempt that does not wait for a witness is
// finished.
func (s *Session) Sent(n uint64, local bool) (Outcome, error) {
	i := s.index(n)
	if i < 0 || s.entries[i].Terminal || s.entries[i].Sent || s.entries[i].Effective == 0 {
		return Outcome{}, ErrState
	}
	e := &s.entries[i]
	if local && (e.Contact || e.Waiting || e.Class != "") {
		return Outcome{}, ErrState
	}
	if !local && !e.Contact {
		return Outcome{}, ErrState
	}
	delivery := wire.DeliveryBackend
	if local {
		delivery = wire.DeliveryLocal
	}
	o := Outcome{n, wire.StageSent, wire.ReasonNone, e.Attempt.Verb, e.Effective, delivery}
	e.Sent = true
	e.Result = &o
	if !e.Waiting {
		return s.finish(i, o), nil
	}
	return o, nil
}

// Taint marks class as unable to prove exclusive effects from now on.
func (s *Session) Taint(class string) error {
	v, ok := s.classes[class]
	if !ok {
		return ErrState
	}
	v.tainted = true
	s.classes[class] = v
	return nil
}

// Eligible reports whether attempt n may still be finished as done at now.
func (s *Session) Eligible(n uint64, now int64) bool {
	i := s.index(n)
	if i < 0 {
		return false
	}
	e := s.entries[i]
	state, ok := s.classes[e.Class]
	return !s.closed && !e.Terminal && e.Waiting && e.Contact && ok && state.open == n && !state.tainted && now >= e.Deadline-s.config.EvidenceMS && now < e.Deadline
}

// Done is called only with an exclusive, typed witness established by a backend.
func (s *Session) Done(n uint64, now int64) (Outcome, error) {
	return s.CommitWitness(n, now, now)
}

// CommitWitness can finalize a candidate at the end of its observation window.
// The witness itself must have arrived strictly before the deadline. Call this
// before Expire when a profile requires a full-window exclusion check.
func (s *Session) CommitWitness(n uint64, observed, now int64) (Outcome, error) {
	if !s.Eligible(n, observed) {
		return Outcome{}, ErrState
	}
	i := s.index(n)
	e := s.entries[i]
	if now < observed || now > e.Deadline {
		return Outcome{}, ErrState
	}
	return s.finish(i, Outcome{n, wire.StageDone, wire.ReasonNone, e.Attempt.Verb, e.Effective, wire.DeliveryBackend}), nil
}

// Expire finishes as unknown every attempt whose evidence window ended by now.
func (s *Session) Expire(now int64) []Outcome {
	var out []Outcome
	for i := range s.entries {
		e := s.entries[i]
		if !e.Terminal && e.Contact && now >= e.Deadline {
			o, _ := s.Fail(e.Attempt.N, wire.ReasonTimeout)
			out = append(out, o)
		}
	}
	return out
}

// Relocate invalidates old execution plans. A specifically designated pending
// receipt may survive the relocation; its evidence is still checked separately.
func (s *Session) Relocate(survivor uint64) ([]Outcome, error) {
	if s.closed {
		return nil, ErrState
	}
	if err := s.bump(&s.projection); err != nil {
		return nil, err
	}
	var out []Outcome
	for i := range s.entries {
		e := s.entries[i]
		if e.Terminal {
			continue
		}
		if e.Attempt.N == survivor && e.Contact {
			continue
		}
		o, err := s.Refuse(e.Attempt.N, wire.ReasonInterrupted)
		if err != nil {
			return nil, err
		}
		out = append(out, o)
	}
	return out, nil
}

// Close refuses every unfinished attempt and closes the session.
func (s *Session) Close() []Outcome {
	var out []Outcome
	for i := range s.entries {
		e := s.entries[i]
		if !e.Terminal {
			o, _ := s.Refuse(e.Attempt.N, wire.ReasonWorldClosed)
			out = append(out, o)
		}
	}
	s.closed = true
	return out
}
