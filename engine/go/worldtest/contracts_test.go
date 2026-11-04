// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"context"
	"sync"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

func TestG01AuthoredDialogue(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		cmds := r.port.commands()
		if len(cmds) != 1 || cmds[0].Verb != 5 {
			t.Fatalf("engage is not one talk: got %+v", cmds)
		}
		speech, result := -1, -1
		codec, err := meaning.NewCodec(r.c)
		if err != nil {
			t.Fatal(err)
		}
		for i, rec := range r.p.Records {
			if rec.Type == wire.Fact && gatewaytest.U(rec, 32) == 1 {
				speech = i
			}
			if rec.Type == wire.Result && gatewaytest.U(rec, 2) == uint64(wire.StageDone) {
				result = i
			}
			b, err := codec.Encode([]wire.Record{rec})
			if err != nil {
				t.Fatal(err)
			}
			if bytes.Contains(b, []byte("npc_")) {
				t.Fatal("saved world action escaped participant boundary")
			}
		}
		if speech < 0 || speech >= result {
			t.Fatalf("speech/result order: %d/%d", speech, result)
		}
	})
}

// lose removes selected live observations or receipt evidence from the real
// port. It does not forge a world result or run a scripted endpoint.
func lose(kinds map[uint64]bool, outcomes bool) func(gateway.Update) gateway.Update {
	return func(u gateway.Update) gateway.Update {
		p, ok := u.Value.(worldlink.Publication)
		if !ok {
			return u
		}
		if outcomes {
			p.Commit = world.Commit{}
		}
		if kinds[eventKind(p.Event)] {
			p.Event = nil
		}
		u.Value = p
		return u
	}
}
func eventKind(e world.Event) uint64 {
	switch e.(type) {
	case world.DialoguePage:
		return 11
	case world.TradeList:
		return 12
	case world.LearnList:
		return 22
	case world.InventoryChanged:
		return 15
	case world.ItemPicked:
		return 9
	case world.SkillsShown:
		return 23
	case world.Revived:
		return 7
	}
	return 0
}

// harmAround kills the participant once, just before or just after its first
// engage write reaches the world. It forges no result.
type harmAround struct {
	gateway.World
	w     *world.World
	owner world.Ref
	after bool
	once  sync.Once
}

func (h *harmAround) Notify() <-chan struct{}         { return h.World.(gateway.Receiver).Notify() }
func (h *harmAround) Capacity() int                   { return h.World.(gateway.Receiver).Capacity() }
func (h *harmAround) Receive() (gateway.Update, bool) { return h.World.(gateway.Receiver).Receive() }
func (h *harmAround) Write(ctx context.Context, step gateway.Step) error {
	harm := func() {
		h.once.Do(func() {
			if err := h.w.Harm(context.Background(), world.Ref{}, h.owner, 1e6); err != nil {
				panic(err)
			}
		})
	}
	if !h.after {
		harm()
	}
	err := h.World.Write(ctx, step)
	if h.after {
		harm()
	}
	return err
}

// Engage on a creature is one strike: the world selects the target within it.
// The soak once saw a participant die while Engage waited for a separate
// selection, which left the attempt Unknown although the strike was never sent.
// A death just before or just after the single write leaves nothing in doubt.
func TestG02EngageIsOneWorldCommand(t *testing.T) {
	for _, after := range []bool{false, true} {
		t.Run(map[bool]string{false: "death-before", true: "death-after"}[after], func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.X, char.Y = -70357, 256863
				var h *harmAround
				policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
					return meaning.Capabilities{Strike: o.Role == "creature"}
				}}
				r := newRigWithPort(t, cat, char, allSources(), nil, policy, func(p gateway.World) gateway.World {
					h = &harmAround{World: p, after: after}
					return h
				})
				h.w = r.w
				h.owner = r.port.Port.Owner()
				target := r.actor(18003)
				r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
				// Strike is complete once handed off (G07): a strike refused for the
				// dead attacker, or admitted just before the death, stays Sent.
				r.expect(1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
				r.advance(2 * time.Second)
				for _, rec := range r.p.Records {
					if rec.Type == wire.Result && gatewaytest.U(rec, 2) == uint64(wire.StageUnknown) {
						t.Fatalf("engage became Unknown: %+v", rec)
					}
				}
				if cmds := r.port.commands(); len(cmds) != 1 || cmds[0].Verb != 3 {
					t.Fatalf("engage is not one strike: %+v", cmds)
				}
			})
		})
	}
}

