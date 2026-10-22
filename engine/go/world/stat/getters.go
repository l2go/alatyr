// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package stat

import "github.com/l2go/alatyr/engine/go/world/catalog"

func bonusIndex(v int32) int32 { return min(max(v, 0), 99) }

func (p *Player) STR() int32 { return bonusIndex(int32(p.calc(STR, float64(p.Template.STR)))) }
func (p *Player) CON() int32 { return bonusIndex(int32(p.calc(CON, float64(p.Template.CON)))) }
func (p *Player) DEX() int32 { return bonusIndex(int32(p.calc(DEX, float64(p.Template.DEX)))) }
func (p *Player) INT() int32 { return bonusIndex(int32(p.calc(INT, float64(p.Template.INT)))) }
func (p *Player) WIT() int32 { return bonusIndex(int32(p.calc(WIT, float64(p.Template.WIT)))) }
func (p *Player) MEN() int32 { return bonusIndex(int32(p.calc(MEN, float64(p.Template.MEN)))) }

func (p *Player) MaxHP() int32 { return int32(p.calc(MaxHP, float64(float32(p.Template.BaseHP)))) }
func (p *Player) MaxMP() int32 { return int32(p.calc(MaxMP, float64(float32(p.Template.BaseMP)))) }
func (p *Player) MaxCP() int32 { return int32(p.calc(MaxCP, float64(float32(p.Template.BaseCP)))) }

func (p *Player) PAtk() int32 { return int32(p.calc(PAtk, p.Template.PAtk)) }
func (p *Player) MAtk() int32 { return int32(p.calc(MAtk, p.Template.MAtk)) }
func (p *Player) PDef() int32 { return int32(p.calc(PDef, p.Template.PDef)) }
func (p *Player) MDef() int32 { return int32(p.calc(MDef, p.Template.MDef)) }

func (p *Player) PAtkSpd() int32 { return min(int32(p.calc(PAtkSpd, p.Template.AtkSpd)), maxPAtkSpeed) }
func (p *Player) MAtkSpd() int32 {
	return min(int32(p.calc(MAtkSpd, p.Template.CastSpd)), maxMAtkSpeed)
}

func (p *Player) Accuracy() int32 { return int32(p.calc(Accuracy, 0)) }
func (p *Player) Evasion() int32  { return min(int32(p.calc(Evasion, 0)), maxEvasion) }

func (p *Player) baseCrit() float64 { return float64(int32(p.Template.Crit) / 10) }

func (p *Player) Critical() int32 { return min(int32(p.calc(Crit, p.baseCrit())), critCap) }

func (p *Player) baseRun() int32 { return int32(p.Template.RunSpd * rateRunSpeed) }

func (p *Player) RunSpeed() int32 {
	return min(int32(p.calc(RunSpd, float64(p.baseRun()))*float64(float32(rateRunSpeed))), maxRunSpeed)
}

func (p *Player) WalkSpeed() int32 { return p.RunSpeed() * walkPercent / 100 }

func (p *Player) MovementMultiplier() float32 {
	return float32(p.RunSpeed()) * 1 / float32(p.baseRun())
}

func (p *Player) AttackSpeedMultiplier() float32 {
	return float32(1.1 * float64(p.PAtkSpd()) / p.Template.AtkSpd)
}

func (p *Player) ShownRun() int32  { return int32(float32(p.RunSpeed()) / p.MovementMultiplier()) }
func (p *Player) ShownWalk() int32 { return int32(float32(p.WalkSpeed()) / p.MovementMultiplier()) }

func (p *Player) MaxLoad() int32 { return int32(p.calc(MaxLoad, baseMaxLoad) * weightLimit) }

func (p *Player) Collision() (radius, height float64) {
	i := 0
	if p.Female {
		i = 1
	}
	return p.Template.CollisionRadius[i], p.Template.CollisionHeight[i]
}

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

type WornItem struct {
	TemplateID int32
	Slot       int32
}

const (
	RegenHP Name = "regHp"
	RegenMP Name = "regMp"
	RegenCP Name = "regCp"
)

func (p *Player) Regen(stat Name, init float64) float64 { return p.calc(stat, init) }

func (p *Player) LevelMod() float64 { return p.levelMod() }

func STRBonus(v int32) float64 { return strBonus[bonusIndex(v)] }

func CONBonus(v int32) float64 { return conBonus[bonusIndex(v)] }
func MENBonus(v int32) float64 { return menBonus[bonusIndex(v)] }
