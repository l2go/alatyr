// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"slices"
	"strings"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func (t translator) self(s world.Self) ([]fact, error) {
	if !validRef(s.Entity) || s.Char.ObjectID != s.Entity.ID || s.Char.Level < 1 || s.Char.Exp < 0 || s.Char.SP < 0 {
		return nil, errSource
	}
	speed := s.WalkSpeed
	if s.Running {
		speed = s.RunSpeed
	}
	if speed < 0 {
		return nil, errSource
	}
	name, err := text(12, s.Char.Name, wire.ProvenancePlayer)
	if err != nil {
		return nil, err
	}
	f := fact{1, "objects", []wire.Field{wire.S(64, true, int64(s.Entity.ID)), wire.Data(11, true, "player"), name, point(13, world.Point{X: s.Char.X, Y: s.Char.Y, Z: s.Char.Z}), wire.U(15, true, uint64(speed)), boolean(19, true), boolean(70, !s.Dead), appearance(s)}}
	stats := []wire.Field{}
	for i, v := range []float64{s.Char.CurHP, s.Char.MaxHP, s.Char.CurMP, s.Char.MaxMP} {
		n, err := display(v)
		if err != nil {
			return nil, err
		}
		stats = append(stats, wire.S(uint32(i+1), true, n))
	}
	stats = append(stats, wire.S(5, true, int64(s.Char.Level)), wire.S(6, true, s.Char.Exp), wire.S(7, true, int64(s.Char.SP)))
	stats = append(stats, wire.S(8, true, int64(s.PAtk)))
	if s.PDefKnown {
		stats = append(stats, wire.S(9, true, int64(s.PDef)))
	}
	return []fact{f, {16, "self", []wire.Field{wire.S(64, true, int64(s.Entity.ID)), wire.Obj(25, true, stats), appearance(s)}}}, nil
}

func (t translator) npc(v world.NPCView, class int32) (fact, error) {
	if !validRef(v.Entity) || v.Entity.ID != v.ObjectID || v.Template.ID <= 0 {
		return fact{}, errSource
	}
	role := "unknown"
	if v.Template.Role == catalog.Trainer && t.catalog != nil && t.catalog.CanTeach(v.Template.ID, class) {
		role = "tutor"
	} else if v.Template.Role == catalog.Merchant {
		role = "provisioner"
	} else if v.Template.Role == catalog.Monster {
		role = "creature"
	} else if v.Template.Role == catalog.Folk {
		role = "folk"
	}
	speed := v.Template.WalkSpeed
	if v.Running {
		speed = v.Template.RunSpeed
	}
	n, err := display(speed)
	if err != nil || n > 1<<31-1 {
		return fact{}, errSource
	}
	fields := []wire.Field{wire.S(64, true, int64(v.ObjectID)), wire.Data(11, true, role), point(13, v.Pos), wire.U(15, true, uint64(n)), boolean(70, !v.Dead), wire.S(73, true, int64(v.Template.ID))}
	// A hidden template name is not a visible name.
	if v.Template.SendsServerName {
		name, err := text(12, v.Template.Name, wire.ProvenanceAuthored)
		if err != nil {
			return fact{}, err
		}
		fields = append(fields, name)
	}
	return fact{1, "objects", fields}, nil
}

func (t translator) good(it world.Item) (wire.Value, bool, error) {
	if t.catalog == nil || it.ObjectID <= 0 || it.TemplateID <= 0 || it.Count < 0 {
		return wire.Value{}, false, errSource
	}
	template, ok := t.catalog.Item(it.TemplateID)
	if !ok || template.ID != it.TemplateID {
		return wire.Value{}, false, errSource
	}
	name, err := text(6, template.Name, wire.ProvenanceAuthored)
	if err != nil {
		return wire.Value{}, false, err
	}
	return wire.Value{Fields: []wire.Field{wire.S(2, true, int64(it.ObjectID)), wire.S(3, true, int64(it.TemplateID)), wire.S(4, true, it.Count), name, wire.S(8, true, int64(it.Slot))}}, template.ConsumeType == "asset", nil
}

