// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package world owns live characters, movement and scheduled work.
// Run owns all mutable state. Commands return detached snapshots and buffered
// semantic events; persistence runs on the caller's goroutine after departure.
// The caller must leave characters before stopping Run.
package world
