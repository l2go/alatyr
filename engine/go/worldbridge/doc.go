// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package worldbridge translates the Go world's observable sources into raw
// bridge/1 facts and receipts. It keeps wire policy out of the simulation.
//
// A scene proves coverage only when its sources are complete and representable.
// Events describe observations; receipts use immutable source commitments, not
// arrival order or differences between snapshots. World outcome stages are
// translated by meaning, including the bridge's narrower completion sets.
//
// Open resolves a host-owned ready character and supplies a bridge.Port plus
// its complete opening snapshot. One owner goroutine serializes writes,
// source publications, checkpoints and recovery. The host owns the shared
// World.Run; a request context never becomes the session's lifetime.
//
// Observation loss detaches the port, not the character. Resume captures the
// same incarnation in a new epoch and replays copied receipts, never commands.
// Close waits for departure and reports persistence failure without a hidden
// retry. The host can retry that captured Owner and must close ports before Run.
package worldbridge
