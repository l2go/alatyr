// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package goworld composes the authored Go world with the participant gateway.
// It holds the trusted parts of that composition: the dictionary limits the
// gateway advertises, the classification of world-owned roles, the evidence
// authority of the world's exclusive event sources, and the opening of one
// character's world port. The server host and any other host of the same world
// share it, so they serve the world on the same terms.
package goworld
