// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type plan struct {
	owner                              *state
	inner                              *gateway.Action
	n                                  uint64
	effectKey                          uint64
	helperSent, helperDone, effectDone bool
	receiptMS                          int64
	contact, deadline                  int64
}
type operation struct {
	value meaning.Operation
	plan  *plan
	index int
}

type state struct {
	*meaning.State
	world         *World
	input         meaning.Input
	self          int32
	g, epoch, seq uint64
	ready         bool
}

func (s *Session) scope(snapshot bridge.Snapshot) (*World, gateway.State, error) {
	if !snapshot.Ready || snapshot.GatewayEpoch == 0 || len(snapshot.Incomplete) != 0 || len(snapshot.Facts) > 65536 || snapshot.EncodedBytes < 0 || snapshot.EncodedBytes > 2097152 {
		return nil, nil, wire.ErrFrame
	}
	w := &World{session: s, updates: make(chan gateway.Update, 8), stop: make(chan struct{}), done: make(chan struct{}), pending: make(map[uint64]pending)}
	st := &state{State: s.projection, world: w, epoch: snapshot.GatewayEpoch, seq: snapshot.FenceSeq}
	w.state = st
	now := s.cfg.Now().Sub(s.cfg.Origin).Milliseconds()
	codec, err := profile.NewProfileCodec()
	if err != nil {
		return nil, nil, err
	}
	var seq uint64
	bytes := 0
	for _, r := range snapshot.Facts {
		if profile.U(r, 4) != st.epoch || profile.U(r, 2) <= seq || profile.U(r, 2) > snapshot.FenceSeq {
			return nil, nil, wire.ErrFrame
		}
		seq = profile.U(r, 2)
		b, err := codec.Encode([]wire.Record{r})
		if err != nil {
			return nil, nil, err
		}
		if len(b) > 2097152-bytes {
			return nil, nil, wire.ErrFrame
		}
		bytes += len(b)
		e, err := event(r)
		if err != nil {
			return nil, nil, err
		}
		if e.Kind == meaning.Appeared && e.Mine {
			if st.self != 0 || e.ID <= 0 {
				return nil, nil, wire.ErrFrame
			}
			st.self = e.ID
		}
		// Snapshot proposals retain the original session-relative birth. This
		// conservative lower bound cannot renew them after recovery.
		input, err := st.eventInput(r, e, now)
		if err != nil {
			return nil, nil, err
		}
		if _, err = st.State.Apply(gateway.Update{Value: input}, now); err != nil {
			return nil, nil, err
		}
		if e.Mine && profile.Has(r, 70) && profile.U(r, 70) == 0 {
			if _, err = st.State.Apply(gateway.Update{Value: meaning.Input{Event: meaning.Event{Kind: meaning.Died, ID: e.ID, MS: e.MS}, ReceivedAt: now}}, now); err != nil {
				return nil, nil, err
			}
		}
	}
	if st.self == 0 {
		return nil, nil, wire.ErrFrame
	}
	// The ready barrier certifies visibility and absent capabilities. Lists
	// still need explicit complete, present captures (including known empty).
	if _, err := st.State.Apply(gateway.Update{Value: meaning.Input{Complete: []string{"objects", "self", "trade", "dialogue"}, ReceivedAt: now}}, now); err != nil {
		return nil, nil, err
	}
	projection, err := st.State.Snapshot(now)
	if err != nil || !projection.Ready {
		return nil, nil, wire.ErrFrame
	}
	st.ready = true
	return w, st, nil
}

func (s *state) eventInput(r wire.Record, e meaning.Event, at int64) (meaning.Input, error) {
	in := meaning.Input{Event: e, ReceivedAt: at}
	if e.Kind != meaning.Spoke && e.Kind != meaning.Shop && e.Kind != meaning.Offer && e.Kind != meaning.Practice {
		return in, nil
	}
	if !profile.Has(r, 74) {
		return in, wire.ErrFrame
	}
	cfg := s.world.session.cfg
	ms := profile.S(r, 74)
	if ms < 0 || ms > (1<<63-1)/int64(time.Millisecond)-60000 {
		return in, wire.ErrFrame
	}
	expiry, ok := bridge.ProposalExpiry(cfg.Origin, cfg.Origin.Add(time.Duration(at)*time.Millisecond), int64(ms))
	if !ok {
		return in, wire.ErrFrame
	}
	born := expiry.Sub(cfg.Origin).Milliseconds() - 60000
	if born < 0 {
		return in, wire.ErrFrame
	}
	in.ProposalBorn = &born
	return in, nil
}

