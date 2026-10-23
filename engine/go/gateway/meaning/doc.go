// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package meaning provides the fixed alatyr/1 participant dictionary, bounded
// references and saved actions, and an actor-owned State for the gateway.
// It has no world-specific role mapping, network backend, or production wiring.
// Hosts supply explicit limits and trusted capabilities. A zero Source supplies
// no causal authority; delivery evidence is supplied only by a trusted adapter.
// State operations are serialized by the gateway actor, not safe for concurrent
// use. Codec is immutable and may be shared. DTOs preserve nil versus known-empty
// lists and unknown versus known-zero stats. Captured action text is never a
// participant command field: callers resolve numbered live handles instead.
package meaning
