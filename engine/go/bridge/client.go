// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"cmp"
	"context"
	"errors"
	"math"
	"net"
	"reflect"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unicode/utf8"

	"github.com/l2go/l2jfree/engine/go/bridge/internal/transport"
	"github.com/l2go/l2jfree/engine/go/bridge/profile"
	"github.com/l2go/l2jfree/engine/go/protocol/wire"
)

var sessions atomic.Int32

type Client struct {
	cfg                   Config
	codec                 *profile.Codec
	updates               chan Update
	requests              chan operation
	events                chan event
	stop, done            chan struct{}
	closing               sync.Once
	writeBusy, resumeBusy atomic.Bool
	closeErr              error // published by closing done
}
type response struct {
	n        uint64
	recovery Recovery
	err      error
}
type operation struct {
	kind    uint8
	ctx     context.Context
	command Command
	reply   chan response
}
type event struct {
	token               uint64
	records             []wire.Record
	bytes               int
	at                  time.Time
	err                 error
	written, closeWrite bool
	op                  *operation
	conn                net.Conn
}
type outgoing struct {
	records    []wire.Record
	ctx        context.Context
	op         *operation
	closeWrite bool
}
type entry struct {
	command  Command
	n, epoch uint64
	record   wire.Record
	result   *wire.Record
	deadline time.Time
}
type pendingUpdate struct {
	u     Update
	bytes int
}
type owner struct {
	c                                     *Client
	phase                                 uint8 // 0 opening, 1 live, 2 detached, 3 recovery
	conn                                  *transport.Conn
	cancel                                context.CancelFunc
	workers                               sync.WaitGroup
	writeq, service                       chan outgoing
	token, epoch, session, next, seq, g   uint64
	self                                  int64
	ready                                 bool
	origin, detached, barrier, checkpoint time.Time
	handshake                             *operation
	handshakeCtx                          context.CancelFunc
	snap                                  Snapshot
	inCheckpoint, ended, confirmed        bool
	checkpointBytes                       int
	protocolKind                          wire.CloseReason
	replay                                []Receipt
	entries                               map[uint64]*entry
	completed                             []uint64
	active                                *operation
	activeN                               uint64
	queue                                 []pendingUpdate
	queueBytes                            int
	deliveredBytes                        int
	proof                                 Batch
	proofBytes                            int
	proofDeadline                         time.Time
	nonce, ping                           uint64
	pingAt, lastPing                      time.Time
	serviceTokens                         int64
	serviceAt                             time.Time
}

