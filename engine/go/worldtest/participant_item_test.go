// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestParticipantUseItem(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.CurHP = 20
		char.Items[1].Slot = -1
		r := newRig(t, cat, char, allSources(), nil)
		stats := func() meaning.Stats {
			t.Helper()
			r.sync()
			var out meaning.Stats
			for _, rec := range r.p.Records {
				if rec.Type != wire.Fact {
					continue
				}
				d, err := meaning.EventValue(rec)
				if err != nil {
					t.Fatal(err)
				}
				if d.Event.ID != 1 || d.Event.Stats == nil {
					continue
				}
				v := d.Event.Stats
				if v.HP != nil {
					out.HP = v.HP
				}
				if v.PAtk != nil {
					out.PAtk = v.PAtk
				}
			}
			return out
		}
		before := stats()
		for i := uint64(1); i <= 3; i++ {
			r.p.Send(r.intent(i, 14, wire.S(21, true, 11)))
			r.expect(i, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
			found := false
			for _, rec := range r.p.Records {
				if rec.Type != wire.Fact {
					continue
				}
				cause, _ := wire.Find(rec.Fields, 32)
				if cause.U != i {
					continue
				}
				d, err := meaning.EventValue(rec)
				if err != nil {
					t.Fatal(err)
				}
				for _, row := range d.Event.Goods {
					if row.Object == 11 {
						want := int64(catalog.SlotRHand)
						if i == 2 {
							want = -1
						}
						if row.Slot == nil || *row.Slot != want {
							t.Fatalf("participant slot=%v want %d", row.Slot, want)
						}
						found = true
					}
				}
			}
			if !found {
				t.Fatal("causal equipment row missing")
			}
			r.advance(100 * time.Millisecond)
			after := stats()
			if before.PAtk == nil || after.PAtk == nil {
				t.Fatal("attack absent")
			}
			if i == 2 {
				if *after.PAtk != *before.PAtk {
					t.Fatal("unequip did not restore attack")
				}
			} else if *after.PAtk <= *before.PAtk {
				t.Fatal("equipment attack not visible")
			}
		}
		r.p.Send(r.intent(4, 14, wire.S(21, true, 12)))
		r.p.Result(4, wire.StageSent)
		r.advance(500 * time.Millisecond)
		r.expect(4, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		if hp := stats().HP; hp == nil || *hp != 92 {
			t.Fatalf("HP=%v", hp)
		}
		if quantity(inventory(t, r), 1060) != 2 {
			t.Fatal("consumption count")
		}
		// Replaying an identical participant attempt cannot consume twice.
		r.p.Send(r.intent(4, 14, wire.S(21, true, 12)))
		r.sync()
		r.p.Send(r.intent(5, 14, wire.S(21, true, 12)))
		r.expect(5, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone, 0)
		r.advance(time.Second)
		if quantity(inventory(t, r), 1060) != 2 {
			t.Fatal("reuse consumed twice")
		}
		r.p.Send(r.intent(6, 14, wire.S(21, true, 9999)))
		r.p.Result(6, wire.StageRefused)
		r.p.Send(r.intent(7, 14, wire.S(21, true, 10)))
		r.p.Result(7, wire.StageRefused)
		if err := r.session.Close(); err != nil {
			t.Fatal(err)
		}
		rows, err := r.store.Characters(t.Context(), char.Account)
		if err != nil {
			t.Fatal(err)
		}
		if len(rows) != 1 {
			t.Fatal(rows)
		}
		saved := rows[0]
		if saved.CurHP != 92 || saved.Items[1].Slot != catalog.SlotRHand || saved.Items[2].Count != 2 {
			t.Fatalf("saved=%+v", saved)
		}
	})
}

func TestParticipantItemEvidenceRequired(t *testing.T) {
	for _, loss := range []string{"source", "attribution", "fact"} {
		t.Run(loss, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				source := allSources()
				if loss == "source" {
					source.ItemUse = false
				}
				filter := func(u bridge.Update) bridge.Update {
					for i, rec := range u.Batch.Records {
						if rec.Type != wire.Fact {
							continue
						}
						kind, _ := wire.Find(rec.Fields, 1)
						if kind.U != 15 {
							continue
						}
						if loss == "fact" {
							for j, f := range rec.Fields {
								if f.Number == 24 {
									rec.Fields[j] = wire.Items(24, true, []wire.Value{})
								}
							}
						}
						if loss == "attribution" {
							for j, f := range rec.Fields {
								if f.Number == 75 {
									rec.Fields[j] = wire.U(75, true, 99999)
								}
							}
						}
						u.Batch.Records[i] = rec
					}
					return u
				}
				r := newRig(t, cat, readyCharacter(), source, filter)
				r.p.Send(r.intent(1, 14, wire.S(21, true, 11)))
				r.p.Result(1, wire.StageSent)
				r.advance(5 * time.Second)
				r.expect(1, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
			})
		})
	}
}

func TestParticipantConsumesLastItemAndRejectsStaleRow(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.CurHP = 20
		char.Items[2].Count = 1
		r := newRig(t, cat, char, allSources(), nil)
		r.p.Send(r.intent(1, 14, wire.S(21, true, 12)))
		r.p.Result(1, wire.StageSent)
		r.advance(500 * time.Millisecond)
		r.expect(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.p.Send(r.intent(2, 14, wire.S(21, true, 12)))
		r.p.Result(2, wire.StageRefused)
		if quantity(inventory(t, r), 1060) != 0 {
			t.Fatal("last item retained")
		}
	})
}