// TestG04MerchantBuySellAreWitnessed runs an authored trade through a host
// that audits merchant exchanges: the inventory change names the trade intent,
// so the participant sees Done. Without that source G04 stays Unknown.
func TestG04MerchantBuySellAreWitnessed(t *testing.T) {
	for _, sell := range []bool{false, true} {
		t.Run(map[bool]string{false: "buy", true: "sell"}[sell], func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				char := readyCharacter()
				char.Y = 258243
				r := newRig(t, cat, char, allSources(), nil)
				target := r.actor(30002)
				r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
				r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				choice := 0
				kind, verb := uint64(12), uint64(7)
				if sell {
					choice = 1
					kind = 13
					verb = 8
				}
				r.choose(2, choice)
				r.p.Result(2, wire.StageSent)
				list := r.p.Fact(kind)
				d, err := meaning.EventValue(list)
				if err != nil {
					t.Fatal(err)
				}
				index := -1
				for i, g := range d.Event.Goods {
					if g.ID == 1060 {
						index = i
					}
				}
				if index < 0 {
					t.Fatalf("draught absent in authored proposal: %+v", d)
				}
				row := d.Event.Goods[index]
				wantPrice := int64(12)
				if sell {
					wantPrice = 6
				}
				if row.Price != wantPrice {
					t.Fatalf("price: got %d, want %d", row.Price, wantPrice)
				}
				r.p.Send(r.intent(3, verb, wire.S(14, true, int64(d.Event.List)), wire.U(15, true, d.Context.Offers[index]), wire.S(16, true, 1), wire.S(17, true, row.Price)))
				r.p.Result(3, wire.StageSent)
				r.sync()
				raw := r.port.Port
				scene, err := r.w.Capture(t.Context(), raw.Owner())
				if err != nil {
					t.Fatal(err)
				}
				coin, count := int64(0), int64(0)
				for _, it := range scene.Inventory {
					if it.Item.TemplateID == 57 {
						coin += it.Item.Count
					}
					if it.Item.TemplateID == 1060 {
						count += it.Item.Count
					}
				}
				wantCoin, wantCount := int64(988), int64(4)
				if sell {
					wantCoin, wantCount = 1006, 2
				}
				if coin != wantCoin || count != wantCount {
					t.Fatalf("atomic trade: got %d coins/%d items, want %d/%d", coin, count, wantCoin, wantCount)
				}
				r.advance(5 * time.Second)
				r.expect(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				r.expect(2, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
				// A distinct command can reuse a live list; Intent replay remains
				// covered separately by the participant contract.
				r.p.Send(r.intent(4, verb, wire.S(14, true, int64(d.Event.List)), wire.U(15, true, d.Context.Offers[index]), wire.S(16, true, 1), wire.S(17, true, row.Price)))
				r.p.Result(4, wire.StageSent)
				r.sync()
				after, err := r.w.Capture(t.Context(), raw.Owner())
				if err != nil {
					t.Fatal(err)
				}
				if sell {
					wantCoin += 6
					wantCount--
				} else {
					wantCoin -= 12
					wantCount++
				}
				if quantity(after.Inventory, 57) != wantCoin || quantity(after.Inventory, 1060) != wantCount {
					t.Fatal("distinct trade did not update both balances")
				}
				r.advance(5 * time.Second)
				r.expect(4, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)

			})
		})
	}
}

// TestDeferredTalkIsWitnessed starts beyond interaction range of the merchant.
// The world walks there before it speaks; the page names the talk intent, so
// the participant sees Done instead of a timeout.
func TestDeferredTalkIsWitnessed(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243 - 400
		r := newRig(t, cat, char, allSources(), nil)
		target := r.actor(30002)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.p.Result(1, wire.StageSent)
		r.advance(4 * time.Second)
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	})
}

func TestG05AuthoredLearningFromCommit(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.choose(2, 0)
		r.p.Result(2, wire.StageSent)
		practice := r.p.Fact(22)
		d, err := meaning.EventValue(practice)
		if err != nil {
			t.Fatal(err)
		}
		index := -1
		for i, g := range d.Event.Goods {
			if g.ID == 257 {
				index = i
			}
		}
		if index < 0 {
			t.Fatal("authored passive lesson absent")
		}
		r.p.Send(r.intent(3, 13, wire.S(14, true, int64(d.Event.List)), wire.U(15, true, d.Context.Offers[index])))
		r.p.Result(3, wire.StageSent)

		r.expect(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.expect(2, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	})
}

func TestG06AuthoredReturnAndDrain(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.CurHP = 0
		r := newRig(t, cat, char, allSources(), nil)
		r.p.Send(r.intent(1, 9))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		if r.p.Epoch != 2 || !r.p.Ready {
			t.Fatalf("returned scope: epoch=%d ready=%t", r.p.Epoch, r.p.Ready)
		}
		if cmds := r.port.commands(); len(cmds) != 1 || cmds[0].Verb != 9 {
			t.Fatalf("return sent a service acknowledgement: %+v", cmds)
		}
		scene, err := r.w.Capture(t.Context(), r.port.Port.Owner())
		if err != nil {
			t.Fatal(err)
		}
		if scene.Self.Dead || scene.Self.Teleporting || scene.End.Fence == 0 {
			t.Fatalf("return not complete: %+v", scene.Self)
		}
	})
}

