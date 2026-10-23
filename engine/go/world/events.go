// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// NPCSeen supplies a detached NPC observation, not proof of creation.
type NPCSeen struct {
	stamp
	NPC NPCView
}

// PlayerSeen supplies a detached player observation.
type PlayerSeen struct {
	stamp
	Player Self
}

// ObjectGone removes an object from the observer's scene, not necessarily existence.
type ObjectGone struct {
	stamp
	ObjectID int32
}

// SelfChanged supplies refreshed self state, not a command outcome.
type SelfChanged struct {
	stamp
	Self Self
}

// PositionObserved supplies an authoritative position without claiming a correction occurred.
type PositionObserved struct {
	stamp
	ObjectID int32
	Pos      Point
	Heading  int32
}

// TargetChosen announces an actor's selected target to its observers.
type TargetChosen struct {
	stamp
	ObjectID, TargetID int32
	Pos                Point
}

// TargetDetails supplies target details to the selecting player.
type TargetDetails struct {
	stamp
	TargetID, LevelDifference int32
}

// TargetCleared announces removal of an actor's selected target.
type TargetCleared struct {
	stamp
	ObjectID int32
	Pos      Point
}

// Vitals supplies current resource balances, not resource deltas.
type Vitals struct {
	stamp
	ObjectID            int32
	HP, MP, CP          float64
	MaxHP, MaxMP, MaxCP int32
}

// AttackStarted announces a calculated swing before any scheduled harm is applied.
type AttackStarted struct {
	stamp
	AttackerID, TargetID int32
	Damage               int32
	Critical, Miss       bool
	From, TargetPos      Point
}

// DamageNotice describes calculated damage; cancellation may still prevent application.
type DamageNotice struct {
	stamp
	AttackerID, TargetID int32
	Damage               float64
	Critical, Miss       bool
}

// CombatStance announces whether an actor is in combat.
type CombatStance struct {
	stamp
	ObjectID int32
	On       bool
}

// GaitChanged announces walking or running.
type GaitChanged struct {
	stamp
	ObjectID int32
	Running  bool
}

// PostureChanged announces sitting or standing.
type PostureChanged struct {
	stamp
	ObjectID int32
	Sitting  bool
	Pos      Point
}

// Died announces an actor's death.
type Died struct {
	stamp
	ObjectID int32
}

// Animation announces an ambient authored animation.
type Animation struct {
	stamp
	ObjectID int32
	Forced   bool
}

// Following announces an actor moving toward another object.
type Following struct {
	stamp
	ObjectID, TargetID, Distance int32
	From, To                     Point
}

// ZoneChanged supplies the world's rules at an actor's position.
type ZoneChanged struct {
	stamp
	ObjectID int32
	Zone     catalog.ZoneState
}

// Teleported announces relocation before Appear completes it.
type Teleported struct {
	stamp
	ObjectID int32
	Pos      Point
	Heading  int32
	Entity   Ref
	Fence    Fence
}

// Revived announces revival at the current position, not return to a start point.
type Revived struct {
	stamp
	ObjectID int32
}

// DialoguePage supplies an authored page and its detached issued authority.
// Dialogue.HTML is presentation, not permission to execute arbitrary text.
type DialoguePage struct {
	stamp
	Dialogue Dialogue
}

// LearnList supplies available trainer lessons, not learning outcomes.
type LearnList struct {
	stamp
	Dialogue Dialogue
}

// SkillsShown supplies effective skills and their detached runtime availability.
// Complete is false when a skill's catalog source is missing.
type SkillsShown struct {
	stamp
	Known    []KnownSkill
	Complete bool
}

// SkillCast announces the start of a cast, not its outcome.
type SkillCast struct {
	stamp
	CasterID, TargetID, SkillID, Level int32
	HitTime, Reuse                     int32
	From, TargetPos                    Point
}

// SkillLaunched announces the cast's observed targets, not applied harm.
type SkillLaunched struct {
	stamp
	CasterID, SkillID, Level int32
	Targets                  []int32
}

// SkillCanceled announces cancellation of a cast.
type SkillCanceled struct {
	stamp
	ObjectID int32
}

