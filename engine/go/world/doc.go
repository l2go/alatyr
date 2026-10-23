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
// commit fixes no intent outcome. StageUnknown for a nonzero Intent is not success.
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
// # Commands and observation
//
// Execute admits a Request with captured incarnations, an exact skill level,
// an optional world-time deadline and an issued affordance identity. It returns
// the same Commit published by the source observer, not a second effect ledger.
// Selection never acts on its target. Delayed work checks its captured target
// and deadline again; it never promotes or rewrites a prior Unknown outcome.
// Every new attempt gets a world-local IntentID, not a replay-safe session key.
//
// Capture reads supported observable sources in one loop turn. Scene.End is
// the final marker; Ready requires source completeness and no pending relocation.
// Missing sources stay incomplete. Trade and quests are explicitly absent
// mechanics, not unconditionally complete snapshots of invented state.
// Capture does not renew Affordance expiry, mutate gameplay or repeat effects.
// Subscribe replaces an observation stream and captures the same incarnation
// atomically; it cannot restore a departed character or a stopped world.
// Subscription.End distinguishes overflow, leave, stop and replacement.
// Run stamps each delivered Event with Time and a world-wide Seq. The latter
// is independent of Commit.Sequence; Scene markers retain both boundaries.
// Pages and lesson lists carry their own detached Dialogue and affordances.
// SkillsShown carries detached effective skills and runtime reuse availability.
// Delayed reading never borrows authority from a later Capture.
// Leave and Logout check a captured Ref in the same turn as departure. Only
// that incarnation can retry its pending detached save; Store is unchanged.
// Departure always requires a captured incarnation. Parameters is the complete
// issued parameter set; lesson capabilities and costs have one source, Affordance.
//
// Fence identifies relocation, independently of Ref.Gen and session epochs.
// Execute with VerbAppear requires the current Fence; legacy Appear trusts its
// host to select the current relocation. Protocol encodings, delivery receipts,
// checkpoint framing, session deduplication and proofs belong to consumers.
package world
