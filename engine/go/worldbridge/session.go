// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"errors"
	"math"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

const (
	operationTimeout    = 10 * time.Second
	maxBacklogBytes     = 256 << 10
	maxBacklogUpdates   = 1024
	maxSessionKeys      = 4096
	maxRetainedReceipts = 64
)

type pending struct {
	update bridge.Update
	bytes  int
}

// session is touched only by run, including all operation closures. Client
// fields are either immutable, atomic, or published after done closes.
type session struct {
	client                                  *Client
	tr                                      translator
	scene                                   world.Scene
	sub                                     world.Subscription
	origin                                  world.Time
	epoch, nextFact, nextAttempt, sourceSeq uint64
	keys                                    map[uint64]bool
	receipts                                []bridge.Receipt
	queue                                   []pending
	queueBytes, deliveredBytes              int
	detached                                time.Time
	ended                                   bool
	failure                                 bridge.FailureKind
}

func (s *session) run(ctx context.Context) {
	c := s.client
	defer close(c.done)
	defer c.cancel()
	defer func() {
		cleanup, cancel := context.WithTimeout(context.Background(), c.cfg.CloseBudget)
		defer cancel()
		c.closeErr = c.cfg.World.Leave(cleanup, c.owner)
		if c.closeErr != nil && s.failure == 0 {
			s.failure = bridge.RemoteCloseUnconfirmed
		}
		select {
		case <-c.updates:
		default:
		}
		c.updates <- bridge.Update{Kind: bridge.Closed, FailureKind: s.failure, GatewayEpoch: s.epoch}
		close(c.updates)
	}()
	timer := time.NewTimer(time.Hour)
	defer timer.Stop()
	for !s.ended {
		var output chan bridge.Update
		var next bridge.Update
		if len(s.queue) != 0 {
			output = c.updates
			next = s.queue[0].update
		}
		wait := time.Hour
		if !s.detached.IsZero() {
			wait = s.detached.Add(c.cfg.ResumeBudget).Sub(c.cfg.Now())
			if wait <= 0 {
				s.failure = bridge.Timeout
				return
			}
		}
		timer.Reset(wait)
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
		case op := <-c.requests:
			op.reply <- op.call(ctx, s)
		case output <- next:
			s.deliveredBytes = s.queue[0].bytes
			s.queueBytes -= s.queue[0].bytes
			s.queue[0] = pending{}
			s.queue = s.queue[1:]
		case e, ok := <-s.sub.Events:
			if !ok {
				s.endSubscription()
				continue
			}
			if err := s.event(ctx, e); err != nil {
				if ctx.Err() != nil {
					return
				}
				s.sourceFailure(err)
			}
		}
	}
}

func errorKind(err error) bridge.FailureKind {
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return bridge.Timeout
	}
	if errors.Is(err, errCapacity) {
		return bridge.Capacity
	}
	if errors.Is(err, world.ErrStopped) || errors.Is(err, world.ErrNotPresent) {
		return bridge.StateError
	}
	return bridge.Protocol
}

func (s *session) sourceFailure(err error) {
	if errors.Is(err, world.ErrStopped) || errors.Is(err, world.ErrNotPresent) {
		s.ended = true
		s.failure = bridge.StateError
		return
	}
	s.detach(errorKind(err))
}

func (s *session) endSubscription() {
	reason := <-s.sub.End
	if reason == world.CloseOverflow || reason == world.CloseReplaced {
		s.detach(bridge.Capacity)
		return
	}
	s.sub = world.Subscription{}
	s.failure = bridge.StateError
	s.ended = true
}

func (s *session) detach(kind bridge.FailureKind) {
	if !s.detached.IsZero() || s.ended {
		return
	}
	s.detached = s.client.cfg.Now()
	s.sub = world.Subscription{}
	s.queue = nil
	s.queueBytes, s.deliveredBytes = 0, 0
	select {
	case <-s.client.updates:
	default:
	}
	s.queue = []pending{{bridge.Update{Kind: bridge.Detached, FailureKind: kind, GatewayEpoch: s.epoch}, 64}}
	s.queueBytes = 64
}

