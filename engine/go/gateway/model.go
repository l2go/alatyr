// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"errors"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Codec validates entire participant frames before returning any records.
// Implementations must be immutable and safe for concurrent use.
type Codec interface {
	Encode([]wire.Record) ([]byte, error)
	Decode([]byte) ([]wire.Record, error)
}

// Transport owns exactly one bounded frame on each side. Close unblocks IO.
type Transport interface {
	Read(time.Time) ([]byte, error)
	Write([]byte, time.Time) error
	Close() error
}

// Step is opaque to the gateway. Value belongs to a local backend adapter;
// implementations must reject unexpected types with an error, not a panic.
// Await pauses after a selection helper until
// State.Confirmed is true. Interferes names evidence classes affected by IO.
type Step struct {
	Value      any
	Await      bool
	Interferes []string
}

// Action is an immutable captured plan supplied by State.Plan.
type Action struct {
	Effective                  uint64
	Class                      string
	Waiting, Local, FullWindow bool
	SurviveRelocation          bool
	AfterCheckpoint            bool
	Steps                      []Step
	Value                      any
}

// Update carries an opaque local backend observation and bounded system steps.
// State must reject unexpected Value types with an error, not a panic.
type Update struct {
	Value    any
	Relocate bool
	Service  []Step
}

// Observation contains independently owned facts in backend receive order.
type Observation struct{ Facts []wire.Record }

// Snapshot is a bounded current projection, not a history of outcomes.
// Domains names incomplete domains when Ready is false.
type Snapshot struct {
	Facts   []wire.Record
	Ready   bool
	Domains []string
}

// Witness describes evidence supplied by the trusted State implementation.
type Witness uint8

const (
	// NoWitness provides no evidence for the action.
	NoWitness Witness = iota
	// Match proves the action within its original evidence window.
	Match
	// Candidate must remain valid until the full window or checkpoint ends.
	Candidate
)

// State is the injected dictionary/projection; it contains no transport logic.
// Snapshot returns independent bounded records, never historical outcomes.
type State interface {
	// Apply is called by the session loop once for each backend update.
	Apply(Update, int64) (Observation, error)
	// Snapshot is called by the loop at admission, resync, and readiness checks.
	Snapshot(int64) (Snapshot, error)
	// Plan captures a participant intent after bounded protocol admission.
	Plan(wire.Record, int64) (*Action, wire.Reason)
	// Validate rechecks a captured plan immediately before pacing and contact.
	Validate(*Action, int64) wire.Reason
	// Start is called by the loop before the first step of a plan.
	Start(*Action)
	// Confirmed rechecks a helper's current target before an effect is written.
	Confirmed(*Action) bool
	// Witness is called by the loop for eligible actions on observed facts.
	Witness(*Action, Update) Witness
	// CandidateValid is called before committing a retained witness.
	CandidateValid(*Action) bool
	// Proof supplies the retained or current fact when a candidate commits.
	Proof(*Action, int64) (wire.Record, error)
	// Invalidate retires references and offers when the projection relocates.
	Invalidate()
	// Stop is pure and derives a fresh guarded step from current state.
	Stop() *Step
}

// World events are backpressured. No reader may call Write. Implementations
// respect ctx deadlines and report possible contact on ambiguous write errors.
// Close must be idempotent and return promptly after unblocking Updates and Write.
type World interface {
	Updates() <-chan Update
	Write(context.Context, Step) error
	Close() error
}

// Pacer is optional. A World that implements it may hold a step before contact.
// Ready must not block: the game loop continues processing input and updates.
type Pacer interface {
	Ready(step Step, now int64) bool
}

// Factory opens one world and its initial projection for a trusted identity.
// The context bounds admission only; it must not own the returned world's life.
// On error Factory releases its resources. On success Gateway owns World.Close.
type Factory func(context.Context, string) (World, State, error)

// Identity is a host-authenticated participant permission, not wire input.
type Identity struct{ Act bool }

