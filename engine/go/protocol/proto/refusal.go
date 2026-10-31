// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto

import "github.com/l2go/alatyr/engine/go/protocol/wire"

// RefusalEvidence is a trusted backend's proof that the entire attempt had no
// effect. The backend must establish live, exclusive correspondence for every
// contacted step; a receipt for only the last step is insufficient.
type RefusalEvidence struct {
	// Reason is the world's dictionary reason, never ReasonNone.
	Reason wire.Reason
	// ObservedMS is the original live observation time in session milliseconds.
	ObservedMS int64
}

// ConfirmRefusal terminates a contacted attempt with proven absence of effects.
// The live observation must be within the original window; processing may reach
// its deadline, as with CommitWitness, but cannot replace an expired outcome.
// The class slot is released without adding or clearing any uncertainty taint.
// Sent may advance to Refused; terminal outcomes cannot be replaced.
func (s *Session) ConfirmRefusal(n uint64, proof RefusalEvidence, now int64) (Outcome, error) {
	i := s.index(n)
	if s.closed || i < 0 || s.entries[i].Terminal || proof.Reason < wire.ReasonNotReady || proof.Reason > wire.ReasonBackendError {
		return Outcome{}, ErrState
	}
	e := s.entries[i]
	if !e.Contact || proof.ObservedMS < e.Deadline-s.config.EvidenceMS || proof.ObservedMS >= e.Deadline || now < proof.ObservedMS || now > e.Deadline {
		return Outcome{}, ErrState
	}
	return s.finish(i, Outcome{n, wire.StageRefused, proof.Reason, e.Attempt.Verb, e.Effective, wire.DeliveryNone}), nil
}
