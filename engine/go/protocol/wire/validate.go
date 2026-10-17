// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

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
		if u(2) == 0 || u(1) == Begin && u(3) != 0 {
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
		switch u(1) {
		case TakeControl:
			if has(2) || has(3) {
				return ErrFrame
			}
		case Granted:
			if !has(2) || !has(3) || u(2) == 0 || u(3) == 0 {
				return ErrFrame
			}
		case Lost, Resync:
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
		case Sent:
			if reason != None || (delivery != Local && delivery != Backend) {
				return ErrFrame
			}
		case Done:
			if reason != None || delivery != Backend {
				return ErrFrame
			}
		case Refused:
			if reason == None || delivery != NoDelivery {
				return ErrFrame
			}
		case Unrecoverable:
			if reason != TooOld || delivery != NoDelivery {
				return ErrFrame
			}
		case Unknown:
			if delivery != Backend && delivery != Partial {
				return ErrFrame
			}
			switch reason {
			case Timeout, WorldClosed, Interrupted, TargetUnconfirmed, BackendError:
			default:
				return ErrFrame
			}
		}
	}
	return nil
}
