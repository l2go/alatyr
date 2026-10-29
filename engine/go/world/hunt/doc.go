// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package hunt calculates ordinary-monster rewards from a frozen death snapshot.
// Damage is measured in integer HP units chosen by the caller. It includes lost
// and neutral contributions; recipient eligibility is decided by the world.
package hunt
