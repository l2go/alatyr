// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

// ResourceKind identifies the resource whose quantity or balance changed.
type ResourceKind string

const (
	// ResourceHP is health, measured continuously.
	ResourceHP ResourceKind = "hp"
	// ResourceMP is magic power, measured continuously.
	ResourceMP ResourceKind = "mp"
	// ResourceCP is combat power, measured continuously.
	ResourceCP ResourceKind = "cp"
	// ResourceSP is the exact integer skill-point resource.
	ResourceSP ResourceKind = "sp"
	// ResourceXP is the exact integer experience resource.
	ResourceXP ResourceKind = "xp"
	// ResourceItem is an exact integer quantity of an authored item template.
	ResourceItem ResourceKind = "item"
)

// Place identifies a resource location within an entity or a world source or sink.
type Place string

const (
	// PlaceWorld is a general world resource source or sink.
	PlaceWorld Place = "world"
	// PlaceCharacter is a character's resource balance.
	PlaceCharacter Place = "character"
	// PlaceInventory is an inventory row.
	PlaceInventory Place = "inventory"
	// PlaceMerchant is the authored merchant source or sink.
	PlaceMerchant Place = "merchant"
	// PlaceGround is an item on the ground.
	PlaceGround Place = "ground"
	// PlaceReward is the source of a world-awarded resource.
	PlaceReward Place = "reward"
	// PlaceDecay is the sink for a decayed item.
	PlaceDecay Place = "decay"
	// PlaceLearning is the sink for skill-learning costs.
	PlaceLearning Place = "learning"
)

// Capability identifies a world-provided ability.
type Capability string

const (
	// CapabilitySkill is an acquired skill level.
	CapabilitySkill Capability = "skill"
	// CapabilitySkillEnabled is skill availability after cooldown.
	CapabilitySkillEnabled Capability = "skill-enabled"
)

// SanctionKind identifies a world-imposed restriction. No nonzero kind is
// defined until the world implements sanctions.
type SanctionKind string

// SanctionNone is the absence of an identified sanction.
const SanctionNone SanctionKind = ""

// ObligationState identifies an obligation's lifecycle state. No nonzero state
// is defined until the world implements obligations.
type ObligationState string

// ObligationUnknown means no obligation state has been established.
const ObligationUnknown ObligationState = ""

// Verb identifies a world operation independently of a wire verb registry.
type Verb string

const (
	// VerbEnter loads a ready character.
	VerbEnter Verb = "enter"
	// VerbLeave removes and saves a character.
	VerbLeave Verb = "leave"
	// VerbMove requests movement.
	VerbMove Verb = "move"
	// VerbAction selects or acts on an object.
	VerbAction Verb = "action"
	// VerbSelect changes only the selected target, never performing an action.
	VerbSelect Verb = "select"
	// VerbTalk requests dialogue with a known non-hostile NPC.
	VerbTalk Verb = "talk"
	// VerbAttack requests an attack intention.
	VerbAttack Verb = "attack"
	// VerbPosture requests sitting or standing.
	VerbPosture Verb = "posture"
	// VerbGait requests walking or running.
	VerbGait Verb = "gait"
	// VerbPosition supplies a host position observation.
	VerbPosition Verb = "position"
	// VerbTeleport relocates a character.
	VerbTeleport Verb = "teleport"
	// VerbAppear completes relocation.
	VerbAppear Verb = "appear"
	// VerbPickup requests collection of a ground item.
	VerbPickup Verb = "pickup"
	// VerbChoose executes an authored dialogue choice.
	VerbChoose Verb = "choose"
	// VerbLearn requests a trainer lesson.
	VerbLearn Verb = "learn"
	// VerbBuy exchanges currency for an issued merchant item.
	VerbBuy Verb = "buy"
	// VerbSell exchanges an issued carried item for currency.
	VerbSell Verb = "sell"
	// VerbRestart returns a dead character to an authored normal restart point.
	VerbRestart Verb = "restart"
	// VerbCast requests use of a skill.
	VerbCast Verb = "cast"
)

// Rule identifies the world rule responsible for a transition.
type Rule string