// Config sets policy, capacities, and deadlines. New rejects missing limits;
// the zero value is not a usable configuration. All times passed to State and
// Pacer are monotonic milliseconds since New, never participant wall clocks.
// Now and Ticks default to time.Now and time.NewTicker. Custom implementations
// must use the same clock and be safe for concurrent use. Diagnostic must not block.
// Shutdown defaults to one second and bounds how long Close waits for owners.
type Config struct {
	Debug                                                   bool
	Codec                                                   Codec
	Factory                                                 Factory
	Identities                                              map[string]Identity
	Versions                                                []uint64
	Meanings                                                []string
	World                                                   string
	Limits                                                  []wire.Field
	Attempts                                                proto.Config
	FrameBytes, OutBytes, Sessions, Connections, Teleports  int
	IntentBurst, ServiceBurst                               float64
	IntentRate, ServiceRate                                 float64
	Hello, Write, Checkpoint, Ping, Pong, Batch, TargetWait time.Duration
	Now                                                     func() time.Time
	Ticks                                                   func(time.Duration) (<-chan time.Time, func())
	Shutdown                                                time.Duration
	Diagnostic                                              func(Summary)
}

// Summary is redacted numeric operational metadata; it cannot contain content.
type Summary struct {
	AtMS                                                              int64
	Record, Mode, Kind, WireMin, WireMax                              uint64
	Debug, Redacted                                                   bool
	Session, Connection, Epoch, Control, Seq, N, Requested, Effective uint64
	Stage, Reason, Bytes                                              uint64
	ElapsedMS                                                         int64
}

// ErrFull means a session, connection, or opening reservation is at capacity.
var ErrFull = errors.New("gateway: participant capacity exceeded")

// ErrAuth means the trusted identity is unavailable or the gateway is closed.
var ErrAuth = errors.New("gateway: participant unavailable")

// ErrFrame means transport framing is malformed or exceeds its configured bound.
var ErrFrame = errors.New("gateway: invalid transport frame")

func uintField(r wire.Record, n uint32) uint64 { v, _ := wire.Find(r.Fields, n); return v.U }
func cloneFields(fields []wire.Field) []wire.Field {
	out := slices.Clone(fields)
	for i := range out {
		out[i].Value = cloneValue(out[i].Value)
	}
	return out
}

func cloneValue(v wire.Value) wire.Value {
	v.Data = slices.Clone(v.Data)
	v.Fields = cloneFields(v.Fields)
	v.Items = slices.Clone(v.Items)
	for i := range v.Items {
		v.Items[i] = cloneValue(v.Items[i])
	}
	return v
}
func record(t uint64, fields ...wire.Field) wire.Record { return wire.Record{Type: t, Fields: fields} }
func bye(reason wire.CloseReason) wire.Record {
	return record(wire.Bye, wire.U(1, true, uint64(reason)))
}
func checkpoint(begin bool, epoch uint64, ready bool, domains []string) wire.Record {
	phase := wire.CheckpointEnd
	if begin {
		phase = wire.CheckpointBegin
	}
	b := uint64(0)
	if ready {
		b = 1
	}
	r := record(wire.Checkpoint, wire.U(1, true, uint64(phase)), wire.U(2, true, epoch), wire.U(3, true, b))
	if len(domains) > 0 {
		items := make([]wire.Value, 0, len(domains))
		for _, s := range domains {
			items = append(items, wire.Value{Data: []byte(s)})
		}
		r.Fields = append(r.Fields, wire.Items(4, true, items))
	}
	return r
}
func control(op wire.ControlOp, epoch, next uint64) wire.Record {
	r := record(wire.Control, wire.U(1, true, uint64(op)), wire.U(2, true, epoch))
	if op == wire.ControlGranted {
		r.Fields = append(r.Fields, wire.U(3, true, next))
	}
	return r
}

type bucket struct {
	tokens, burst, rate float64
	at                  int64
}

func (b *bucket) take(now int64) bool {
	if now > b.at {
		b.tokens += float64(now-b.at) * b.rate / 1000
		if b.tokens > b.burst {
			b.tokens = b.burst
		}
		b.at = now
	}
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}
