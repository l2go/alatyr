// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package fixture

import (
	"context"
	"math"
	"slices"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Config bounds queues and retained writes. The zero value selects bridge caps.
type Config struct {
	// Queue bounds raw updates; zero selects eight, and values above eight fail.
	Queue int
	// WriteBytes bounds the retained log; zero selects 262144 bytes.
	WriteBytes int
	// OnWrite optionally scripts a response before Write returns. It must obey ctx.
	OnWrite func(context.Context, bridge.Command, uint64) error
	// Now shares the test clock; nil selects time.Now.
	Now func() time.Time
}

// Write records one handed-off raw command and its monotonically increasing N.
type Write struct {
	// Command is an independently owned raw command, including its Key.
	Command bridge.Command
	// N is the raw attempt number, independent of participant attempts.
	N uint64
}

// World owns bounded update and write queues, plus one explicit recovery value.
// Close unblocks Emit; Updates closes only when the owner calls Close.
type World struct {
	mu               sync.Mutex
	emitMu           sync.Mutex
	updates          chan bridge.Update
	done             chan struct{}
	once             sync.Once
	cfg              Config
	codec            *profile.Codec
	writes           []Write
	writeBytes       int
	n, lastKey       uint64
	detached, closed bool
	recovery         *bridge.Recovery
	entries          map[uint64]entry
	completed        []uint64
}

type entry struct {
	command  bridge.Command
	deadline time.Time
	terminal bool
}

// New constructs a scripted raw port with no automatic world behaviour.
func New(cfg Config) (*World, error) {
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.Queue == 0 {
		cfg.Queue = 8
	}
	if cfg.WriteBytes == 0 {
		cfg.WriteBytes = 262144
	}
	if cfg.Queue < 1 || cfg.Queue > 8 || cfg.WriteBytes < 1 || cfg.WriteBytes > 262144 {
		return nil, wire.ErrConfig
	}
	c, err := profile.NewProfileCodec()
	if err != nil {
		return nil, err
	}
	return &World{cfg: cfg, codec: c, updates: make(chan bridge.Update, cfg.Queue), done: make(chan struct{}), entries: make(map[uint64]entry)}, nil
}

// Updates yields raw observations in scripted order.
func (w *World) Updates() <-chan bridge.Update { return w.updates }

// Emit transfers an owned update, respecting queue backpressure and cancellation.
// The producer must stop before Close; it may run concurrently with Write.
func (w *World) Emit(ctx context.Context, u bridge.Update) error {
	w.emitMu.Lock()
	defer w.emitMu.Unlock()
	w.mu.Lock()
	if w.closed {
		w.mu.Unlock()
		return &bridge.Error{Kind: bridge.StateError}
	}
	if u.Kind == bridge.Detached {
		w.detached = true
	}
	if u.Kind == bridge.LiveBatch {
		if len(u.Batch.Records) != len(u.Batch.ReceivedAt) || len(u.Batch.Records) > 1024 || len(u.Batch.Receipts) > 72 {
			w.mu.Unlock()
			return wire.ErrFrame
		}
		// A Port batch can span multiple wire frames. Validate each record
		// with the shared codec, then bound their aggregate retained bytes.
		bytes := 0
		records := make([]wire.Record, 0, len(u.Batch.Records))
		for _, record := range u.Batch.Records {
			b, err := w.codec.Encode([]wire.Record{record})
			if err != nil {
				w.mu.Unlock()
				return err
			}
			rs, err := w.codec.Decode(b)
			if err != nil {
				w.mu.Unlock()
				return err
			}
			if len(b) > 262144-bytes {
				w.mu.Unlock()
				return &bridge.Error{Kind: bridge.Capacity}
			}
			bytes += len(b)
			records = append(records, rs...)
		}
		u.Batch.Records = records
		for _, r := range u.Batch.Receipts {
			e, ok := w.entries[r.N]
			if !ok || e.command.Key != r.Key || r.ObservedAt.IsZero() {
				w.mu.Unlock()
				return wire.ErrFrame
			}
			if err := checkReceipt(r, e.command); err != nil {
				w.mu.Unlock()
				return err
			}
		}
		for _, r := range u.Batch.Receipts {
			w.finish(r.N)
		}
	}
	w.mu.Unlock()
	select {
	case <-w.done:
		return &bridge.Error{Kind: bridge.StateError}
	case <-ctx.Done():
		return ctx.Err()
	case w.updates <- u:
		return nil
	}
}

// Write captures a command before running OnWrite; its N and Key never reset.
// Log eviction is oldest-first and bounded by bytes and 64 records.
func (w *World) Write(ctx context.Context, c bridge.Command) (uint64, error) {
	if c.QueueTTLMS != nil && *c.QueueTTLMS > 5000 {
		return 0, &bridge.Error{Kind: bridge.ConfigError}
	}
	w.mu.Lock()
	if w.closed || w.detached || c.Key <= w.lastKey || w.n == math.MaxUint64 {
		w.mu.Unlock()
		return 0, &bridge.Error{Kind: bridge.StateError}
	}
	r := wire.Record{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, w.n+1), wire.U(2, true, c.Verb), wire.U(3, true, 1), wire.U(4, true, 5000)}, c.Payload...)}
	pending := 0
	for n, e := range w.entries {
		if !e.terminal && !w.cfg.Now().Before(e.deadline) {
			w.finish(n)
		} else if !e.terminal {
			pending++
		}
	}
	if pending >= 8 {
		w.mu.Unlock()
		return 0, &bridge.Error{Kind: bridge.Capacity}
	}
	b, err := w.codec.Encode([]wire.Record{r})
	if err != nil {
		w.mu.Unlock()
		return 0, err
	}
	rs, err := w.codec.Decode(b)
	if err != nil {
		w.mu.Unlock()
		return 0, err
	}
	c.Payload = slices.Clone(rs[0].Fields[4:])
	if c.QueueTTLMS != nil {
		ttl := *c.QueueTTLMS
		c.QueueTTLMS = &ttl
	}
	w.n++
	w.lastKey = c.Key
	n := w.n
	w.entries[n] = entry{command: c, deadline: w.cfg.Now().Add(5 * time.Second)}
	cost := len(b) + 64
	for len(w.writes) > 0 && (w.writeBytes+cost > w.cfg.WriteBytes || len(w.writes) >= 64) {
		old := w.writes[0]
		w.writeBytes -= old.bytes(w.codec)
		w.writes[0] = Write{}
		w.writes = w.writes[1:]
	}
	if cost <= w.cfg.WriteBytes {
		w.writes = append(w.writes, Write{Command: c, N: n})
		w.writeBytes += cost
	}
	fn := w.cfg.OnWrite
	w.mu.Unlock()
	if fn != nil {
		return n, fn(ctx, c, n)
	}
	return n, nil
}

