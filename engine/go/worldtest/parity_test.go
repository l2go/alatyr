// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Selected semantic outcomes, not a sorted or rewritten packet transcript.
// Opaque world references are checked against their own original witnesses.
type parityResult struct {
	Outcomes []string
	Order    []string
	Balances map[string]int64
	Checks   map[string]bool
}

func TestOkolitsaParity(t *testing.T) {
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	writeParityCatalog(t, root)
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	for index, name := range parityNames() {
		t.Run(name, func(t *testing.T) {
			var want parityResult
			t.Run("go", func(t *testing.T) {
				synctest.Test(t, func(t *testing.T) { want = runParityCase(newParityRig(t, "go", index, cat), name) })
			})
			if os.Getenv("JAVA_BRIDGE_ENDPOINT") == "" {
				return
			}
			t.Run("java", func(t *testing.T) {
				got := runParityCase(newParityRig(t, "java", index, cat), name)
				if !reflect.DeepEqual(got, want) {
					t.Fatalf("semantic Go/Java difference\nGo:   %+v\nJava: %+v", want, got)
				}
			})
		})
	}
}

func (r *parityRig) outcome(n uint64, stage wire.Stage, reason wire.Reason, delivery wire.Delivery, causes int) string {
	r.t.Helper()
	rec := r.p.Result(n, stage)
	r.sync()
	if gatewaytest.U(rec, 3) != uint64(reason) || gatewaytest.U(rec, 7) != uint64(delivery) {
		r.t.Fatalf("attempt %d outcome: %+v, want %v/%v/%v", n, rec, stage, reason, delivery)
	}
	count := 0
	for _, f := range r.p.Records {
		if f.Type == wire.Fact && gatewaytest.U(f, 32) == n {
			count++
		}
	}
	if count != causes {
		r.t.Fatalf("attempt %d causal facts=%d, want %d", n, count, causes)
	}
	return fmt.Sprintf("%d/%d/%d", stage, reason, delivery)
}

func (r *parityRig) causalOrder(n uint64, label string) []string {
	var order []string
	for _, f := range r.p.Records {
		if f.Type == wire.Fact && gatewaytest.U(f, 32) == n {
			order = append(order, label)
		}
		if f.Type == wire.Result && gatewaytest.U(f, 1) == n && gatewaytest.U(f, 2) == uint64(wire.StageDone) {
			order = append(order, "done")
		}
	}
	if !slices.Equal(order, []string{label, "done"}) {
		r.t.Fatalf("causal publication order: %v", order)
	}
	return order
}