func (s *session) enqueue(batch bridge.Batch) error {
	bytes := 64 + len(batch.Records)*16 + len(batch.Receipts)*64
	for _, r := range batch.Records {
		bytes += r.EncodedBytes
	}
	for _, receipt := range batch.Receipts {
		records, err := encode([]wire.Record{receiptRecord(receipt)})
		if err != nil {
			return err
		}
		bytes += records[0].EncodedBytes
	}
	unread := len(s.client.updates)
	if unread == 0 {
		s.deliveredBytes = 0
	}
	if bytes > maxBacklogBytes-s.queueBytes-s.deliveredBytes || len(s.queue)+unread >= maxBacklogUpdates {
		return errCapacity
	}
	s.queue = append(s.queue, pending{bridge.Update{Kind: bridge.LiveBatch, Batch: batch, GatewayEpoch: s.epoch}, bytes})
	s.queueBytes += bytes
	return nil
}

func (s *session) batch(records []wire.Record, snapshot bool) error {
	if len(records) == 0 {
		return nil
	}
	at := s.client.cfg.Now()
	times := make([]time.Time, len(records))
	for i := range times {
		times[i] = at
	}
	return s.enqueue(bridge.Batch{Records: records, ReceivedAt: times, Snapshot: snapshot})
}

// drain consumes only the bounded queue already present. It does not wait for
// future publications, and checks closure even with a nonempty buffered stream.
func (s *session) drain(ctx context.Context) error {
	for range 64 {
		select {
		case reason, ok := <-s.sub.End:
			if ok && (reason == world.CloseOverflow || reason == world.CloseReplaced) {
				s.detach(bridge.Capacity)
			} else {
				s.ended = true
				s.failure = bridge.StateError
			}
			return portError(bridge.StateError)
		default:
		}
		select {
		case e, ok := <-s.sub.Events:
			if !ok {
				s.endSubscription()
				return portError(bridge.StateError)
			}
			if err := s.event(ctx, e); err != nil {
				s.sourceFailure(err)
				return err
			}
		default:
			return nil
		}
	}
	return nil
}

func (s *session) write(ctx, lifetime context.Context, command bridge.Command, deadline time.Time) result {
	if s.ended || !s.detached.IsZero() {
		return result{err: portError(bridge.StateError)}
	}
	if s.keys[command.Key] {
		return result{err: portError(bridge.StateError)}
	}
	if s.nextAttempt == 0 || len(s.keys) >= maxSessionKeys {
		return result{err: portError(bridge.Capacity)}
	}
	if err := ctx.Err(); err != nil {
		return result{err: err}
	}
	// Notifications belong to the session, not the command being admitted.
	// A canceled command must not cancel another operation's teleport fence.
	if err := s.drain(lifetime); err != nil {
		return result{err: err}
	}
	scene, err := s.client.cfg.World.Capture(ctx, s.client.owner)
	if err != nil {
		if errors.Is(err, world.ErrStopped) || errors.Is(err, world.ErrNotPresent) {
			s.ended = true
			s.failure = bridge.StateError
		}
		return result{err: err}
	}
	if scene.Self.Teleporting {
		// A concurrent host teleport can arrive between drain and Capture.
		// Publish its original correction before Appear/checkpoint, never make
		// a ready checkpoint that bypasses the relocation's live fence.
		if err := s.drain(lifetime); err != nil {
			return result{err: err}
		}
		scene, err = s.client.cfg.World.Capture(ctx, s.client.owner)
		if err != nil {
			return result{err: err}
		}
		if scene.Self.Teleporting {
			return result{err: portError(bridge.StateError)}
		}
	}
	remaining := deadline.Sub(s.client.cfg.Now())
	if remaining <= 0 {
		return result{err: context.DeadlineExceeded}
	}
	// Millisecond rounding only shortens the original budget. The world's
	// marker supplies the absolute world-time origin, not a guessed wall clock.
	ttl := uint64(remaining / time.Millisecond)
	command.QueueTTLMS = &ttl
	p, err := prepare(command, scene, scene.End.Time)
	if err != nil {
		return result{err: err}
	}
	if err := ctx.Err(); err != nil {
		return result{err: err}
	}
	n := s.nextAttempt
	s.nextAttempt++ // uint64 wraps to zero, an exhausted state, never reused.
	s.keys[command.Key] = true
	var commit world.Commit
	if p.refusal == "" {
		commit, err = s.client.cfg.World.Execute(ctx, p.request)
	}
	r, translationErr := s.tr.receipt(p, commit, err, n, s.epoch, s.client.cfg.Now())
	if translationErr != nil {
		s.detach(errorKind(translationErr))
		return result{n: n, err: translationErr}
	}
	// Retain an independent canonical receipt before exposing it or draining
	// observations: overflow cannot lose the result or trigger reexecution.
	s.receipts = append(s.receipts, cloneReceipt(r))
	if len(s.receipts) > maxRetainedReceipts {
		s.receipts[0] = bridge.Receipt{}
		s.receipts = s.receipts[1:]
	}
	// Port receipts are typed evidence, as in bridge.Client. Encoding them
	// again as observation records would make consumers decode Result as Fact.
	if enqueueErr := s.enqueue(bridge.Batch{Receipts: []bridge.Receipt{r}}); enqueueErr != nil {
		s.detach(bridge.Capacity)
		return result{n: n, err: errors.Join(err, enqueueErr)}
	}
	if drainErr := s.drain(lifetime); drainErr != nil {
		return result{n: n, err: errors.Join(err, drainErr)}
	}
	return result{n: n, err: err}
}

