// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import "github.com/l2go/alatyr/engine/go/protocol/wire"

// Event kinds.
const (
	// Appeared identifies an object entering the observed horizon.
	Appeared = "appeared"
	// Moved identifies movement state.
	Moved = "moved"
	// Stopped identifies stopped movement state.
	Stopped = "stopped"
	// Corrected identifies a corrected position.
	Corrected = "corrected"
	// Struck identifies a reported harm outcome.
	Struck = "struck"
	// Died identifies a reported death.
	Died = "died"
	// Revived identifies a live revival outcome.
	Revived = "revived"
	// Dropped identifies an item entering the observed horizon.
	Dropped = "dropped"
	// Took identifies an item transfer outcome.
	Took = "took"
	// Gone identifies an object leaving the observed horizon.
	Gone = "gone"
	// Spoke identifies a dialogue and proposal report.
	Spoke = "spoke"
	// Shop identifies a purchase proposal.
	Shop = "shop"
	// Offer identifies a sale proposal.
	Offer = "offer"
	// Carried identifies a full inventory report.
	Carried = "carried"
	// Inventory identifies an inventory change report.
	Inventory = "inventory"
	// Vitals identifies a partial self stat report.
	Vitals = "vitals"
	// Reward identifies a resource reward report.
	Reward = "reward"
	// Notice identifies a display notice.
	Notice = "notice"
	// History identifies a bounded history report.
	History = "history"
	// Quest identifies a quest progress outcome.
	Quest = "quest"
	// Quests identifies current quest states.
	Quests = "quests"
	// Practice identifies a learning proposal.
	Practice = "practice"
	// Known identifies known skill state.
	Known = "known"
	// Targeted identifies current selection state.
	Targeted = "targeted"
)

// Neutral roles; all other world labels remain opaque.
const (
	// RolePlayer is the neutral own-actor label, not a permission.
	RolePlayer = "player"
	// RoleDrop is the neutral collectible label, not a permission.
	RoleDrop = "drop"
	// RoleUnknown is the neutral unspecified label.
	RoleUnknown = "unknown"
)

// Command kinds a policy may ask the wire to attempt.
const (
	// Walk identifies a position command.
	Walk = "walk"
	// Aim identifies a local selection command.
	Aim = "aim"
	// Strike identifies a hostile action.
	Strike = "strike"
	// Take identifies an item transfer command.
	Take = "take"
	// Talk identifies a dialogue command.
	Talk = "talk"
	// Say identifies a resolved saved dialogue choice.
	Say = "say"
	// Buy identifies a resolved purchase.
	Buy = "buy"
	// Sell identifies a resolved sale.
	Sell = "sell"
	// Rise identifies a return command for a dead self.
	Rise = "rise"
	// Focus identifies a local focus command.
	Focus = "focus"
	// Engage identifies a capability-resolved target action.
	Engage = "engage"
	// Cast identifies a known active skill action.
	Cast = "cast"
	// Learn identifies a resolved learning proposal.
	Learn = "learn"
	// UseItem toggles equipment or consumes an authored item.
	UseItem = "use-item"
)

// QuestState reports current progress without supplying authored quest logic.
type QuestState struct {
	// ID is the backend quest identifier.
	ID int32 `json:"id"`
	// Step is the current progress step.
	Step int32 `json:"step"`
	// Name is an opaque world label.
	Name string `json:"name,omitempty"`
	// State is the reported status label.
	State string `json:"state"`
}

// Skill describes a known skill; availability does not imply permission for a target.
type Skill struct {
	// ID is the backend skill identifier.
	ID int32 `json:"id"`
	// Level is the known skill level.
	Level int32 `json:"level"`
	// Passive excludes direct skill use.
	Passive bool `json:"passive"`
	// Disabled marks current skill unavailability.
	Disabled bool `json:"disabled"`
}

// Line pairs a display label with server-owned action text, never sent on the wire.
type Line struct {
	// Label is display text only.
	Label string `json:"label"`
	// Say is server-owned saved action text; EventRecord never serializes it.
	Say string `json:"say"`
}

// Good describes an inventory or proposal row; its field roles depend on Event.Kind.
type Good struct {
	// Object is an inventory object ID, or the maximum level in a Practice row.
	Object int32 `json:"object"`
	// ID is an item or skill identifier, not an observation reference.
	ID int32 `json:"id"`
	// Count is the available quantity, or offered level in a Practice row.
	Count int64 `json:"count"`
	// Price is the captured unit cost.
	Price int64 `json:"price"`
	// Name is an opaque display label.
	Name string `json:"name,omitempty"`
	// Change is backend inventory change metadata.
	Change int32 `json:"change,omitempty"`
	// Slot is the known equipment slot; -1 means carried unequipped.
	Slot *int64 `json:"slot,omitempty"`
}

