// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"math"
	"slices"
	"sync"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Gateway owns bounded sessions and their transport loops. Use New to construct it.
// Bind, Status, and Close may be called concurrently.
type Gateway struct {
	start        time.Time
	cfg          Config
	mu           sync.Mutex
	games        map[string]*game
	prefix, next uint32
	opening      map[string]bool
	stop         chan struct{}
	done         chan struct{}
	once         sync.Once
	wg           sync.WaitGroup
	closed       bool
	err          error
}
type game struct {
	gate          *Gateway
	id            uint64
	world         World
	pacer         Pacer
	state         State
	attempts      *proto.Session
	input         chan input
	conns         map[uint64]*connection
	nextConn, seq uint64
	actions       map[uint64]*execution
	rate          bucket
	closed        bool
	stop          bool
	services      []Step
	done          chan struct{}
	cancel        context.CancelFunc
	worldOnce     sync.Once
	provisional   bool
}
type execution struct {
	action       *Action
	index        int
	waiting      bool
	helperAt     int64
	candidate    int64
	hasCandidate bool
}
type input struct {
	received   int64
	bytes      int
	status     chan Status
	conn       *connection
	records    []wire.Record
	err        error
	checkpoint uint64
	abort      bool
}
type outgoing struct {
	raw   []byte
	fence uint64
}
type connection struct {
	receivedAt                 int64
	awaitComplete              bool
	closing                    bool
	transfer                   bool
	capturedReady              bool
	restarted                  bool
	game                       *game
	id                         uint64
	t                          Transport
	act, ready, hello, mayAuto bool
	mode                       wire.Mode
	service                    bucket
	lastPong, lastPing         int64
	nonce                      uint64
	pendingPing                bool
	generation                 uint64
	incompleteAt               int64
	mu                         sync.Mutex
	queue                      []outgoing
	bytes                      int
	active                     int
	snapshot                   [][]byte
	cursor                     int
	fence                      uint64
	signal                     chan struct{}
	done                       chan struct{}
	writerDone                 chan struct{}
	once                       sync.Once
	closed                     bool
}

// New validates explicit limits and copies host-owned configuration collections.
// It starts no session or transport until Bind succeeds.
func New(c Config) (*Gateway, error) {
	for _, rate := range []float64{c.IntentRate, c.IntentBurst, c.ServiceRate, c.ServiceBurst} {
		if math.IsNaN(rate) || math.IsInf(rate, 0) {
			return nil, wire.ErrConfig
		}
	}
	if c.Codec == nil || c.Factory == nil || len(c.Identities) == 0 || len(c.Versions) == 0 || len(c.Meanings) == 0 || c.FrameBytes < 1 || c.OutBytes < c.FrameBytes || c.Sessions < 1 || c.Connections < 1 || c.Teleports < 1 || c.IntentBurst < 1 || c.IntentRate <= 0 || c.ServiceBurst < 1 || c.ServiceRate <= 0 || c.Hello <= 0 || c.Write <= 0 || c.Checkpoint <= 0 || c.Ping <= 0 || c.Pong < c.Ping || c.Batch <= 0 || c.TargetWait <= 0 {
		return nil, wire.ErrConfig
	}
	if _, err := proto.New(c.Attempts); err != nil {
		return nil, err
	}
	if c.Now == nil {
		c.Now = time.Now
	}
	if c.Ticks == nil {
		c.Ticks = func(d time.Duration) (<-chan time.Time, func()) {
			t := time.NewTicker(d)
			return t.C, t.Stop
		}
	}
	if c.Shutdown == 0 {
		c.Shutdown = time.Second
	}
	if c.Shutdown < 0 {
		return nil, wire.ErrConfig
	}
	c.Versions = slices.Clone(c.Versions)
	c.Meanings = slices.Clone(c.Meanings)
	c.Limits = cloneFields(c.Limits)
	c.Attempts.Classes = slices.Clone(c.Attempts.Classes)
	ids := make(map[string]Identity, len(c.Identities))
	for k, v := range c.Identities {
		if k == "" || len(k) > 128 {
			return nil, wire.ErrConfig
		}
		ids[k] = v
	}
	c.Identities = ids
	var seed [4]byte
	if _, err := rand.Read(seed[:]); err != nil {
		return nil, err
	}
	if c.Origin.IsZero() {
		c.Origin = c.Now()
	} else if c.Origin.After(c.Now()) {
		return nil, wire.ErrConfig
	}
	return &Gateway{start: c.Origin, cfg: c, games: map[string]*game{}, opening: map[string]bool{}, prefix: binary.LittleEndian.Uint32(seed[:]), stop: make(chan struct{}), done: make(chan struct{})}, nil
}

