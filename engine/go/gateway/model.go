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
	// Effective is the normalized verb ID recorded by protocol admission and results.
	Effective uint64
	// Class selects the configured evidence window and interference class for this plan.
	// An empty class follows the protocol's classless-action rules.
	Class string
	// Waiting keeps the sent attempt open for a witness. False finishes it at Sent;
	// class occupancy can refuse admission regardless of this flag.
	Waiting bool
	// Local completes without World.Write and interrupts unsent selection-helper plans.
	// False executes Steps against the backend.
	Local bool
	// FullWindow permits a valid retained candidate to commit at the evidence deadline.
	// False leaves an uncommitted candidate to the normal expiration rules.
	FullWindow bool
	// SurviveRelocation selects this plan as the survivor of a relocation; retained
	// evidence is cleared. State must not mark multiple active plans as survivors.
	SurviveRelocation bool
	// AfterCheckpoint retains matching evidence until the relocated projection is
	// ready and the checkpoint completes, instead of completing on the observed fact.
	AfterCheckpoint bool
	// Steps is the ordered captured backend plan. Await steps require current target
	// confirmation before the loop advances; an empty plan performs no backend write.
	Steps []Step
	// Value is opaque local-adapter data, never a wire payload. State methods must
	// reject an unexpected type with an error or refusal, not a panic.
	Value any
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
	// WitnessNone provides no evidence for the action.
	WitnessNone Witness = iota
	// WitnessMatch proves the action within its original evidence window.
	WitnessMatch
	// WitnessCandidate must remain valid until the full window or checkpoint ends.
	WitnessCandidate
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
	// Invalid adapter values return an error; the runtime closes the participant
	// with an internal reason without logging the value or error text.
	Start(*Action) error
	// Confirmed rechecks a helper's current target before an effect is written.
	// Invalid adapter values return an error, not false; the runtime closes the
	// participant with an internal reason without logging the value or error text.
	Confirmed(*Action) (bool, error)
	// Witness is called by the loop for eligible actions on observed facts.
	// Invalid adapter values return an error, not WitnessNone; the runtime closes
	// the participant with an internal reason without logging the value or error text.
	Witness(*Action, Update) (Witness, error)
	// CandidateValid is called before committing a retained witness.
	// Invalid adapter values return an error, not false; the runtime closes the
	// participant with an internal reason without logging the value or error text.
	CandidateValid(*Action) (bool, error)
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
	// Debug includes numeric Hello, Welcome, and Control traces. False omits them.
	Debug bool
	// Codec is the required immutable frame codec, safe for concurrent use.
	Codec Codec
	// Factory is the required opener for one backend and State per admitted identity.
	Factory Factory
	// Identities is the required nonempty set of trusted identities and permissions.
	// Keys must contain 1 to 128 bytes. New copies the map.
	Identities map[string]Identity
	// Versions is the required nonempty supported wire-version list for negotiation.
	// New copies it; there is no default version.
	Versions []uint64
	// Meanings is the required nonempty supported meaning-ID list for negotiation.
	// New copies it; there is no default meaning.
	Meanings []string
	// World is the world identifier advertised in Welcome. New does not validate or
	// default it; the host must provide an identifier accepted by Codec.
	World string
	// Limits is the advertised Welcome limit object, copied deeply by New.
	// Field 30 bounds checkpoint snapshot bytes; it has no fallback bound.
	Limits []wire.Field
	// Attempts supplies the required protocol session capacities, classes, and
	// millisecond windows. New validates it with proto.New and copies Classes.
	Attempts proto.Config
	// FrameBytes is the required positive frame bound in bytes for owned transports.
	FrameBytes int
	// OutBytes is the per-connection queued plus active-output bound in bytes.
	// It is required and must be at least FrameBytes; checkpoint storage is separate.
	OutBytes int
	// Sessions bounds accepted sessions plus in-progress backend opens. Required, positive.
	Sessions int
	// Connections bounds connections, including pre-Hello reservations, per session.
	// Required, positive; also sizes the session input channel.
	Connections int
	// Teleports bounds queued backend system-service steps, including relocation
	// acknowledgements. Required, positive; it is not a count of player teleports.
	Teleports int
	// IntentBurst is the required per-session intent bucket capacity in records.
	// It must be finite and at least one; the bucket starts full.
	IntentBurst float64
	// ServiceBurst is the required per-connection service-record bucket capacity.
	// It must be finite and at least one; the bucket starts full.
	ServiceBurst float64
	// IntentRate is the required finite positive intent refill rate in records per second.
	IntentRate float64
	// ServiceRate is the required finite positive service-record refill rate per second.
	ServiceRate float64
	// Hello is the required positive timeout for opening a backend and receiving
	// the first participant frame. HTTP frame I/O waits for upgrade completion,
	// but admission consumes this same initial budget.
	Hello time.Duration
	// Write is the required positive deadline for backend writes, transport writes,
	// the WebSocket handshake, and the session's final writer drain.
	Write time.Duration
	// Checkpoint is the required positive timeout for an incomplete projection or
	// checkpoint transfer; expiry closes the connection as an incomplete snapshot.
	Checkpoint time.Duration
	// Ping is the required positive interval between participant liveness probes.
	Ping time.Duration
	// Pong is the required liveness/read timeout, at least Ping. A missed response
	// closes the connection; there is no default.
	Pong time.Duration
	// Batch is the required positive session ticker interval for expiration and
	// scheduler progress when neither input nor backend updates arrive.
	Batch time.Duration
	// TargetWait is the required positive wait for State.Confirmed after writing
	// an Await helper; expiry fails the plan as target-unconfirmed.
	TargetWait time.Duration
	// Now is the concurrency-safe clock used for session-relative milliseconds.
	// Nil defaults to time.Now; custom Now and Ticks must use the same clock.
	Now func() time.Time
	// Ticks creates the session ticker and its stop function. Nil defaults to
	// time.NewTicker; a custom implementation must be concurrency-safe.
	Ticks func(time.Duration) (<-chan time.Time, func())
	// Shutdown bounds each Close wait for all tracked owners. Zero defaults to
	// one second; negative values are invalid. Timeout does not discard owners.
	Shutdown time.Duration
	// Diagnostic receives redacted numeric summaries synchronously. Nil disables
	// tracing; a configured callback must not block and must be concurrency-safe.
	Diagnostic func(Summary)
}