func (t translator) carried(rows []world.Carried) (fact, error) {
	if len(rows) > maxRows {
		return fact{}, errCapacity
	}
	goods := []wire.Value{}
	coin := int64(0)
	coins := 0
	ids := map[int32]bool{}
	for _, row := range rows {
		if !validRef(row.Entity) || row.Entity.ID != row.Item.ObjectID || row.Item.Count <= 0 || ids[row.Entity.ID] {
			return fact{}, errSource
		}
		ids[row.Entity.ID] = true
		v, money, err := t.good(row.Item)
		if err != nil {
			return fact{}, err
		}
		if money {
			coins++
			coin = row.Item.Count
		} else {
			goods = append(goods, v)
		}
	}
	if coins > 1 {
		return fact{}, errSource
	}
	return fact{14, "inventory", []wire.Field{wire.Items(24, true, goods), wire.S(23, true, coin), boolean(31, true)}}, nil
}

func (t translator) known(rows []world.KnownSkill) (fact, error) {
	if len(rows) > maxRows {
		return fact{}, errCapacity
	}
	values := []wire.Value{}
	ids := map[int32]bool{}
	for _, r := range rows {
		if t.catalog == nil || r.Skill.ID <= 0 || r.Skill.Level < 1 || ids[r.Skill.ID] {
			return fact{}, errSource
		}
		sk, ok := t.catalog.Skill(r.Skill.ID, r.Skill.Level)
		if !ok || sk.ID != r.Skill.ID || sk.Level != r.Skill.Level || sk.DisplayID != 0 && sk.DisplayID != sk.ID || sk.Passive != r.Passive {
			return fact{}, errSource
		}
		ids[r.Skill.ID] = true
		values = append(values, wire.Value{Fields: []wire.Field{wire.S(1, true, int64(r.Skill.ID)), wire.S(2, true, int64(r.Skill.Level)), boolean(3, r.Passive), boolean(4, r.Disabled || sk.NotDone())}})
	}
	return fact{23, "skills", []wire.Field{wire.Items(30, true, values), boolean(31, true)}}, nil
}

func choices(d world.Dialogue) (page, []wire.Value, error) {
	p, err := parseHTML(d.HTML)
	if err != nil {
		return page{}, nil, err
	}
	opts := []wire.Value{}
	bound := make(map[string]bool)
	ids := make(map[uint64]bool)
	for _, a := range d.Affordances {
		if a.Verb != world.VerbChoose {
			continue
		}
		action, ok := choiceAction(a)
		if !ok || !validRef(a.Target) || a.Target != d.Speaker || a.ID == 0 || ids[a.ID] || a.Expires <= a.Issued || len(a.Cost) != 0 || bound[action] {
			return page{}, nil, errSource
		}
		ids[a.ID], bound[action] = true, true
		matched := false
		for _, o := range p.options {
			if o.action != action {
				continue
			}
			if matched {
				return page{}, nil, errSource
			}
			matched = true
			label, err := text(2, o.label, wire.ProvenanceAuthored)
			if err != nil {
				return page{}, nil, err
			}
			label.Essential = true
			opts = append(opts, wire.Value{Fields: []wire.Field{wire.U(1, true, uint64(len(opts)+1)), label, wire.Data(3, true, o.action)}})
		}
		if !matched {
			return page{}, nil, errSource
		}
	}
	return p, opts, nil
}

func choiceAction(a world.Affordance) (string, bool) {
	if a.Verb != world.VerbChoose || len(a.Parameters) != 1 || a.Parameters[0].Name != "action" {
		return "", false
	}
	action, ok := a.Parameters[0].Value.Label()
	return action, ok && action != "" && !strings.ContainsRune(action, 0)
}

