// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

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
	// PropertyAnimation is a forced ambient animation announcement (IntValue 0/1).
	PropertyAnimation Property = "animation"
	// PropertyGameClock is an accelerated clock minute (IntValue).
	PropertyGameClock Property = "game-clock"
	// PropertyDialogue is the current authored dialogue presentation (LabelValue).
	PropertyDialogue Property = "dialogue"
	// PropertyFollow is the followed incarnation (RefValue).
	PropertyFollow Property = "follow"
	// PropertyFollowRange is the acting range of following (IntValue).
	PropertyFollowRange Property = "follow-range"
)
