// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest

import (
	"context"
	"errors"
	"io"
	"net"
	"sync"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Config returns explicit synthetic capacities, not deployment defaults.
func Config() meaning.Config {
	c := meaning.Config{Wire: wire.Limits{FrameBytes: 8192, StringBytes: 4096, ListItems: 128, Depth: 4, Records: 64, Fields: 128, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts:   proto.Config{Results: 64, Pending: 8, IntentBytes: 65536, EvidenceMS: 5000, QueueMS: 5000, Classes: []string{"dialog", "take", "trade", "revive", "learn"}},
		ProposalMS: 60000, WindowBytes: 65536, Windows: 8, Objects: 128, HistoryEntries: 16, HistoryBytes: 8192}
	for i := range c.Advertised {
		c.Advertised[i] = 262144
	}
	c.Advertised[0] = uint64(c.Wire.FrameBytes)
	return c
}

// New constructs a real gateway with explicit synthetic host permissions.
// Origin is shared with the backend and must precede the original raw Open.
func New(t *testing.T, c meaning.Config, f gateway.Factory, origin time.Time) *gateway.Gateway {
	t.Helper()
	codec, err := meaning.NewCodec(c)
	if err != nil {
		t.Fatal(err)
	}
	cfg := gateway.Config{Codec: codec, Factory: f, Origin: origin,
		Identities: map[string]gateway.Identity{"one": {Act: true}, "two": {Act: true}},
		Versions:   []uint64{1}, Meanings: []string{meaning.Meaning}, World: "test-world", Limits: c.LimitsFields(), Attempts: c.Attempts,
		FrameBytes: c.Wire.FrameBytes, OutBytes: 262144, Sessions: 2, Connections: 4, Teleports: 8,
		IntentBurst: 1000, IntentRate: 1000, ServiceBurst: 1000, ServiceRate: 1000,
		Hello: time.Second, Write: time.Second, Checkpoint: 10 * time.Second, Ping: time.Hour, Pong: time.Hour, Batch: 10 * time.Millisecond, TargetWait: 1500 * time.Millisecond}
	g, err := gateway.New(cfg)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := g.Close(); err != nil {
			t.Error(err)
		}
	})
	return g
}

type frame struct {
	records []wire.Record
	err     error
}

// Peer is a bounded participant transport with an ordered decoded transcript.
// Calls other than its owned reader are serial, on the test goroutine.
type Peer struct {
	t          *testing.T
	codec      *meaning.Codec
	stream     *gateway.Stream
	frames     chan frame
	stop, done chan struct{}
	once       sync.Once
	// Records retains every decoded participant record, in arrival order.
	Records []wire.Record
	// Control is the latest granted control epoch.
	Control uint64
	// Session is the game session advertised by Welcome.
	Session uint64
	// Epoch is the latest participant projection epoch.
	Epoch uint64
	// Ready reports a completed ready checkpoint, not an initial raw flag.
	Ready bool
}

// Join sends Hello and waits for a ready checkpoint and, when requested, control.
// An optional wrapper can hold or fault the server-side transport for lifecycle
// tests. At most one wrapper is accepted; nil uses the ordinary in-memory pipe.
func Join(t *testing.T, g *gateway.Gateway, c meaning.Config, id string, mode wire.Mode, control bool, wrappers ...func(gateway.Transport) gateway.Transport) *Peer {
	t.Helper()
	if len(wrappers) > 1 {
		t.Fatal("at most one transport wrapper")
	}
	var s *gateway.Stream
	var err error
	if len(wrappers) == 1 && wrappers[0] != nil {
		a, b := net.Pipe()
		s = &gateway.Stream{Conn: a, Limit: c.Wire.FrameBytes}
		server := &gateway.Stream{Conn: b, Limit: c.Wire.FrameBytes}
		err = g.Bind(t.Context(), id, wrappers[0](server))
		if err != nil {
			if closeErr := a.Close(); closeErr != nil {
				t.Error(closeErr)
			}
			if closeErr := b.Close(); closeErr != nil {
				t.Error(closeErr)
			}
		}
	} else {
		s, err = g.Pipe(t.Context(), id)
	}
	if err != nil {
		t.Fatal(err)
	}
	codec, err := meaning.NewCodec(c)
	if err != nil {
		t.Fatal(err)
	}
	p := &Peer{t: t, codec: codec, stream: s, frames: make(chan frame, 256), stop: make(chan struct{}), done: make(chan struct{})}
	go func() {
		defer close(p.done)
		defer close(p.frames)
		for {
			b, err := s.Read(time.Now().Add(time.Minute))
			var rs []wire.Record
			if err == nil {
				rs, err = codec.Decode(b)
			}
			select {
			case p.frames <- frame{rs, err}:
			case <-p.stop:
				return
			}
			if err != nil {
				return
			}
		}
	}()
	t.Cleanup(p.Close)
	p.Send(wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(mode))}})
	p.Until(func() bool { return p.Ready && (!control || p.Control != 0) })
	return p
}