func (t translator) dialogue(d world.Dialogue, origin world.Time) ([]fact, error) {
	if d.Page == "" && d.HTML == "" && d.Speaker == (world.Ref{}) && len(d.Affordances) == 0 {
		return nil, nil
	}
	if !validRef(d.Speaker) || d.Page == "" || d.HTML == "" || len(d.Affordances) > maxRows {
		return nil, errSource
	}
	if len(d.Affordances) > 0 {
		if offer, ok := d.Affordances[0].Trade(); ok {
			return t.trade(d, offer.List, d.Affordances[0].Verb == world.VerbSell, origin)
		}
	}
	p, opts, err := choices(d)
	if err != nil {
		return nil, err
	}
	txt, err := text(20, p.text, wire.ProvenanceAuthored)
	if err != nil {
		return nil, err
	}
	fields := []wire.Field{wire.S(64, true, int64(d.Speaker.ID)), txt, wire.Items(68, true, opts)}
	var issued world.Time
	haveIssue := false
	lessons := []wire.Value{}
	ids := map[uint64]bool{}
	for _, a := range d.Affordances {
		if a.Verb != world.VerbChoose && a.Verb != world.VerbLearn {
			return nil, errSource
		}
		if a.ID == 0 || ids[a.ID] || a.Target != d.Speaker || a.Expires <= a.Issued || a.Expires-a.Issued != world.Time(60_000_000_000) || a.Issued < 0 {
			return nil, errSource
		}
		ids[a.ID] = true
		if !haveIssue || a.Issued < issued {
			issued = a.Issued
		}
		haveIssue = true
		if a.Verb != world.VerbLearn {
			continue
		}
		id, level, ok := lesson(a)
		if !ok {
			return nil, errSource
		}
		if t.catalog == nil {
			return nil, errSource
		}
		sk, ok := t.catalog.Skill(id, level)
		if !ok || sk.DisplayID != 0 && sk.DisplayID != id {
			return nil, errSource
		}
		maxLevel := level
		// Skill XML defines consecutive authored levels, bounded at 1000. Read
		// their actual maximum rather than label the offered level as the maximum.
		for next := level + 1; next <= 1000; next++ {
			if _, ok := t.catalog.Skill(id, next); !ok {
				break
			}
			maxLevel = next
		}
		lessons = append(lessons, wire.Value{Fields: []wire.Field{wire.U(1, true, a.ID), wire.S(2, true, int64(id)), wire.S(3, true, int64(level)), wire.S(4, true, int64(maxLevel)), wire.S(5, true, a.Cost[0].Quantity)}})
	}
	if haveIssue {
		ms, err := elapsed(issued, origin)
		if err != nil {
			return nil, err
		}
		fields = append(fields, wire.S(74, true, ms))
	}
	result := []fact{{11, "dialogue", fields}}
	if len(lessons) > 0 {
		ms, err := elapsed(issued, origin)
		if err != nil {
			return nil, err
		}
		result = append(result, fact{22, "skills", []wire.Field{wire.S(64, true, int64(d.Speaker.ID)), wire.S(22, true, 0), wire.U(69, true, 0), wire.Items(34, true, lessons), boolean(31, true), wire.S(74, true, ms)}})
	}
	return result, nil
}

func lesson(a world.Affordance) (int32, int32, bool) {
	if a.Verb != world.VerbLearn || len(a.Parameters) != 2 || len(a.Cost) != 1 || a.Cost[0].Resource != (world.Resource{Kind: world.ResourceSP}) || a.Cost[0].Quantity < 0 {
		return 0, 0, false
	}
	var id, level int64
	for _, p := range a.Parameters {
		v, ok := p.Value.Integer()
		if !ok {
			return 0, 0, false
		}
		switch p.Name {
		case "skill":
			id = v
		case "level":
			level = v
		default:
			return 0, 0, false
		}
	}
	if id < 1 || id > 32000 || level < 1 || level > 1000 {
		return 0, 0, false
	}
	return int32(id), int32(level), true
}

