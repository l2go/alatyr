// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package world owns live characters, NPCs, ground items and gameplay timers.
// Run owns all mutable state. Commands return detached snapshots and buffered
// semantic events. Movement, combat, learning and visibility share that owner.
// Persistence runs on the caller's goroutine, not inside the world loop.
// The caller must leave characters before stopping Run.
//
// # Transition commitments
//
// WithCommits observes mutations at their source, independently of coalesced
// Event notifications. Each Commit contains an ordered set of EffectGroups.
// Every group carries one Cause: an IntentID or a world Rule. An Outcome is
// fixed with those groups in the owning loop; a zero Outcome.Intent means this
// commit fixes no intent outcome. Unknown for a nonzero Intent is not success.
// Starting an attack is not applying harm, and observing a refreshed snapshot
// never proves execution of a command.
//
// Groups are indivisible. For learning, the SP transfer and the capability
// change belong to one group. The subsequent external PutSkill write has its
// own Durability signal; its failure cannot rewrite the in-memory commitment
// or claim a rollback. Commitments do not promise storage transactions.
//
// Ref identifies an incarnation, not a session or a relocation. Time is elapsed
// monotonic world time; Sequence orders commits even at equal times. Fact
// values are closed, comparable values with no retained mutable references.
// Observer values are detached from the simulation. The observer is a trusted,
// bounded synchronous host callback; it must project allowed knowledge before
// external delivery and must not reenter the world. There is no retained log,
// deduplication or recovery service here.
//
// Affordance and Intent are neutral forms. The existing command methods do not
// yet admit deadline-bearing intents or issue general affordances. Protocol
// encodings, checkpoints, delivery receipts and proofs belong to consumers.
package world
