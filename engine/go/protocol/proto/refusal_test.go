// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestConfirmedRefusalReleasesClassAfterSent(t *testing.T) {
	s := fixture(t)
	add(t, s, 1, "a", true)
	if err := s.Contact(1, 10); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Sent(1, false); err != nil {
		t.Fatal(err)
	}
	o, err := s.ConfirmRefusal(1, RefusalEvidence{wire.ReasonWorld, 11}, 12)
	if err != nil || o.Stage != wire.StageRefused || o.Reason != wire.ReasonWorld || o.Delivery != wire.DeliveryNone || o.Requested != 9 || o.Effective != 7 {
		t.Fatalf("confirmed refusal: got %+v/%v", o, err)
	}
	if _, err := s.Done(1, 12); err == nil {
		t.Fatal("terminal refusal reopened")
	}
	add(t, s, 2, "a", true)
	if err := s.Contact(2, 12); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Sent(2, false); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Done(2, 13); err != nil {
		t.Fatalf("refusal tainted class: %v", err)
	}
}

func TestRefusalEvidenceCannotCreateOrEraseCertainty(t *testing.T) {
	for _, name := range []string{"no-contact", "before-contact", "deadline", "future", "none", "invalid-reason", "terminal", "closed"} {
		t.Run(name, func(t *testing.T) {
			s := fixture(t)
			add(t, s, 1, "a", true)
			proof, now := RefusalEvidence{wire.ReasonWorld, 11}, int64(12)
			if name != "no-contact" {
				if err := s.Contact(1, 10); err != nil {
					t.Fatal(err)
				}
			}
			switch name {
			case "before-contact":
				proof.ObservedMS = 9
			case "deadline":
				proof.ObservedMS, now = 110, 110
			case "future":
				proof.ObservedMS = 13
			case "none":
				proof.Reason = wire.ReasonNone
			case "invalid-reason":
				proof.Reason = 255
			case "terminal":
				if _, err := s.Fail(1, wire.ReasonTimeout); err != nil {
					t.Fatal(err)
				}
			case "closed":
				s.Close()
			}
			if _, err := s.ConfirmRefusal(1, proof, now); err == nil {
				t.Fatal("invalid refusal evidence accepted")
			}
		})
	}
	for _, generic := range []bool{false, true} {
		s := fixture(t)
		add(t, s, 1, "a", true)
		if err := s.Contact(1, 10); err != nil {
			t.Fatal(err)
		}
		var o Outcome
		var err error
		if generic {
			o, err = s.Refuse(1, wire.ReasonWorld)
		} else {
			o, err = s.Fail(1, wire.ReasonTimeout)
		}
		if err != nil || o.Stage != wire.StageUnknown {
			t.Fatalf("unproven failure: %+v/%v", o, err)
		}
		add(t, s, 2, "a", true)
		if err := s.Contact(2, 20); err != nil {
			t.Fatal(err)
		}
		if _, err := s.ConfirmRefusal(2, RefusalEvidence{wire.ReasonWorld, 21}, 21); err != nil {
			t.Fatal(err)
		}
		add(t, s, 3, "a", true)
		if err := s.Contact(3, 22); err != nil {
			t.Fatal(err)
		}
		if s.Eligible(3, 23) {
			t.Fatal("proven refusal cleared an earlier Unknown taint")
		}
	}
}

func TestConfirmedRefusalOriginalWindow(t *testing.T) {
	for _, tc := range []struct {
		name          string
		observed, now int64
		accepted      bool
	}{
		{"contact", 10, 10, true},
		{"before-deadline", 109, 109, true},
		{"process-at-deadline", 109, 110, true},
		{"observe-at-deadline", 110, 110, false},
		{"process-after-deadline", 109, 111, false},
		{"before-contact", 9, 10, false},
		{"future-observation", 11, 10, false},
		{"process-before-contact", 10, 9, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := fixture(t)
			add(t, s, 1, "a", true)
			if err := s.Contact(1, 10); err != nil {
				t.Fatal(err)
			}
			if _, err := s.Sent(1, false); err != nil {
				t.Fatal(err)
			}
			o, err := s.ConfirmRefusal(1, RefusalEvidence{wire.ReasonWorld, tc.observed}, tc.now)
			if tc.accepted {
				if err != nil || o.Stage != wire.StageRefused || o.Delivery != wire.DeliveryNone {
					t.Fatalf("ConfirmRefusal = %+v/%v, want Refused/None", o, err)
				}
				return
			}
			if err != ErrState {
				t.Fatalf("ConfirmRefusal error = %v, want ErrState", err)
			}
			e, ok := s.Lookup(1)
			if !ok || e.Terminal {
				t.Fatal("rejected proof changed the attempt")
			}
			o, err = s.Fail(1, wire.ReasonTimeout)
			if err != nil || o.Stage != wire.StageUnknown {
				t.Fatalf("fallback = %+v/%v, want Unknown", o, err)
			}
		})
	}
}