func (s *session) resume(ctx context.Context) result {
	if s.ended || s.detached.IsZero() {
		return result{err: portError(bridge.StateError)}
	}
	if !s.client.cfg.Now().Before(s.detached.Add(s.client.cfg.ResumeBudget)) {
		s.ended = true
		s.failure = bridge.Timeout
		return result{err: portError(bridge.Timeout)}
	}
	if s.epoch == math.MaxUint64 {
		return result{err: portError(bridge.Capacity)}
	}
	expires := s.detached.Add(s.client.cfg.ResumeBudget)
	ctx, cancel := context.WithTimeout(ctx, expires.Sub(s.client.cfg.Now()))
	defer cancel()
	fail := func(err error) result {
		s.sub = world.Subscription{}
		if !s.client.cfg.Now().Before(expires) {
			s.ended = true
			s.failure = bridge.Timeout
			err = errors.Join(err, portError(bridge.Timeout))
		}
		return result{err: err}
	}
	if err := ctx.Err(); err != nil {
		return fail(err)
	}
	scene, sub, err := s.client.cfg.World.Subscribe(ctx, s.client.owner)
	if err != nil {
		if errors.Is(err, world.ErrStopped) || errors.Is(err, world.ErrNotPresent) {
			s.ended = true
			s.failure = bridge.StateError
		}
		return fail(err)
	}
	// Relocation is an internal fenced operation, never a participant command.
	s.sub = sub
	if err := ctx.Err(); err != nil {
		return fail(err)
	}
	if scene.Self.Teleporting {
		if err := s.completeRelocation(ctx, scene.End.Fence); err != nil {
			return fail(err)
		}
		scene, sub, err = s.client.cfg.World.Subscribe(ctx, s.client.owner)
		if err != nil {
			return fail(err)
		}
	}
	snap, next, err := s.tr.checkpoint(scene, s.epoch+1, s.nextFact, s.origin)
	if err != nil || !snap.Ready {
		return fail(errors.Join(err, errSource))
	}
	replay := make([]bridge.Receipt, len(s.receipts))
	for i, r := range s.receipts {
		replay[i] = cloneReceipt(r)
		replay[i].Replay = true
	}
	if err := ctx.Err(); err != nil {
		return fail(err)
	}
	// The original loss deadline bounds completion as well as admission.
	// An admitted world step can finish late, but cannot renew this session.
	if !s.client.cfg.Now().Before(expires) {
		return fail(portError(bridge.Timeout))
	}
	s.epoch++
	s.nextFact, s.sourceSeq = next, scene.End.Seq
	s.scene, s.sub = scene, sub
	s.detached = time.Time{}
	s.queue = nil
	s.queueBytes, s.deliveredBytes = 0, 0
	select {
	case <-s.client.updates:
	default:
	}
	return result{recovery: bridge.Recovery{Snapshot: snap, Receipts: replay}}
}

