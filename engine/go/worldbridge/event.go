// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"slices"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func (t translator) dropped(v world.ItemDropped) (fact, error) {
	if v.Count <= 0 || v.ObjectID <= 0 || v.DropperID < 0 {
		return fact{}, errSource
	}
	good, _, err := t.good(world.Item{ObjectID: v.ObjectID, TemplateID: v.TemplateID, Count: v.Count, Slot: -1})
	if err != nil {
		return fact{}, err
	}
	fs := []wire.Field{wire.S(64, true, int64(v.ObjectID)), wire.Data(11, true, "drop"), point(13, v.Pos), wire.S(17, true, v.Count), wire.Items(24, true, []wire.Value{good})}
	if v.DropperID != 0 {
		fs = append(fs, wire.S(65, true, int64(v.DropperID)))
	}
	return fact{8, "objects", fs}, nil
}

// events translates observations, never effects. Pages, lessons and skills
// carry their own issued source; a later Scene cannot replace that authority.
// Other notifications still use actual captured scene context when needed.
func (t translator) events(e world.Event, s world.Scene, origin world.Time) ([]fact, error) {
	one := func(kind uint64, domain string, fields ...wire.Field) ([]fact, error) {
		return []fact{{kind, domain, fields}}, nil
	}
	switch v := e.(type) {
	case world.NPCSeen:
		f, err := t.npc(v.NPC, s.Self.Char.ClassID)
		return []fact{f}, err
	case world.PlayerSeen:
		fs, err := t.self(v.Player)
		if err != nil {
			return nil, err
		}
		fs[0].fields = slices.DeleteFunc(fs[0].fields, func(f wire.Field) bool { return f.Number == 19 })
		return fs[:1], nil
	case world.SelfChanged:
		return t.self(v.Self)
	case world.Moving:
		return one(2, "objects", wire.S(64, true, int64(v.ObjectID)), point(13, v.From), point(14, v.To))
	case world.Following:
		return one(2, "objects", wire.S(64, true, int64(v.ObjectID)), point(13, v.From), point(14, v.To))
	case world.Stopped:
		return one(3, "objects", wire.S(64, true, int64(v.ObjectID)), point(13, v.Pos))
	case world.PositionObserved:
		return one(4, "objects", wire.S(64, true, int64(v.ObjectID)), point(13, v.Pos))
	case world.Teleported:
		fs := []wire.Field{wire.S(64, true, int64(v.ObjectID)), point(13, v.Pos)}
		if v.ObjectID == s.Self.Entity.ID {
			if v.Entity != s.Self.Entity || v.Fence == 0 {
				return nil, errSource
			}
			fs = append(fs, wire.U(66, true, uint64(v.Fence)), boolean(67, true))
		}
		return one(4, "objects", fs...)
	case world.ObjectGone:
		return one(10, "objects", wire.S(64, true, int64(v.ObjectID)))
	case world.TargetChosen:
		return one(24, "objects", wire.S(64, true, int64(v.ObjectID)), wire.S(65, true, int64(v.TargetID)))
	case world.TargetCleared:
		return one(24, "objects", wire.S(64, true, int64(v.ObjectID)), wire.S(65, true, 0))
	case world.TargetDetails: // Details are not another target mutation.
		return nil, nil
	case world.Vitals:
		if v.MaxHP < 0 || v.MaxMP < 0 {
			return nil, errSource
		}
		hp, err := display(v.HP)
		if err != nil {
			return nil, err
		}
		mp, err := display(v.MP)
		if err != nil {
			return nil, err
		}
		domain := "objects"
		if v.ObjectID == s.Self.Entity.ID {
			domain = "self"
		}
		return one(16, domain, wire.S(64, true, int64(v.ObjectID)), wire.Obj(25, true, []wire.Field{wire.S(1, true, hp), wire.S(2, true, int64(v.MaxHP)), wire.S(3, true, mp), wire.S(4, true, int64(v.MaxMP))}))
	case world.AttackStarted:
		if v.Damage < 0 {
			return nil, errSource
		}
		return one(5, "objects", wire.S(64, true, int64(v.AttackerID)), wire.S(65, true, int64(v.TargetID)), wire.S(17, true, int64(v.Damage)), boolean(18, !v.Miss))
	case world.Died:
		return one(6, "objects", wire.S(64, true, int64(v.ObjectID)))
	case world.Revived:
		return one(7, "objects", wire.S(64, true, int64(v.ObjectID)))
	case world.ItemDropped:
		f, err := t.dropped(v)
		return []fact{f}, err
	case world.ItemPicked:
		return one(9, "objects", wire.S(64, true, int64(v.PlayerID)), wire.S(65, true, int64(v.ObjectID)), point(13, v.Pos))
	case world.InventoryChanged:
		if len(v.Changes) > maxRows {
			return nil, errCapacity
		}
		fs := []fact{}
		coins := 0
		for _, change := range v.Changes {
			var kind int64
			switch change.Kind {
			case "added":
				kind = 1
			case "modified":
				kind = 2
			case "removed":
				kind = 3
			default:
				return nil, errSource
			}
			good, money, err := t.good(change.Item)
			if err != nil {
				return nil, err
			}
			fields := []wire.Field{wire.S(22, true, kind)}
			if money {
				coins++
				if coins > 1 {
					return nil, errSource
				}
				count := change.Item.Count
				if kind == 3 {
					count = 0
				}
				fields = append(fields, wire.S(23, true, count))
			} else {
				fields = append(fields, wire.Items(24, true, []wire.Value{good}))
			}
			fs = append(fs, fact{15, "inventory", fields})
		}
		return fs, nil
	case world.SkillsShown:
		if !v.Complete {
			return nil, errSource
		}
		f, err := t.known(v.Known)
		return []fact{f}, err
	case world.LearnList:
		for _, a := range v.Dialogue.Affordances {
			if a.Verb != world.VerbLearn {
				return nil, errSource
			}
		}
		fs, err := t.dialogue(v.Dialogue, origin)
		if err != nil {
			return nil, err
		}
		var practice *fact
		for i := range fs {
			if fs[i].kind == 22 {
				practice = &fs[i]
			}
		}
		if len(v.Dialogue.Affordances) == 0 {
			if practice != nil {
				return nil, errSource
			}
			if !validRef(v.Dialogue.Speaker) || v.Dialogue.Page == "" || v.Dialogue.HTML == "" {
				return nil, errSource
			}
			return one(22, "skills", wire.S(64, true, int64(v.Dialogue.Speaker.ID)), wire.S(22, true, 0), wire.U(69, true, 0), wire.Items(34, true, []wire.Value{}), boolean(31, true))
		}
		if practice == nil {
			return nil, errSource
		}
		return []fact{*practice}, nil
	case world.DialoguePage:
		fs, err := t.dialogue(v.Dialogue, origin)
		if err != nil {
			return nil, err
		}
		if len(fs) == 0 {
			return nil, errSource
		}
		return fs[:1], nil
	case world.AffordancesChanged:
		// DialoguePage and LearnList carry the corresponding authority. This
		// refresh is neither another utterance nor another lesson proposal.
		return nil, nil
	case world.Rewarded:
		if v.XP < 0 || v.SP < 0 {
			return nil, errSource
		}
		return one(17, "self", wire.S(64, true, int64(v.ObjectID)), wire.Obj(25, true, []wire.Field{wire.S(6, true, v.XP), wire.S(7, true, int64(v.SP))}))
	case world.LevelChanged:
		if v.After < 1 {
			return nil, errSource
		}
		return one(16, "self", wire.S(64, true, int64(v.ObjectID)), wire.Obj(25, true, []wire.Field{wire.S(5, true, int64(v.After))}))
	case world.Announcement:
		txt, err := text(20, v.Text, wire.ProvenanceSystem)
		if err != nil {
			return nil, err
		}
		return one(18, "dialogue", txt)
	case world.GaitChanged:
		if v.ObjectID == s.Self.Entity.ID {
			if v.Running != s.Self.Running {
				return nil, errSource
			}
			fs, err := t.self(s.Self)
			if err != nil {
				return nil, err
			}
			return fs[:1], nil
		}
		for _, o := range s.Objects {
			if o.Entity.ID != v.ObjectID {
				continue
			}
			switch state := o.State.(type) {
			case world.NPCSeen:
				if state.NPC.Running != v.Running {
					return nil, errSource
				}
				f, err := t.npc(state.NPC, s.Self.Char.ClassID)
				return []fact{f}, err
			case world.PlayerSeen:
				if state.Player.Running != v.Running {
					return nil, errSource
				}
				return t.events(state, s, origin)
			}
		}
		return nil, errSource
	case world.CombatStance, world.PostureChanged, world.Animation, world.ZoneChanged, world.DamageNotice, world.SkillCast, world.SkillLaunched, world.SkillCanceled, world.SkillReuse, world.ActionIgnored, world.GameClock, world.LeftWorld:
		// No matching fact in the profile. Skill runtime and closure are captured
		// by their real sources; housekeeping and damage calculations are not outcomes.
		return nil, nil
	default:
		return nil, errSource
	}
}