// Stats is a partial report. Nil is unknown, and a pointer to zero is known.
type Stats struct {
	// HP is known health, or nil when unknown.
	HP *int64 `json:"hp,omitempty"`
	// MaxHP is known maximum health, or nil when unknown.
	MaxHP *int64 `json:"maxHP,omitempty"`
	// MP is known energy, or nil when unknown.
	MP *int64 `json:"mp,omitempty"`
	// MaxMP is known maximum energy, or nil when unknown.
	MaxMP *int64 `json:"maxMP,omitempty"`
	// Level is known actor level, or nil when unknown.
	Level *int64 `json:"level,omitempty"`
	// XP is known experience, or nil when unknown.
	XP *int64 `json:"xp,omitempty"`
	// SP is known skill points, or nil when unknown.
	SP *int64 `json:"sp,omitempty"`
	// PAtk is known physical attack.
	PAtk *int64 `json:"pAtk,omitempty"`
	// PDef is known physical defense.
	PDef *int64 `json:"pDef,omitempty"`
}

// Entry is a bounded history item, not causal evidence.
type Entry struct {
	// MS is the original history item time in milliseconds.
	MS int64 `json:"ms"`
	// Category is the opaque item category.
	Category string `json:"category"`
	// Text is display content, never interpreted as an action.
	Text string `json:"text"`
}

// Number represents a known stat value, including zero.
func Number(v int64) *int64 { return &v }

// Appearance describes a player without exposing a class ID or granting actions.
// Each zero member explicitly means unknown; nil Event.Appearance is no report.
type Appearance struct {
	// Race is the player's authored people, or RaceUnknown.
	Race wire.Race `json:"race"`
	// Sex is the reported sex, or SexUnknown.
	Sex wire.Sex `json:"sex"`
	// Branch is fighter/mystic only when authored class-kind metadata is known.
	Branch wire.Branch `json:"branch"`
}

// Event is one thing the server has already decided.
type Event struct {
	// Appearance is an optional player report. It is valid on Appeared for a
	// player or Vitals for self; absence does not erase a known appearance.
	Appearance *Appearance `json:"appearance,omitempty"`
	// MS is the original backend fact time in milliseconds.
	MS int64 `json:"ms"`
	// Kind selects one of the fixed fact kinds.
	Kind string `json:"kind"`
	// ID is the subject's backend identifier.
	ID int32 `json:"id"`
	// Role is an opaque world label, never a capability verdict.
	Role string `json:"role,omitempty"`
	// Name is the subject's display label.
	Name string `json:"name,omitempty"`
	// X is the reported position's first coordinate.
	X int32 `json:"x"`
	// Y is the reported position's second coordinate.
	Y int32 `json:"y"`
	// Z is the reported position's third coordinate.
	Z int32 `json:"z"`
	// ToX is the destination's first coordinate.
	ToX int32 `json:"toX"`
	// ToY is the destination's second coordinate.
	ToY int32 `json:"toY"`
	// ToZ is the destination's third coordinate.
	ToZ int32 `json:"toZ"`
	// Speed is the nonnegative reported movement speed.
	Speed int32 `json:"speed"`
	// Target is the related object's backend identifier.
	Target int32 `json:"target"`
	// Amount is kind-specific outcome metadata.
	Amount int32 `json:"amount"`
	// Hit is the reported hit flag.
	Hit bool `json:"hit"`
	// Mine identifies the participant's own actor.
	Mine bool `json:"mine"`
	// Text is display content; it never becomes an arbitrary command.
	Text string `json:"text,omitempty"`
	// Lines contains saved dialogue actions; nil means unknown, empty means known empty.
	Lines []Line `json:"lines,omitempty"`
	// List identifies the backend proposal list.
	List int32 `json:"list,omitempty"`
	// Coin is a known balance, including zero; nil means unknown.
	Coin *int64 `json:"coin,omitempty"`
	// Goods contains inventory or proposal rows with presence preserved.
	Goods []Good `json:"goods,omitempty"`
	// Stats contains a partial stat report; nil means no report.
	Stats *Stats `json:"stats,omitempty"`
	// Category is an opaque report category.
	Category string `json:"category,omitempty"`
	// CombatUntil is the reported combat deadline in backend milliseconds.
	CombatUntil int64 `json:"combatUntil,omitempty"`
	// Entries contains bounded history items with presence preserved.
	Entries []Entry `json:"entries,omitempty"`
	// Quests contains current quest states with presence preserved.
	Quests []QuestState `json:"quests,omitempty"`
	// Skills contains known skills with presence preserved.
	Skills []Skill `json:"skills,omitempty"`
	// Complete reports explicit list completeness, not delivery or causal authority.
	Complete bool `json:"complete,omitempty"`
}

// Command is one attempt. Entering the world is not a command: it is how a
// connection is opened. A watch connection never sends a command after that.
type Command struct {
	// Kind selects a fixed backend command.
	Kind string `json:"kind"`
	// X is the captured destination's first coordinate.
	X int32 `json:"x"`
	// Y is the captured destination's second coordinate.
	Y int32 `json:"y"`
	// Z is the captured destination's third coordinate.
	Z int32 `json:"z"`
	// Target is the captured backend target ID.
	Target int32 `json:"target"`
	// Text is server-owned saved action text, never accepted from an Intent.
	Text string `json:"text,omitempty"`
	// List is the captured backend proposal list.
	List int32 `json:"list,omitempty"`
	// Item is the captured item or skill ID.
	Item int32 `json:"item,omitempty"`
	// Count is the captured quantity or offered skill level.
	Count int64 `json:"count,omitempty"`
	// Force requests the explicit forced-action permission check.
	Force bool `json:"force,omitempty"`
}
