// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"errors"
	"reflect"
	"strconv"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func parityHuntPickup(r *parityRig) parityResult {
	out := parityResult{Balances: make(map[string]int64), Checks: make(map[string]bool)}
	target := r.actor(18003)
	before := r.current()
	r.p.Send(r.intent(1, 12, r.ref(target.ID, 10), wire.Obj(18, true, []wire.Field{wire.S(1, true, 3), wire.S(2, true, 1)})))
	out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0))
	r.wait(func() bool { return r.current().MP == before.MP-4 })
	out.Balances["pdam-mp-cost"] = 4
	out.Checks["pdam-known-active"] = true
	r.p.Send(r.intent(2, 3, r.ref(target.ID, 10)))
	out.Outcomes = append(out.Outcomes, r.outcome(2, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0))
	var drop meaning.DecodedEvent
	var coinCount int64
	r.wait(func() bool {
		for _, f := range r.p.Records {
			if f.Type != wire.Fact || gatewaytest.U(f, 1) != 8 {
				continue
			}
			d, err := meaning.EventValue(f)
			if err != nil {
				r.t.Fatal(err)
			}
			if len(d.Event.Goods) != 1 {
				continue
			}
			if d.Event.Goods[0].ID == 57 {
				coinCount = d.Event.Goods[0].Count
			}
			if d.Event.Goods[0].ID == 1060 && drop.Event.ID == 0 {
				drop = d
			}
		}
		return drop.Event.ID != 0 && coinCount != 0
	})
	count := drop.Event.Goods[0].Count
	if coinCount < 4 || coinCount > 8 || count != 1 {
		r.t.Fatalf("authored coins/probe draught=%d/%d, want 4..8/1", coinCount, count)
	}
	r.p.Send(r.intent(3, 1, wire.At(11, true, drop.Event.X, drop.Event.Y, drop.Event.Z)))
	r.outcome(3, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
	r.wait(func() bool {
		s := r.current()
		dx, dy := int64(s.Point[0])-int64(drop.Event.X), int64(s.Point[1])-int64(drop.Event.Y)
		return dx*dx+dy*dy < 100
	})
	// A killing hit can still own a protected attack animation. In the
	// reference world a pickup queued behind it has no synchronous receipt.
	// Let the authored attack and movement finish before requesting the take.
	r.advance(2 * time.Second)
	request := r.intent(4, 4, r.ref(drop.Event.ID, 20))
	r.p.Send(request)
	r.result(4, wire.StageSent)
	r.advance(100 * time.Millisecond)
	out.Outcomes = append(out.Outcomes, r.outcome(4, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1))
	out.Order = r.causalOrder(4, "take")
	state := r.current()
	if state.XP != 156 || state.SP != 108 || state.count(57) != 1000 || state.count(1060) != 3+count {
		r.t.Fatalf("hunt x1/pickup balances: %+v, observed draughts=%d", state, count)
	}
	writesBefore := len(r.port.commands())
	r.p.Send(request)
	r.wait(func() bool {
		done := 0
		for _, f := range r.p.Records {
			if f.Type == wire.Result && gatewaytest.U(f, 1) == 4 && gatewaytest.U(f, 2) == uint64(wire.StageDone) {
				done++
			}
		}
		return done == 2
	})
	if len(r.port.commands()) != writesBefore || r.current().count(57) != state.count(57) || r.current().count(1060) != state.count(1060) {
		r.t.Fatal("pickup replay changed world")
	}
	saved := r.save()
	if saved.XP != state.XP || saved.SP != state.SP || saved.count(57) != state.count(57) || saved.count(1060) != state.count(1060) {
		r.t.Fatal("hunt rewards did not survive Close")
	}
	// The authored coin roll remains random. A guaranteed extra draught row
	// provides a shared item-transfer witness: the reference bridge does not
	// certify currency-only pickups. Coins remain on the ground in this case.
	out.Balances["xp"], out.Balances["sp"] = saved.XP, int64(saved.SP)
	out.Balances["draughts"], out.Balances["coins"] = saved.count(1060), saved.count(57)
	out.Checks["observed-item-count-conserved"], out.Checks["ordinary-roll-4-to-8"], out.Checks["saved-offline"] = true, true, true
	return out
}

