// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink

import (
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
)

func (t translator) events(e world.Event, class int32, self world.Ref) ([]meaning.Input, uint64, error) {
	var out []meaning.Input
	var incarnation uint64
	appendEvent := func(e meaning.Event) { out = append(out, meaning.Input{Event: e}) }
	switch v := e.(type) {
	case world.NPCSeen:
		ev, err := t.npc(v.NPC, class)
		if err != nil {
			return nil, 0, err
		}
		appendEvent(ev)
		incarnation = v.NPC.Entity.Gen
	case world.PlayerSeen:
		es, err := t.player(v.Player, false)
		if err != nil {
			return nil, 0, err
		}
		for _, ev := range es {
			appendEvent(ev)
		}
		incarnation = v.Player.Entity.Gen
	case world.SelfChanged:
		es, err := t.player(v.Self, true)
		if err != nil {
			return nil, 0, err
		}
		for _, ev := range es {
			appendEvent(ev)
		}
		incarnation = v.Self.Entity.Gen
	case world.Moving:
		appendEvent(toward(at(meaning.Event{Kind: meaning.Moved, ID: v.ObjectID}, v.From), v.To))
	case world.Following:
		appendEvent(toward(at(meaning.Event{Kind: meaning.Moved, ID: v.ObjectID}, v.From), v.To))
	case world.Stopped:
		appendEvent(at(meaning.Event{Kind: meaning.Stopped, ID: v.ObjectID}, v.Pos))
	case world.PositionObserved:
		appendEvent(at(meaning.Event{Kind: meaning.Corrected, ID: v.ObjectID}, v.Pos))
	case world.Teleported:
		appendEvent(at(meaning.Event{Kind: meaning.Corrected, ID: v.ObjectID}, v.Pos))
	case world.ObjectGone:
		appendEvent(meaning.Event{Kind: meaning.Gone, ID: v.ObjectID})
	case world.TargetChosen:
		appendEvent(meaning.Event{Kind: meaning.Targeted, ID: v.ObjectID, Target: v.TargetID})
	case world.TargetCleared:
		appendEvent(meaning.Event{Kind: meaning.Targeted, ID: v.ObjectID})
	case world.Vitals:
		hp, err := display(v.HP)
		if err != nil {
			return nil, 0, err
		}
		mp, err := display(v.MP)
		if err != nil || v.MaxHP < 0 || v.MaxMP < 0 {
			return nil, 0, errSource
		}
		appendEvent(meaning.Event{Kind: meaning.Vitals, ID: v.ObjectID, Stats: &meaning.Stats{HP: meaning.Number(hp), MP: meaning.Number(mp), MaxHP: meaning.Number(int64(v.MaxHP)), MaxMP: meaning.Number(int64(v.MaxMP))}})
	case world.AttackStarted:
		if v.Damage < 0 {
			return nil, 0, errSource
		}
		appendEvent(meaning.Event{Kind: meaning.Struck, ID: v.AttackerID, Target: v.TargetID, Amount: v.Damage, Hit: !v.Miss})
	case world.Died:
		appendEvent(meaning.Event{Kind: meaning.Died, ID: v.ObjectID, Mine: v.ObjectID == self.ID})
	case world.Revived:
		appendEvent(meaning.Event{Kind: meaning.Revived, ID: v.ObjectID})
	case world.ItemDropped:
		ev, err := t.drop(v)
		if err != nil {
			return nil, 0, err
		}
		appendEvent(ev)
		incarnation = v.Entity.Gen
	case world.ItemPicked:
		appendEvent(at(meaning.Event{Kind: meaning.Took, ID: v.PlayerID, Target: v.ObjectID}, v.Pos))
	case world.InventoryChanged:
		coins := 0
		for _, change := range v.Changes {
			g, money, err := t.good(change.Item)
			if err != nil {
				return nil, 0, err
			}
			switch change.Kind {
			case "added":
				g.Change = 1
			case "modified":
				g.Change = 2
			case "removed":
				g.Change = 3
			default:
				return nil, 0, errSource
			}
			ev := meaning.Event{Kind: meaning.Inventory, List: g.Change}
			if money {
				coins++
				if coins > 1 {
					return nil, 0, errSource
				}
				ev.Coin = meaning.Number(g.Count)
				if g.Change == 3 {
					ev.Coin = meaning.Number(0)
				}
			} else {
				ev.Goods = []meaning.Good{g}
			}
			appendEvent(ev)
		}
	case world.SkillsShown:
		if !v.Complete {
			return nil, 0, errSource
		}
		ev, err := t.known(v.Known)
		if err != nil {
			return nil, 0, err
		}
		appendEvent(ev)
	case world.TradeList:
		in, err := t.trade(v.Dialogue, v.List, v.Sell)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, in)
	case world.LearnList:
		inputs, err := t.dialogue(v.Dialogue)
		if err != nil {
			return nil, 0, err
		}
		for _, in := range inputs {
			if in.Event.Kind == meaning.Practice {
				out = append(out, in)
			}
		}
		if len(out) == 0 {
			appendEvent(meaning.Event{Kind: meaning.Practice, ID: v.Dialogue.Speaker.ID, Goods: []meaning.Good{}, Complete: true})
		}
	case world.DialoguePage:
		inputs, err := t.dialogue(v.Dialogue)
		if err != nil || len(inputs) == 0 {
			return nil, 0, errSource
		}
		out = append(out, inputs[0])
	case world.Rewarded:
		if v.XP < 0 || v.SP < 0 {
			return nil, 0, errSource
		}
		appendEvent(meaning.Event{Kind: meaning.Reward, ID: v.ObjectID, Stats: &meaning.Stats{XP: meaning.Number(v.XP), SP: meaning.Number(int64(v.SP))}})
	case world.LevelChanged:
		appendEvent(meaning.Event{Kind: meaning.Vitals, ID: v.ObjectID, Stats: &meaning.Stats{Level: meaning.Number(int64(v.After))}})
	case world.Announcement:
		appendEvent(meaning.Event{Kind: meaning.Notice, Text: v.Text})
	case world.GaitChanged:
		if v.Actor == nil {
			return nil, 0, errSource
		}
		var err error
		out, incarnation, err = t.events(v.Actor, class, self)
		if err != nil {
			return nil, 0, err
		}
	case world.TargetDetails, world.AffordancesChanged, world.CombatStance, world.PostureChanged, world.Animation, world.ZoneChanged, world.DamageNotice, world.SkillCast, world.SkillLaunched, world.SkillCanceled, world.SkillReuse, world.ActionIgnored, world.GameClock, world.LeftWorld:
	default:
		return nil, 0, errSource
	}
	for i := range out {
		out[i].Event.MS = t.ms(e.Time())
		out[i].ReceivedAt = t.gatewayMS(e.Time())
	}
	return out, incarnation, nil
}