func (s *state) Apply(u gateway.Update, now int64) (gateway.Observation, error) {
	p, ok := u.Value.(packet)
	if !ok {
		return gateway.Observation{}, meaning.ErrValue
	}
	s.input = meaning.Input{ReceivedAt: now}
	if p.kind == bridge.Detached {
		return gateway.Observation{Detached: true}, nil
	}
	if p.kind == bridge.Closed {
		return gateway.Observation{WorldClosed: true}, nil
	}
	if p.kind != bridge.LiveBatch {
		return gateway.Observation{}, wire.ErrFrame
	}
	var failures []gateway.Failure
	for _, r := range p.receipts {
		binding, ok := s.world.pending[r.Key]
		if !ok || r.Replay {
			continue
		}
		if r.N != binding.n || r.Requested != binding.verb || r.Effective != binding.verb || r.GatewayEpoch != s.epoch {
			return gateway.Observation{}, wire.ErrFrame
		}
		delete(s.world.pending, r.Key)
		at := r.ObservedAt.Sub(s.world.session.cfg.Origin).Milliseconds()
		if at < binding.contact || at >= binding.deadline && binding.deadline != 0 {
			continue
		}
		plan := binding.plan
		if binding.role == gateway.StepHelper && r.Stage == wire.StageDone {
			plan.helperDone = true
		}
		if binding.role == gateway.StepEffect && r.Stage == wire.StageDone {
			plan.effectDone = true
			plan.receiptMS = at
		}
		if binding.role == gateway.StepSystem {
			continue
		}
		reason := wire.ReasonNone
		text := ""
		switch {
		case r.BridgeReason != nil && (*r.BridgeReason == 9 || *r.BridgeReason == 10):
			return gateway.Observation{WorldClosed: true}, nil
		case r.Stage == wire.StageUnrecoverable || r.Reason == wire.ReasonBackendError:
			reason = wire.ReasonBackendError
		case binding.role == gateway.StepHelper && (r.Stage == wire.StageRefused || r.Stage == wire.StageUnknown):
			reason = wire.ReasonTargetUnconfirmed
		case r.Stage == wire.StageRefused:
			reason = wire.ReasonInterrupted
			text = "The world rejected the request after handoff."
		case r.Reason == wire.ReasonInterrupted:
			reason = wire.ReasonInterrupted
		}
		if reason != wire.ReasonNone {
			failures = append(failures, gateway.Failure{N: plan.n, Reason: reason, Text: text})
		}
	}
	if p.record.Type == 0 {
		return gateway.Observation{Failures: failures}, nil
	}
	r := p.record
	if p.at.IsZero() {
		return gateway.Observation{}, wire.ErrFrame
	}
	at := p.at.Sub(s.world.session.cfg.Origin).Milliseconds()
	s.input.ReceivedAt = at
	if r.Type == wire.Checkpoint {
		if profile.U(r, 2) != s.epoch {
			return gateway.Observation{}, wire.ErrFrame
		}
		if profile.U(r, 1) == uint64(wire.CheckpointBegin) {
			s.ready = false
		}
		incomplete, _ := wire.Find(r.Fields, 4)
		for _, v := range incomplete.Items {
			s.input.Incomplete = append(s.input.Incomplete, string(v.Data))
		}
		if profile.U(r, 1) == uint64(wire.CheckpointEnd) && profile.U(r, 3) == 1 && len(incomplete.Items) == 0 {
			s.ready = true
			s.input.Complete = []string{"objects", "self", "trade", "dialogue"}
		}
		_, err := s.State.Apply(gateway.Update{Value: s.input}, now)
		return gateway.Observation{Failures: failures}, err
	}
	e, err := event(r)
	if err != nil {
		return gateway.Observation{}, err
	}
	if profile.U(r, 4) != s.epoch {
		return gateway.Observation{}, wire.ErrFrame
	}
	seq := profile.U(r, 2)
	if seq <= s.seq {
		return gateway.Observation{}, wire.ErrFrame
	}
	s.seq = seq
	if profile.Has(r, 67) {
		g := profile.U(r, 66)
		if e.Kind != meaning.Corrected || e.ID != s.self || g == 0 {
			return gateway.Observation{}, wire.ErrFrame
		}
		if g <= s.g {
			return gateway.Observation{Failures: failures}, nil
		}
		s.g = g
		s.ready = false
	}
	s.input, err = s.eventInput(r, e, at)
	if err != nil {
		return gateway.Observation{}, err
	}
	if !p.snapshot && s.world.session.cfg.Source != nil {
		s.input.Source = s.world.session.cfg.Source(e)
	}
	if u.Relocate && s.input.Source.Return {
		p := s.world.returnPlan
		if p != nil && at >= p.contact && at < p.deadline {
			s.input.Returned = p.n
		}
	}
	obs, err := s.State.Apply(gateway.Update{Value: s.input}, now)
	obs.Failures = failures
	return obs, err
}

