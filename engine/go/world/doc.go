// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package world owns live characters, NPCs, ground items and gameplay timers.
// Run owns all mutable state. Commands return detached snapshots and buffered
// semantic events. Movement, combat, learning and visibility share that owner.
// Persistence runs on the caller's goroutine, not inside the world loop.
// Events are state notifications, not a transport or an effect ledger.
// The caller must leave characters before stopping Run.
package world