func cloneReceipt(r bridge.Receipt) bridge.Receipt {
	r.Effects = cloneValues(r.Effects)
	if r.BridgeReason != nil {
		code := *r.BridgeReason
		r.BridgeReason = &code
	}
	if r.Text != nil {
		text := *r.Text
		text.Fields = cloneFields(text.Fields)
		text.Items = cloneValues(text.Items)
		text.Data = slices.Clone(text.Data)
		r.Text = &text
	}
	return r
}
func cloneFields(fields []wire.Field) []wire.Field {
	out := slices.Clone(fields)
	for i := range out {
		out[i].Value = cloneValues([]wire.Value{out[i].Value})[0]
	}
	return out
}
func cloneValues(values []wire.Value) []wire.Value {
	out := slices.Clone(values)
	for i := range out {
		out[i].Fields = cloneFields(out[i].Fields)
		out[i].Items = cloneValues(out[i].Items)
		out[i].Data = slices.Clone(out[i].Data)
	}
	return out
}

func (s *session) event(ctx context.Context, e world.Event) error {
	if e.Seq() == 0 {
		return errSource
	}
	if e.Seq() <= s.sourceSeq {
		return nil
	} // captured by an atomic checkpoint
	if e.Time() < s.origin {
		return errSource
	}
	s.sourceSeq = e.Seq()
	if err := s.project(e); err != nil {
		return err
	}
	fs, err := s.tr.events(e, s.scene, s.origin)
	if err != nil {
		return err
	}
	rs, next, err := factRecords(fs, s.epoch, s.nextFact, e.Time(), s.origin)
	if err != nil {
		return err
	}
	s.nextFact = next
	if err := s.batch(rs, false); err != nil {
		return err
	}
	if v, ok := e.(world.Teleported); ok && v.Entity == s.client.owner {
		request, cancel := context.WithTimeout(ctx, operationTimeout)
		defer cancel()
		return s.relocate(request, v.Fence)
	}
	return nil
}

// project retains only the captured context needed by notification translation.
// It is not an outcome ledger or a substitute for Capture before commands.
func (s *session) project(e world.Event) error {
	switch v := e.(type) {
	case world.SelfChanged:
		s.scene.Self = v.Self
	case world.NPCSeen:
		return s.replaceObject(world.Observation{Entity: v.NPC.Entity, State: v})
	case world.PlayerSeen:
		return s.replaceObject(world.Observation{Entity: v.Player.Entity, State: v})
	case world.ObjectGone:
		s.scene.Objects = slices.DeleteFunc(s.scene.Objects, func(o world.Observation) bool { return o.Entity.ID == v.ObjectID })
	case world.Moving:
		s.position(v.ObjectID, v.From)
	case world.Following:
		s.position(v.ObjectID, v.From)
	case world.Stopped:
		s.position(v.ObjectID, v.Pos)
	case world.PositionObserved:
		s.position(v.ObjectID, v.Pos)
	case world.Teleported:
		s.position(v.ObjectID, v.Pos)
	case world.TargetChosen:
		s.position(v.ObjectID, v.Pos)
	case world.TargetCleared:
		s.position(v.ObjectID, v.Pos)
	case world.PostureChanged:
		s.position(v.ObjectID, v.Pos)
	case world.AttackStarted:
		s.position(v.AttackerID, v.From)
		s.position(v.TargetID, v.TargetPos)
	case world.SkillCast:
		s.position(v.CasterID, v.From)
		s.position(v.TargetID, v.TargetPos)
	case world.Died:
		s.actor(v.ObjectID, func(p *world.Self) { p.Dead = true }, func(n *world.NPCView) { n.Dead = true })
	case world.Revived:
		s.actor(v.ObjectID, func(p *world.Self) { p.Dead = false }, func(n *world.NPCView) { n.Dead = false })
	case world.GaitChanged:
		s.actor(v.ObjectID, func(p *world.Self) { p.Running = v.Running }, func(n *world.NPCView) { n.Running = v.Running })
	}
	return nil
}

