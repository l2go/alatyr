// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

// Package proto implements transport-independent session state: attempt
// admission and outcomes on the world side (Session) and projection, control
// and attempt numbering on the participant side (Connection).
//
// A caller serializes operations; this package starts no goroutines or timers.
// Time is passed in by the caller as milliseconds.
package proto