// Close stops admission, cancels owners, and joins session, opening, and transport
// loops within Config.Shutdown. It returns the first close error or a deadline
// error. A timed-out owner remains tracked; a later Close can wait for it again.
func (g *Gateway) Close() error {
	g.once.Do(func() {
		g.mu.Lock()
		g.closed = true
		close(g.stop)
		for _, s := range g.games {
			s.cancel()
		}
		g.mu.Unlock()
		go func() {
			g.wg.Wait()
			close(g.done)
		}()
	})
	timer := time.NewTimer(g.cfg.Shutdown)
	defer timer.Stop()
	select {
	case <-g.done:
		g.mu.Lock()
		defer g.mu.Unlock()
		return g.err
	case <-timer.C:
		return fmt.Errorf("gateway: shutdown: %w", context.DeadlineExceeded)
	}
}

func (g *Gateway) closeError(err error) {
	if err != nil {
		g.mu.Lock()
		if g.err == nil {
			g.err = fmt.Errorf("gateway: close: %w", err)
		}
		g.mu.Unlock()
	}
}

// Bind admits an already authenticated identity and takes ownership of t only on
// success. ctx bounds opening, not the accepted session's lifetime. A concurrent
// open for the same identity returns ErrFull rather than opening a second world.
func (g *Gateway) Bind(ctx context.Context, identity string, t Transport) error {
	_, _, err := g.bind(ctx, identity, t, false)
	return err
}

