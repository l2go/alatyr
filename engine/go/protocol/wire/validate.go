// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

// validateEnvelope checks the cross-field rules of a session record that a
// Schema alone cannot express.
func validateEnvelope(r Record) error {
	u := func(n uint32) uint64 { v, _ := Find(r.Fields, n); return v.U }
	has := func(n uint32) bool { _, ok := Find(r.Fields, n); return ok }
	switch r.Type {
	case Hello:
		if u(1) > u(2) {
			return ErrFrame
		}
	case Welcome:
		if u(1) == 0 || u(4) == 0 {
			return ErrFrame
		}
	case Checkpoint:
		if u(2) == 0 || CheckpointPhase(u(1)) == CheckpointBegin && u(3) != 0 {
			return ErrFrame
		}
	case Fact:
		v, _ := Find(r.Fields, 3)
		if u(2) == 0 || u(4) == 0 || v.S < 0 {
			return ErrFrame
		}
	case Intent:
		if u(1) == 0 {
			return ErrFrame
		}
	case Control:
		switch ControlOp(u(1)) {
		case ControlTake:
			if has(2) || has(3) {
				return ErrFrame
			}
		case ControlGranted:
			if !has(2) || !has(3) || u(2) == 0 || u(3) == 0 {
				return ErrFrame
			}
		case ControlLost, ControlResync:
			if !has(2) || u(2) == 0 || has(3) {
				return ErrFrame
			}
		}
	case Result:
		if u(1) == 0 {
			return ErrFrame
		}
		stage, reason, delivery := Stage(u(2)), Reason(u(3)), Delivery(u(7))
		switch stage {
		case StageSent:
			if reason != ReasonNone || (delivery != DeliveryLocal && delivery != DeliveryBackend) {
				return ErrFrame
			}
		case StageDone:
			if reason != ReasonNone || delivery != DeliveryBackend {
				return ErrFrame
			}
		case StageRefused:
			if reason == ReasonNone || delivery != DeliveryNone {
				return ErrFrame
			}
		case StageUnrecoverable:
			if reason != ReasonTooOld || delivery != DeliveryNone {
				return ErrFrame
			}
		case StageUnknown:
			if delivery != DeliveryBackend && delivery != DeliveryPartial {
				return ErrFrame
			}
			switch reason {
			case ReasonTimeout, ReasonWorldClosed, ReasonInterrupted, ReasonTargetUnconfirmed, ReasonBackendError:
			default:
				return ErrFrame
			}
		}
	}
	return nil
}
