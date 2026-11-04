// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"context"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
)

// Write executes one already captured participant effect. Exact commitments
// flow through the same mailbox as continuations, except synchronous refusals
// returned as gateway.WriteRefusal. The gateway owns numbering;
// no raw command keys, receipts, epochs or Resume protocol are needed.
func (s *Session) Write(ctx context.Context, step gateway.Step) error {
	op, ok := step.Value.(operation)
	if !ok {
		return meaning.ErrValue
	}
	if (step.N == 0) != (op.plan == nil) || step.N != 0 && (op.plan.owner != s.state || op.plan.n != step.N || step.Index != 0 || step.Role != gateway.StepEffect) || step.N == 0 && (step.Role != gateway.StepSystem || step.Index != 0) {
		return meaning.ErrValue
	}
	ctx, end, err := s.operation(ctx)
	if err != nil {
		return err
	}
	defer end()
	queueDeadline := time.Time{}
	if step.QueueTTLMS != nil {
		if *step.QueueTTLMS > 5000 {
			return meaning.ErrValue
		}
		queueDeadline = s.cfg.Now().Add(time.Duration(*step.QueueTTLMS) * time.Millisecond)
	}
	scene, err := s.cfg.World.Capture(ctx, s.owner)
	if err != nil {
		return err
	}
	var req world.Request
	if op.plan != nil {
		req = op.plan.request
	} else {
		req, err = request(op.command, scene)
		if err != nil {
			return err
		}
	}
	if step.QueueTTLMS != nil {
		remaining := queueDeadline.Sub(s.cfg.Now())
		// Let the source author an exact expired outcome instead of returning an
		// ambiguous Write error after contact. Zero is the world's unset sentinel;
		// at the clock's first instant, one nanosecond permits only immediate work.
		req.AdmissionDeadline = max(world.Time(1), scene.End.Time+world.Time(max(remaining, 0)/time.Millisecond)*world.Time(time.Millisecond))
	}
	now := s.cfg.Now().Sub(s.cfg.Origin).Milliseconds()
	for id, p := range s.pending {
		if p.deadline <= now {
			delete(s.pending, id)
			s.mailbox.Forget(id)
		}
	}
	if op.plan != nil && op.plan.plan.Effect.Waiting {
		if len(s.pending) >= s.cfg.Meaning.Attempts.Pending {
			return meaning.ErrLimit
		}
		req.Observer = s.mailbox
	}
	commit, err := s.cfg.World.Execute(ctx, req)
	if s.cfg.Handoff != nil {
		s.cfg.Handoff(Handoff{N: step.N, Intent: commit.Intent.ID})
	}
	if req.Observer != nil && commit.Outcome.Stage == world.StageRefused {
		// A failed reservation cannot publish its refusal to the mailbox. Return
		// synchronous refusals directly and retire any successful reservation,
		// including its queued refusal, so neither pending nor replay is needed.
		s.mailbox.Forget(commit.Intent.ID)
		return &gateway.WriteRefusal{Reason: refusal(commit.Outcome.Reason), ObservedMS: max(step.ContactMS, s.tr.gatewayMS(commit.Time))}
	}
	if op.plan != nil && commit.Intent.ID.N != 0 {
		op.plan.contact = step.ContactMS
		op.plan.deadline = step.DeadlineMS
		// Terminal Sent commands never acquire a pending outcome binding.
		if op.plan.plan.Effect.Waiting {
			s.pending[commit.Intent.ID] = op.plan
		}
	}
	// In-memory certainty survives a failed persistence signal. Retain the first
	// external write error for Close, where the host owns fenced save retries.
	if commit.Outcome.Stage == world.StageDone {
		if err != nil {
			s.errMu.Lock()
			if s.writeErr == nil {
				s.writeErr = err
			}
			s.errMu.Unlock()
		}
		return nil
	}
	return err
}

func request(c meaning.Command, s world.Scene) (world.Request, error) {
	req := world.Request{Owner: s.Self.Entity, Ctrl: c.Force}
	switch c.Kind {
	case meaning.Walk:
		req.Verb = world.VerbMove
		req.Position = world.Point{X: c.X, Y: c.Y, Z: c.Z}
	case meaning.Strike:
		req.Verb = world.VerbAttack
	case meaning.Take:
		req.Verb = world.VerbPickup
	case meaning.Talk:
		req.Verb = world.VerbTalk
	case meaning.Say:
		req.Verb = world.VerbChoose
	case meaning.Buy:
		req.Verb = world.VerbBuy
		req.Quantity = c.Count
	case meaning.Sell:
		req.Verb = world.VerbSell
		req.Quantity = c.Count
	case meaning.Rise:
		req.Verb = world.VerbRestart
	case meaning.Cast:
		req.Verb = world.VerbCast
		req.Skill = world.Skill{ID: c.Item, Level: int32(c.Count)}
	case meaning.Learn:
		req.Verb = world.VerbLearn
		req.Skill = world.Skill{ID: c.Item, Level: int32(c.Count)}
	case meaning.UseItem:
		req.Verb = world.VerbUseItem
	default:
		return req, meaning.ErrValue
	}
	if c.Target == s.Self.Entity.ID {
		req.Target = s.Self.Entity
	}
	for _, o := range s.Objects {
		if o.Entity.ID == c.Target {
			req.Target = o.Entity
		}
	}
	if c.Kind == meaning.UseItem {
		for _, it := range s.Inventory {
			if it.Entity.ID == c.Target {
				req.Target = it.Entity
			}
		}
	}
	for _, a := range s.Dialogue.Affordances {
		if a.Verb != req.Verb || a.Expires <= s.End.Time || a.Issued > s.End.Time {
			continue
		}
		matches := false
		switch c.Kind {
		case meaning.Say:
			action, ok := choiceAction(a)
			matches = ok && action == c.Text && a.Target == req.Target
		case meaning.Learn:
			id, level, ok := lesson(a)
			matches = ok && id == c.Item && int64(level) == c.Count && c.List == 0
		case meaning.Buy, meaning.Sell:
			terms, ok := a.Trade()
			matches = ok && terms.List == c.List && terms.Item == c.Item && (c.Kind != meaning.Sell || terms.Object == c.Target)
		}
		if matches {
			if req.AffordanceID != 0 {
				return req, errSource
			}
			req.AffordanceID = a.ID
			req.Target = a.Target
		}
	}
	return req, nil
}

// CommandValue returns the captured command for a trusted instrumentation
// wrapper. Foreign values fail without exposing their contents.
func CommandValue(step gateway.Step) (meaning.Command, error) {
	op, ok := step.Value.(operation)
	if !ok {
		return meaning.Command{}, meaning.ErrValue
	}
	return op.command, nil
}
