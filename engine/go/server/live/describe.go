// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package live

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// describe renders a fact for the log. name resolves object IDs as the
// participant sees them.
func describe(e meaning.Event, name func(int32) string) (string, bool) {
	who := name(e.ID)
	switch e.Kind {
	case meaning.Appeared:
		if e.Mine {
			return "you enter the world", true
		}
		return who + " comes into view", true
	case meaning.Dropped:
		return who + " lies on the ground", true
	case meaning.Took:
		return who + " picks up " + name(e.Target), true
	case meaning.Struck:
		if !e.Hit {
			return fmt.Sprintf("%s swings at %s and misses", who, name(e.Target)), true
		}
		return fmt.Sprintf("%s strikes %s for %d", who, name(e.Target), e.Amount), true
	case meaning.Died:
		return who + " falls", true
	case meaning.Revived:
		return who + " rises again", true
	case meaning.Targeted:
		if e.Target == 0 {
			return who + " lowers their guard", true
		}
		return who + " turns to " + name(e.Target), true
	case meaning.Spoke:
		return who + " speaks", true
	case meaning.Shop:
		return who + " opens a shop", true
	case meaning.Offer:
		return who + " offers to buy", true
	case meaning.Practice:
		return who + " offers lessons", true
	case meaning.Carried:
		return "your pack changes", true
	case meaning.Reward:
		return "you are rewarded", true
	case meaning.Notice:
		return e.Text, e.Text != ""
	}
	return "", false
}

func stageName(s wire.Stage) string {
	switch s {
	case wire.StageSent:
		return "Sent"
	case wire.StageDone:
		return "Done"
	case wire.StageRefused:
		return "Refused"
	case wire.StageUnknown:
		return "Unknown"
	case wire.StageUnrecoverable:
		return "Unrecoverable"
	}
	return fmt.Sprintf("stage %d", s)
}

var reasons = map[wire.Reason]string{
	wire.ReasonNotReady: "not ready", wire.ReasonWatch: "watching only", wire.ReasonStaleControl: "stale control",
	wire.ReasonStaleTarget: "target out of sight", wire.ReasonStaleOffer: "offer gone", wire.ReasonQueueExpired: "waited too long",
	wire.ReasonRate: "too fast", wire.ReasonBusy: "busy", wire.ReasonUnknownEssential: "unknown essential",
	wire.ReasonInvalid: "invalid", wire.ReasonNoAction: "nothing to do", wire.ReasonNotAlive: "not alive",
	wire.ReasonSkillUnknown: "skill unknown", wire.ReasonSkillPassive: "skill is passive", wire.ReasonSkillDisabled: "skill unavailable",
	wire.ReasonWorld: "the world's rules", wire.ReasonTimeout: "no proof in time", wire.ReasonWorldClosed: "world closed",
	wire.ReasonInterrupted: "interrupted", wire.ReasonTooOld: "too old", wire.ReasonOutOfOrder: "out of order",
	wire.ReasonDuplicateMismatch: "duplicate mismatch", wire.ReasonTargetUnconfirmed: "target unconfirmed", wire.ReasonBackendError: "world error",
}

func reasonName(r wire.Reason) string {
	if s, ok := reasons[r]; ok {
		return s
	}
	return fmt.Sprintf("reason %d", r)
}