// checkpoint translates every supported captured domain independently. It can
// return incomplete data for diagnostics, never a partial ready checkpoint.
func (t translator) checkpoint(s world.Scene, epoch, next uint64, origin world.Time) (bridge.Snapshot, uint64, error) {
	out := bridge.Snapshot{GatewayEpoch: epoch}
	initial := next
	if len(s.Objects) > maxRows || len(s.Coverage) > 7 {
		return out, next, errCapacity
	}
	if s.Begin.Sequence != s.End.Sequence || s.Begin.Seq != s.End.Seq || s.Begin.Time != s.End.Time || s.Begin.Fence != s.End.Fence {
		return out, next, errSource
	}
	coverage := map[world.Domain]world.Coverage{}
	for _, c := range s.Coverage {
		switch c.Domain {
		case world.DomainSelf, world.DomainObjects, world.DomainInventory, world.DomainSkills, world.DomainDialogue, world.DomainTrade, world.DomainQuests:
		default:
			return out, next, errSource
		}
		if _, ok := coverage[c.Domain]; ok {
			return out, next, errSource
		}
		coverage[c.Domain] = c
	}
	domains := []world.Domain{world.DomainSelf, world.DomainObjects, world.DomainInventory, world.DomainSkills, world.DomainDialogue, world.DomainTrade, world.DomainQuests}
	for _, domain := range domains {
		c, ok := coverage[domain]
		if !ok || !c.Complete || c.Absent && domain != world.DomainTrade && domain != world.DomainQuests || domain == world.DomainQuests && !c.Absent {
			out.Incomplete = append(out.Incomplete, string(domain))
			continue
		}
		var fs []fact
		var err error
		switch domain {
		case world.DomainSelf:
			fs, err = t.self(s.Self)
		case world.DomainObjects:
			for _, o := range s.Objects {
				if !validRef(o.Entity) {
					err = errSource
					break
				}
				var f fact
				switch v := o.State.(type) {
				case world.NPCSeen:
					if v.NPC.Entity != o.Entity {
						err = errSource
					} else {
						f, err = t.npc(v.NPC, s.Self.Char.ClassID)
					}
				case world.PlayerSeen:
					if v.Player.Entity != o.Entity {
						err = errSource
						break
					}
					var playerFacts []fact
					playerFacts, err = t.self(v.Player)
					if err == nil {
						f = playerFacts[0]
						f.fields = slices.DeleteFunc(f.fields, func(f wire.Field) bool { return f.Number == 19 })
					}
				case world.ItemDropped:
					f, err = t.dropped(v)
				default:
					err = errSource
				}
				if err != nil {
					break
				}
				fs = append(fs, f)
				if o.Target != (world.Ref{}) && !validRef(o.Target) {
					err = errSource
					break
				}
				fs = append(fs, fact{24, "objects", []wire.Field{wire.S(64, true, int64(o.Entity.ID)), wire.S(65, true, int64(o.Target.ID))}})
				if o.Moving {
					fs = append(fs, fact{2, "objects", []wire.Field{wire.S(64, true, int64(o.Entity.ID)), point(14, o.Destination)}})
				}
			}
			if err == nil {
				if s.Target != (world.Ref{}) && !validRef(s.Target) {
					err = errSource
				} else {
					fs = append(fs, fact{24, "objects", []wire.Field{wire.S(64, true, int64(s.Self.Entity.ID)), wire.S(65, true, int64(s.Target.ID))}})
				}
				if s.Moving {
					fs = append(fs, fact{2, "objects", []wire.Field{wire.S(64, true, int64(s.Self.Entity.ID)), point(14, s.Destination)}})
				}
			}
		case world.DomainInventory:
			var f fact
			f, err = t.carried(s.Inventory)
			fs = []fact{f}
		case world.DomainSkills:
			var f fact
			f, err = t.known(s.Skills)
			fs = []fact{f}
		case world.DomainDialogue:
			fs, err = t.dialogue(s.Dialogue, origin)
		case world.DomainQuests:
			if c.Absent {
				fs = []fact{{21, "quests", []wire.Field{wire.Items(29, true, []wire.Value{}), boolean(31, true)}}}
			}
		}
		if err != nil {
			if errors.Is(err, errSource) {
				out.Incomplete = append(out.Incomplete, string(domain))
				continue
			}
			return bridge.Snapshot{}, initial, err
		}
		rs, newNext, err := factRecords(fs, epoch, next, s.End.Time, origin)
		if err != nil {
			return bridge.Snapshot{}, initial, err
		}
		for _, r := range rs {
			if r.EncodedBytes > maxCheckpointBytes-out.EncodedBytes {
				return bridge.Snapshot{}, initial, errCapacity
			}
			out.EncodedBytes += r.EncodedBytes
		}
		out.Facts = append(out.Facts, rs...)
		next = newNext
	}
	out.Ready = s.End.Ready && !s.Self.Teleporting && len(out.Incomplete) == 0
	out.FenceSeq = next - 1
	return out, next, nil
}