// SkillReuse supplies the remaining reuse interval.
type SkillReuse struct {
	stamp
	SkillID               int32
	RemainingMilliseconds int64
}

// Rewarded announces awarded XP and SP, not total balances.
type Rewarded struct {
	stamp
	ObjectID int32
	XP       int64
	SP       int32
}

// LevelChanged announces the old and new character level.
type LevelChanged struct {
	stamp
	ObjectID, Before, After int32
}

// ItemDropped supplies a ground item observation, which may be late visibility.
type ItemDropped struct {
	stamp
	ObjectID, TemplateID int32
	Pos                  Point
	Count                int64
	Stackable            bool
	DropperID            int32
}

// ItemPicked announces pickup before insertion into inventory, not a transfer receipt.
type ItemPicked struct {
	stamp
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
	stamp
	Changes []ItemChange
	Load    int64
}

// ActionIgnored is housekeeping for requests that did not start work. It
// does not prove a refusal or the outcome of an earlier protected action.
type ActionIgnored struct {
	stamp
}

// Announcement supplies an authored system notice, not a typed refusal.
type Announcement struct {
	stamp
	Text string
}

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

// publishEvent stamps and detaches one known event in a single value copy.
// Its closed type switch keeps publication explicit when an event is added.
func publishEvent(e Event, at stamp) Event {
	switch value := e.(type) {
	case Announcement:
		value.stamp = at
		return value
	case NPCSeen:
		value.stamp = at
		return value
	case PlayerSeen:
		value.stamp = at
		value.Player.Char = value.Player.Char.Clone()
		return value
	case ObjectGone:
		value.stamp = at
		return value
	case SelfChanged:
		value.stamp = at
		value.Self.Char = value.Self.Char.Clone()
		return value
	case PositionObserved:
		value.stamp = at
		return value
	case TargetChosen:
		value.stamp = at
		return value
	case TargetDetails:
		value.stamp = at
		return value
	case TargetCleared:
		value.stamp = at
		return value
	case Vitals:
		value.stamp = at
		return value
	case AttackStarted:
		value.stamp = at
		return value
	case DamageNotice:
		value.stamp = at
		return value
	case CombatStance:
		value.stamp = at
		return value
	case GaitChanged:
		value.stamp = at
		return value
	case PostureChanged:
		value.stamp = at
		return value
	case Died:
		value.stamp = at
		return value
	case Animation:
		value.stamp = at
		return value
	case Following:
		value.stamp = at
		return value
	case ZoneChanged:
		value.stamp = at
		return value
	case Teleported:
		value.stamp = at
		return value
	case Revived:
		value.stamp = at
		return value
	case DialoguePage:
		value.stamp = at
		value.Dialogue = cloneDialogue(value.Dialogue)
		return value
	case LearnList:
		value.stamp = at
		value.Dialogue = cloneDialogue(value.Dialogue)
		return value
	case SkillsShown:
		value.stamp = at
		value.Known = slices.Clone(value.Known)
		for i := range value.Known {
			value.Known[i].Disabled = value.Known[i].ReuseUntil > at.time
		}
		return value
	case SkillCast:
		value.stamp = at
		return value
	case SkillLaunched:
		value.stamp = at
		value.Targets = slices.Clone(value.Targets)
		return value
	case SkillCanceled:
		value.stamp = at
		return value
	case SkillReuse:
		value.stamp = at
		return value
	case Rewarded:
		value.stamp = at
		return value
	case LevelChanged:
		value.stamp = at
		return value
	case ItemDropped:
		value.stamp = at
		return value
	case ItemPicked:
		value.stamp = at
		return value
	case InventoryChanged:
		value.stamp = at
		value.Changes = slices.Clone(value.Changes)
		return value
	case ActionIgnored:
		value.stamp = at
		return value
	case Moving:
		value.stamp = at
		return value
	case Stopped:
		value.stamp = at
		return value
	case LeftWorld:
		value.stamp = at
		return value
	case GameClock:
		value.stamp = at
		return value
	case AffordancesChanged:
		value.stamp = at
		value.Affordances = cloneAffordances(value.Affordances)
		return value
	default:
		return nil // Unsupported concrete source type; delivery stops the world.
	}
}