func normalize(cfg Config, id Identity) (Config, error) {
	if cfg.Endpoint == "" {
		cfg.Endpoint = "127.0.0.1:9016"
	}
	host, port, e := net.SplitHostPort(cfg.Endpoint)
	p, pe := strconv.Atoi(port)
	if e != nil || pe != nil || host != "127.0.0.1" || p < 1 || p > 65535 || cfg.Secret == [32]byte{} {
		return cfg, failure(ConfigError)
	}
	if cfg.ResumeBudget == 0 {
		cfg.ResumeBudget = 30 * time.Second
	}
	if cfg.ResumeBudget < time.Second || cfg.ResumeBudget > 300*time.Second {
		return cfg, failure(ConfigError)
	}
	for _, s := range []string{id.Account, id.Character} {
		if len(s) == 0 || len(s) > 128 || !utf8.ValidString(s) || strings.IndexByte(s, 0) >= 0 {
			return cfg, failure(ConfigError)
		}
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.DialContext == nil {
		cfg.DialContext = (&net.Dialer{}).DialContext
	}
	return cfg, nil
}

func Open(ctx context.Context, cfg Config, id Identity) (*Client, Snapshot, error) {
	cfg, e := normalize(cfg, id)
	if e != nil {
		return nil, Snapshot{}, e
	}
	if sessions.Add(1) > 64 {
		sessions.Add(-1)
		return nil, Snapshot{}, failure(Capacity)
	}
	codec, e := profile.NewProfileCodec()
	if e != nil {
		sessions.Add(-1)
		return nil, Snapshot{}, failure(ConfigError)
	}
	c := &Client{cfg: cfg, codec: codec, updates: make(chan Update, 1), requests: make(chan operation), events: make(chan event), stop: make(chan struct{}), done: make(chan struct{})}
	op := operation{ctx: ctx, reply: make(chan response, 1)}
	o := &owner{c: c, epoch: 1, next: 1, entries: map[uint64]*entry{}, serviceTokens: 4 * int64(time.Second), origin: cfg.Now()}
	go o.run(op, id)
	r := c.wait(op)
	if r.err != nil {
		<-c.done
		return nil, Snapshot{}, r.err
	}
	return c, r.recovery.Snapshot, nil
}
func (c *Client) Updates() <-chan Update { return c.updates }
func (c *Client) wait(op operation) response {
	select {
	case r := <-op.reply:
		return r
	case <-op.ctx.Done():
		// The owner observes cancellation too. Waiting for its reply preserves the
		// registered number even when cancellation races a successful handoff.
		select {
		case r := <-op.reply:
			return r
		case <-c.done:
			select {
			case r := <-op.reply:
				return r
			default:
				return response{err: failure(StateError)}
			}
		}
	case <-c.done:
		select {
		case r := <-op.reply:
			return r
		default:
			return response{err: failure(StateError)}
		}
	}
}
func (c *Client) Write(ctx context.Context, command Command) (uint64, error) {
	if !c.writeBusy.CompareAndSwap(false, true) {
		return 0, failure(StateError)
	}
	defer c.writeBusy.Store(false)
	if e := validateCommand(command); e != nil {
		return 0, e
	}
	op := operation{kind: 1, ctx: ctx, command: command, reply: make(chan response, 1)}
	select {
	case c.requests <- op:
	case <-ctx.Done():
		return 0, failure(Timeout)
	case <-c.done:
		return 0, failure(StateError)
	case <-c.stop:
		return 0, failure(StateError)
	}
	r := c.wait(op)
	return r.n, r.err
}
func (c *Client) Resume(ctx context.Context) (Recovery, error) {
	if !c.resumeBusy.CompareAndSwap(false, true) {
		return Recovery{}, failure(StateError)
	}
	defer c.resumeBusy.Store(false)
	op := operation{kind: 2, ctx: ctx, reply: make(chan response, 1)}
	select {
	case c.requests <- op:
	case <-ctx.Done():
		return Recovery{}, failure(Timeout)
	case <-c.done:
		return Recovery{}, failure(StateError)
	case <-c.stop:
		return Recovery{}, failure(StateError)
	}
	r := c.wait(op)
	return r.recovery, r.err
}
func (c *Client) Close() error { c.closing.Do(func() { close(c.stop) }); <-c.done; return c.closeErr }
func (o *owner) reply(op *operation, r response) {
	if op != nil {
		op.reply <- r
	}
}
func (o *owner) launch(op *operation, first wire.Record) {
	o.token++
	token := o.token
	ctx, cancel := context.WithTimeout(op.ctx, 10*time.Second)
	o.handshakeCtx = cancel
	o.handshake = op
	o.barrier = o.c.cfg.Now().Add(10 * time.Second)
	if o.phase == 3 {
		budget := o.detached.Add(o.c.cfg.ResumeBudget)
		if budget.Before(o.barrier) {
			o.barrier = budget
		}
	}
	o.confirmed = false
	o.ended = false
	o.inCheckpoint = false
	o.snap = Snapshot{GatewayEpoch: o.epoch}
	o.replay = nil
	o.workers.Add(1)
	go func() {
		defer o.workers.Done()
		conn, e := o.c.cfg.DialContext(ctx, "tcp4", o.c.cfg.Endpoint)
		ev := event{token: token, conn: conn, err: e, records: []wire.Record{first}}
		select {
		case o.c.events <- ev:
		case <-ctx.Done():
			if conn != nil {
				conn.Close()
			}
		case <-o.c.done:
			if conn != nil {
				conn.Close()
			}
		}
	}()
}
func (o *owner) attach(raw net.Conn, first []wire.Record) {
	ctx, cancel := context.WithCancel(context.Background())
	o.cancel = cancel
	o.conn = transport.New(raw, o.c.codec)
	o.readBound()
	o.writeq = make(chan outgoing, 1)
	o.service = make(chan outgoing, 1)
	conn := o.conn
	token := o.token
	writeq, service := o.writeq, o.service
	o.workers.Add(2)
	go func() {
		defer o.workers.Done()
		for {
			rs, n, e := conn.Read()
			ev := event{token: token, records: rs, bytes: n, at: o.c.cfg.Now(), err: e}
			select {
			case o.c.events <- ev:
			case <-ctx.Done():
				return
			}
			if e != nil {
				return
			}
		}
	}()
	go func() {
		defer o.workers.Done()
		for {
			var job outgoing
			select {
			case <-ctx.Done():
				return
			case job = <-service:
			default:
				select {
				case <-ctx.Done():
					return
				case job = <-service:
				case job = <-writeq:
				}
			}
			e := conn.Write(job.ctx, job.records)
			select {
			case o.c.events <- event{token: token, written: true, closeWrite: job.closeWrite, op: job.op, err: e}:
			case <-ctx.Done():
				return
			}
			if e != nil {
				return
			}
		}
	}()
	o.writeq <- outgoing{records: first, ctx: o.handshake.ctx}
}
func (o *owner) readBound() {
	if o.conn == nil {
		return
	}
	var deadline time.Time
	add := func(t time.Time) {
		if !t.IsZero() && (deadline.IsZero() || t.Before(deadline)) {
			deadline = t
		}
	}
	if o.phase == 0 || o.phase == 3 {
		add(o.barrier)
	}
	add(o.checkpoint)
	if o.ping != 0 {
		add(o.pingAt.Add(30 * time.Second))
	}
	o.conn.Bound(deadline)
}
func (o *owner) drop() {
	if o.cancel != nil {
		o.cancel()
		o.cancel = nil
	}
	if o.conn != nil {
		o.conn.Close()
		o.conn = nil
	}
	if o.handshakeCtx != nil {
		o.handshakeCtx()
		o.handshakeCtx = nil
	}
}
func (o *owner) detachment(k FailureKind) {
	o.drop()
	o.phase = 2
	o.ready = false
	o.detached = o.c.cfg.Now()
	o.queue = nil
	o.queueBytes = 0
	select {
	case <-o.c.updates:
	default:
	}
	o.deliveredBytes = 0
	o.checkpoint = time.Time{}
	o.inCheckpoint = false
	o.snap = Snapshot{}
	o.replay = nil
	o.proof = Batch{}
	o.proofBytes = 0
	o.proofDeadline = time.Time{}
	o.ping = 0
	o.queue = append(o.queue, pendingUpdate{u: Update{Kind: Detached, FailureKind: k, GatewayEpoch: o.epoch}, bytes: 64})
	o.queueBytes = 64
	if o.active != nil {
		o.reply(o.active, response{n: o.activeN, err: failure(k)})
		o.active = nil
	}
	if o.handshake != nil {
		o.reply(o.handshake, response{err: failure(k)})
		o.handshake = nil
	}
}
func (o *owner) finish(k FailureKind, orderly bool) {
	if o.handshakeCtx != nil {
		o.handshakeCtx()
	}
	if o.active != nil {
		o.reply(o.active, response{n: o.activeN, err: failure(StateError)})
		o.active = nil
	}
	if o.handshake != nil {
		o.reply(o.handshake, response{err: &Error{Kind: k, ProtocolKind: o.protocolKind}})
		o.handshake = nil
	}
	confirmed := orderly
	if o.conn != nil && !orderly && o.session != 0 {
		ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
		closeFrame := wire.Record{Type: profile.Close, Fields: []wire.Field{wire.U(1, true, o.session), wire.U(2, true, o.epoch), wire.U(3, true, 0)}}
		select {
		case o.service <- outgoing{records: []wire.Record{closeFrame}, ctx: ctx, closeWrite: true}:
		default:
		}
		closeWritten, byeSeen := false, false
		waiting := true
		for waiting {
			select {
			case ev := <-o.c.events:
				if ev.token != o.token {
					continue
				}
				if ev.closeWrite {
					if ev.err != nil {
						waiting = false
					} else {
						closeWritten = true
					}
				}
				if !ev.written {
					for _, r := range ev.records {
						if r.Type == wire.Bye && profile.U(r, 1) == 0 {
							byeSeen = true
						}
					}
				}
				if closeWritten && byeSeen {
					confirmed = true
					waiting = false
				}
			case <-ctx.Done():
				waiting = false
			}
		}
		cancel()
	}
	o.drop()
	if !confirmed {
		o.c.closeErr = failure(RemoteCloseUnconfirmed)
	}
	// Drain all fixed workers before publishing closed. No user callback is held.
	o.workers.Wait()
	select {
	case <-o.c.updates:
	default:
	}
	o.c.updates <- Update{Kind: Closed, FailureKind: k, GatewayEpoch: o.epoch}
	close(o.c.updates)
	sessions.Add(-1)
	close(o.c.done)
}
func (o *owner) run(first operation, id Identity) {
	o.launch(&first, wire.Record{Type: profile.Open, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, profile.Name), wire.Data(4, true, string(o.c.cfg.Secret[:])), wire.Data(5, true, id.Account), wire.Data(6, true, id.Character), wire.U(7, true, 1)}})
	tick := time.NewTicker(10 * time.Millisecond)
	defer tick.Stop()
	for {
		if len(o.c.updates) == 0 {
			o.deliveredBytes = 0
		}
		var output chan Update
		var next Update
		if len(o.queue) > 0 {
			output = o.c.updates
			next = o.queue[0].u
		}
		var handshakeCancel, writeCancel <-chan struct{}
		if o.handshake != nil {
			handshakeCancel = o.handshake.ctx.Done()
		}
		if o.active != nil {
			writeCancel = o.active.ctx.Done()
		}
		select {
		case <-o.c.stop:
			o.finish(StateError, false)
			return
		case <-handshakeCancel:
			if o.phase == 3 && !o.confirmed {
				o.finish(RemoteCloseUnconfirmed, false)
				return
			}
			o.finish(Timeout, false)
			return
		case <-writeCancel:
			o.detachment(Timeout)
		case output <- next:
			o.deliveredBytes = o.queue[0].bytes
			o.queueBytes -= o.queue[0].bytes
			o.queue = o.queue[1:]
		case op := <-o.c.requests:
			select {
			case <-o.c.stop:
				o.reply(&op, response{err: failure(StateError)})
				o.finish(StateError, false)
				return
			default:
			}
			if op.ctx.Err() != nil {
				o.reply(&op, response{err: failure(Timeout)})
				continue
			}
			if op.kind == 1 {
				o.write(op)
			} else {
				if o.phase != 2 || o.session == 0 || o.epoch == math.MaxUint64 || o.c.cfg.Now().Sub(o.detached) >= o.c.cfg.ResumeBudget {
					o.reply(&op, response{err: failure(StateError)})
					continue
				}
				o.phase = 3
				o.epoch++
				o.launch(&op, wire.Record{Type: profile.Resume, Fields: []wire.Field{wire.U(1, true, o.session), wire.U(2, true, o.epoch), wire.Data(3, true, string(o.c.cfg.Secret[:])), wire.Data(4, true, profile.Name)}})
			}
		case ev := <-o.c.events:
			if ev.token != o.token {
				if ev.conn != nil {
					ev.conn.Close()
				}
				continue
			}
			if ev.conn != nil {
				if o.handshake == nil {
					ev.conn.Close()
				} else {
					o.attach(ev.conn, ev.records)
				}
				continue
			}
			if ev.err != nil {
				if errors.Is(ev.err, wire.ErrFrame) || errors.Is(ev.err, wire.ErrUnsupported) {
					o.setProtocol(ev.err)
					o.finish(Protocol, false)
					return
				}
				if o.phase == 0 {
					kind := Transport
					if o.conn == nil {
						kind = DialError
					}
					o.reply(o.handshake, response{err: failure(kind)})
					o.handshake = nil
					o.finish(kind, false)
					return
				}
				if o.phase == 3 && !o.confirmed {
					if o.conn == nil {
						o.epoch--
						o.phase = 2
						o.handshakeCtx()
						o.handshakeCtx = nil
						o.reply(o.handshake, response{err: failure(DialError)})
						o.handshake = nil
						continue
					}
					o.finish(RemoteCloseUnconfirmed, false)
					return
				}
				kind := Transport
				var ne net.Error
				if errors.As(ev.err, &ne) && ne.Timeout() {
					kind = Timeout
				}
				o.detachment(kind)
				continue
			}
			if ev.written {
				if ev.op != nil && o.active == ev.op {
					o.reply(o.active, response{n: o.activeN})
					o.active = nil
				}
				continue
			}
			if e := o.validateFrame(ev.records); e != nil {
				o.setProtocol(e)
				if errorKind(e) == Capacity && o.phase == 1 {
					o.detachment(Capacity)
					continue
				}
				o.finish(errorKind(e), false)
				return
			}
			if e := o.apply(ev); e != nil {
				o.setProtocol(e)
				if errorKind(e) == Capacity && o.phase == 1 {
					o.detachment(Capacity)
					continue
				}
				o.finish(errorKind(e), false)
				return
			}
		case <-tick.C:
			now := o.c.cfg.Now()
			if o.phase == 0 || o.phase == 3 {
				if !now.Before(o.barrier) {
					k := Timeout
					if o.phase == 3 && !o.confirmed {
						k = RemoteCloseUnconfirmed
					}
					o.finish(k, false)
					return
				}
				continue
			}
			if o.phase == 2 {
				if now.Sub(o.detached) >= o.c.cfg.ResumeBudget {
					o.finish(RemoteCloseUnconfirmed, false)
					return
				}
				continue
			}
			if !o.checkpoint.IsZero() && !now.Before(o.checkpoint) {
				o.detachment(Timeout)
				continue
			}
			if o.proofBytes > 0 && !now.Before(o.proofDeadline) {
				if !o.flushProof(false) {
					o.detachment(Capacity)
					continue
				}
			}
			if o.ping != 0 && now.Sub(o.pingAt) >= 30*time.Second {
				o.detachment(Timeout)
				continue
			}
			if o.ping == 0 && now.Sub(o.lastPing) >= 15*time.Second {
				if o.nonce == math.MaxUint64 {
					o.finish(Protocol, false)
					return
				}
				o.nonce++
				o.ping = o.nonce
				o.pingAt = now
				o.lastPing = now
				if !o.sendService(wire.Record{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, o.ping)}}) {
					o.detachment(Capacity)
					continue
				}
				o.readBound()
			}
		}
	}
}
func (o *owner) setProtocol(e error) {
	o.protocolKind = wire.FrameError
	if errors.Is(e, wire.ErrUnsupported) {
		o.protocolKind = wire.Unsupported
	}
}
func errorKind(e error) FailureKind {
	var typed *Error
	if errors.As(e, &typed) {
		return typed.Kind
	}
	return Protocol
}
func (o *owner) write(op operation) {
	if o.phase != 1 || !o.ready || o.active != nil || o.next == 0 {
		o.reply(&op, response{err: failure(StateError)})
		return
	}
	pending := 0
	for _, e := range o.entries {
		if e.command.Key == op.command.Key {
			o.reply(&op, response{err: failure(StateError)})
			return
		}
		if e.result == nil {
			pending++
		}
	}
	if pending >= 8 {
		o.reply(&op, response{err: failure(Capacity)})
		return
	}
	ttl := uint64(5000)
	if op.command.QueueTTLMS != nil {
		ttl = *op.command.QueueTTLMS
	}
	now := o.c.cfg.Now()
	if deadline, ok := op.ctx.Deadline(); ok {
		remaining := time.Until(deadline)
		if remaining <= 0 {
			o.reply(&op, response{err: failure(Timeout)})
			return
		}
		if uint64(remaining/time.Millisecond) < ttl {
			ttl = uint64(remaining / time.Millisecond)
		}
	}
	r := commandRecord(op.command, o.next, o.epoch, ttl)
	data, e := o.c.codec.Encode([]wire.Record{r})
	if e != nil || len(data) > 4096 {
		kind := Protocol
		if len(data) > 4096 {
			kind = Capacity
		}
		o.reply(&op, response{err: failure(kind)})
		return
	}
	own, e := o.c.codec.Decode(data)
	if e != nil {
		o.reply(&op, response{err: failure(Protocol)})
		return
	}
	r = own[0]
	command := op.command
	command.Payload = append([]wire.Field(nil), r.Fields[4:]...)
	command.QueueTTLMS = nil
	n := o.next
	if n == math.MaxUint64 {
		o.next = 0
	} else {
		o.next++
	}
	o.entries[n] = &entry{command: command, n: n, epoch: o.epoch, record: r, deadline: now.Add(5 * time.Second)}
	o.active = &op
	o.activeN = n
	o.writeq <- outgoing{records: []wire.Record{r}, ctx: op.ctx, op: o.active}
}