// actor changes only source-observed fields. It never captures later state
// under an earlier event stamp or derives positions by extrapolating movement.
func (s *session) actor(id int32, player func(*world.Self), npc func(*world.NPCView)) {
	if id == s.scene.Self.Entity.ID {
		player(&s.scene.Self)
	}
	for i, o := range s.scene.Objects {
		if o.Entity.ID != id {
			continue
		}
		switch v := o.State.(type) {
		case world.PlayerSeen:
			player(&v.Player)
			s.scene.Objects[i].State = v
		case world.NPCSeen:
			npc(&v.NPC)
			s.scene.Objects[i].State = v
		}
	}
}

func (s *session) position(id int32, pos world.Point) {
	s.actor(id, func(p *world.Self) { p.Char.X, p.Char.Y, p.Char.Z = pos.X, pos.Y, pos.Z }, func(n *world.NPCView) { n.Pos = pos })
}
func (s *session) replaceObject(o world.Observation) error {
	for i := range s.scene.Objects {
		if s.scene.Objects[i].Entity.ID == o.Entity.ID {
			s.scene.Objects[i] = o
			return nil
		}
	}
	if len(s.scene.Objects) >= maxRows {
		return errCapacity
	}
	s.scene.Objects = append(s.scene.Objects, o)
	return nil
}

func (s *session) completeRelocation(ctx context.Context, fence world.Fence) error {
	c, err := s.client.cfg.World.Execute(ctx, world.Request{Owner: s.client.owner, Verb: world.VerbAppear, Fence: fence})
	if err != nil {
		return err
	}
	if c.Outcome.Stage != world.StageDone {
		return errSource
	}
	return nil
}

func checkpointRecord(phase wire.CheckpointPhase, epoch uint64, ready bool) wire.Record {
	return wire.Record{Type: wire.Checkpoint, Fields: []wire.Field{wire.U(1, true, uint64(phase)), wire.U(2, true, epoch), boolean(3, ready)}}
}

func (s *session) relocate(ctx context.Context, fence world.Fence) error {
	if fence == 0 {
		return errSource
	}
	if err := s.completeRelocation(ctx, fence); err != nil {
		return err
	}
	// Preserve live records emitted by Appear (notably Revived) before adopting
	// the barrier. They remain live, never synthesized from the snapshot.
	for range 64 {
		select {
		case e, ok := <-s.sub.Events:
			if !ok {
				return errSource
			}
			if e.Seq() <= s.sourceSeq {
				continue
			}
			if v, ok := e.(world.Teleported); ok && v.Entity == s.client.owner {
				return errSource
			}
			if err := s.event(ctx, e); err != nil {
				return err
			}
		default:
			goto drained
		}
	}
drained:
	scene, err := s.client.cfg.World.Capture(ctx, s.client.owner)
	if err != nil {
		return err
	}
	if scene.End.Fence != fence {
		return errSource
	}
	snap, next, err := s.tr.checkpoint(scene, s.epoch, s.nextFact, s.origin)
	if err != nil {
		return err
	}
	if !snap.Ready {
		return errSource
	}
	markers, err := encode([]wire.Record{checkpointRecord(wire.CheckpointBegin, s.epoch, false), checkpointRecord(wire.CheckpointEnd, s.epoch, true)})
	if err != nil {
		return err
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	// Begin may be appended to by its consumer while End is still queued.
	// Limit capacity so that append cannot overwrite the future marker.
	if err := s.batch(markers[:1:1], true); err != nil {
		return err
	}
	if err := s.batch(snap.Facts, true); err != nil {
		return err
	}
	if err := s.batch(markers[1:], true); err != nil {
		return err
	}
	s.nextFact = next
	s.sourceSeq = scene.End.Seq
	s.scene = scene
	return nil
}
