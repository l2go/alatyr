// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

// Teaches reports whether NPC template npc trains any class.
func (c *Catalog) Teaches(npc int32) bool { return len(c.teach[npc]) > 0 }