func (s *state) Snapshot(now int64) (gateway.Snapshot, error) {
	p, err := s.State.Snapshot(now)
	p.Ready = p.Ready && s.ready
	if !p.Ready && len(p.Domains) == 0 {
		// A raw checkpoint barrier is outstanding even if the retained
		// dictionary happens to contain all required domains.
		p.Domains = []string{"objects"}
	}
	return p, err
}

func (s *state) Plan(r wire.Record, now int64) (*gateway.Action, wire.Reason) {
	a, reason := s.State.Plan(r, now)
	if a == nil {
		return nil, reason
	}
	p := &plan{owner: s, inner: a, n: profile.U(r, 1)}
	out := *a
	out.Value = p
	out.Steps = append([]gateway.Step(nil), a.Steps...)
	for i := range out.Steps {
		op, err := meaning.OperationValue(out.Steps[i])
		if err != nil {
			return nil, wire.ReasonInvalid
		}
		if op.Command.Kind == meaning.Cast {
			captured, err := s.State.Describe(a)
			if err != nil {
				return nil, wire.ReasonInvalid
			}
			op.Command.Count = int64(captured.SkillLevel)
		}
		out.Steps[i].Value = operation{value: op, plan: p, index: i}
	}
	return &out, reason
}

func (s *state) plan(a *gateway.Action) (*plan, error) {
	if a == nil {
		return nil, meaning.ErrValue
	}
	p, ok := a.Value.(*plan)
	if !ok || p == nil || p.owner != s {
		return nil, meaning.ErrValue
	}
	return p, nil
}
func (s *state) Validate(a *gateway.Action, now int64) wire.Reason {
	p, err := s.plan(a)
	if err != nil {
		return wire.ReasonInvalid
	}
	return s.State.Validate(p.inner, now)
}
func (s *state) Start(a *gateway.Action) error {
	p, err := s.plan(a)
	if err != nil {
		return err
	}
	return s.State.Start(p.inner)
}
func (s *state) Confirmed(a *gateway.Action) (bool, error) {
	p, err := s.plan(a)
	if err != nil {
		return false, err
	}
	confirmed, err := s.State.Confirmed(p.inner)
	return confirmed && (!p.helperSent || p.helperDone), err
}
func (s *state) Witness(a *gateway.Action, u gateway.Update) (gateway.Witness, error) {
	p, err := s.plan(a)
	if err != nil {
		return gateway.WitnessNone, err
	}
	packet, ok := u.Value.(packet)
	if !ok {
		return gateway.WitnessNone, meaning.ErrValue
	}
	in := s.input
	if !packet.snapshot && p.effectKey != 0 && profile.U(packet.record, 75) == p.effectKey && in.ReceivedAt >= p.contact && in.ReceivedAt < p.deadline {
		switch p.inner.Effective {
		case 14:
			in.Used = p.n
		case 6:
			in.Chosen = p.n
		}
	}
	if p.effectDone && in.ReceivedAt >= p.contact && in.ReceivedAt < p.deadline {
		in.Completed = p.n
	}
	return s.State.Witness(p.inner, gateway.Update{Value: in, Relocate: u.Relocate})
}
func (s *state) CandidateValid(a *gateway.Action) (bool, error) {
	p, err := s.plan(a)
	if err != nil {
		return false, err
	}
	return s.State.CandidateValid(p.inner)
}
func (s *state) Proof(a *gateway.Action, now int64) (wire.Record, error) {
	p, err := s.plan(a)
	if err != nil {
		return wire.Record{}, err
	}
	return s.State.Proof(p.inner, now)
}
func (s *state) Stop() *gateway.Step {
	step := s.State.Stop()
	if step == nil {
		return nil
	}
	op, err := meaning.OperationValue(*step)
	if err != nil {
		return nil
	}
	step.Value = operation{value: op, plan: &plan{owner: s}}
	return step
}

var _ gateway.State = (*state)(nil)
