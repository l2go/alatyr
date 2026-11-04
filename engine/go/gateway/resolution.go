// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// resolve applies source commitments through the pinned protocol state machine.
// A known commitment is stronger than an inferred witness. Bound completion
// does not depend on earlier class taint. Drain consumes source publications
// before expiry: replaying a timely commitment uses the original deadline,
// even when scheduler delay made its delivery late. A genuinely late completion
// still expires as Unknown, and an existing terminal result is never rewritten.
func resolve(s *proto.Session, evidenceMS int64, r Resolution, now int64) (proto.Outcome, error) {
	e, ok := s.Lookup(r.N)
	if !ok || e.Terminal || !e.Contact || r.ObservedMS < e.Deadline-evidenceMS || r.ObservedMS > now {
		return proto.Outcome{}, proto.ErrState
	}
	if r.Stage != wire.StageDone && r.Stage != wire.StageRefused && r.Stage != wire.StageUnknown || r.Stage == wire.StageDone && r.Reason != wire.ReasonNone || r.Stage != wire.StageDone && (r.Reason < wire.ReasonNotReady || r.Reason > wire.ReasonBackendError) {
		return proto.Outcome{}, proto.ErrState
	}
	if r.ObservedMS >= e.Deadline {
		return s.Fail(r.N, wire.ReasonTimeout)
	}
	switch r.Stage {
	case wire.StageDone:
		return s.CommitBound(r.N, r.ObservedMS, min(now, e.Deadline))
	case wire.StageRefused:
		return s.ConfirmRefusal(r.N, proto.RefusalEvidence{Reason: r.Reason, ObservedMS: r.ObservedMS}, min(now, e.Deadline))
	default:
		return s.Fail(r.N, r.Reason)
	}
}
