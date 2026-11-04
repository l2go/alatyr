// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"errors"
	"testing"
)

func TestMailboxCoalescesStateAndPreservesEventBarriers(t *testing.T) {
	m := newMailbox(8, 2)
	m.publish(PositionObserved{ObjectID: 1, Pos: Point{X: 1}})
	m.publish(Vitals{ObjectID: 2, HP: 10})
	m.publish(PositionObserved{ObjectID: 1, Pos: Point{X: 2}})
	m.publish(AttackStarted{AttackerID: 1, TargetID: 2})
	m.publish(PositionObserved{ObjectID: 1, Pos: Point{X: 3}})
	var got []Event
	for {
		p, ok := m.Next()
		if !ok {
			break
		}
		got = append(got, p.Event)
	}
	if len(got) != 4 {
		t.Fatalf("publications=%d, want 4", len(got))
	}
	if _, ok := got[0].(Vitals); !ok {
		t.Fatalf("first=%T, want Vitals", got[0])
	}
	for i, want := range map[int]int32{1: 2, 3: 3} {
		e, ok := got[i].(PositionObserved)
		if !ok || e.Pos.X != want {
			t.Fatalf("position at %d=%v, want %d", i, got[i], want)
		}
	}
	if _, ok := got[2].(AttackStarted); !ok {
		t.Fatalf("barrier=%T, want AttackStarted", got[2])
	}
}

func TestMailboxOverflowPreservesExactOutcome(t *testing.T) {
	m := newMailbox(3, 1)
	id := IntentID{Owner: Ref{ID: 1, Gen: 1}, N: 1}
	if !m.watch(id) {
		t.Fatal("outcome subscription refused")
	}
	m.publish(DialoguePage{})
	m.outcome(Commit{Sequence: 1, Outcome: Outcome{Intent: id, Stage: StageDone}})
	if m.watch(IntentID{Owner: id.Owner, N: 2}) {
		t.Fatal("queued outcome released its capacity before consumption")
	}
	for range 4 {
		m.publish(Announcement{Text: "event"})
	}
	p, ok := m.Next()
	if !ok || !p.Resync {
		t.Fatal("overflow did not request a checkpoint")
	}
	if m.Closed() {
		t.Fatal("event overflow closed the observer")
	}
	m.fence()
	p, ok = m.Next()
	if !ok || p.Commit.Outcome.Intent != id || p.Commit.Outcome.Stage != StageDone {
		t.Fatalf("retained outcome=%+v", p)
	}
	if _, ok := m.Next(); ok {
		t.Fatal("checkpoint retained an obsolete event")
	}
	if !m.watch(IntentID{Owner: id.Owner, N: 2}) {
		t.Fatal("consumption did not release outcome capacity")
	}
}

func TestMailboxForgetReleasesOnlyTheRetiredAttempt(t *testing.T) {
	m := newMailbox(3, 2)
	one := IntentID{Owner: Ref{ID: 1, Gen: 1}, N: 1}
	two := IntentID{Owner: one.Owner, N: 2}
	if !m.watch(one) || !m.watch(two) {
		t.Fatal("subscription failed")
	}
	m.publish(Announcement{Text: "keep"})
	m.outcome(Commit{Outcome: Outcome{Intent: one, Stage: StageDone}})
	m.outcome(Commit{Outcome: Outcome{Intent: two, Stage: StageRefused}})
	m.Forget(one)
	p, ok := m.Next()
	if !ok {
		t.Fatal("event disappeared")
	}
	if e, ok := p.Event.(Announcement); !ok || e.Text != "keep" {
		t.Fatalf("event=%+v", p)
	}
	p, ok = m.Next()
	if !ok || p.Commit.Outcome.Intent != two {
		t.Fatalf("other attempt=%+v", p)
	}
	if _, ok := m.Next(); ok {
		t.Fatal("retired outcome remained queued")
	}
}

