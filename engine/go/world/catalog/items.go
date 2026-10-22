// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

// Equipment positions are persisted inventory slots, not message identifiers.
const (
	SlotHead   = 8
	SlotRHand  = 9
	SlotLHand  = 10
	SlotGloves = 11
	SlotChest  = 12
	SlotLegs   = 13
	SlotFeet   = 14
	SlotLRHand = 16
)

// BodyPart recognizes equipment used by the authored world, without a global
// creature, race, class or pet table.
func (t ItemTemplate) BodyPart() int32 {
	switch t.BodyPartName {
	case "head":
		return 0x0040
	case "rhand":
		return 0x0080
	case "lhand":
		return 0x0100
	case "gloves":
		return 0x0200
	case "chest":
		return 0x0400
	case "legs":
		return 0x0800
	case "feet":
		return 0x1000
	case "lrhand":
		return 0x4000
	case "fullarmor":
		return 0x8000
	}
	return 0
}
func (t ItemTemplate) IsKind(kind string) bool {
	return t.Kind == Weapon && t.WeaponType == kind || t.Kind == Armor && t.ArmorType == kind
}
