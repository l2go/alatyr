// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"testing"
)

func TestExactResolutionIgnoresAnEarlierClassTaint(t *testing.T) {
	s := resolutionFixture(t)
	resolutionAdd(t, s, 1, "a", true)
	if err := s.Contact(1, 10); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Sent(1, false); err != nil {
		t.Fatal(err)
	}
	s.Expire(110)
	resolutionAdd(t, s, 2, "a", true)
	if err := s.Contact(2, 120); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Sent(2, false); err != nil {
		t.Fatal(err)
	}
	o, err := resolve(s, 100, Resolution{N: 2, Stage: wire.StageDone, ObservedMS: 121}, 122)
	if err != nil || o.Stage != wire.StageDone || o.Delivery != wire.DeliveryBackend {
		t.Fatalf("exact result=%+v/%v", o, err)
	}
	if _, err := resolve(s, 100, Resolution{N: 1, Stage: wire.StageDone, ObservedMS: 11}, 122); err == nil {
		t.Fatal("exact result reopened a terminal Unknown")
	}
}

func TestResolutionRejectsUncontactedOrMalformedOutcomes(t *testing.T) {
	for _, name := range []string{"no-contact", "before-contact", "future", "stage", "done-reason", "refused-reason", "terminal-sent", "closed"} {
		t.Run(name, func(t *testing.T) {
			s := resolutionFixture(t)
			waiting := name != "terminal-sent"
			resolutionAdd(t, s, 1, "a", waiting)
			if name != "no-contact" {
				if err := s.Contact(1, 10); err != nil {
					t.Fatal(err)
				}
				if _, err := s.Sent(1, false); err != nil {
					t.Fatal(err)
				}
			}
			stage, reason, observed, now := wire.StageDone, wire.ReasonNone, int64(11), int64(12)
			switch name {
			case "before-contact":
				observed = 9
			case "future":
				observed = 13
			case "stage":
				stage = wire.StageSent
			case "done-reason":
				reason = wire.ReasonWorld
			case "refused-reason":
				stage = wire.StageRefused
			case "closed":
				s.Close()
			}
			if _, err := resolve(s, 100, Resolution{N: 1, Stage: stage, Reason: reason, ObservedMS: observed}, now); err == nil {
				t.Fatal("invalid resolution accepted")
			}
		})
	}
}

func resolutionFixture(t *testing.T) *proto.Session {
	t.Helper()
	s, err := proto.New(proto.Config{Results: 5, Pending: 2, IntentBytes: 16, EvidenceMS: 100, QueueMS: 20, Classes: []string{"a", "b"}})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := s.Take(1); err != nil {
		t.Fatal(err)
	}
	return s
}
func resolutionAdd(t *testing.T, s *proto.Session, n uint64, class string, wait bool) {
	t.Helper()
	_, epoch := s.Controller()
	a := proto.Attempt{N: n, Control: epoch, Verb: 9, Payload: []byte{byte(n)}}
	ad := s.Begin(1, true, true, a, 10)
	if !ad.Allocated || ad.Result != nil || ad.Close != nil {
		t.Fatalf("admission=%+v", ad)
	}
	if o, err := s.Prepare(n, 7, class, wait); err != nil || o != nil {
		t.Fatal(o, err)
	}
}

func TestTimelyCommitSurvivesDelayedDelivery(t *testing.T) {
	for _, stage := range []wire.Stage{wire.StageDone, wire.StageRefused} {
		s := resolutionFixture(t)
		resolutionAdd(t, s, 1, "a", true)
		if err := s.Contact(1, 10); err != nil {
			t.Fatal(err)
		}
		if _, err := s.Sent(1, false); err != nil {
			t.Fatal(err)
		}
		reason := wire.ReasonNone
		if stage == wire.StageRefused {
			reason = wire.ReasonWorld
		}
		out, err := resolve(s, 100, Resolution{N: 1, Stage: stage, Reason: reason, ObservedMS: 11}, 500)
		if err != nil || out.Stage != stage {
			t.Fatalf("delayed commitment=%+v/%v", out, err)
		}
	}
	s := resolutionFixture(t)
	resolutionAdd(t, s, 1, "a", true)
	if err := s.Contact(1, 10); err != nil {
		t.Fatal(err)
	}
	out, err := resolve(s, 100, Resolution{N: 1, Stage: wire.StageDone, ObservedMS: 110}, 500)
	if err != nil || out.Stage != wire.StageUnknown || out.Reason != wire.ReasonTimeout {
		t.Fatalf("late completion=%+v/%v", out, err)
	}
}
