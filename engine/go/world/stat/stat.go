// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package stat

import (
	"math"
	"slices"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

type Name string

const (
	MaxHP    Name = "maxHp"
	MaxMP    Name = "maxMp"
	MaxCP    Name = "maxCp"
	PDef     Name = "pDef"
	MDef     Name = "mDef"
	PAtk     Name = "pAtk"
	MAtk     Name = "mAtk"
	PAtkSpd  Name = "pAtkSpd"
	MAtkSpd  Name = "mAtkSpd"
	Evasion  Name = "rEvas"
	Crit     Name = "rCrit"
	MCrit    Name = "mCritRate"
	Accuracy Name = "accCombat"
	AtkRange Name = "pAtkRange"
	RunSpd   Name = "runSpd"
	MaxLoad  Name = "maxLoad"
	STR      Name = "STR"
	CON      Name = "CON"
	DEX      Name = "DEX"
	INT      Name = "INT"
	WIT      Name = "WIT"
	MEN      Name = "MEN"
)

type env struct {
	value, base float64
}

type fn struct {
	order int
	apply func(e *env)
}

var (
	strBonus = bonusTable(1.036, 34.845)
	intBonus = bonusTable(1.020, 31.375)
	dexBonus = bonusTable(1.009, 19.360)
	witBonus = bonusTable(1.050, 20.000)
	conBonus = bonusTable(1.030, 27.632)
	menBonus = bonusTable(1.010, -0.060)
)

func bonusTable(a, b float64) [100]float64 {
	var t [100]float64
	for i := range t {
		t[i] = math.Floor(math.Pow(a, float64(i)-b)*100+.5) / 100
	}
	return t
}

const (
	rateRunSpeed   = 1.0
	weightLimit    = 1.0
	critCap        = 500
	maxPAtkSpeed   = 1500
	maxMAtkSpeed   = 1999
	maxRunSpeed    = 250
	maxEvasion     = 200
	bagNoDwarf     = 80
	baseMaxLoad    = 69000
	walkPercent    = 70
	weaponTypeBow  = "bow"
	weaponTypeXbow = "crossbow"
)

type Player struct {
	Template   catalog.PlayerTemplate
	Class      catalog.Class
	Level      int32
	Female     bool
	Worn       map[int32]catalog.ItemTemplate
	Funcs      []catalog.ItemFunc
	SkillFuncs []catalog.ItemFunc
}

func (p *Player) calc(stat Name, init float64) float64 {
	fns := p.standard(stat)
	for _, f := range p.Funcs {
		if Name(f.Stat) == stat {
			fns = insert(fns, itemFn(f))
		}
	}
	for _, f := range p.SkillFuncs {
		if Name(f.Stat) == stat && !f.Conditional && (f.Cond == nil || p.holds(*f.Cond)) {
			fns = insert(fns, itemFn(f))
		}
	}
	if len(fns) == 0 {
		return init
	}
	e := env{value: init, base: math.NaN()}
	for _, f := range fns {
		if f.order == 0x30 && math.IsNaN(e.base) {
			e.base = e.value
		}
		f.apply(&e)
	}
	if e.value <= 0 && floorOne[stat] {
		return 1
	}
	return e.value
}

var floorOne = map[Name]bool{
	MaxHP: true, MaxMP: true, MaxCP: true, PDef: true, MDef: true, PAtk: true, MAtk: true,
	PAtkSpd: true, MAtkSpd: true, STR: true, CON: true, DEX: true, INT: true, WIT: true, MEN: true,
}

func insert(fns []fn, f fn) []fn {
	i := 0
	for i < len(fns) && f.order >= fns[i].order {
		i++
	}
	return slices.Insert(fns, i, f)
}

func itemFn(f catalog.ItemFunc) fn {
	v := f.Value
	switch f.Op {
	case "set":
		return fn{f.Order, func(e *env) { e.value = v }}
	case "add":
		return fn{f.Order, func(e *env) { e.value += v }}
	case "sub":
		return fn{f.Order, func(e *env) { e.value -= v }}
	case "mul":
		return fn{f.Order, func(e *env) { e.value *= v }}
	case "div":
		return fn{f.Order, func(e *env) { e.value /= v }}
	case "basemul":
		return fn{f.Order, func(e *env) { e.value += e.base * v }}
	}
	return fn{f.Order, func(*env) {}}
}

func (p *Player) levelMod() float64 { return float64(89+p.Level) / 100 }

func (p *Player) holds(c catalog.Cond) bool {
	switch c.Op {
	case "and":
		for _, a := range c.Args {
			if !p.holds(a) {
				return false
			}
		}
		return true
	case "or":
		return slices.ContainsFunc(c.Args, p.holds)
	case "not":
		return !p.holds(c.Args[0])
	case "using":
		if c.Mask == catalog.MaskLight || c.Mask == catalog.MaskHeavy || c.Mask == catalog.MaskMagic {
			chest, ok := p.Worn[catalog.SlotChest]
			if !ok || c.Mask&chest.ItemMask() == 0 {
				return false
			}
			if chest.BodyPart() == fullArmor {
				return true
			}
			legs, ok := p.Worn[catalog.SlotLegs]
			return ok && c.Mask&legs.ItemMask() != 0
		}
		worn := int32(0)
		for _, t := range p.Worn {
			worn |= t.ItemMask()
		}
		return c.Mask&worn != 0
	}
	return false
}

const fullArmor = 0x8000

func (p *Player) hasWeapon() bool {
	_, ok := p.Worn[catalog.SlotRHand]
	return ok
}

func (p *Player) weaponType() string { return p.Worn[catalog.SlotRHand].WeaponType }

func (p *Player) standard(stat Name) []fn {
	switch stat {
	case MaxHP:
		return []fn{
			{0x10, func(e *env) { e.value += p.levelGain(p.Template.HPPerLevel, p.Template.HPIncrement) }},
			{0x20, func(e *env) { e.value *= conBonus[p.CON()] }},
		}
	case MaxCP:
		return []fn{
			{0x10, func(e *env) { e.value += p.levelGain(p.Template.CPPerLevel, p.Template.CPIncrement) }},
			{0x20, func(e *env) { e.value *= conBonus[p.CON()] }},
		}
	case MaxMP:
		return []fn{
			{0x10, func(e *env) { e.value += p.levelGain(p.Template.MPPerLevel, p.Template.MPIncrement) }},
			{0x20, func(e *env) { e.value *= menBonus[p.MEN()] }},
		}
	case AtkRange:
		return []fn{
			{0x10, func(e *env) {
				if p.weaponType() == weaponTypeBow {
					e.value += 460
				}
			}},
			{0x10, func(e *env) {
				if p.weaponType() == weaponTypeXbow {
					e.value += 360
				}
			}},
		}
	case PAtk:
		return []fn{{0x30, func(e *env) { e.value *= strBonus[p.STR()] * p.levelMod() }}}
	case MAtk:
		return []fn{{0x20, func(e *env) {
			ib, lm := intBonus[p.INT()], p.levelMod()
			e.value *= (lm * lm) * (ib * ib)
		}}}
	case PDef:
		return []fn{{0x20, func(e *env) { e.value = p.pDefMod(e.value) }}}
	case MDef:
		return []fn{{0x20, func(e *env) { e.value = p.mDefMod(e.value) }}}
	case Crit:
		return []fn{{0x09, func(e *env) {
			if !p.hasWeapon() {
				e.value = 40
				return
			}
			e.value *= dexBonus[p.DEX()]
			e.value *= 10
		}}}
	case MCrit:
		return []fn{{0x29, func(e *env) {
			if p.hasWeapon() {
				e.value *= witBonus[p.WIT()]
			}
		}}}
	case Accuracy, Evasion:
		return []fn{{0x10, func(e *env) {
			e.value += math.Sqrt(float64(p.DEX())) * 6
			e.value += float64(p.Level)
			if p.Level > 77 {
				e.value += float64(p.Level - 77)
			}
			if p.Level > 69 {
				e.value += float64(p.Level - 69)
			}
		}}}
	case PAtkSpd:
		return []fn{{0x20, func(e *env) { e.value *= dexBonus[p.DEX()] }}}
	case MAtkSpd:
		return []fn{{0x20, func(e *env) { e.value *= witBonus[p.WIT()] }}}
	case RunSpd:
		return []fn{{0x30, func(e *env) { e.value *= dexBonus[p.DEX()] }}}
	case MaxLoad:
		return []fn{{0x30, func(e *env) { e.value *= conBonus[p.CON()] }}}
	case STR, CON, DEX, INT, WIT, MEN:
		return []fn{{0x10, func(*env) {}}}
	}
	return nil
}

func (p *Player) levelGain(add, mod float64) float64 {
	lvl := p.Level - p.Template.ClassBaseLevel
	fadd := float32(add)
	fmod := float64(float32(mod) * float32(lvl))
	max := (float64(fadd) + fmod) * float64(lvl)
	min := float64(fadd*float32(lvl)) + fmod
	return (max + min) / 2
}

func (p *Player) pDefMod(v float64) float64 {
	mage := p.Class.Mage
	if _, ok := p.Worn[catalog.SlotHead]; ok {
		v -= 12
	}
	chest, hasChest := p.Worn[catalog.SlotChest]
	if hasChest {
		v -= pick(mage, 15, 31)
	}
	if _, ok := p.Worn[catalog.SlotLegs]; ok || hasChest && chest.BodyPart() == 0x8000 {
		v -= pick(mage, 8, 18)
	}
	if _, ok := p.Worn[catalog.SlotGloves]; ok {
		v -= 8
	}
	if _, ok := p.Worn[catalog.SlotFeet]; ok {
		v -= 7
	}
	return v * p.levelMod()
}

func (p *Player) mDefMod(v float64) float64 {
	for slot, minus := range map[int32]float64{5: 5, 6: 5, 2: 9, 1: 9, 4: 13} {
		if _, ok := p.Worn[slot]; ok {
			v -= minus
		}
	}
	return v * menBonus[p.MEN()] * p.levelMod()
}

func pick(mage bool, ifMage, otherwise float64) float64 {
	if mage {
		return ifMage
	}
	return otherwise
}