// Close stops and joins the owned participant reader.
func (p *Peer) Close() {
	p.once.Do(func() {
		close(p.stop)
		if err := p.stream.Close(); err != nil {
			p.t.Error(err)
		}
		<-p.done
	})
}

// Send validates and writes one participant frame without rewriting its records.
func (p *Peer) Send(rs ...wire.Record) {
	p.t.Helper()
	b, err := p.codec.Encode(rs)
	if err != nil {
		p.t.Fatal(err)
	}
	p.Raw(b)
}

// Raw writes a bounded frame, including deliberately malformed test input.
func (p *Peer) Raw(b []byte) {
	p.t.Helper()
	if err := p.stream.Write(b, time.Now().Add(time.Second)); err != nil {
		p.t.Fatal(err)
	}
}

func (p *Peer) accept(f frame) {
	p.t.Helper()
	if f.err != nil {
		p.t.Fatal(f.err)
	}
	p.Records = append(p.Records, f.records...)
	for _, r := range f.records {
		switch r.Type {
		case wire.Welcome:
			p.Session = U(r, 4)
		case wire.Checkpoint:
			p.Epoch = U(r, 2)
			p.Ready = U(r, 1) == uint64(wire.CheckpointEnd) && U(r, 3) == 1
		case wire.Control:
			if U(r, 1) == uint64(wire.ControlGranted) {
				p.Control = U(r, 2)
			} else if U(r, 1) == uint64(wire.ControlLost) {
				p.Control = 0
			}
		}
	}
}

// Read appends the next frame or fails with a bounded timeout.
func (p *Peer) Read() {
	p.t.Helper()
	select {
	case f, ok := <-p.frames:
		if !ok {
			p.t.Fatal("participant closed")
		}
		p.accept(f)
	case <-time.After(time.Second):
		p.t.Fatal("participant frame timeout")
	}
}

// Drain appends all currently delivered frames; it never discards a record.
// Use a deterministic actor barrier before calling it.
func (p *Peer) Drain() {
	for {
		select {
		case f, ok := <-p.frames:
			if !ok {
				return
			}
			if f.err != nil {
				if !errors.Is(f.err, io.EOF) && !errors.Is(f.err, io.ErrClosedPipe) && !errors.Is(f.err, net.ErrClosed) {
					p.t.Fatal(f.err)
				}
				return
			}
			p.accept(f)
		default:
			return
		}
	}
}

// Until receives records until the predicate holds.
func (p *Peer) Until(f func() bool) {
	p.t.Helper()
	for !f() {
		p.Read()
	}
}

// Result waits for a specific participant stage without erasing earlier records.
func (p *Peer) Result(n uint64, stage wire.Stage) wire.Record {
	p.t.Helper()
	var out wire.Record
	p.Until(func() bool {
		for _, r := range p.Records {
			if r.Type == wire.Result && U(r, 1) == n && U(r, 2) == uint64(stage) {
				out = r
				return true
			}
		}
		return false
	})
	return out
}

// Fact waits for a fact kind without filtering the ordered transcript.
func (p *Peer) Fact(kind uint64) wire.Record {
	p.t.Helper()
	var out wire.Record
	p.Until(func() bool {
		for _, r := range p.Records {
			if r.Type == wire.Fact && U(r, 1) == kind {
				out = r
				return true
			}
		}
		return false
	})
	return out
}

// U returns a UInt field for assertions; missing fields read as zero.
func U(r wire.Record, n uint32) uint64 { f, _ := wire.Find(r.Fields, n); return f.U }

// Barrier waits for an actor-owned status request, without polling or sleeping.
func Barrier(t *testing.T, g *gateway.Gateway, id string) gateway.Status {
	t.Helper()
	ctx, cancel := context.WithTimeout(t.Context(), time.Second)
	defer cancel()
	s, err := g.Status(ctx, id)
	if err != nil {
		t.Fatal(err)
	}
	return s
}
