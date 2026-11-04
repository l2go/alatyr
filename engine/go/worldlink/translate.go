// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"errors"
	"math"
	"slices"
	"strings"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

var errSource = errors.New("worldlink: incomplete source")

type translator struct {
	cat                  *catalog.Catalog
	origin               world.Time
	clockOrigin          time.Time
	gatewayOrigin        time.Time
	textBytes, listItems int
}

func display(v float64) (int64, error) {
	if math.IsNaN(v) || math.IsInf(v, 0) || v < 0 || v >= 0x1p63 {
		return 0, errSource
	}
	return int64(v), nil
}
func validRef(r world.Ref) bool { return r.ID > 0 && r.Gen != 0 }
func (t translator) ms(at world.Time) int64 {
	return int64((at - t.origin) / world.Time(time.Millisecond))
}

// gatewayMS preserves the source instant, including sub-millisecond phase.
// A caller's resume time is later than its capture and cannot anchor this clock.
func (t translator) gatewayMS(at world.Time) int64 {
	return t.clockOrigin.Add(time.Duration(at)).Sub(t.gatewayOrigin).Milliseconds()
}
func at(e meaning.Event, p world.Point) meaning.Event { e.X, e.Y, e.Z = p.X, p.Y, p.Z; return e }
func toward(e meaning.Event, p world.Point) meaning.Event {
	e.ToX, e.ToY, e.ToZ = p.X, p.Y, p.Z
	return e
}
func appearance(s world.Self) *meaning.Appearance {
	race := wire.RaceUnknown
	switch s.Char.Race {
	case 0:
		race = wire.RaceHuman
	case 1:
		race = wire.RaceElf
	case 2:
		race = wire.RaceDarkElf
	case 3:
		race = wire.RaceKruvan
	case 4:
		race = wire.RaceDwarf
	}
	sex := wire.SexMale
	if s.Char.Female {
		sex = wire.SexFemale
	}
	branch := wire.BranchUnknown
	if s.ClassKindKnown {
		branch = wire.BranchFighter
		if s.Mage {
			branch = wire.BranchMystic
		}
	}
	return &meaning.Appearance{Race: race, Sex: sex, Branch: branch}
}
func (t translator) player(s world.Self, mine bool) ([]meaning.Event, error) {
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
	a := at(meaning.Event{Kind: meaning.Appeared, ID: s.Entity.ID, Role: meaning.RolePlayer, Name: s.Char.Name, Mine: mine, Speed: speed, Appearance: appearance(s)}, world.Point{X: s.Char.X, Y: s.Char.Y, Z: s.Char.Z})
	if !mine {
		return []meaning.Event{a}, nil
	}
	values := []*int64{}
	for _, v := range []float64{s.Char.CurHP, s.Char.MaxHP, s.Char.CurMP, s.Char.MaxMP} {
		n, err := display(v)
		if err != nil {
			return nil, err
		}
		values = append(values, meaning.Number(n))
	}
	stats := &meaning.Stats{HP: values[0], MaxHP: values[1], MP: values[2], MaxMP: values[3], Level: meaning.Number(int64(s.Char.Level)), XP: meaning.Number(s.Char.Exp), SP: meaning.Number(int64(s.Char.SP)), PAtk: meaning.Number(int64(s.PAtk))}
	if s.PDefKnown {
		stats.PDef = meaning.Number(int64(s.PDef))
	}
	return []meaning.Event{a, {Kind: meaning.Vitals, ID: s.Entity.ID, Stats: stats, Appearance: appearance(s)}}, nil
}
func (t translator) npc(n world.NPCView, class int32) (meaning.Event, error) {
	if !validRef(n.Entity) || n.Entity.ID != n.ObjectID || n.Template.ID <= 0 {
		return meaning.Event{}, errSource
	}
	role := meaning.RoleUnknown
	switch n.Template.Role {
	case catalog.Trainer:
		if t.cat.CanTeach(n.Template.ID, class) {
			role = "tutor"
		}
	case catalog.Merchant:
		role = "provisioner"
	case catalog.Monster:
		role = "creature"
	case catalog.Folk:
		role = "folk"
	}
	speed := n.Template.WalkSpeed
	if n.Running {
		speed = n.Template.RunSpeed
	}
	v, err := display(speed)
	if err != nil || v > math.MaxInt32 {
		return meaning.Event{}, errSource
	}
	e := at(meaning.Event{Kind: meaning.Appeared, ID: n.ObjectID, Role: role, Speed: int32(v), Form: n.Template.Form}, n.Pos)
	if n.Template.SendsServerName {
		e.Name = n.Template.Name
	}
	if e.Form != "" && !wire.ValidForm(e.Form) {
		return meaning.Event{}, errSource
	}
	return e, nil
}
func (t translator) good(it world.Item) (meaning.Good, bool, error) {
	if it.ObjectID <= 0 || it.TemplateID <= 0 || it.Count < 0 {
		return meaning.Good{}, false, errSource
	}
	item, ok := t.cat.Item(it.TemplateID)
	if !ok {
		return meaning.Good{}, false, errSource
	}
	return meaning.Good{Object: it.ObjectID, ID: it.TemplateID, Count: it.Count, Name: item.Name, Slot: meaning.Number(int64(it.Slot))}, item.ConsumeType == "asset", nil
}
func (t translator) carried(rows []world.Carried) (meaning.Event, error) {
	e := meaning.Event{Kind: meaning.Carried, Goods: []meaning.Good{}, Coin: meaning.Number(0)}
	coins := 0
	for _, row := range rows {
		if !validRef(row.Entity) || row.Entity.ID != row.Item.ObjectID || row.Item.Count <= 0 {
			return meaning.Event{}, errSource
		}
		g, money, err := t.good(row.Item)
		if err != nil {
			return meaning.Event{}, err
		}
		if money {
			coins++
			e.Coin = meaning.Number(g.Count)
		} else {
			e.Goods = append(e.Goods, g)
		}
	}
	if coins > 1 {
		return meaning.Event{}, errSource
	}
	return e, nil
}
func (t translator) known(rows []world.KnownSkill) (meaning.Event, error) {
	e := meaning.Event{Kind: meaning.Known, Skills: []meaning.Skill{}, Complete: true}
	for _, row := range rows {
		k, ok := t.cat.Skill(row.Skill.ID, row.Skill.Level)
		if !ok || k.DisplayID != 0 && k.DisplayID != k.ID || k.Passive != row.Passive {
			return meaning.Event{}, errSource
		}
		e.Skills = append(e.Skills, meaning.Skill{ID: k.ID, Level: k.Level, Passive: k.Passive, Disabled: row.Disabled || k.NotDone()})
	}
	return e, nil
}
func choiceAction(a world.Affordance) (string, bool) {
	if a.Verb != world.VerbChoose || len(a.Parameters) != 1 || a.Parameters[0].Name != "action" {
		return "", false
	}
	action, ok := a.Parameters[0].Value.Label()
	return action, ok && action != "" && !strings.ContainsRune(action, 0)
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
func (t translator) dialogue(d world.Dialogue) ([]meaning.Input, error) {
	if d.Page == "" && d.HTML == "" && d.Speaker == (world.Ref{}) && len(d.Affordances) == 0 {
		return nil, nil
	}
	if !validRef(d.Speaker) || d.Page == "" || d.HTML == "" {
		return nil, errSource
	}
	if len(d.Affordances) > 0 {
		if terms, ok := d.Affordances[0].Trade(); ok {
			in, err := t.trade(d, terms.List, d.Affordances[0].Verb == world.VerbSell)
			return []meaning.Input{in}, err
		}
	}
	page, err := parseHTML(d.HTML, t.listItems, t.textBytes)
	if err != nil {
		return nil, err
	}
	e := meaning.Event{Kind: meaning.Spoke, ID: d.Speaker.ID, Text: page.text, Lines: []meaning.Line{}}
	practice := meaning.Event{Kind: meaning.Practice, ID: d.Speaker.ID, Goods: []meaning.Good{}, Complete: true}
	var born *int64
	token := uint64(0)
	seen := map[uint64]bool{}
	for _, a := range d.Affordances {
		if a.ID == 0 || seen[a.ID] || a.Target != d.Speaker || a.Expires-a.Issued != world.Time(time.Minute) || a.Issued < 0 {
			return nil, errSource
		}
		seen[a.ID] = true
		b := t.gatewayMS(a.Issued)
		if born == nil || b < *born {
			born = meaning.Number(b)
		}
		if token == 0 {
			token = a.ID
		}
		switch a.Verb {
		case world.VerbChoose:
			action, ok := choiceAction(a)
			if !ok || len(a.Cost) != 0 {
				return nil, errSource
			}
			found := false
			for _, option := range page.options {
				if option.action == action {
					if found {
						return nil, errSource
					}
					found = true
					e.Lines = append(e.Lines, meaning.Line{Label: option.label, Say: action})
				}
			}
			if !found {
				return nil, errSource
			}
		case world.VerbLearn:
			id, level, ok := lesson(a)
			if !ok {
				return nil, errSource
			}
			skill, ok := t.cat.Skill(id, level)
			if !ok || skill.DisplayID != 0 && skill.DisplayID != id {
				return nil, errSource
			}
			maxLevel := level
			for next := level + 1; next <= 1000; next++ {
				if _, ok := t.cat.Skill(id, next); !ok {
					break
				}
				maxLevel = next
			}
			practice.Goods = append(practice.Goods, meaning.Good{ID: id, Count: int64(level), Object: maxLevel, Price: a.Cost[0].Quantity})
		default:
			return nil, errSource
		}
	}
	if born == nil {
		born = meaning.Number(0)
	}
	out := []meaning.Input{{Event: e, Token: token, ProposalBorn: born}}
	if len(practice.Goods) > 0 {
		out = append(out, meaning.Input{Event: practice, Token: token, ProposalBorn: born})
	}
	return out, nil
}
func (t translator) trade(d world.Dialogue, list int32, sell bool) (meaning.Input, error) {
	e := meaning.Event{Kind: meaning.Shop, ID: d.Speaker.ID, List: list, Goods: []meaning.Good{}, Complete: true}
	verb := world.VerbBuy
	if sell {
		e.Kind = meaning.Offer
		verb = world.VerbSell
	}
	if !validRef(d.Speaker) || d.Page == "" || d.HTML == "" {
		return meaning.Input{}, errSource
	}
	born := int64(0)
	token := uint64(0)
	seen := map[uint64]bool{}
	for _, a := range d.Affordances {
		terms, ok := a.Trade()
		if !ok || a.Verb != verb || a.ID == 0 || seen[a.ID] || a.Target != d.Speaker || terms.List != list || a.Expires-a.Issued != world.Time(time.Minute) {
			return meaning.Input{}, errSource
		}
		seen[a.ID] = true
		b := t.gatewayMS(a.Issued)
		if token == 0 || b < born {
			born = b
		}
		if token == 0 {
			token = a.ID
		}
		item, ok := t.cat.Item(terms.Item)
		if !ok {
			return meaning.Input{}, errSource
		}
		e.Goods = append(e.Goods, meaning.Good{Object: terms.Object, ID: terms.Item, Count: terms.Quantity, Price: terms.Price, Name: item.Name})
	}
	return meaning.Input{Event: e, Token: token, ProposalBorn: meaning.Number(born)}, nil
}

func (t translator) view(s world.Scene) (meaning.View, error) {
	out := meaning.View{Ready: s.End.Ready, Incarnations: map[int32]uint64{}}
	for _, coverage := range s.Coverage {
		if !coverage.Complete {
			out.Domains = append(out.Domains, string(coverage.Domain))
			out.Ready = false
		}
	}
	if s.Begin.Sequence != s.End.Sequence || s.Begin.Seq != s.End.Seq {
		return out, errSource
	}
	events, err := t.player(s.Self, true)
	if err != nil {
		return out, err
	}
	self := events[0]
	self.Stats = events[1].Stats
	out.Incarnations[s.Self.Entity.ID] = s.Self.Entity.Gen
	actors := []meaning.Event{self}
	var spatial []meaning.Event
	if s.Self.Dead {
		spatial = append(spatial, meaning.Event{Kind: meaning.Died, ID: self.ID})
	}
	if s.Moving {
		spatial = append(spatial, toward(at(meaning.Event{Kind: meaning.Moved, ID: self.ID}, world.Point{X: s.Self.Char.X, Y: s.Self.Char.Y, Z: s.Self.Char.Z}), s.Destination))
	}
	for _, o := range s.Objects {
		if !validRef(o.Entity) {
			return out, errSource
		}
		out.Incarnations[o.Entity.ID] = o.Entity.Gen
		var e meaning.Event
		var dead bool
		switch v := o.State.(type) {
		case world.NPCSeen:
			e, err = t.npc(v.NPC, s.Self.Char.ClassID)
			dead = v.NPC.Dead
		case world.PlayerSeen:
			var es []meaning.Event
			es, err = t.player(v.Player, false)
			if err == nil {
				e = es[0]
			}
			dead = v.Player.Dead
		case world.ItemDropped:
			e, err = t.drop(v)
		default:
			err = errSource
		}
		if err != nil {
			return out, err
		}
		actors = append(actors, e)
		if dead {
			spatial = append(spatial, meaning.Event{Kind: meaning.Died, ID: e.ID})
		}
		if o.Moving {
			spatial = append(spatial, toward(meaning.Event{Kind: meaning.Moved, ID: e.ID}, o.Destination))
		}
	}
	slices.SortFunc(actors, func(a, b meaning.Event) int {
		if a.ID < b.ID {
			return -1
		}
		if a.ID > b.ID {
			return 1
		}
		return 0
	})
	// The legacy checkpoint lists actor descriptions, current movement/death,
	// then self resources and complete lists. Source targets stay on descriptions.
	for _, e := range actors {
		if e.ID == self.ID {
			e.Target = s.Target.ID
		} else {
			for _, o := range s.Objects {
				if o.Entity.ID == e.ID {
					e.Target = o.Target.ID
					break
				}
			}
		}
		out.Events = append(out.Events, meaning.Input{Event: e})
		for _, v := range spatial {
			if v.ID == e.ID {
				out.Events = append(out.Events, meaning.Input{Event: v})
			}
		}
	}
	out.Events = append(out.Events, meaning.Input{Event: events[1]})
	inv, err := t.carried(s.Inventory)
	if err != nil {
		return out, err
	}
	known, err := t.known(s.Skills)
	if err != nil {
		return out, err
	}
	out.Events = append(out.Events, meaning.Input{Event: inv}, meaning.Input{Event: known}, meaning.Input{Event: meaning.Event{Kind: meaning.Quests, Quests: []meaning.QuestState{}, Complete: true}})
	dialog, err := t.dialogue(s.Dialogue)
	if err != nil {
		return out, err
	}
	out.Events = append(out.Events, dialog...)
	for i := range out.Events {
		out.Events[i].Event.MS = t.ms(s.End.Time)
		out.Events[i].ReceivedAt = t.gatewayMS(s.End.Time)
	}
	return out, nil
}
func (t translator) drop(v world.ItemDropped) (meaning.Event, error) {
	if v.Count <= 0 || v.Count > math.MaxInt32 || v.DropperID < 0 {
		return meaning.Event{}, errSource
	}
	g, _, err := t.good(world.Item{ObjectID: v.ObjectID, TemplateID: v.TemplateID, Count: v.Count, Slot: -1})
	if err != nil {
		return meaning.Event{}, err
	}
	return at(meaning.Event{Kind: meaning.Dropped, ID: v.ObjectID, Role: meaning.RoleDrop, Target: v.DropperID, Amount: int32(v.Count), Goods: []meaning.Good{g}}, v.Pos), nil
}
