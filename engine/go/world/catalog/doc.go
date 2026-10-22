// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package catalog reads an immutable rendered authored world.
// Accessors return values or detached copies. Templates, spawns, skill trees,
// drops, stat XML, authored pages and geometry share one rendered root.
// Missing class metadata, skill levels and restart regions are not inferred.
package catalog