func parityResume(r *parityRig) parityResult {
	out := parityResult{Checks: make(map[string]bool), Balances: make(map[string]int64)}
	target := r.actor(30001)
	old := r.ref(target.ID, 10)
	r.p.Send(r.intent(1, 11, old))
	r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	oldSession := r.p.Session
	beforeState := r.current()
	if _, _, err := r.w.Subscribe(r.t.Context(), r.owner); err != nil {
		r.t.Fatal(err)
	}
	r.wait(func() bool {
		if !r.detached.Load() {
			return false
		}
		_, err := r.g.Status(r.t.Context(), "one")
		return errors.Is(err, gateway.ErrAuth)
	})
	if err := r.scope.Detach(); err != nil {
		r.t.Fatal(err)
	}
	scope, state, err := r.session.Resume(r.t.Context())
	if err != nil {
		r.t.Fatal(err)
	}
	r.mu.Lock()
	r.scope, r.state = scope, state
	r.mu.Unlock()
	r.p = gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeAct, true)
	r.sync()
	if r.p.Session == oldSession {
		r.t.Fatal("Resume reused participant session")
	}
	for _, f := range r.p.Records {
		if f.Type == wire.Result || f.Type == wire.Fact && gatewaytest.U(f, 32) != 0 {
			r.t.Fatal("checkpoint replay invented outcome/cause")
		}
	}
	fresh := r.ref(target.ID, 10)
	oldGeneration, _ := wire.Find(old.Value.Fields, 2)
	newGeneration, _ := wire.Find(fresh.Value.Fields, 2)
	if newGeneration.U <= oldGeneration.U {
		r.t.Fatal("Resume reset observed generations")
	}
	commandsBefore := r.port.commands()
	r.p.Send(r.intent(1, 5, old))
	out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageRefused, wire.ReasonStaleTarget, wire.DeliveryNone, 0))
	if len(r.port.commands()) != len(commandsBefore) {
		r.t.Fatal("retired reference reached raw port")
	}
	r.p.Send(r.intent(2, 1, wire.At(11, true, r.char.X+10, r.char.Y, r.char.Z)))
	out.Outcomes = append(out.Outcomes, r.outcome(2, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0))
	commands := r.port.commands()
	if len(commands) != len(commandsBefore)+1 || commands[len(commands)-1].Key <= commandsBefore[len(commandsBefore)-1].Key {
		r.t.Fatal("Resume reset raw key allocation")
	}
	after := r.current()
	if after.XP != beforeState.XP || after.SP != beforeState.SP || after.count(57) != beforeState.count(57) {
		r.t.Fatal("Resume mutated balances")
	}
	out.Checks["same-world-no-reexecution"], out.Checks["fresh-observation-generation"] = true, true
	return out
}