func (x Write) bytes(c *profile.Codec) int {
	b, err := c.Encode([]wire.Record{{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, x.N), wire.U(2, true, x.Command.Verb), wire.U(3, true, 1), wire.U(4, true, 5000)}, x.Command.Payload...)}})
	if err != nil {
		return 0
	} // Retained records were validated before insertion.
	return len(b) + 64
}

// Writes returns an independently owned retained write log.
func (w *World) Writes() []Write {
	w.mu.Lock()
	defer w.mu.Unlock()
	out := slices.Clone(w.writes)
	for i := range out {
		out[i].Command.Payload = cloneFields(out[i].Command.Payload)
		if out[i].Command.QueueTTLMS != nil {
			ttl := *out[i].Command.QueueTTLMS
			out[i].Command.QueueTTLMS = &ttl
		}
	}
	return out
}

// RetainedWrites reports retained records and accounted bytes.
func (w *World) RetainedWrites() (int, int) {
	w.mu.Lock()
	defer w.mu.Unlock()
	return len(w.writes), w.writeBytes
}

// Recover installs the explicit next recovery, never a replay of business writes.
// The caller transfers ownership and must use a fresh transport epoch.
func (w *World) Recover(r bridge.Recovery) { w.mu.Lock(); defer w.mu.Unlock(); w.recovery = &r }

// Resume returns the supplied recovery only after detach. It never sends Open.
func (w *World) Resume(ctx context.Context) (bridge.Recovery, error) {
	if err := ctx.Err(); err != nil {
		return bridge.Recovery{}, err
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed || !w.detached || w.recovery == nil {
		return bridge.Recovery{}, &bridge.Error{Kind: bridge.StateError}
	}
	r := *w.recovery
	w.recovery = nil
	w.detached = false
	return r, nil
}

// Close ends the fixture once. Producers must already have joined their emits.
func (w *World) Close() error {
	w.once.Do(func() {
		w.mu.Lock()
		w.closed = true
		w.mu.Unlock()
		close(w.done)
		w.emitMu.Lock()
		defer w.emitMu.Unlock()
		close(w.updates)
	})
	return nil
}

func cloneFields(fs []wire.Field) []wire.Field {
	fs = slices.Clone(fs)
	for i := range fs {
		fs[i].Value = cloneValue(fs[i].Value)
	}
	return fs
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

var _ bridge.Port = (*World)(nil)

func (w *World) finish(n uint64) {
	e, ok := w.entries[n]
	if !ok || e.terminal {
		return
	}
	e.terminal = true
	w.entries[n] = e
	w.completed = append(w.completed, n)
	if len(w.completed) > 64 {
		delete(w.entries, w.completed[0])
		w.completed = w.completed[1:]
	}
}