func (o *owner) sendService(r wire.Record) bool {
	if o.conn == nil {
		return false
	}
	select {
	case o.service <- outgoing{records: []wire.Record{r}, ctx: context.Background()}:
		return true
	default:
		return false
	}
}
func (o *owner) enqueue(b Batch, bytes int) bool {
	bytes += 64 + len(b.Records)*16 + len(b.Receipts)*64
	if len(o.c.updates) == 0 {
		o.deliveredBytes = 0
	}
	if bytes > 262144-o.queueBytes-o.deliveredBytes || len(o.queue) >= 1024 {
		return false
	}
	o.queue = append(o.queue, pendingUpdate{u: Update{Kind: LiveBatch, Batch: b, GatewayEpoch: o.epoch}, bytes: bytes})
	o.queueBytes += bytes
	return true
}
func (o *owner) flushProof(proven bool) bool {
	if len(o.proof.Records) == 0 && len(o.proof.Receipts) == 0 {
		return true
	}
	b := o.proof
	n := o.proofBytes
	o.proof = Batch{}
	o.proofBytes = 0
	o.proofDeadline = time.Time{}
	if !proven {
		b.Receipts = nil
	}
	return o.enqueue(b, n)
}
func (o *owner) validateFrame(rs []wire.Record) error {
	seq := o.seq
	inCheckpoint := o.inCheckpoint
	ended := o.ended
	phase := o.phase
	checkpointBytes := o.checkpointBytes
	results := map[uint64]wire.Record{}
	for _, r := range rs {
		switch r.Type {
		case wire.Ping, wire.Pong, wire.Bye:
		case profile.Opened:
			if phase != 0 && phase != 3 {
				return wire.ErrFrame
			}
			if profile.U(r, 1) == 1 && (!ended || inCheckpoint || profile.U(r, 2) == 0 || profile.U(r, 4) != o.epoch || o.session != 0 && profile.U(r, 2) != o.session) {
				return wire.ErrFrame
			}
			phase = 1
		case wire.Checkpoint:
			if profile.U(r, 2) != o.epoch {
				return wire.ErrFrame
			}
			if profile.U(r, 1) == wire.Begin {
				if inCheckpoint || ended && (phase == 0 || phase == 3) {
					return wire.ErrFrame
				}
				inCheckpoint = true
				checkpointBytes = 0
			} else {
				if !inCheckpoint {
					return wire.ErrFrame
				}
				inCheckpoint = false
				ended = true
			}
		case wire.Fact:
			if (phase == 0 || phase == 3) && !inCheckpoint && !ended {
				return wire.ErrFrame
			}
			if inCheckpoint {
				if r.EncodedBytes > 2097152-checkpointBytes {
					return failure(Capacity)
				}
				checkpointBytes += r.EncodedBytes
			}
			if r.Unsupported {
				return wire.ErrUnsupported
			}
			kind := profile.U(r, 1)
			if kind == 0 || kind > 24 {
				return wire.ErrUnsupported
			}
			if string(profile.Data(r, 5)) != factDomain(kind) || profile.U(r, 4) != o.epoch || profile.U(r, 2) <= seq {
				return wire.ErrFrame
			}
			seq = profile.U(r, 2)
			if profile.Has(r, 67) && (kind != 4 || profile.S(r, 64) != o.self || o.self == 0) {
				return wire.ErrFrame
			}
		case wire.Result:
			if phase == 0 || phase == 3 && !ended {
				return wire.ErrFrame
			}
			e := o.entries[profile.U(r, 1)]
			if e == nil {
				return wire.ErrFrame
			}
			receipt := decodeReceipt(r, e, o.c.cfg.Now(), phase == 3)
			if r.EncodedBytes > 8192 || len(receipt.Effects) > 64 {
				return wire.ErrFrame
			}
			if err := checkReceipt(receipt, e.command); err != nil {
				return err
			}
			if e.result != nil && !sameRecord(*e.result, r) {
				return wire.ErrFrame
			}
			if prev, ok := results[e.n]; ok && !sameRecord(prev, r) {
				return wire.ErrFrame
			}
			results[e.n] = r
		case profile.IntentError:
			return wire.ErrFrame
		default:
			return wire.ErrFrame
		}
	}
	return nil
}
func sameRecord(a, b wire.Record) bool {
	a.EncodedBytes = 0
	b.EncodedBytes = 0
	a.Fields = orderedFields(a.Fields)
	b.Fields = orderedFields(b.Fields)
	return reflect.DeepEqual(a, b)
}
func orderedFields(fields []wire.Field) []wire.Field {
	out := slices.Clone(fields)
	slices.SortFunc(out, func(a, b wire.Field) int { return cmp.Compare(a.Number, b.Number) })
	for i := range out {
		out[i].Value.Fields = orderedFields(out[i].Value.Fields)
		out[i].Value.Items = slices.Clone(out[i].Value.Items)
		for j := range out[i].Value.Items {
			out[i].Value.Items[j].Fields = orderedFields(out[i].Value.Items[j].Fields)
		}
	}
	return out
}
func factDomain(k uint64) string {
	if k <= 10 || k == 24 {
		return "objects"
	}
	switch k {
	case 11, 18:
		return "dialogue"
	case 12, 13:
		return "trade"
	case 14, 15:
		return "inventory"
	case 16, 17:
		return "self"
	case 19:
		return "history"
	case 20, 21:
		return "quests"
	default:
		return "skills"
	}
}
func decodeReceipt(r wire.Record, e *entry, at time.Time, replay bool) Receipt {
	v := Receipt{N: e.n, Key: e.command.Key, GatewayEpoch: e.epoch, Stage: wire.Stage(profile.U(r, 2)), Reason: wire.Reason(profile.U(r, 3)), Delivery: wire.Delivery(profile.U(r, 7)), Requested: profile.U(r, 5), Effective: profile.U(r, 6), ObservedAt: at, Replay: replay, Namespace: string(profile.Data(r, 64))}
	if x, ok := wire.Find(r.Fields, 65); ok {
		v.BridgeReason = &x.U
	}
	if x, ok := wire.Find(r.Fields, 4); ok {
		v.Text = &x
	}
	if x, ok := wire.Find(r.Fields, 66); ok {
		v.EffectsPresent = true
		v.Effects = x.Items
	}
	return v
}
func (o *owner) apply(ev event) error {
	for _, r := range ev.records {
		switch r.Type {
		case wire.Bye:
			kind := StateError
			if profile.U(r, 1) == 5 {
				kind = Authentication
			}
			if o.handshake != nil {
				o.reply(o.handshake, response{err: failure(kind)})
				o.handshake = nil
			}
			return failure(kind)
		case wire.Ping, wire.Pong:
			if r.Type == wire.Pong && o.ping != 0 && profile.U(r, 1) == o.ping {
				o.ping = 0
				o.readBound()
				continue
			}
			now := o.c.cfg.Now()
			if o.serviceAt.IsZero() {
				o.serviceAt = now
			}
			elapsed := now.Sub(o.serviceAt)
			if elapsed < 0 {
				return wire.ErrFrame
			}
			if elapsed >= 2*time.Second {
				o.serviceTokens = 4 * int64(time.Second)
			} else {
				o.serviceTokens += int64(elapsed) * 2
				if o.serviceTokens > 4*int64(time.Second) {
					o.serviceTokens = 4 * int64(time.Second)
				}
			}
			o.serviceAt = now
			if o.serviceTokens < int64(time.Second) {
				return wire.ErrFrame
			}
			o.serviceTokens -= int64(time.Second)
			if r.Type == wire.Ping && !o.sendService(wire.Record{Type: wire.Pong, Fields: r.Fields}) {
				return failure(Capacity)
			}
		case wire.Checkpoint:
			if !o.flushProof(false) {
				return failure(Capacity)
			}
			if profile.U(r, 1) == wire.Begin {
				o.inCheckpoint = true
				o.checkpointBytes = 0
				o.ready = false
				o.checkpoint = o.c.cfg.Now().Add(10 * time.Second)
				if o.phase == 3 {
					o.confirmed = true
				}
				o.readBound()
			} else {
				o.inCheckpoint = false
				o.ended = true
				o.ready = profile.U(r, 3) == 1
				o.checkpoint = time.Time{}
				inc, _ := wire.Find(r.Fields, 4)
				o.snap.Incomplete = nil
				for _, x := range inc.Items {
					o.snap.Incomplete = append(o.snap.Incomplete, string(x.Data))
				}
				if len(o.snap.Incomplete) > 0 {
					o.ready = false
				}
				o.snap.Ready = o.ready
				o.snap.FenceSeq = o.seq
				if o.phase == 1 {
					o.readBound()
				}
			}
			if o.phase == 1 {
				if !o.enqueue(Batch{Records: []wire.Record{r}, ReceivedAt: []time.Time{ev.at}, Snapshot: true}, r.EncodedBytes) {
					return failure(Capacity)
				}
			}
		case wire.Fact:
			o.seq = profile.U(r, 2)
			if o.inCheckpoint {
				o.checkpointBytes += r.EncodedBytes
			}
			if (o.phase == 0 || o.phase == 3) && o.inCheckpoint {
				if r.EncodedBytes > 2097152-o.snap.EncodedBytes || len(o.snap.Facts) >= 65536 {
					return failure(Capacity)
				}
				o.snap.Facts = append(o.snap.Facts, r)
				o.snap.EncodedBytes += r.EncodedBytes
				if profile.U(r, 19) == 1 && profile.U(r, 1) == 1 {
					self := profile.S(r, 64)
					if self <= 0 || o.self != 0 && o.self != self {
						return wire.ErrFrame
					}
					o.self = self
				}
			} else {
				if profile.Has(r, 67) {
					g := profile.U(r, 66)
					if g <= o.g {
						continue
					}
					o.g = g
					o.ready = false
					if !o.flushProof(false) {
						return failure(Capacity)
					}
					if !o.enqueue(Batch{Records: []wire.Record{r}, ReceivedAt: []time.Time{ev.at}}, r.EncodedBytes) {
						return failure(Capacity)
					}
					continue
				}
				pending := false
				deadline := ev.at.Add(5 * time.Second)
				for _, e := range o.entries {
					if e.result == nil && ev.at.Before(e.deadline) {
						pending = true
						if e.deadline.Before(deadline) {
							deadline = e.deadline
						}
					}
				}
				if o.inCheckpoint {
					pending = false
				}
				if pending {
					if o.proofBytes+r.EncodedBytes+o.queueBytes+o.deliveredBytes+64 > 262144 || len(o.proof.Records) >= 1024 {
						return failure(Capacity)
					}
					if o.proofBytes == 0 {
						o.proofDeadline = deadline
					}
					o.proof.Records = append(o.proof.Records, r)
					o.proof.ReceivedAt = append(o.proof.ReceivedAt, ev.at)
					o.proofBytes += r.EncodedBytes + 16
				} else {
					if !o.flushProof(false) {
						return failure(Capacity)
					}
					if !o.enqueue(Batch{Records: []wire.Record{r}, ReceivedAt: []time.Time{ev.at}, Snapshot: o.inCheckpoint}, r.EncodedBytes) {
						return failure(Capacity)
					}
				}
			}
		case wire.Result:
			e := o.entries[profile.U(r, 1)]
			replay := o.phase == 3 || e.result != nil
			receipt := decodeReceipt(r, e, ev.at, replay)
			if e.result == nil {
				copy := r
				e.result = &copy
				o.completed = append(o.completed, e.n)
				if len(o.completed) > 64 {
					delete(o.entries, o.completed[0])
					o.completed = o.completed[1:]
				}
			}
			if o.phase == 3 {
				o.replay = append(o.replay, receipt)
			} else if !replay {
				if len(o.proof.Records) > 0 && ev.at.Before(o.proofDeadline) {
					o.proof.Receipts = append(o.proof.Receipts, receipt)
					o.proofBytes += r.EncodedBytes
					if !o.flushProof(true) {
						return failure(Capacity)
					}
				} else {
					if !o.flushProof(false) {
						return failure(Capacity)
					}
					if !o.enqueue(Batch{Receipts: []Receipt{receipt}}, r.EncodedBytes) {
						return failure(Capacity)
					}
				}
			}
		case profile.Opened:
			if profile.U(r, 1) == 2 {
				err := &Error{Kind: OpenRefused, Code: profile.U(r, 5)}
				o.reply(o.handshake, response{err: err})
				o.handshake = nil
				return err
			}
			if !o.snap.Ready || len(o.snap.Incomplete) > 0 || o.self == 0 {
				return wire.ErrFrame
			}
			o.session = profile.U(r, 2)
			o.confirmed = true
			o.phase = 1
			o.ready = true
			o.lastPing = o.c.cfg.Now()
			o.readBound()
			o.handshakeCtx()
			o.handshakeCtx = nil
			o.reply(o.handshake, response{recovery: Recovery{Snapshot: o.snap, Receipts: o.replay}})
			o.handshake = nil
			o.snap = Snapshot{}
			o.replay = nil
		}
	}
	return nil
}