func TestMailboxConcurrentStateDeliveryEndsAtLatestValue(t *testing.T) {
	m := newMailbox(8, 1)
	done := make(chan struct{})
	go func() {
		defer close(done)
		for i := uint64(1); i <= 128; i++ {
			m.publish(PositionObserved{stamp: stamp{seq: i}, ObjectID: 1, Pos: Point{X: int32(i)}})
		}
		m.close()
	}()
	var last uint64
	for {
		p, ok := m.Next()
		if !ok {
			if m.Closed() {
				break
			}
			<-m.Notify()
			continue
		}
		if p.Resync {
			t.Fatal("one coalescible property overflowed")
		}
		if p.Event.Seq() <= last {
			t.Fatalf("sequence=%d after %d", p.Event.Seq(), last)
		}
		last = p.Event.Seq()
	}
	<-done
	if last != 128 {
		t.Fatalf("last=%d, want 128", last)
	}
}

func TestMailboxCloseWakesAnIdleConsumer(t *testing.T) {
	m := newMailbox(8, 1)
	m.close()
	select {
	case <-m.Notify():
	default:
		t.Fatal("close did not wake consumer")
	}
	if _, ok := m.Next(); ok || !m.Closed() {
		t.Fatal("closed empty mailbox did not reach EOF")
	}
}

func TestClosedMailboxDrainsItsExactOutcomeBeforeEOF(t *testing.T) {
	m := newMailbox(8, 1)
	id := IntentID{Owner: Ref{ID: 1, Gen: 1}, N: 1}
	if !m.watch(id) {
		t.Fatal("subscription")
	}
	if _, ok := m.Next(); ok {
		t.Fatal("new mailbox is not empty")
	}
	// Publication and closure can happen between an empty Next and the
	// consumer's EOF query. Closed alone would skip this terminal outcome.
	m.outcome(Commit{Outcome: Outcome{Intent: id, Stage: StageDone}})
	m.close()
	if !m.Closed() || m.Drained() {
		t.Fatal("closed publisher discarded its tail")
	}
	p, ok := m.Next()
	if !ok || p.Commit.Outcome.Intent != id || !m.Drained() {
		t.Fatalf("tail=%+v/%v", p, ok)
	}
}

func TestMailboxFenceReportsMissedObservationLifetimes(t *testing.T) {
	for _, tc := range []struct {
		name   string
		events []Event
		gap    bool
	}{
		{"states", []Event{PositionObserved{ObjectID: 1}}, false},
		{"departure", []Event{ObjectGone{ObjectID: 2}, NPCSeen{}}, true},
		{"pickup", []Event{ItemPicked{ObjectID: 2}}, true},
		{"overflow", []Event{Announcement{}, Announcement{}, Announcement{}}, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			m := newMailbox(2, 1)
			for _, e := range tc.events {
				m.publish(e)
			}
			if gap := m.fence(); gap != tc.gap {
				t.Fatalf("gap=%v, want %v", gap, tc.gap)
			}
			if m.fence() {
				t.Fatal("fence retained an old gap")
			}
		})
	}
}

func TestSupersededObserverCannotDepartReplacement(t *testing.T) {
	var log commitLog
	w, _, ctx := startObservedWorld(t, &log)
	entry := enterTest(t, w, ctx, syntheticCharacter(1))
	_, old, err := w.Observe(ctx, entry.Self.Entity, 8, 2)
	if err != nil {
		t.Fatal(err)
	}
	_, replacement, err := w.Observe(ctx, entry.Self.Entity, 8, 2)
	if err != nil {
		t.Fatal(err)
	}
	if !old.Closed() || replacement.Closed() {
		t.Fatal("observer replacement did not fence its predecessor")
	}
	if err := w.LeaveObserver(ctx, entry.Self.Entity, old); err != nil {
		t.Fatal(err)
	}
	if _, err := w.Capture(ctx, entry.Self.Entity); err != nil {
		t.Fatalf("retired observer removed incarnation: %v", err)
	}
	if err := w.LeaveObserver(ctx, entry.Self.Entity, replacement); err != nil {
		t.Fatal(err)
	}
}