func (g *Gateway) bind(ctx context.Context, identity string, t Transport, provisional bool) (*connection, bool, error) {
	if err := ctx.Err(); err != nil {
		return nil, false, err
	}
	if t == nil {
		return nil, false, wire.ErrConfig
	}
	g.mu.Lock()
	permission, ok := g.cfg.Identities[identity]
	if !ok || g.closed {
		g.mu.Unlock()
		return nil, false, ErrAuth
	}
	for id, existing := range g.games {
		if existing.closed {
			select {
			case <-existing.done:
				delete(g.games, id)
			default:
			}
		}
	}
	s := g.games[identity]
	if s != nil && s.closed {
		g.mu.Unlock()
		return nil, false, ErrFull
	}
	created := s == nil
	if created {
		if g.opening[identity] || len(g.games)+len(g.opening) >= g.cfg.Sessions {
			g.mu.Unlock()
			return nil, false, ErrFull
		}
		if g.next == math.MaxUint32 {
			g.mu.Unlock()
			return nil, false, proto.ErrExhausted
		}
		g.next++
		id := uint64(g.prefix)<<32 | uint64(g.next)
		g.opening[identity] = true
		g.wg.Add(1)
		g.mu.Unlock()
		defer g.wg.Done()
		openCtx, cancelOpen := context.WithTimeout(ctx, g.cfg.Hello)
		openDone := make(chan struct{})
		cancelDone := make(chan struct{})
		go func() {
			defer close(cancelDone)
			select {
			case <-g.stop:
				cancelOpen()
			case <-openDone:
			}
		}()
		world, state, err := g.cfg.Factory(openCtx, identity)
		if err == nil {
			err = openCtx.Err()
		}
		cancelOpen()
		close(openDone)
		<-cancelDone
		attempts, attemptErr := proto.New(g.cfg.Attempts)
		err = errors.Join(err, attemptErr)
		g.mu.Lock()
		if err != nil || g.closed || world == nil || state == nil {
			g.mu.Unlock()
			if world != nil {
				g.closeError(world.Close())
			}
			g.mu.Lock()
			delete(g.opening, identity)
			g.mu.Unlock()
			if err != nil {
				return nil, false, err
			}
			return nil, false, ErrAuth
		}
		delete(g.opening, identity)
		actorCtx, cancel := context.WithCancel(context.Background())
		s = &game{gate: g, id: id, world: world, state: state, attempts: attempts, input: make(chan input, g.cfg.Connections), conns: map[uint64]*connection{}, actions: map[uint64]*execution{}, rate: bucket{g.cfg.IntentBurst, g.cfg.IntentBurst, g.cfg.IntentRate, g.now()}, done: make(chan struct{}), cancel: cancel}
		s.pacer, _ = world.(Pacer)
		s.provisional = provisional
		g.games[identity] = s
		g.wg.Add(1)
		go func() { defer g.wg.Done(); s.run(actorCtx) }()
	}
	// Reservations include connections which have not sent Hello yet.
	if err := ctx.Err(); err != nil {
		if created {
			s.cancel()
		}
		g.mu.Unlock()
		return nil, false, err
	}
	if len(s.conns) >= g.cfg.Connections {
		g.mu.Unlock()
		return nil, false, ErrFull
	}
	if s.nextConn == math.MaxUint64 {
		g.mu.Unlock()
		return nil, false, proto.ErrExhausted
	}
	s.nextConn++
	now := g.now()
	c := &connection{game: s, id: s.nextConn, t: t, act: permission.Act, signal: make(chan struct{}, 1), done: make(chan struct{}), lastPong: now, lastPing: now, service: bucket{g.cfg.ServiceBurst, g.cfg.ServiceBurst, g.cfg.ServiceRate, now}}
	s.conns[c.id] = c
	if !provisional {
		s.provisional = false
	}
	c.writerDone = make(chan struct{})
	g.wg.Add(2)
	if provisional {
		// The HTTP owner spans Hijack, handshake I/O, and transport handoff.
		// Register under the admission lock, before Close can start Wait.
		g.wg.Add(1)
	}
	g.mu.Unlock()
	go func() { defer g.wg.Done(); c.writeLoop() }()
	go func() { defer g.wg.Done(); c.readLoop() }()
	return c, created, nil
}
func (g *Gateway) now() int64 { return g.cfg.Now().Sub(g.start).Milliseconds() }

