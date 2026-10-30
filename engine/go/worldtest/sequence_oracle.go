// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// This oracle deliberately reads raw packets, actual contact times and world
// commitments; it never calls the implementation's Witness or planner again.
func checkRawEvidence(records []wire.Record, attempts map[uint64]*sequenceAttempt, handoffs []backend.Handoff, trace []bridge.Update, writes []sequenceWrite, evidenceMS int64) error {
	byAttempt := make(map[uint64][]backend.Handoff)
	contact := make(map[uint64]time.Time)
	effects := make(map[uint64]int)
	for _, h := range handoffs {
		if h.N == 0 {
			continue
		}
		byAttempt[h.N] = append(byAttempt[h.N], h)
		if h.Role == gateway.StepEffect {
			effects[h.N]++
			if effects[h.N] > 1 {
				return fmt.Errorf("attempt %d executed more than one effect", h.N)
			}
		}
		for _, w := range writes {
			if w.command.Key == h.Key {
				if contact[h.N].IsZero() || w.at.Before(contact[h.N]) {
					contact[h.N] = w.at
				}
				break
			}
		}
	}
	causes := make(map[uint64]wire.Record)
	for _, r := range records {
		if r.Type == wire.Fact && gatewaytest.U(r, 32) != 0 {
			causes[gatewaytest.U(r, 32)] = r
		}
		if r.Type != wire.Result {
			continue
		}
		n := gatewaytest.U(r, 1)
		stage := wire.Stage(gatewaytest.U(r, 2))
		if stage == wire.StageRefused && len(byAttempt[n]) != 0 {
			return fmt.Errorf("refused attempt %d reached the world", n)
		}
		if stage != wire.StageDone {
			continue
		}
		fact, ok := causes[n]
		if !ok {
			return fmt.Errorf("done %d has no causal fact", n)
		}
		a := attempts[n]
		if a == nil {
			return fmt.Errorf("unknown Done %d", n)
		}
		epoch := gatewaytest.U(fact, 4)
		effective := gatewaytest.U(r, 6)
		if epoch != a.epoch && !(effective == verbRise && epoch == a.epoch+1) {
			return fmt.Errorf("done %d crossed a projection epoch", n)
		}
		targetField := uint32(10)
		if effective == verbTake {
			targetField = 20
		}
		target, _ := wire.Find(a.record.Fields, targetField)
		if effective == verbChoose {
			target = wire.Value{Fields: []wire.Field{wire.S(1, true, int64(a.chosen.ID)), wire.U(2, true, a.chosen.Gen)}}
		}
		if effective == verbTalk || effective == verbChoose {
			observed, _ := wire.Find(fact.Fields, 10)
			if !sameRef(target, observed) {
				return fmt.Errorf("done %d crossed a target generation", n)
			}
		}
		found := false
		for _, h := range byAttempt[n] {
			if h.Role != gateway.StepEffect {
				continue
			}
			deadline := contact[n].Add(time.Duration(evidenceMS) * time.Millisecond)
			for _, u := range trace {
				if u.Kind != bridge.LiveBatch || u.Batch.Snapshot {
					continue
				}
				for i, raw := range u.Batch.Records {
					if raw.Type != wire.Fact || gatewaytest.U(raw, 1) != gatewaytest.U(fact, 1) {
						continue
					}
					at := u.Batch.ReceivedAt[i]
					if contact[n].IsZero() || at.Before(contact[n]) || !at.Before(deadline) {
						continue
					}
					rawMS, _ := wire.Find(raw.Fields, 3)
					publicMS, _ := wire.Find(fact.Fields, 3)
					// Full-window learning emits the current skill proof at expiry;
					// its original live candidate retains the earlier receive time.
					if effective != verbLearn && rawMS.S != publicMS.S {
						continue
					}
					subject, _ := wire.Find(raw.Fields, 64)
					publicRef, _ := wire.Find(fact.Fields, 10)
					id, _ := wire.Find(publicRef.Fields, 1)
					if subject.S != id.S {
						continue
					}
					if effective == verbChoose || effective == verbUseItem {
						if gatewaytest.U(raw, 75) != h.Key {
							continue
						}
					} else if effective != verbRise && !matchingReceipt(trace, h, contact[n], deadline, gatewaytest.U(raw, 4)) {
						continue
					}
					if effective == verbTake {
						picked, _ := wire.Find(raw.Fields, 65)
						requested, _ := wire.Find(target.Fields, 1)
						if picked.S != requested.S {
							continue
						}
					}
					found = true
				}
			}
		}
		if !found {
			return fmt.Errorf("done %d has no timely live raw witness for its effect", n)
		}
	}
	return nil
}
func sameRef(a, b wire.Value) bool {
	ai, _ := wire.Find(a.Fields, 1)
	bi, _ := wire.Find(b.Fields, 1)
	ag, _ := wire.Find(a.Fields, 2)
	bg, _ := wire.Find(b.Fields, 2)
	return ai.S == bi.S && ag.U == bg.U
}
func matchingReceipt(trace []bridge.Update, h backend.Handoff, contact, deadline time.Time, epoch uint64) bool {
	for _, u := range trace {
		if u.Kind != bridge.LiveBatch || u.Batch.Snapshot {
			continue
		}
		for _, r := range u.Batch.Receipts {
			if r.Key == h.Key && r.N == h.RawN && r.GatewayEpoch == epoch && !r.Replay && r.Stage == wire.StageDone && !r.ObservedAt.Before(contact) && r.ObservedAt.Before(deadline) {
				return true
			}
		}
	}
	return false
}

func checkEconomy(commits []world.Commit) error {
	type consumption struct {
		intent world.IntentID
		item   world.Ref
	}
	used := make(map[consumption]bool)
	for _, c := range commits {
		for _, g := range c.Groups {
			for _, effect := range g.Effects {
				e, ok := effect.(world.ResourceTransferred)
				if !ok {
					continue
				}
				amount, integer := e.Amount.Integer()
				if !integer {
					continue
				}
				if amount < 0 {
					return fmt.Errorf("negative resource transfer")
				}
				for _, side := range []struct {
					location world.ResourceLocation
					balance  world.Balance
					debit    bool
				}{{e.From, e.FromBalance, true}, {e.To, e.ToBalance, false}} {
					if side.location.Entity == (world.Ref{}) {
						continue
					}
					before, okBefore := side.balance.Before.Integer()
					after, okAfter := side.balance.After.Integer()
					if !okBefore || !okAfter || before < 0 || after < 0 {
						return fmt.Errorf("invalid integer resource balance")
					}
					if side.debit {
						if before < amount || before-amount != after {
							return fmt.Errorf("resource debit underflow or mismatch")
						}
					} else {
						if after < before || after-before != amount {
							return fmt.Errorf("resource credit overflow or mismatch")
						}
					}
				}
				if e.To.Place == world.PlaceConsumption && e.Resource.Kind == world.ResourceItem {
					key := consumption{g.Cause.Intent, e.From.Item}
					if used[key] || amount != 1 {
						return fmt.Errorf("item consumed more than once per intent")
					}
					used[key] = true
				}
			}
		}
	}
	return nil
}
