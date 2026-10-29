// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func TestG01AuthoredDialogue(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		cmds := r.port.commands()
		if len(cmds) != 2 || cmds[0].Verb != 2 || cmds[1].Verb != 5 {
			t.Fatalf("helper/effect: got %+v", cmds)
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
func lose(kinds map[uint64]bool, receipts bool) func(bridge.Update) bridge.Update {
	return func(u bridge.Update) bridge.Update {
		if u.Kind != bridge.LiveBatch {
			return u
		}
		records := []wire.Record{}
		times := []time.Time{}
		for i, rec := range u.Batch.Records {
			if rec.Type == wire.Fact && kinds[gatewaytest.U(rec, 1)] || receipts && rec.Type == wire.Result {
				continue
			}
			records = append(records, rec)
			times = append(times, u.Batch.ReceivedAt[i])
		}
		u.Batch.Records, u.Batch.ReceivedAt = records, times
		if receipts {
			u.Batch.Receipts = nil
		}
		return u
	}
}
func TestG02HelperNeedsTargetAndReceipt(t *testing.T) {
	for _, branch := range []string{"target-only", "receipt-only"} {
		t.Run(branch, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				kinds := map[uint64]bool{}
				if branch == "receipt-only" {
					kinds[24] = true
				}
				r := newRig(t, cat, readyCharacter(), allSources(), lose(kinds, branch == "target-only"))
				target := r.actor(30001)
				r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
				r.sync()
				r.advance(1500 * time.Millisecond)
				r.expect(1, wire.StageUnknown, wire.ReasonTargetUnconfirmed, wire.DeliveryPartial, 0)
				if cmds := r.port.commands(); len(cmds) != 1 || cmds[0].Verb != 2 {
					t.Fatalf("effect escaped unconfirmed helper: %+v", cmds)
				}
			})
		})
	}
}

func TestG04MerchantBuySellStayUnknown(t *testing.T) {
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
				raw := r.port.Port.(*worldbridge.Client)
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
				r.expect(3, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
				r.expect(2, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
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
				r.expect(4, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)

			})
		})
	}
}

func TestG05AuthoredLearningFullWindow(t *testing.T) {
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
		r.advance(4990 * time.Millisecond)
		for _, rec := range r.p.Records {
			if rec.Type == wire.Result && gatewaytest.U(rec, 1) == 3 && gatewaytest.U(rec, 2) != uint64(wire.StageSent) {
				t.Fatalf("learning ended before evidence window: %+v", rec)
			}
		}
		r.advance(10 * time.Millisecond)
		r.expect(3, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.expect(2, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
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
		scene, err := r.w.Capture(t.Context(), r.port.Port.(*worldbridge.Client).Owner())
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

func TestG09ExplicitResumeKeepsWorldAndKeys(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		r := newRig(t, cat, readyCharacter(), allSources(), nil)
		target := r.actor(30001)
		oldRef := r.ref(target.ID, 10)
		oldSession := r.p.Session
		r.p.Send(r.intent(1, 5, oldRef))
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		owner := r.port.Port.(*worldbridge.Client).Owner()
		if _, _, err := r.w.Subscribe(t.Context(), owner); err != nil {
			t.Fatal(err)
		} // Replace the observation stream, not the incarnation.
		r.sync()
		scope, state, err := r.session.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		r.mu.Lock()
		r.scope, r.state = scope, state
		r.mu.Unlock()
		r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
		r.sync()
		if r.p.Session == oldSession {
			t.Fatal("resume reused participant scope")
		}
		for _, rec := range r.p.Records {
			if rec.Type == wire.Result || rec.Type == wire.Fact && gatewaytest.U(rec, 32) != 0 {
				t.Fatal("recovery invented old outcome or cause")
			}
		}
		before := len(r.port.commands())
		fresh := r.ref(target.ID, 10)
		oldGen, _ := wire.Find(oldRef.Value.Fields, 2)
		newGen, _ := wire.Find(fresh.Value.Fields, 2)
		if newGen.U <= oldGen.U {
			t.Fatal("resume reset observation generations")
		}
		r.p.Send(r.intent(1, 5, oldRef))
		r.expect(1, wire.StageRefused, wire.ReasonStaleTarget, wire.DeliveryNone, 0)
		if len(r.port.commands()) != before {
			t.Fatal("retired reference reached the world")
		}
		r.p.Send(r.intent(2, 1, wire.At(11, true, -71047, 257883, -3104)))
		r.p.Result(2, wire.StageSent)
		cmds := r.port.commands()
		if len(cmds) != before+1 || cmds[len(cmds)-1].Key <= cmds[before-1].Key {
			t.Fatal("resume replayed a command or reset key")
		}
		if r.port.Port.(*worldbridge.Client).Owner() != owner {
			t.Fatal("resume replaced world incarnation")
		}
	})
}

func TestG10UnattributedSpeechCannotComplete(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		source := allSources()
		source.Dialogue = false
		r := newRig(t, cat, readyCharacter(), source, nil)
		target := r.actor(30001)
		r.p.Send(r.intent(1, 5, r.ref(target.ID, 10)))
		r.p.Result(1, wire.StageSent)
		r.p.Fact(11)
		r.advance(5 * time.Second)
		r.expect(1, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
	})
}

func TestG03AuthoredDropPickupAndDuplicate(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.X, char.Y = -70357, 256863
		r := newRig(t, cat, char, allSources(), nil)
		target := r.actor(18003)
		owner := r.port.Port.(*worldbridge.Client).Owner()
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