func TestAdmissionExpiryPublishesItsExactOutcome(t *testing.T) {
	for _, deadline := range []string{"admission", "execution"} {
		t.Run(deadline, func(t *testing.T) {
			var log commitLog
			w, _, ctx := startObservedWorld(t, &log)
			entry := enterTest(t, w, ctx, syntheticCharacter(1))
			_, box, err := w.Observe(ctx, entry.Self.Entity, 8, 2)
			if err != nil {
				t.Fatal(err)
			}
			req := Request{Owner: entry.Self.Entity, Observer: box, Verb: VerbMove}
			if deadline == "admission" {
				req.AdmissionDeadline = 1
			} else {
				req.Deadline = 1
			}
			c, err := w.Execute(ctx, req)
			if err != nil || c.Outcome.Stage != StageExpired {
				t.Fatalf("admission=%+v/%v", c.Outcome, err)
			}
			if _, err := w.Capture(ctx, entry.Self.Entity); err != nil {
				t.Fatal(err)
			}
			found := false
			for {
				p, ok := box.Next()
				if !ok {
					break
				}
				found = found || p.Commit.Outcome == c.Outcome
			}
			if !found {
				t.Fatal("admission expiry lost its exact outcome")
			}
		})
	}
}

func TestCaptureObserverRejectsMissingObserver(t *testing.T) {
	var log commitLog
	w, _, ctx := startObservedWorld(t, &log)
	entry := enterTest(t, w, ctx, syntheticCharacter(1))
	if _, err := w.CaptureObserver(ctx, entry.Self.Entity, nil); !errors.Is(err, ErrUninitialized) {
		t.Fatalf("nil observer error=%v", err)
	}
}

func TestCompletionCounterExhaustionEndsTheWorld(t *testing.T) {
	w := New(nil, nil)
	w.s.commitSeq = ^uint64(0)
	w.s.continuation(Outcome{Intent: IntentID{Owner: Ref{ID: 1, Gen: 1}, N: 1}, Stage: StageDone})
	if !errors.Is(w.s.err, ErrExhausted) || w.s.commitSeq != ^uint64(0) {
		t.Fatal("completion counter wrapped or silently lost an outcome")
	}
}

func TestOutcomeCannotBeOvertakenByTheNextTransition(t *testing.T) {
	var log commitLog
	w, _, ctx := startObservedWorld(t, &log)
	entry := enterTest(t, w, ctx, syntheticCharacter(1))
	_, box, err := w.Observe(ctx, entry.Self.Entity, 8, 2)
	if err != nil {
		t.Fatal(err)
	}
	var intent IntentID
	if err := w.submit(ctx, func(s *state) {
		p := s.players[entry.Self.Entity.ID]
		s.command(entry.Self.Entity, VerbTalk, func() {
			intent = s.cause().Intent
			if !box.watch(intent) {
				t.Error("subscription")
			}
			p.send(s, Announcement{Text: "completed"})
			s.finish(StageDone)
		})
		s.command(entry.Self.Entity, VerbTeleport, func() { p.send(s, Teleported{Entity: entry.Self.Entity, ObjectID: entry.Self.Entity.ID}) })
	}); err != nil {
		t.Fatal(err)
	}
	first, _ := box.Next()
	outcome, _ := box.Next()
	last, _ := box.Next()
	if _, ok := first.Event.(Announcement); !ok {
		t.Fatalf("first=%+v", first)
	}
	if outcome.Commit.Outcome.Intent != intent || outcome.Commit.Outcome.Stage != StageDone {
		t.Fatalf("outcome=%+v", outcome)
	}
	if _, ok := last.Event.(Teleported); !ok {
		t.Fatalf("last=%+v", last)
	}
}

func TestGroundAppearanceKeepsItsIncarnationUntilFlush(t *testing.T) {
	w := New(nil, nil)
	p := &player{mailbox: newMailbox(8, 1)}
	w.s.players[1] = p
	w.s.ground[2] = &groundItem{id: 2, generation: 9}
	p.send(w.s, ItemDropped{ObjectID: 2})
	delete(w.s.ground, 2)
	w.flush(w.s)
	publication, ok := p.mailbox.Next()
	if !ok {
		t.Fatal("ground appearance disappeared")
	}
	drop, ok := publication.Event.(ItemDropped)
	if !ok || drop.Entity != (Ref{ID: 2, Gen: 9}) {
		t.Fatalf("appearance=%+v", publication)
	}
}