func runParityCase(r *parityRig, name string) parityResult {
	r.t.Helper()
	out := parityResult{Balances: make(map[string]int64), Checks: make(map[string]bool)}
	switch name {
	case "g01":
		target := r.actor(30001)
		r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1))
		out.Order = r.causalOrder(1, "speech")
		commands := r.port.commands()
		if len(commands) != 2 || commands[0].Verb != 2 || commands[1].Verb != 5 {
			r.t.Fatalf("helper command order: %+v", commands)
		}
		codec, err := meaning.NewCodec(r.c)
		if err != nil {
			r.t.Fatal(err)
		}
		for _, f := range r.p.Records {
			b, err := codec.Encode([]wire.Record{f})
			if err != nil {
				r.t.Fatal(err)
			}
			if bytes.Contains(b, []byte("npc_")) {
				r.t.Fatal("saved world authority leaked to participant")
			}
		}
		out.Checks["saved-authority-private"] = true
	case "g02-target", "g02-receipt":
		target := r.actor(30001)
		r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
		r.sync()
		r.advance(1600 * time.Millisecond)
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageUnknown, wire.ReasonTargetUnconfirmed, wire.DeliveryPartial, 0))
		commands := r.port.commands()
		if len(commands) != 1 || commands[0].Verb != 2 {
			r.t.Fatalf("unconfirmed helper dispatched an effect: %+v", commands)
		}
		out.Checks["no-unconfirmed-effect"] = true
	case "g03":
		return parityHuntPickup(r)
	case "g04-buy", "g04-sell":
		sell := name == "g04-sell"
		target := r.actor(30002)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1))
		choice, kind, verb, price := 0, uint64(12), uint64(7), int64(12)
		if sell {
			choice, kind, verb, price = 1, 13, 8, 6
		}
		r.choose(2, choice)
		r.p.Result(2, wire.StageSent)
		list, err := meaning.EventValue(r.p.Fact(kind))
		if err != nil {
			r.t.Fatal(err)
		}
		row := -1
		for i, g := range list.Event.Goods {
			if g.ID == 1060 {
				row = i
				if g.Price != price {
					r.t.Fatalf("price %d, want %d", g.Price, price)
				}
			}
		}
		if row < 0 {
			r.t.Fatal("authored offer absent")
		}
		request := r.intent(3, verb, wire.S(14, true, int64(list.Event.List)), wire.U(15, true, list.Context.Offers[row]), wire.S(16, true, 1), wire.S(17, true, price))
		r.p.Send(request)
		r.p.Result(3, wire.StageSent)
		wantCoin, wantItem := int64(988), int64(4)
		if sell {
			wantCoin, wantItem = 1006, 2
		}
		r.wait(func() bool { s := r.current(); return s.count(57) == wantCoin && s.count(1060) == wantItem })
		r.advance(5100 * time.Millisecond)
		out.Outcomes = append(out.Outcomes, r.outcome(3, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0))
		before := len(r.port.commands())
		r.p.Send(request)
		r.wait(func() bool {
			count := 0
			for _, f := range r.p.Records {
				if f.Type == wire.Result && gatewaytest.U(f, 1) == 3 && gatewaytest.U(f, 2) == uint64(wire.StageUnknown) {
					count++
				}
			}
			return count == 2
		})
		if len(r.port.commands()) != before {
			r.t.Fatal("duplicate participant trade executed twice")
		}
		// A new Intent uses the still-live list, while the duplicate above
		// retains its terminal outcome without another raw write.
		r.p.Send(r.intent(4, verb, wire.S(14, true, int64(list.Event.List)), wire.U(15, true, list.Context.Offers[row]), wire.S(16, true, 1), wire.S(17, true, price)))
		r.p.Result(4, wire.StageSent)
		if sell {
			wantCoin += 6
			wantItem--
		} else {
			wantCoin -= 12
			wantItem++
		}
		r.wait(func() bool { s := r.current(); return s.count(57) == wantCoin && s.count(1060) == wantItem })
		r.advance(5100 * time.Millisecond)
		out.Outcomes = append(out.Outcomes, r.outcome(4, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0))
		beforeRefusal := len(r.port.commands())
		r.p.Send(r.intent(5, verb, wire.S(14, true, int64(list.Event.List)), wire.U(15, true, list.Context.Offers[row]), wire.S(16, true, 1), wire.S(17, true, price+1)))
		out.Outcomes = append(out.Outcomes, r.outcome(5, wire.StageRefused, wire.ReasonStaleOffer, wire.DeliveryNone, 0))
		if len(r.port.commands()) != beforeRefusal {
			r.t.Fatal("wrong price reached world")
		}
		saved := r.save()
		if saved.count(57) != wantCoin || saved.count(1060) != wantItem {
			r.t.Fatalf("saved trade balances: %+v", saved)
		}
		out.Balances["coins"], out.Balances["draughts"] = saved.count(57), saved.count(1060)
		out.Checks["duplicate-replays"], out.Checks["saved-offline"] = true, true
	case "g05", "learning-next":
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.choose(2, 0)
		r.p.Result(2, wire.StageSent)
		lesson, err := meaning.EventValue(r.p.Fact(22))
		if err != nil {
			r.t.Fatal(err)
		}
		id, level, cost := int32(257), int32(1), int32(12)
		if name == "learning-next" {
			id, level, cost = 3, 2, 32
		}
		row := -1
		for i, g := range lesson.Event.Goods {
			if g.ID == id && g.Count == int64(level) && g.Price == int64(cost) {
				row = i
			}
		}
		if row < 0 {
			r.t.Fatalf("exact next lesson %d/%d absent: %+v", id, level, lesson.Event.Goods)
		}
		request := r.intent(3, 13, wire.S(14, true, int64(lesson.Event.List)), wire.U(15, true, lesson.Context.Offers[row]))
		r.p.Send(request)
		r.p.Result(3, wire.StageSent)
		r.advance(4 * time.Second)
		for _, f := range r.p.Records {
			if f.Type == wire.Result && gatewaytest.U(f, 1) == 3 && gatewaytest.U(f, 2) != uint64(wire.StageSent) {
				r.t.Fatal("learning ended before its full evidence window")
			}
		}
		r.advance(1100 * time.Millisecond)
		out.Outcomes = append(out.Outcomes, r.outcome(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1))
		out.Order = r.causalOrder(3, "known-skill")
		state := r.current()
		if state.SP != 100-cost || state.skill(id) != level {
			r.t.Fatalf("lesson balances: %+v", state)
		}
		before := len(r.port.commands())
		r.p.Send(request)
		r.wait(func() bool {
			count := 0
			for _, f := range r.p.Records {
				if f.Type == wire.Result && gatewaytest.U(f, 1) == 3 && gatewaytest.U(f, 2) == uint64(wire.StageDone) {
					count++
				}
			}
			return count == 2
		})
		if len(r.port.commands()) != before {
			r.t.Fatal("duplicate lesson charged twice")
		}
		saved := r.save()
		if saved.SP != state.SP || saved.skill(id) != level {
			r.t.Fatal("Close did not retain learned level and SP together")
		}
		out.Balances["sp"], out.Balances["skill-level"] = int64(saved.SP), int64(saved.skill(id))
		out.Checks["exact-next-level"], out.Checks["saved-offline"] = true, true
	case "g06", "death-save":
		if name == "death-save" {
			if r.engine == "java" {
				if r.fixture("die") != "ok" {
					r.t.Fatal("death rejected")
				}
			} else {
				r.harm(world.Ref{}, r.owner, 1000000)
			}
			r.wait(func() bool { return r.current().Dead })
		}
		r.p.Send(r.intent(1, 9))
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1))
		out.Order = r.causalOrder(1, "revived")
		state := r.current()
		if state.Dead || state.HP <= 0 || r.p.Epoch != 2 || !r.p.Ready || state.Point != [3]int32{-72117, 258063, -3099} {
			r.t.Fatalf("authored fenced return: %+v epoch=%d", state, r.p.Epoch)
		}
		saved := r.save()
		if saved.Dead || saved.HP <= 0 || saved.Point != state.Point {
			r.t.Fatal("return did not save alive at its authored point")
		}
		out.Checks["fenced-alive-at-authored-point"], out.Checks["saved-offline"] = true, true
	case "g07":
		request := r.intent(1, 1, wire.At(11, true, r.char.X+10, r.char.Y, r.char.Z))
		r.p.Send(request)
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0))
		r.advance(time.Second)
		before := len(r.port.commands())
		r.p.Send(request)
		r.wait(func() bool {
			count := 0
			for _, f := range r.p.Records {
				if f.Type == wire.Result && gatewaytest.U(f, 1) == 1 {
					if gatewaytest.U(f, 2) != uint64(wire.StageSent) {
						r.t.Fatal("late event rewrote terminal Sent")
					}
					count++
				}
			}
			return count == 2
		})
		if len(r.port.commands()) != before {
			r.t.Fatal("duplicate movement reached world")
		}
		out.Checks["immutable-terminal"], out.Checks["duplicate-replays"] = true, true
	case "g08-before", "g08-after":
		before := name == "g08-before"
		r.hold.Store(before)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.sync()
		if !before {
			r.p.Result(1, wire.StageSent)
		}
		r.closed = true
		if err := r.port.Close(); err != nil {
			r.t.Fatal(err)
		}
		stage, delivery, writes := wire.StageUnknown, wire.DeliveryBackend, 1
		if before {
			stage, delivery, writes = wire.StageRefused, wire.DeliveryNone, 0
		}
		out.Outcomes = append(out.Outcomes, r.outcome(1, stage, wire.ReasonWorldClosed, delivery, 0))
		if len(r.port.commands()) != writes {
			r.t.Fatal("close crossed the contact boundary")
		}
		out.Checks["contact-boundary"] = true
	case "g09":
		return parityResume(r)
	case "g10":
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.p.Result(1, wire.StageSent)
		r.p.Fact(11)
		r.advance(5100 * time.Millisecond)
		out.Outcomes = append(out.Outcomes, r.outcome(1, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0))
		out.Checks["unattributed-speech-cannot-complete"] = true
	case "equip":
		initial := r.current()
		r.use(4)
		r.wait(func() bool { return r.current().item(4).Slot == -1 })
		unarmed := r.current()
		if unarmed.PAtk >= initial.PAtk {
			r.t.Fatal("unequipping weapon did not recalculate attack")
		}
		r.advance(600 * time.Millisecond)
		r.use(4)
		r.wait(func() bool { return r.current().item(4).Slot == catalog.SlotRHand })
		if r.current().PAtk != initial.PAtk {
			r.t.Fatal("re-equipping weapon did not restore attack")
		}
		for _, template := range []int32{23, 28, 39} {
			r.advance(600 * time.Millisecond)
			r.use(template)
			r.wait(func() bool { return r.current().item(template).Slot >= 0 })
		}
		worn := r.current()
		if worn.PDef <= initial.PDef {
			r.t.Fatal("armor did not recalculate effective defense")
		}
		saved := r.save()
		for _, template := range []int32{4, 23, 28, 39} {
			if saved.item(template).Slot != worn.item(template).Slot {
				r.t.Fatal("equipment slots not saved")
			}
		}
		out.Balances["unarmed-patk"], out.Balances["armed-patk"], out.Balances["worn-pdef"] = int64(unarmed.PAtk), int64(initial.PAtk), int64(worn.PDef)
		out.Checks["equipment-saved"] = true
	case "heal":
		before := r.current()
		r.use(1060)
		r.wait(func() bool { return r.current().count(1060) == before.count(1060)-1 })
		after := r.current()
		if after.HP != before.HP+72 {
			r.t.Fatalf("HEAL_STATIC before/after HP %g/%g", before.HP, after.HP)
		}
		r.wait(func() bool { return r.current().HP > after.HP })
		regenerated := r.current()
		if regenerated.HP > regenerated.MaxHP {
			r.t.Fatal("regeneration exceeded maximum")
		}
		out.Checks["normal-regeneration"] = true
		saved := r.save()
		if saved.count(1060) != 2 || saved.HP != math.Round(regenerated.HP) {
			r.t.Fatal("healing count/HP not saved")
		}
		out.Balances["heal"], out.Balances["draughts"] = 72, saved.count(1060)
		out.Checks["heal-and-cost-saved"] = true
	case "hunt-parties", "hunt-neutral":
		return parityRewardSides(r, name)
	case "unknown-item":
		if r.current().count(9999001) != 0 {
			r.t.Fatal("missing template entered active inventory")
		}
		saved := r.save()
		if saved.count(9999001) != 7 || saved.count(57) != 1000 {
			r.t.Fatal("Close lost unavailable saved items")
		}
		out.Balances["unavailable-preserved"], out.Balances["coins"] = saved.count(9999001), saved.count(57)
	}
	return out
}
