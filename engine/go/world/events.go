// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"slices"
)

// Events describe world state. They are not a wire format or command receipt.
type NPCSeen struct{ NPC NPCView }
type PlayerSeen struct{ Player Self }
type ObjectGone struct{ ObjectID int32 }
type SelfInfo struct{ Self Self }
type PositionSync struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}
type TargetChosen struct {
	ObjectID, TargetID int32
	Pos                Point
}
type MyTarget struct{ TargetID, LevelDifference int32 }
type TargetCleared struct {
	ObjectID int32
	Pos      Point
}
type Vitals struct {
	ObjectID            int32
	HP, MP, CP          float64
	MaxHP, MaxMP, MaxCP int32
}
type AttackDone struct {
	AttackerID, TargetID int32
	Damage               int32
	Critical, Miss       bool
	From, TargetPos      Point
}
type DamageNotice struct {
	AttackerID, TargetID int32
	Damage               float64
	Critical, Miss       bool
}
type CombatStance struct {
	ObjectID int32
	On       bool
}
type MoveType struct {
	ObjectID int32
	Running  bool
}
type WaitType struct {
	ObjectID int32
	Sitting  bool
	Pos      Point
}
type Died struct{ ObjectID int32 }
type Animation struct {
	ObjectID int32
	Forced   bool
}
type Following struct {
	ObjectID, TargetID, Distance int32
	From, To                     Point
}
type ZoneChanged struct {
	ObjectID int32
	Zone     catalog.ZoneState
}
type Teleported struct {
	ObjectID int32
	Pos      Point
	Heading  int32
}
type Revived struct{ ObjectID int32 }
type NPCPage struct {
	NPCID int32
	HTML  string
}
type LearnList struct {
	NPCID  int32
	Skills []catalog.SkillLearn
}
type SkillsShown struct{ Skills []Skill }
type SkillCast struct {
	CasterID, TargetID, SkillID, Level int32
	HitTime, Reuse                     int32
	From, TargetPos                    Point
}
type SkillLaunched struct {
	CasterID, SkillID, Level int32
	Targets                  []int32
}
type SkillCanceled struct{ ObjectID int32 }
type SkillReuse struct {
	SkillID               int32
	RemainingMilliseconds int64
}
type Rewarded struct {
	ObjectID int32
	Exp      int64
	SP       int32
}
type LevelChanged struct{ ObjectID, Before, After int32 }
type ItemDropped struct {
	ObjectID, TemplateID int32
	Pos                  Point
	Count                int64
	Stackable            bool
	DropperID            int32
}
type ItemPicked struct {
	PlayerID, ObjectID int32
	Pos                Point
}
type ItemChange struct {
	Kind string
	Item Item
}
type InventoryChanged struct {
	Changes []ItemChange
	Load    int64
}

// RequestIgnored is housekeeping for requests that did not start work. It
// does not prove a refusal or the outcome of an earlier protected action.
type RequestIgnored struct{}
type Announcement struct{ Text string }

func (Announcement) event() {}

func (NPCSeen) event()          {}
func (PlayerSeen) event()       {}
func (ObjectGone) event()       {}
func (SelfInfo) event()         {}
func (PositionSync) event()     {}
func (TargetChosen) event()     {}
func (MyTarget) event()         {}
func (TargetCleared) event()    {}
func (Vitals) event()           {}
func (AttackDone) event()       {}
func (DamageNotice) event()     {}
func (CombatStance) event()     {}
func (MoveType) event()         {}
func (WaitType) event()         {}
func (Died) event()             {}
func (Animation) event()        {}
func (Following) event()        {}
func (ZoneChanged) event()      {}
func (Teleported) event()       {}
func (Revived) event()          {}
func (NPCPage) event()          {}
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
func (RequestIgnored) event()   {}

func cloneEvent(e Event) Event {
	switch value := e.(type) {
	case SelfInfo:
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
