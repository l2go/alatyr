// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package itemlog formats and writes an item-change journal in UTC.
// Log serializes concurrent writes; Discard records nothing. Gameplay callers
// supply the operation, owner and reference rather than protocol identifiers.
package itemlog