func parityRewardSides(r *parityRig, name string) parityResult {
	out := parityResult{Balances: make(map[string]int64), Checks: make(map[string]bool)}
	if name == "hunt-neutral" {
		npc := r.actor(910002)
		r.harm(r.owner, npc, 40)
		r.harm(world.Ref{}, npc, 1000000)
		state := r.current()
		if state.XP != 108 || state.SP != 108 || state.count(57) != 1005 {
			r.t.Fatalf("neutral dominant share was redistributed: %+v", state)
		}
		out.Balances["xp"], out.Balances["sp"], out.Balances["coins"] = state.XP, int64(state.SP), state.count(57)
		out.Checks["neutral-share-lost"] = true
		return out
	}
	var members []int32
	for a := 1; a <= 3; a++ {
		members = append(members, r.openAux(a))
	}
	r.party(members)
	npc := r.actor(910001)
	r.harm(r.actorRef(members[0]), npc, 60)
	r.harm(r.owner, npc, 1000000)
	for i, id := range members {
		state := r.stateOf(id)
		coin := int64(1003)
		if i == 2 {
			coin = 1002
		}
		if state.XP != 88 || state.SP != 104 || state.count(57) != coin {
			r.t.Fatalf("party first death member %d: %+v", i, state)
		}
	}
	primary := r.current()
	if primary.XP != 108 || primary.SP != 108 || primary.count(57) != 1005 {
		r.t.Fatalf("solo opposing party: %+v", primary)
	}
	next := r.actor(910003)
	r.harm(r.actorRef(members[0]), next, 1000000)
	for i, id := range members {
		state := r.stateOf(id)
		if state.XP != 121 || state.SP != 110 || state.count(57) != 1007 {
			r.t.Fatalf("party repeated death member %d: %+v", i, state)
		}
		out.Balances[fmtKey("party-xp", i)], out.Balances[fmtKey("party-sp", i)], out.Balances[fmtKey("party-coins", i)] = state.XP, int64(state.SP), state.count(57)
	}
	// Observe source drops in arrival order. No sorting by object ID or kind.
	var templates []int32
	var foreignDrop int32
	r.wait(func() bool {
		templates = nil
		seen := make(map[int32]bool)
		for _, f := range r.p.Records {
			if f.Type != wire.Fact || gatewaytest.U(f, 1) != 8 {
				continue
			}
			d, err := meaning.EventValue(f)
			if err != nil {
				r.t.Fatal(err)
			}
			if seen[d.Event.ID] || len(d.Event.Goods) != 1 {
				continue
			}
			seen[d.Event.ID] = true
			templates = append(templates, d.Event.Goods[0].ID)
			if foreignDrop == 0 {
				foreignDrop = d.Event.ID
			}
		}
		return len(templates) == 4
	})
	if !reflect.DeepEqual(templates, []int32{1060, 4, 1060, 4}) {
		r.t.Fatalf("table roll/item order: %v", templates)
	}
	r.advance(16 * time.Second)
	before := r.current()
	r.p.Send(r.intent(1, 4, r.ref(foreignDrop, 20)))
	r.result(1, wire.StageSent)
	r.advance(5100 * time.Millisecond)
	// Neither an ordinary refusal nor an unproven approach may transfer it.
	after := r.current()
	if after.count(1060) != before.count(1060) || after.count(4) != before.count(4) {
		r.t.Fatal("foreign pickup stole protected hunt item after the old timeout")
	}
	// The source must retire the dead instance before a new life appears.
	respawned := r.actor(910001)
	if respawned.ID == npc.ID {
		r.t.Fatal("NPC respawn reused a retired instance")
	}
	gone, appeared, err := parityLifeOrder(r.p.Records, npc.ID, respawned.ID)
	if err != nil {
		r.t.Fatal(err)
	}
	if gone < 0 || appeared <= gone {
		r.t.Fatalf("NPC lifecycle order gone/appeared=%d/%d", gone, appeared)
	}
	out.Checks["npc-respawn-fresh-instance"] = true
	out.Balances["solo-xp"], out.Balances["solo-sp"], out.Balances["solo-coins"] = primary.XP, int64(primary.SP), primary.count(57)
	out.Order = []string{"draught", "weapon", "draught", "weapon"}
	out.Checks["permanent-owner-protection"], out.Checks["party-remainders-conserved"] = true, true
	for _, id := range members {
		account := r.aux[id].Self.Char.Account
		r.closeAux(id)
		chars, err := r.store.Characters(r.t.Context(), account)
		if err != nil || len(chars) != 1 {
			r.t.Fatalf("saved party member: %v", err)
		}
		saved := paritySelf(world.Self{Char: chars[0]})
		if saved.XP != 121 || saved.SP != 110 || saved.count(57) != 1007 {
			r.t.Fatalf("party Close lost rewards: %+v", saved)
		}
	}
	out.Checks["party-rewards-saved"] = true
	return out
}

func fmtKey(name string, index int) string { return name + "-" + strconv.Itoa(index) }

func parityLifeOrder(records []wire.Record, oldID, nextID int32) (int, int, error) {
	gone, appeared := -1, -1
	for i, f := range records {
		if f.Type != wire.Fact {
			continue
		}
		d, err := meaning.EventValue(f)
		if err != nil {
			return -1, -1, err
		}
		if gone < 0 && d.Event.Kind == meaning.Gone && d.Event.ID == oldID {
			gone = i
		}
		if appeared < 0 && d.Event.Kind == meaning.Appeared && d.Event.ID == nextID {
			appeared = i
		}
	}
	return gone, appeared, nil
}
