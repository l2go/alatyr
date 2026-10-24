// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func (r *rig) offer(kind string, list int32) uint64 {
	r.t.Helper()
	e := meaning.Event{Kind: kind, ID: 2, List: list, Complete: true, Goods: []meaning.Good{{ID: 30, Object: 40, Count: 10, Price: 2}}}
	if kind == meaning.Practice {
		e.Goods = []meaning.Good{{ID: 3, Object: 2, Count: 2, Price: 10}}
	}
	r.emit(e)
	k, _ := meaning.KindByName(kind)
	for i := len(r.p.Records) - 1; i >= 0; i-- {
		rec := r.p.Records[i]
		if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == k.Code {
			d, err := meaning.EventValue(rec)
			if err != nil {
				r.t.Fatal(err)
			}
			if len(d.Context.Offers) != 1 {
				r.t.Fatal("missing offer")
			}
			return d.Context.Offers[0]
		}
	}
	r.t.Fatal("missing proposal")
	return 0
}

func TestG04TradeRemainsUnknown(t *testing.T) {
	for _, sell := range []bool{false, true} {
		t.Run(map[bool]string{false: "buy", true: "sell"}[sell], func(t *testing.T) {
			both(t, func(r *rig) {
				kind, verb := meaning.Shop, uint64(7)
				if sell {
					kind, verb = meaning.Offer, 8
				}
				offer := r.offer(kind, 2)
				r.p.Send(r.intent(1, verb, wire.S(14, true, 2), wire.U(15, true, offer), wire.S(16, true, 1), wire.S(17, true, 2)))
				r.p.Result(1, wire.StageSent)
				log := r.log()
				if len(log) != 1 || log[0].Command.Verb != verb {
					r.t.Fatal(log)
				}
				delta, coin := int64(1), int64(-2)
				if sell {
					delta, coin = -1, 2
				}
				effects := []wire.Value{{Fields: []wire.Field{wire.U(1, true, 1), wire.S(2, true, 30), wire.S(3, true, 40), wire.S(4, true, delta)}}, {Fields: []wire.Field{wire.U(1, true, 2), wire.S(4, true, coin)}}}
				r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Carried, Coin: meaning.Number(100 + coin), Goods: []meaning.Good{{Object: 40, ID: 30, Count: 10 + delta}}}, effects...)
				r.advance(4990 * time.Millisecond)
				for _, rec := range r.p.Records {
					if rec.Type == wire.Result && gatewaytest.U(rec, 2) != uint64(wire.StageSent) {
						r.t.Fatal("trade ended early")
					}
				}
				r.advance(10 * time.Millisecond)
				r.expect(1, verb, verb, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
			})
		})
	}
}

func TestG05LearningFullWindow(t *testing.T) {
	for _, branch := range []string{"clean", "level-before", "level-after", "incomplete", "unattributed"} {
		t.Run(branch, func(t *testing.T) {
			both(t, func(r *rig) {
				offer := r.offer(meaning.Practice, 2)
				r.p.Send(r.intent(1, 13, wire.S(14, true, 2), wire.U(15, true, offer)))
				r.p.Result(1, wire.StageSent)
				log := r.log()
				if len(log) != 1 || log[0].Command.Verb != 13 {
					r.t.Fatal(log)
				}
				if branch == "level-before" {
					r.emit(meaning.Event{Kind: meaning.Vitals, ID: 1, Stats: &meaning.Stats{Level: meaning.Number(2)}})
				}
				if branch == "unattributed" {
					r.source.Learning = false
				}
				r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Known, Skills: []meaning.Skill{{ID: 3, Level: 2}}}, skillEffect())
				if branch == "level-after" {
					r.emit(meaning.Event{Kind: meaning.Vitals, ID: 1, Stats: &meaning.Stats{Level: meaning.Number(2)}})
				}
				if branch == "incomplete" {
					end := cp(r.epoch, wire.CheckpointEnd, false)
					end.Fields = append(end.Fields, wire.Items(4, true, []wire.Value{{Data: []byte("skills")}}))
					r.send(bridge.Batch{Records: []wire.Record{cp(r.epoch, wire.CheckpointBegin, false), end}, ReceivedAt: []time.Time{time.Now(), time.Now()}, Snapshot: true})
				}
				r.advance(4990 * time.Millisecond)
				for _, rec := range r.p.Records {
					if rec.Type == wire.Fact && gatewaytest.U(rec, 32) != 0 {
						r.t.Fatal("early learning cause")
					}
				}
				r.advance(10 * time.Millisecond)
				stage, reason, causes := wire.StageUnknown, wire.ReasonTimeout, 0
				if branch == "clean" {
					stage, reason, causes = wire.StageDone, wire.ReasonNone, 1
				}
				r.expect(1, 13, 13, stage, reason, wire.DeliveryBackend, causes)
			})
		})
	}
}

