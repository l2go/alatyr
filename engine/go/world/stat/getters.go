// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package stat

import "github.com/l2go/alatyr/engine/go/world/catalog"

func bonusIndex(v int32) int32 { return min(max(v, 0), 99) }

// STR returns strength, clamped to the bonus table range 0 to 99.
func (p *Player) STR() int32 { return bonusIndex(int32(p.calc(STR, float64(p.Template.STR)))) }

// CON returns constitution, clamped to 0 to 99.
func (p *Player) CON() int32 { return bonusIndex(int32(p.calc(CON, float64(p.Template.CON)))) }

// DEX returns dexterity, clamped to 0 to 99.
func (p *Player) DEX() int32 { return bonusIndex(int32(p.calc(DEX, float64(p.Template.DEX)))) }

// INT returns intelligence, clamped to 0 to 99.
func (p *Player) INT() int32 { return bonusIndex(int32(p.calc(INT, float64(p.Template.INT)))) }

// WIT returns wit, clamped to 0 to 99.
func (p *Player) WIT() int32 { return bonusIndex(int32(p.calc(WIT, float64(p.Template.WIT)))) }

// MEN returns mentality, clamped to 0 to 99.
func (p *Player) MEN() int32 { return bonusIndex(int32(p.calc(MEN, float64(p.Template.MEN)))) }

// MaxHP returns maximum HP.
func (p *Player) MaxHP() int32 { return int32(p.calc(MaxHP, float64(float32(p.Template.BaseHP)))) }

// MaxMP returns maximum MP.
func (p *Player) MaxMP() int32 { return int32(p.calc(MaxMP, float64(float32(p.Template.BaseMP)))) }

// MaxCP returns maximum CP.
func (p *Player) MaxCP() int32 { return int32(p.calc(MaxCP, float64(float32(p.Template.BaseCP)))) }

// PAtk returns physical attack.
func (p *Player) PAtk() int32 { return int32(p.calc(PAtk, p.Template.PAtk)) }

// MAtk returns magic attack.
func (p *Player) MAtk() int32 { return int32(p.calc(MAtk, p.Template.MAtk)) }

// PDef returns physical defense; see PDefKnown.
func (p *Player) PDef() int32 { return int32(p.calc(PDef, p.Template.PDef)) }

// MDef returns magic defense.
func (p *Player) MDef() int32 { return int32(p.calc(MDef, p.Template.MDef)) }

// PAtkSpd returns attack speed, capped at 1500.
func (p *Player) PAtkSpd() int32 { return min(int32(p.calc(PAtkSpd, p.Template.AtkSpd)), maxPAtkSpeed) }

// MAtkSpd returns casting speed, capped at 1999.
func (p *Player) MAtkSpd() int32 {
	return min(int32(p.calc(MAtkSpd, p.Template.CastSpd)), maxMAtkSpeed)
}

// Accuracy returns accuracy.
func (p *Player) Accuracy() int32 { return int32(p.calc(Accuracy, 0)) }

// Evasion returns evasion, capped at 200.
func (p *Player) Evasion() int32 { return min(int32(p.calc(Evasion, 0)), maxEvasion) }

func (p *Player) baseCrit() float64 { return float64(int32(p.Template.Crit) / 10) }

// Critical returns the critical rate, capped at 500.
func (p *Player) Critical() int32 { return min(int32(p.calc(Crit, p.baseCrit())), critCap) }

func (p *Player) baseRun() int32 { return int32(p.Template.RunSpd * rateRunSpeed) }

// RunSpeed returns the running speed, capped at 250.
func (p *Player) RunSpeed() int32 {
	return min(int32(p.calc(RunSpd, float64(p.baseRun()))*float64(float32(rateRunSpeed))), maxRunSpeed)
}

// WalkSpeed returns the walking speed, 70% of RunSpeed.
func (p *Player) WalkSpeed() int32 { return p.RunSpeed() * walkPercent / 100 }

// MovementMultiplier returns RunSpeed relative to the template base speed.
func (p *Player) MovementMultiplier() float32 {
	return float32(p.RunSpeed()) * 1 / float32(p.baseRun())
}

// AttackSpeedMultiplier returns 1.1 × PAtkSpd relative to the template attack
// speed.
func (p *Player) AttackSpeedMultiplier() float32 {
	return float32(1.1 * float64(p.PAtkSpd()) / p.Template.AtkSpd)
}

// ShownRun returns the running speed divided by MovementMultiplier.
func (p *Player) ShownRun() int32 { return int32(float32(p.RunSpeed()) / p.MovementMultiplier()) }

// ShownWalk returns the walking speed divided by MovementMultiplier.
func (p *Player) ShownWalk() int32 { return int32(float32(p.WalkSpeed()) / p.MovementMultiplier()) }

// MaxLoad returns the carrying capacity.
func (p *Player) MaxLoad() int32 { return int32(p.calc(MaxLoad, baseMaxLoad) * weightLimit) }

// Collision returns the template collision size for the character's sex.
func (p *Player) Collision() (radius, height float64) {
	i := 0
	if p.Female {
		i = 1
	}
	return p.Template.CollisionRadius[i], p.Template.CollisionHeight[i]
}

// Worn resolves equipped items to templates by slot and collects their
// parsable stat functions. Items unknown to cat are skipped. A two-handed
// weapon also occupies SlotLRHand.
func Worn(cat *catalog.Catalog, items []WornItem) (map[int32]catalog.ItemTemplate, []catalog.ItemFunc) {
	worn := map[int32]catalog.ItemTemplate{}
	var funcs []catalog.ItemFunc
	for _, it := range items {
		t, ok := cat.Item(it.TemplateID)
		if !ok {
			continue
		}
		worn[it.Slot] = t
		if t.BodyPart() == 0x4000 {
			worn[catalog.SlotLRHand] = t
		}
		for _, f := range cat.ItemFuncs(it.TemplateID) {
			if !f.Conditional {
				funcs = append(funcs, f)
			}
		}
	}
	return worn, funcs
}

// WornItem is an equipped item template in an inventory slot.
type WornItem struct {
	TemplateID int32
	Slot       int32
}

// Regeneration stats.
const (
	RegenHP Name = "regHp"
	RegenMP Name = "regMp"
	RegenCP Name = "regCp"
)

// Regen evaluates regeneration stat from init, like the other getters.
func (p *Player) Regen(stat Name, init float64) float64 { return p.calc(stat, init) }

// LevelMod returns the level modifier (89 + level) / 100.
func (p *Player) LevelMod() float64 { return p.levelMod() }

// STRBonus returns the strength bonus for v, clamped to 0 to 99.
func STRBonus(v int32) float64 { return strBonus[bonusIndex(v)] }

// CONBonus returns the constitution bonus for v, clamped to 0 to 99.
func CONBonus(v int32) float64 { return conBonus[bonusIndex(v)] }

// MENBonus returns the mentality bonus for v, clamped to 0 to 99.
func MENBonus(v int32) float64 { return menBonus[bonusIndex(v)] }
