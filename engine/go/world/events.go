// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Events describe world state. They are not a wire format or command receipt.
// NPCSeen supplies a detached NPC observation, not proof of creation.
type NPCSeen struct{ NPC NPCView }

// PlayerSeen supplies a detached player observation.
type PlayerSeen struct{ Player Self }

// ObjectGone removes an object from the observer's scene, not necessarily existence.
type ObjectGone struct{ ObjectID int32 }

// SelfChanged supplies refreshed self state, not a command outcome.
type SelfChanged struct{ Self Self }

// PositionObserved supplies an authoritative position without claiming a correction occurred.
type PositionObserved struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}

// TargetChosen announces an actor's selected target to its observers.
type TargetChosen struct {
	ObjectID, TargetID int32
	Pos                Point
}

// TargetDetails supplies target details to the selecting player.
type TargetDetails struct{ TargetID, LevelDifference int32 }

// TargetCleared announces removal of an actor's selected target.
type TargetCleared struct {
	ObjectID int32
	Pos      Point
}

// Vitals supplies current resource balances, not resource deltas.
type Vitals struct {
	ObjectID            int32
	HP, MP, CP          float64
	MaxHP, MaxMP, MaxCP int32
}

// AttackStarted announces a calculated swing before any scheduled harm is applied.
type AttackStarted struct {
	AttackerID, TargetID int32
	Damage               int32
	Critical, Miss       bool
	From, TargetPos      Point
}

// DamageNotice describes calculated damage; cancellation may still prevent application.
type DamageNotice struct {
	AttackerID, TargetID int32
	Damage               float64
	Critical, Miss       bool
}

// CombatStance announces whether an actor is in combat.
type CombatStance struct {
	ObjectID int32
	On       bool
}

// GaitChanged announces walking or running.
type GaitChanged struct {
	ObjectID int32
	Running  bool
}

// PostureChanged announces sitting or standing.
type PostureChanged struct {
	ObjectID int32
	Sitting  bool
	Pos      Point
}

// Died announces an actor's death.
type Died struct{ ObjectID int32 }

// Animation announces an ambient authored animation.
type Animation struct {
	ObjectID int32
	Forced   bool
}

// Following announces an actor moving toward another object.
type Following struct {
	ObjectID, TargetID, Distance int32
	From, To                     Point
}

// ZoneChanged supplies the world's rules at an actor's position.
type ZoneChanged struct {
	ObjectID int32
	Zone     catalog.ZoneState
}

// Teleported announces relocation before Appear completes it.
type Teleported struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}

// Revived announces revival at the current position, not return to a start point.
type Revived struct{ ObjectID int32 }

// DialoguePage supplies an authored dialogue presentation, not permission to execute its text.
type DialoguePage struct {
	NPCID int32
	HTML  string
}

// LearnList supplies available trainer lessons, not learning outcomes.
type LearnList struct {
	NPCID  int32
	Skills []catalog.SkillLearn
}

// SkillsShown supplies the full effective skill list.
type SkillsShown struct{ Skills []Skill }

// SkillCast announces the start of a cast, not its outcome.
type SkillCast struct {
	CasterID, TargetID, SkillID, Level int32
	HitTime, Reuse                     int32
	From, TargetPos                    Point
}

// SkillLaunched announces the cast's observed targets, not applied harm.
type SkillLaunched struct {
	CasterID, SkillID, Level int32
	Targets                  []int32
}

// SkillCanceled announces cancellation of a cast.
type SkillCanceled struct{ ObjectID int32 }

// SkillReuse supplies the remaining reuse interval.
type SkillReuse struct {
	SkillID               int32
	RemainingMilliseconds int64
}

// Rewarded announces awarded XP and SP, not total balances.
type Rewarded struct {
	ObjectID int32
	XP       int64
	SP       int32
}

// LevelChanged announces the old and new character level.
type LevelChanged struct{ ObjectID, Before, After int32 }

// ItemDropped supplies a ground item observation, which may be late visibility.
type ItemDropped struct {
	ObjectID, TemplateID int32
	Pos                  Point
	Count                int64
	Stackable            bool
	DropperID            int32
}

// ItemPicked announces pickup before insertion into inventory, not a transfer receipt.
type ItemPicked struct {
	PlayerID, ObjectID int32
	Pos                Point
}

// ItemChange supplies one inventory row replacement or insertion.
type ItemChange struct {
	Kind string
	Item Item
}

// InventoryChanged supplies inventory row changes and current load.
type InventoryChanged struct {
	Changes []ItemChange
	Load    int64
}

// ActionIgnored is housekeeping for requests that did not start work. It
// does not prove a refusal or the outcome of an earlier protected action.
type ActionIgnored struct{}

// Announcement supplies an authored system notice, not a typed refusal.
type Announcement struct{ Text string }

func (Announcement) event() {}

func (NPCSeen) event()          {}
func (PlayerSeen) event()       {}
func (ObjectGone) event()       {}
func (SelfChanged) event()      {}
func (PositionObserved) event() {}
func (TargetChosen) event()     {}
func (TargetDetails) event()    {}
func (TargetCleared) event()    {}
func (Vitals) event()           {}
func (AttackStarted) event()    {}
func (DamageNotice) event()     {}
func (CombatStance) event()     {}
func (GaitChanged) event()      {}
func (PostureChanged) event()   {}
func (Died) event()             {}
func (Animation) event()        {}
func (Following) event()        {}
func (ZoneChanged) event()      {}
func (Teleported) event()       {}
func (Revived) event()          {}
func (DialoguePage) event()     {}
func (LearnList) event()        {}
func (SkillsShown) event()      {}
func (SkillCast) event()        {}
func (SkillLaunched) event()    {}
func (SkillCanceled) event()    {}
func (SkillReuse) event()       {}
func (Rewarded) event()         {}
func (LevelChanged) event()     {}
func (ItemDropped) event()      {}
func (ItemPicked) event()       {}
func (InventoryChanged) event() {}
func (ActionIgnored) event()    {}

func cloneEvent(e Event) Event {
	switch value := e.(type) {
	case SelfChanged:
		value.Self.Char = value.Self.Char.Clone()
		return value
	case PlayerSeen:
		value.Player.Char = value.Player.Char.Clone()
		return value
	case SkillsShown:
		value.Skills = slices.Clone(value.Skills)
		return value
	case LearnList:
		value.Skills = slices.Clone(value.Skills)
		return value
	case SkillLaunched:
		value.Targets = slices.Clone(value.Targets)
		return value
	case InventoryChanged:
		value.Changes = slices.Clone(value.Changes)
		return value
	default:
		return e
	}
}