// Summary is redacted numeric operational metadata; it cannot contain content.
type Summary struct {
	// AtMS is the observation time in monotonic milliseconds since Gateway.New.
	AtMS int64
	// Record is the numeric wire record type, never its payload.
	Record uint64
	// Mode is the participant mode from Hello or Welcome in debug traces.
	Mode uint64
	// Kind is the numeric fact kind, or Hello client kind in debug traces.
	Kind uint64
	// WireMin is the lowest offered Hello version, or the selected Welcome version.
	WireMin uint64
	// WireMax is the highest offered Hello version, or the selected Welcome version.
	WireMax uint64
	// Debug marks a Hello, Welcome, or Control trace enabled by Config.Debug.
	Debug bool
	// Redacted reports the presence of Hello client metadata without retaining it.
	Redacted bool
	// Session is the gateway-assigned numeric session ID.
	Session uint64
	// Connection is the gateway-assigned connection ID; zero for session-wide results.
	Connection uint64
	// Epoch is the projection epoch, taken from the fact when tracing a Fact.
	Epoch uint64
	// Control is the control epoch sampled from the protocol session, not an owner ID.
	// It is zero on summaries emitted without a connection trace.
	Control uint64
	// Seq is the sequence number of a traced Fact.
	Seq uint64
	// N is the participant intent number for an Intent or Result.
	N uint64
	// Requested is the requested verb ID from an Intent or Result.
	Requested uint64
	// Effective is the normalized verb ID from a Result.
	Effective uint64
	// Stage is the numeric Result stage.
	Stage uint64
	// Reason is the numeric Result reason or Bye close reason, according to Record.
	Reason uint64
	// Bytes is the input frame size in bytes, repeated for each record in that
	// frame. Output and session-wide synthetic summaries leave it zero.
	Bytes uint64
	// ElapsedMS is milliseconds since the corresponding intent was queued, when
	// its protocol entry is retained. Zero when no entry is available.
	ElapsedMS int64
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