func (r *rig) checkpoint() {
	r.send(bridge.Batch{Records: []wire.Record{cp(r.epoch, wire.CheckpointBegin, false)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
	for _, e := range initial() {
		r.send(bridge.Batch{Records: []wire.Record{r.fact(e)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
	}
	r.send(bridge.Batch{Records: []wire.Record{cp(r.epoch, wire.CheckpointEnd, true)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
}

func TestG06ReturnAndDrain(t *testing.T) {
	for _, branch := range []string{"return", "in-place", "unattributed", "snapshot-only", "second-generation", "timeout-drain", "participant-drain", "after-ready", "repeated-generation"} {
		t.Run(branch, func(t *testing.T) {
			both(t, func(r *rig) {
				r.emit(meaning.Event{Kind: meaning.Died, ID: 1})
				r.p.Send(r.intent(1, 9))
				r.p.Result(1, wire.StageSent)
				log := r.log()
				if len(log) != 1 || log[0].Command.Verb != 9 {
					r.t.Fatal(log)
				}
				r.reply(log[0], wire.StageSent, wire.ReasonNone, meaning.Event{})
				if branch == "unattributed" {
					r.source.Return = false
				}
				if branch != "in-place" {
					r.emit(meaning.Event{Kind: meaning.Corrected, ID: 1}, wire.U(66, true, 1), wire.U(67, true, 1))
					if branch != "snapshot-only" && branch != "after-ready" {
						r.emit(meaning.Event{Kind: meaning.Revived, ID: 1, MS: 17})
					}
					if branch == "return" {
						for _, rec := range r.p.Records {
							if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 7 {
								r.t.Fatal("revival escaped before checkpoint drain")
							}
						}
					}
					if branch == "second-generation" {
						r.source.Return = false
						r.emit(meaning.Event{Kind: meaning.Corrected, ID: 1}, wire.U(66, true, 2), wire.U(67, true, 1))
					}
					if branch == "repeated-generation" {
						r.emit(meaning.Event{Kind: meaning.Corrected, ID: 1}, wire.U(66, true, 1), wire.U(67, true, 1))
					}
					if branch == "timeout-drain" {
						r.advance(5 * time.Second)
						r.p.Result(1, wire.StageUnknown)
					}
					if branch == "participant-drain" {
						r.blockDrain.Store(true)
					}
					r.checkpoint()
					if branch == "after-ready" {
						r.emit(meaning.Event{Kind: meaning.Revived, ID: 1, MS: 17})
					}
					if branch == "participant-drain" {
						<-r.drainEntered
						for _, rec := range r.p.Records {
							if rec.Type == wire.Result && gatewaytest.U(rec, 2) == uint64(wire.StageDone) || rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 7 {
								r.t.Fatal("raw ready bypassed participant drain")
							}
						}
						r.blockDrain.Store(false)
						r.drainRelease <- struct{}{}
						r.sync()
					}
				} else {
					r.emit(meaning.Event{Kind: meaning.Revived, ID: 1})
				}
				if branch == "return" || branch == "participant-drain" || branch == "after-ready" || branch == "repeated-generation" {
					r.expect(1, 9, 9, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
					if r.p.Epoch != 2 || !r.p.Ready {
						r.t.Fatal("return not ready in E2")
					}
				} else {
					if branch != "timeout-drain" {
						r.advance(5 * time.Second)
					}
					r.expect(1, 9, 9, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
					if branch == "timeout-drain" {
						count := 0
						for _, rec := range r.p.Records {
							if rec.Type == wire.Fact && gatewaytest.U(rec, 1) == 7 {
								count++
							}
						}
						if count != 1 {
							r.t.Fatal("timed-out live revival lost or duplicated", count)
						}
					}
				}
				if len(r.log()) != 1 {
					r.t.Fatal("return sent service ack")
				}
			})
		})
	}
}