func TestG07TerminalSentSurvivesLaterWorldEvents(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		req := r.intent(1, 1, wire.At(11, true, -71047, 257883, -3104))
		r.p.Send(req)
		r.expect(1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
		r.advance(time.Second)
		before := len(r.port.commands())
		r.p.Send(req)
		r.sync()
		if len(r.port.commands()) != before {
			t.Fatal("duplicate movement executed again")
		}
		for _, rec := range r.p.Records {
			if rec.Type == wire.Result && gatewaytest.U(rec, 1) == 1 && gatewaytest.U(rec, 2) != uint64(wire.StageSent) {
				t.Fatal("later world event rewrote terminal sent")
			}
		}
	})
}

func TestG08WorldCloseByContact(t *testing.T) {
	for _, before := range []bool{true, false} {
		t.Run(map[bool]string{true: "before", false: "after"}[before], func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				r := newRig(t, cat, readyCharacter(), allSources(), lose(map[uint64]bool{11: true}, true))
				r.hold.Store(before)
				target := r.actor(30001)
				r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
				r.sync()
				if !before {
					r.p.Result(1, wire.StageSent)
				}
				if err := r.port.Close(); err != nil {
					t.Fatal(err)
				}
				stage, delivery := wire.StageUnknown, wire.DeliveryBackend
				if before {
					stage, delivery = wire.StageRefused, wire.DeliveryNone
				}
				r.expect(1, stage, wire.ReasonWorldClosed, delivery, 0)
				want := 1
				if before {
					want = 0
				}
				if len(r.port.commands()) != want {
					t.Fatalf("contact count: got %d, want %d", len(r.port.commands()), want)
				}
			})
		})
	}
}

func TestG09ReconnectKeepsIncarnationAndAttempts(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		owner := r.port.Port.Owner()
		session := r.p.Session
		intent := r.intent(1, 5, r.ref(target.ID, 10))
		r.p.Send(intent)
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.reconnect()
		if r.p.Session != session || r.port.Port.Owner() != owner {
			t.Fatal("reconnect replaced the session or incarnation")
		}
		before := len(r.port.commands())
		r.p.Send(intent)
		r.p.Result(1, wire.StageDone)
		r.sync()
		if len(r.port.commands()) != before {
			t.Fatal("reconnect reexecuted the attempt")
		}
		r.p.Send(r.intent(2, 1, wire.At(11, true, -71047, 257883, -3104)))
		r.p.Result(2, wire.StageSent)
	})
}

func TestG10WorldCommitDoesNotRequireProducerExclusivity(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), meaning.Source{}, nil)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
	})
}

func TestG03AuthoredDropPickupAndDuplicate(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.X, char.Y = -70357, 256863
		r := newRig(t, cat, char, allSources(), nil)
		target := r.actor(18003)
		owner := r.port.Port.Owner()
		if _, err := r.w.Execute(t.Context(), world.Request{Owner: owner, Verb: world.VerbAttack, Target: target}); err != nil {
			t.Fatal(err)
		}
		var drop int32
		for step := 0; step < 300 && drop == 0; step++ {
			r.advance(100 * time.Millisecond)
			for _, rec := range r.p.Records {
				if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 8 {
					d, err := meaning.EventValue(rec)
					if err != nil {
						t.Fatal(err)
					}
					if len(d.Event.Goods) > 0 && d.Event.Goods[0].ID == 57 {
						drop = d.Event.ID
					}
				}
			}
		}
		if drop == 0 {
			t.Fatal("authored hunt produced no currency drop")
		}
		var at world.Point
		for _, rec := range r.p.Records {
			if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 8 {
				d, err := meaning.EventValue(rec)
				if err != nil {
					t.Fatal(err)
				}
				if d.Event.ID == drop {
					at = world.Point{X: d.Event.X, Y: d.Event.Y, Z: d.Event.Z}
				}
			}
		}
		if err := r.w.MoveTo(t.Context(), owner.ID, at); err != nil {
			t.Fatal(err)
		}
		for range 20 {
			r.advance(100 * time.Millisecond)
		}
		request := r.intent(1, 4, r.ref(drop, 20))
		r.p.Send(request)
		// Pickup may approach the actual dropped position before the live witness.
		r.p.Result(1, wire.StageSent)
		for range 50 {
			r.advance(100 * time.Millisecond)
		}
		// Setup placed the character beside the real drop, so this pickup fixes
		// its own atomic commitment before emitting the live Took witness.
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		scene, err := r.w.Capture(t.Context(), owner)
		if err != nil {
			t.Fatal(err)
		}
		coins := int64(0)
		for _, it := range scene.Inventory {
			if it.Item.TemplateID == 57 {
				coins += it.Item.Count
			}
		}
		if coins <= 1000 {
			t.Fatalf("real pickup did not transfer currency: got %d", coins)
		}
		before := len(r.port.commands())
		r.p.Send(request)
		r.sync()
		if len(r.port.commands()) != before {
			t.Fatal("duplicate pickup executed twice")
		}
		terminals := 0
		for _, rec := range r.p.Records {
			if rec.Type == wire.Result && gatewaytest.U(rec, 1) == 1 && gatewaytest.U(rec, 2) == uint64(wire.StageDone) {
				terminals++
			}
		}
		if terminals != 2 {
			t.Fatalf("duplicate replayed terminal: got %d, want 2", terminals)
		}
	})
}