const (
	// RuleHost identifies an internal host operation, not a player intent.
	RuleHost Rule = "host"
	// RuleTimer identifies general scheduled maintenance.
	RuleTimer Rule = "timer"
	// RuleSpawn initializes authored NPCs.
	RuleSpawn Rule = "spawn"
	// RuleMovement advances movement.
	RuleMovement Rule = "movement"
	// RuleRefresh publishes coalesced notifications, not reconstructed effects.
	RuleRefresh Rule = "refresh"
	// RuleVisibility updates object knowledge.
	RuleVisibility Rule = "visibility"
	// RuleFollow advances following.
	RuleFollow Rule = "follow"
	// RuleCombatStance expires combat stance.
	RuleCombatStance Rule = "combat-stance"
	// RuleRegeneration restores vitals.
	RuleRegeneration Rule = "regeneration"
	// RuleAI advances NPC decisions.
	RuleAI Rule = "ai"
	// RuleAnimation schedules authored ambient animation.
	RuleAnimation Rule = "animation"
	// RuleAttacked reacts to applied harm.
	RuleAttacked Rule = "attacked"
	// RuleAttackHit applies a scheduled attack hit.
	RuleAttackHit Rule = "attack-hit"
	// RuleAttackReady completes an attack's protected interval.
	RuleAttackReady Rule = "attack-ready"
	// RuleCastLaunch launches a scheduled skill.
	RuleCastLaunch Rule = "cast-launch"
	// RuleCastReady completes a skill's protected interval.
	RuleCastReady Rule = "cast-ready"
	// RuleSkillReuse enables a skill after its reuse interval.
	RuleSkillReuse Rule = "skill-reuse"
	// RuleReward grants hunt rewards.
	RuleReward Rule = "reward"
	// RuleDecay removes dead NPCs.
	RuleDecay Rule = "decay"
	// RuleRespawn replaces a removed NPC with a new incarnation.
	RuleRespawn Rule = "respawn"
	// RuleItemDecay removes expired ground items.
	RuleItemDecay Rule = "item-decay"
	// RuleGameClock advances the accelerated display clock.
	RuleGameClock Rule = "game-clock"
)

// Property identifies the subject state described by a Fact.
type Property string

const (
	// PropertyPosition is an authoritative position (PointValue).
	PropertyPosition Property = "position"
	// PropertyHeading is the heading (IntValue).
	PropertyHeading Property = "heading"
	// PropertyTarget is the selected incarnation (RefValue).
	PropertyTarget Property = "target"
	// PropertyMovement is whether movement is active (IntValue 0/1).
	PropertyMovement Property = "movement"
	// PropertyDestination is the current movement destination (PointValue).
	PropertyDestination Property = "destination"
	// PropertyPosture is sitting (IntValue 0/1).
	PropertyPosture Property = "posture"
	// PropertyGait is running (IntValue 0/1).
	PropertyGait Property = "gait"
	// PropertyDead is dead (IntValue 0/1).
	PropertyDead Property = "dead"
	// PropertyTeleporting is relocating (IntValue 0/1).
	PropertyTeleporting Property = "teleporting"
	// PropertyAttacking is an active attack (IntValue 0/1).
	PropertyAttacking Property = "attacking"
	// PropertyCasting is an active cast (IntValue 0/1).
	PropertyCasting Property = "casting"
	// PropertyCombat is combat stance (IntValue 0/1).
	PropertyCombat Property = "combat"
	// PropertyCombatUntil is its world-time deadline (TimeValue).
	PropertyCombatUntil Property = "combat-until"
	// PropertyLearning is an available trainer window (IntValue 0/1).
	PropertyLearning Property = "learning"
	// PropertyLevel is the character level (IntValue).
	PropertyLevel Property = "level"
	// PropertyKnownObject adds or removes an observed incarnation (RefValue).
	PropertyKnownObject Property = "known-object"
	// PropertyPeaceZone is the peace rule (IntValue 0/1).
	PropertyPeaceZone Property = "peace-zone"
	// PropertyPvPZone is the PvP rule (IntValue 0/1).
	PropertyPvPZone Property = "pvp-zone"
	// PropertyGameClock is an accelerated clock minute (IntValue).
	PropertyGameClock Property = "game-clock"
	// PropertyDialogue is the current authored page's catalog path (LabelValue).
	PropertyDialogue Property = "dialogue"
	// PropertyAffordance adds or removes an issued choice (UintValue).
	PropertyAffordance Property = "affordance"
	// PropertyFence is a relocation barrier (UintValue).
	PropertyFence Property = "fence"
	// PropertyFollow is the followed incarnation (RefValue).
	PropertyFollow Property = "follow"
	// PropertyFollowRange is the acting range of following (IntValue).
	PropertyFollowRange Property = "follow-range"
)
