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
// The translation layer has no exported API, session, transport or goroutines.
// Session ownership and the shared bridge.Port implementation are separate work.
package worldbridge