// Pipe binds an in-memory stream for a trusted participant identity.
// The caller owns the returned stream and must close it.
func (g *Gateway) Pipe(ctx context.Context, identity string) (*Stream, error) {
	a, b := pipe()
	if err := g.Bind(ctx, identity, &Stream{a, g.cfg.FrameBytes}); err != nil {
		return nil, errors.Join(err, a.Close(), b.Close())
	}
	return &Stream{b, g.cfg.FrameBytes}, nil
}
func (c *connection) post(in input) bool {
	select {
	case c.game.input <- in:
		return true
	case <-c.done:
		return false
	case <-c.game.done:
		return false
	case <-c.game.gate.stop:
		return false
	}
}
func (c *connection) readLoop() {
	deadline := time.Now().Add(c.game.gate.cfg.Hello)
	for {
		raw, err := c.t.Read(deadline)
		if err != nil {
			c.post(input{conn: c, err: err})
			return
		}
		received := c.game.gate.now()
		records, err := c.game.gate.cfg.Codec.Decode(raw)
		if !c.post(input{conn: c, records: records, err: err, bytes: len(raw), received: received}) || err != nil {
			return
		}
		deadline = time.Now().Add(c.game.gate.cfg.Pong)
	}
}
func (c *connection) wake() {
	select {
	case c.signal <- struct{}{}:
	default:
	}
}
func (c *connection) enqueue(r wire.Record) bool {
	raw, err := c.game.gate.cfg.Codec.Encode([]wire.Record{r})
	if err != nil {
		return false
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed || len(raw) > c.game.gate.cfg.OutBytes-c.bytes-c.active {
		return false
	}
	c.queue = append(c.queue, outgoing{raw: raw})
	c.bytes += len(raw)
	c.wake()
	return true
}
func (c *connection) close() {
	c.once.Do(func() {
		c.mu.Lock()
		c.closed = true
		c.queue = nil
		c.snapshot = nil
		c.bytes = 0
		c.mu.Unlock()
		close(c.done)
		c.game.gate.closeError(c.t.Close())
		c.game.gate.mu.Lock()
		delete(c.game.conns, c.id)
		c.game.gate.mu.Unlock()
	})
}
func (c *connection) writeLoop() {
	if c.writerDone != nil {
		defer close(c.writerDone)
	}
	for {
		c.mu.Lock()
		var b []byte
		var fence uint64

		if c.cursor < len(c.snapshot) {
			b = c.snapshot[c.cursor]
			c.snapshot[c.cursor] = nil
			c.cursor++
			if c.cursor == len(c.snapshot) {
				fence = c.fence
				c.snapshot = nil
				c.cursor = 0
			}
		} else if len(c.queue) > 0 {
			b = c.queue[0].raw
			fence = c.queue[0].fence
			c.queue[0] = outgoing{}
			c.queue = c.queue[1:]
			c.bytes -= len(b)
		}
		if b != nil {
			c.active = len(b)
		}
		c.mu.Unlock()
		if b == nil {
			c.mu.Lock()
			closing := c.closing
			c.mu.Unlock()
			if closing {
				c.close()
				return
			}
			select {
			case <-c.signal:
				continue
			case <-c.done:
				return
			case <-c.game.gate.stop:
				c.close()
				return
			}
		}
		err := c.t.Write(b, time.Now().Add(c.game.gate.cfg.Write))
		c.mu.Lock()
		c.active = 0
		c.mu.Unlock()
		if err != nil {
			c.post(input{conn: c, err: err})
			c.close()
			return
		}
		if fence != 0 && !c.post(input{conn: c, checkpoint: fence}) {
			return
		}
	}
}
func (s *game) connections() []*connection {
	s.gate.mu.Lock()
	defer s.gate.mu.Unlock()
	out := make([]*connection, 0, len(s.conns))
	for _, c := range s.conns {
		out = append(out, c)
	}
	return out
}
func (s *game) send(c *connection, r wire.Record) {
	s.trace(c, r, 0, false)
	if !c.enqueue(r) {
		if c.transfer && !c.restarted {
			c.restarted = true
			s.checkpoint(c, nil, c.awaitComplete)
		} else {
			s.drop(c, wire.CloseSlowConsumer)
		}
	}
}
func (s *game) broadcast(r wire.Record) {
	for _, c := range s.connections() {
		if c.hello {
			s.send(c, r)
		}
	}
}
func (s *game) drop(c *connection, reason wire.CloseReason) {
	owner, _ := s.attempts.Controller()
	if owner == c.id && !s.closed {
		if err := s.attempts.Lose(c.id); err != nil {
			s.fail(wire.CloseInternal)
			return
		}
		s.stop = true
	}
	s.gate.mu.Lock()
	_, exists := s.conns[c.id]
	s.gate.mu.Unlock()
	if !exists || c.closing {
		return
	}
	s.trace(c, bye(reason), 0, false)
	if reason == wire.CloseNormal || reason == wire.CloseSlowConsumer {
		c.close()
		return
	}
	raw, err := s.gate.cfg.Codec.Encode([]wire.Record{bye(reason)})
	if err != nil {
		c.close()
		return
	}
	c.mu.Lock()
	if len(raw) > s.gate.cfg.OutBytes-c.bytes-c.active {
		c.mu.Unlock()
		c.close()
		return
	}
	// Preserve a preceding allocated rejection before the final Bye. The slot
	// remains reserved until the bounded writer actually closes the transport.
	c.queue = append(c.queue, outgoing{raw: raw})
	c.bytes += len(raw)
	c.snapshot = nil
	c.cursor = 0
	c.closing = true
	c.mu.Unlock()
	c.wake()
}
func (s *game) fail(reason wire.CloseReason) {
	s.endScope(reason, false)
}
func (s *game) endScope(reason wire.CloseReason, detached bool) {
	if s.closed {
		return
	}
	s.gate.mu.Lock()
	s.closed = true
	s.gate.mu.Unlock()
	if s.gate.cfg.Diagnostic != nil {
		s.gate.cfg.Diagnostic(Summary{AtMS: s.gate.now(), Session: s.id, Epoch: s.attempts.Projection(), Record: wire.Bye, Reason: uint64(reason)})
	}
	for _, o := range s.attempts.Close() {
		s.broadcast(o.Record())
	}
	for _, c := range s.connections() {
		s.drop(c, reason)
	}
	s.worldOnce.Do(func() {
		if detached {
			world, ok := s.world.(Detacher)
			if !ok {
				s.gate.closeError(s.world.Close())
				return
			}
			s.gate.closeError(world.Detach())
			return
		}
		s.gate.closeError(s.world.Close())
	})
}
func (s *game) run(ctx context.Context) {
	if s.cancel != nil {
		defer s.cancel()
	}
	ticks, stop := s.gate.cfg.Ticks(s.gate.cfg.Batch)
	defer stop()
	defer close(s.done)
	defer func() {
		conns := s.connections()
		timer := time.NewTimer(s.gate.cfg.Write)
		defer timer.Stop()
		for _, c := range conns {
			if c.writerDone != nil {
				select {
				case <-c.writerDone:
				case <-s.gate.stop:
				case <-timer.C:
					for _, pending := range conns {
						pending.close()
					}
					return
				}
			}
			c.close()
		}
	}()
	defer s.fail(wire.CloseWorldClosed)
	for {
		select {
		case <-ctx.Done():
			return
		case in := <-s.input:
			s.process(in)
		case update, ok := <-s.world.Updates():
			if !ok {
				return
			}
			if !s.observe(update) {
				return
			}
		case <-ticks:
			s.expire()
		}
		if s.closed {
			return
		}
		s.service(ctx)
		if s.closed {
			return
		}
	}
}
func (s *game) checkpoint(c *connection, welcome *wire.Record, hold bool) bool {
	snapshot, err := s.state.Snapshot(s.gate.now())
	if err != nil {
		s.fail(wire.CloseInternal)
		return false
	}
	facts, ready, domains := snapshot.Facts, snapshot.Ready, snapshot.Domains
	c.ready = false
	c.transfer = true
	c.capturedReady = ready
	c.incompleteAt = s.gate.now()
	c.generation++
	if c.generation == 0 {
		s.fail(wire.CloseInternal)
		return false
	}
	records := make([]wire.Record, 0, len(facts)+3)
	if welcome != nil {
		records = append(records, *welcome)
	}
	records = append(records, checkpoint(true, s.attempts.Projection(), false, nil))
	for _, r := range facts {
		if !s.stamp(&r) {
			return false
		}
		records = append(records, r)
	}
	for _, o := range s.attempts.Snapshot() {
		records = append(records, o.Record())
	}
	c.awaitComplete = hold && !ready
	if !c.awaitComplete {
		records = append(records, checkpoint(false, s.attempts.Projection(), ready, domains))
	}
	frames := make([][]byte, 0, len(records))
	total := 0
	for _, r := range records {
		b, e := s.gate.cfg.Codec.Encode([]wire.Record{r})
		if e != nil {
			s.fail(wire.CloseInternal)
			return false
		}
		if len(b) > limit(s.gate.cfg.Limits, 30)-total {
			s.fail(wire.CloseInternal)
			return false
		}
		total += len(b)
		frames = append(frames, b)
	}
	// Snapshot is immutable projection storage, streamed one bounded frame at a
	// time; it is never charged as a wholesale outgoing queue.
	max := limit(s.gate.cfg.Limits, 30)
	if total > max {
		s.fail(wire.CloseInternal)
		return false
	}
	c.mu.Lock()
	c.queue = nil
	c.bytes = 0
	c.snapshot = frames
	c.cursor = 0
	c.fence = c.generation
	if c.awaitComplete {
		c.fence = 0
	}
	c.mu.Unlock()
	c.wake()
	return true
}
func limit(fields []wire.Field, n uint32) int { v, _ := wire.Find(fields, n); return int(v.U) }
func (s *game) checkpointDone(c *connection) {
	ready := c.capturedReady
	c.transfer = false
	c.restarted = false
	c.ready = ready
	if !ready {
		snapshot, err := s.state.Snapshot(s.gate.now())
		if err != nil {
			s.fail(wire.CloseInternal)
			return
		}
		if snapshot.Ready {
			s.checkpoint(c, nil, false)
		}
		return
	}
	c.incompleteAt = 0
	owner, _ := s.attempts.Controller()
	if owner == c.id || owner == 0 && c.mayAuto && c.mode == wire.ModeAct {
		s.grant(c)
	}
	c.mayAuto = false
	s.commitReadyWitnesses()
}
func (s *game) grant(c *connection) {
	old, _ := s.attempts.Controller()
	grant, err := s.attempts.Take(c.id)
	if err != nil || grant.NextN == math.MaxUint64 {
		s.fail(wire.CloseInternal)
		return
	}
	if old != 0 && old != c.id {
		for _, v := range s.connections() {
			if v.id == old {
				s.send(v, control(wire.ControlLost, grant.Epoch, 0))
			}
		}
	}
	s.send(c, control(wire.ControlGranted, grant.Epoch, grant.NextN))
}
func (s *game) receive(c *connection, r wire.Record) bool {
	if c.closing {
		return false
	}
	if !c.hello {
		if r.Type != wire.Hello {
			s.drop(c, wire.CloseFrameError)
			return false
		}
		v, m, err := proto.Negotiate(r, s.gate.cfg.Versions, s.gate.cfg.Meanings)
		if err != nil {
			s.drop(c, wire.CloseUnsupported)
			return false
		}
		c.hello = true
		c.mode = wire.Mode(uintField(r, 5))
		if c.mode == wire.ModeAct && !c.act {
			c.mode = wire.ModeObserve
		}
		owner, epoch := s.attempts.Controller()
		c.mayAuto = owner == 0 && c.mode == wire.ModeAct
		welcome := record(wire.Welcome, wire.U(1, true, v), wire.Data(2, true, m), wire.Data(3, true, s.gate.cfg.World), wire.U(4, true, s.id), wire.U(5, true, uint64(c.mode)), wire.U(6, true, epoch), wire.Obj(7, true, s.gate.cfg.Limits))
		return s.checkpoint(c, &welcome, false)
	}
	switch r.Type {
	case wire.Intent:
		s.intent(c, r)
	case wire.Pong:
		if c.pendingPing && uintField(r, 1) == c.nonce {
			c.pendingPing = false
			c.lastPong = s.gate.now()
		} else if !c.service.take(s.gate.now()) {
			s.drop(c, wire.CloseFrameError)
			return false
		}
	case wire.Ping:
		if !c.service.take(s.gate.now()) {
			s.drop(c, wire.CloseFrameError)
			return false
		}
		s.send(c, record(wire.Pong, wire.U(1, true, uintField(r, 1))))
	case wire.Control:
		if !c.service.take(s.gate.now()) {
			s.drop(c, wire.CloseFrameError)
			return false
		}
		switch wire.ControlOp(uintField(r, 1)) {
		case wire.ControlTake:
			if c.mode != wire.ModeAct || !c.ready {
				s.drop(c, wire.CloseFrameError)
				return false
			}
			s.grant(c)
		case wire.ControlResync:
			if uintField(r, 2) != s.attempts.Projection() {
				s.drop(c, wire.CloseFrameError)
				return false
			}
			s.checkpoint(c, nil, c.awaitComplete)
		default:
			s.drop(c, wire.CloseFrameError)
			return false
		}
	case wire.Bye:
		s.drop(c, wire.CloseNormal)
		return false
	default:
		s.drop(c, wire.CloseFrameError)
		return false
	}
	return true
}
func (s *game) intent(c *connection, r wire.Record) {
	s.expire()
	if s.closed {
		return
	}
	normalized := r
	normalized.Unsupported = false
	bytes, err := s.gate.cfg.Codec.Encode([]wire.Record{normalized})
	if r.Unsupported { // unsupported fields are absent after decoding; retain the marker in normalized identity
		bytes = append(bytes, 0xff)
		bytes = append(bytes, r.UnsupportedHash[:]...)
	}
	if err != nil {
		s.drop(c, wire.CloseFrameError)
		return
	}
	a := proto.Attempt{N: uintField(r, 1), Verb: uintField(r, 2), Control: uintField(r, 3), Payload: bytes, EncodedBytes: r.EncodedBytes}
	if f, ok := wire.Find(r.Fields, 4); ok {
		v := int64(f.U)
		a.QueueMS = &v
	}
	admission := s.attempts.Reserve(c.id, c.mode == wire.ModeAct, c.ready, a, c.receivedAt)
	if admission.Result != nil {
		if admission.Allocated {
			s.broadcast(admission.Result.Record())
		} else {
			s.send(c, admission.Result.Record())
		}
	}
	if admission.Close != nil {
		s.drop(c, *admission.Close)
		return
	}
	if !admission.Allocated || admission.Result != nil {
		return
	}
	refuse := func(reason wire.Reason) {
		o, err := s.attempts.Refuse(a.N, reason)
		if err != nil {
			s.fail(wire.CloseInternal)
			return
		}
		s.broadcast(o.Record())
	}
	if !s.rate.take(s.gate.now()) {
		refuse(wire.ReasonRate)
		return
	}
	if !c.ready {
		refuse(wire.ReasonNotReady)
		return
	}
	if a.EncodedBytes > s.gate.cfg.Attempts.IntentBytes || len(a.Payload) > s.gate.cfg.Attempts.IntentBytes || a.QueueMS != nil && (*a.QueueMS < 0 || *a.QueueMS > s.gate.cfg.Attempts.QueueMS) {
		refuse(wire.ReasonInvalid)
		return
	}
	action, reason := s.state.Plan(r, s.gate.now())
	if reason != wire.ReasonNone {
		refuse(reason)
		return
	}
	if action == nil {
		s.fail(wire.CloseInternal)
		return
	}
	o, err := s.attempts.Prepare(a.N, action.Effective, action.Class, action.Waiting)
	if err != nil {
		s.fail(wire.CloseInternal)
		return
	}
	if o != nil {
		s.broadcast(o.Record())
		return
	}
	s.actions[a.N] = &execution{action: action}
}
func (s *game) stamp(r *wire.Record) bool {
	if s.seq == math.MaxUint64 {
		s.fail(wire.CloseInternal)
		return false
	}
	s.seq++
	set(r, 2, wire.U(2, true, s.seq))
	set(r, 4, wire.U(4, true, s.attempts.Projection()))
	return true
}
func set(r *wire.Record, n uint32, f wire.Field) {
	for i := range r.Fields {
		if r.Fields[i].Number == n {
			r.Fields[i] = f
			return
		}
	}
	r.Fields = append(r.Fields, f)
}
func (s *game) fact(r wire.Record) {
	if s.stamp(&r) {
		s.broadcast(r)
	}
}

func (s *game) process(in input) {
	if in.abort {
		s.gate.mu.Lock()
		unused := len(s.conns) == 0 && s.provisional
		if unused {
			s.closed = true
		}
		s.gate.mu.Unlock()
		if unused {
			s.worldOnce.Do(func() { s.gate.closeError(s.world.Close()) })
		}
		return
	}
	if in.status != nil {
		in.status <- s.status()
		return
	}
	if in.err != nil {
		reason := wire.CloseNormal
		if !in.conn.hello {
			reason = wire.CloseFrameError
		}
		if errors.Is(in.err, wire.ErrUnsupported) {
			reason = wire.CloseUnsupported
		} else if errors.Is(in.err, wire.ErrFrame) || errors.Is(in.err, ErrFrame) {
			reason = wire.CloseFrameError
		}
		var unsupported *wire.UnsupportedRecord
		if errors.As(in.err, &unsupported) && unsupported.Type == wire.Bye {
			reason = wire.CloseNormal
		}
		s.drop(in.conn, reason)
		return
	}
	if in.checkpoint != 0 {
		if in.checkpoint == in.conn.generation {
			s.checkpointDone(in.conn)
		}
		return
	}
	in.conn.receivedAt = in.received
	for _, r := range in.records {
		s.trace(in.conn, r, in.bytes, true)
		if !s.receive(in.conn, r) {
			break
		}
	}
}
