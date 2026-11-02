// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"reflect"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestStagesTranslateByMeaning(t *testing.T) {
	for _, tc := range []struct {
		name       string
		stage      world.Stage
		reason     world.Reason
		want       wire.Stage
		wantReason wire.Reason
		delivery   wire.Delivery
	}{
		{"handoff", world.StageUnknown, "", wire.StageSent, wire.ReasonNone, wire.DeliveryBackend},
		{"move is not done", world.StageDone, "", wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend},
		{"refused", world.StageRefused, world.ReasonState, wire.StageRefused, wire.ReasonWorld, wire.DeliveryNone},
		{"expired", world.StageExpired, "", wire.StageRefused, wire.ReasonQueueExpired, wire.DeliveryNone},
		{"canceled", world.StageCanceled, "", wire.StageUnknown, wire.ReasonInterrupted, wire.DeliveryPartial},
		{"yielded", world.StageYielded, "", wire.StageUnknown, wire.ReasonInterrupted, wire.DeliveryPartial},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			p := preparedTest(t, 1, s)
			c := commit(p, tc.stage)
			c.Outcome.Reason = tc.reason
			r := receiptTest(t, tr, p, c, nil)
			if r.Stage != tc.want || r.Reason != tc.wantReason || r.Delivery != tc.delivery || r.EffectsPresent {
				t.Fatalf("got %+v, want %v/%v/%v without effects", r, tc.want, tc.wantReason, tc.delivery)
			}
		})
	}
}
func TestSelectRequiresItsOwnTargetMutation(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*world.Commit, prepared)
		want   wire.Stage
	}{
		{"unchanged", func(*world.Commit, prepared) {}, wire.StageUnknown},
		{"target changed", func(c *world.Commit, p prepared) {
			g := group(*c)
			g.Facts = []world.Fact{{Subject: p.request.Owner, Property: world.PropertyTarget, Before: world.RefValue(world.Ref{}), After: world.RefValue(p.request.Target)}}
			c.Groups = []world.EffectGroup{g}
		}, wire.StageDone},
		{"foreign owner", func(c *world.Commit, p prepared) {
			g := group(*c)
			g.Facts = []world.Fact{{Subject: p.request.Target, Property: world.PropertyTarget, Before: world.RefValue(world.Ref{}), After: world.RefValue(p.request.Target)}}
			c.Groups = []world.EffectGroup{g}
		}, wire.StageUnknown},
		{"ambient", func(c *world.Commit, p prepared) {
			g := group(*c)
			g.Cause = world.Cause{Rule: world.RuleAI}
			g.Facts = []world.Fact{{Subject: p.request.Owner, Property: world.PropertyTarget, Before: world.RefValue(world.Ref{}), After: world.RefValue(p.request.Target)}}
			c.Groups = []world.EffectGroup{g}
		}, wire.StageUnknown},
		{"wrong value kind", func(c *world.Commit, p prepared) {
			g := group(*c)
			g.Facts = []world.Fact{{Subject: p.request.Owner, Property: world.PropertyTarget, Before: world.IntValue(0), After: world.RefValue(p.request.Target)}}
			c.Groups = []world.EffectGroup{g}
		}, wire.StageUnknown},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			p := preparedTest(t, 2, s)
			c := commit(p, world.StageDone)
			tc.mutate(&c, p)
			before := reflect.ValueOf(c).Interface()
			r := receiptTest(t, tr, p, c, nil)
			if r.Stage != tc.want {
				t.Fatalf("got %+v, want %v", r, tc.want)
			}
			if !reflect.DeepEqual(c, before) {
				t.Fatal("translation mutated source commit")
			}
			if r.Stage == wire.StageDone {
				r.Effects[0].Fields[1].Value.S = 88
				if c.Groups[0].Facts[0].After != world.RefValue(p.request.Target) {
					t.Fatal("receipt aliases source")
				}
			}
		})
	}
}
func TestPickupRequiresOwnedTransferAndBothBalances(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*world.ResourceTransferred)
		want   wire.Stage
	}{
		{"balanced item", func(*world.ResourceTransferred) {}, wire.StageDone},
		{"missing source balance", func(v *world.ResourceTransferred) { v.FromBalance = world.Balance{} }, wire.StageUnknown},
		{"wrong destination balance", func(v *world.ResourceTransferred) { v.ToBalance.After = world.IntegerAmount(9) }, wire.StageUnknown},
		{"foreign recipient", func(v *world.ResourceTransferred) { v.To.Entity.Gen++ }, wire.StageUnknown},
		{"stale source", func(v *world.ResourceTransferred) { v.From.Entity.Gen++ }, wire.StageUnknown},
		{"continuous quantity", func(v *world.ResourceTransferred) { v.Amount = world.ContinuousAmount(3) }, wire.StageUnknown},
		{"currency alone", func(v *world.ResourceTransferred) { v.Resource.TemplateID = 202 }, wire.StageUnknown},
		{"unmapped item", func(v *world.ResourceTransferred) { v.Resource.TemplateID = 999 }, wire.StageUnknown},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			p := preparedTest(t, 4, s)
			c := commit(p, world.StageDone)
			v := world.ResourceTransferred{
				Resource:    world.Resource{Kind: world.ResourceItem, TemplateID: 201},
				From:        world.ResourceLocation{Entity: p.request.Target, Place: world.PlaceGround},
				To:          world.ResourceLocation{Entity: p.request.Owner, Item: world.Ref{ID: 20, Gen: 3}, Place: world.PlaceInventory},
				Amount:      world.IntegerAmount(3),
				FromBalance: world.Balance{Before: world.IntegerAmount(3)},
				ToBalance:   world.Balance{Before: world.IntegerAmount(5), After: world.IntegerAmount(8)},
			}
			tc.mutate(&v)
			c.Groups = []world.EffectGroup{group(c, v, world.ExistenceChanged{Entity: p.request.Target, Before: true})}
			r := receiptTest(t, tr, p, c, nil)
			if r.Stage != tc.want || r.EffectsPresent != (tc.want == wire.StageDone) {
				t.Fatalf("got %+v, want %v with effects only for proven completion", r, tc.want)
			}
			if tc.want == wire.StageDone && len(r.Effects) != 1 {
				t.Fatalf("got %d effects, want one item transfer", len(r.Effects))
			}
		})
	}
}

func TestLearningRequiresOneAtomicGroupAndSuccessfulWrite(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*world.Commit, prepared)
		err    error
		want   wire.Stage
	}{
		{"atomic", func(*world.Commit, prepared) {}, nil, wire.StageDone},
		{"write failed", func(*world.Commit, prepared) {}, errors.New("write failed"), wire.StageUnknown},
		{"split", func(c *world.Commit, p prepared) {
			g := c.Groups[0]
			c.Groups = []world.EffectGroup{group(*c, g.Effects[0]), group(*c, g.Effects[1])}
		}, nil, wire.StageUnknown},
		{"foreign cause", func(c *world.Commit, p prepared) { c.Groups[0].Cause = world.Cause{Rule: world.RuleReward} }, nil, wire.StageUnknown},
		{"wrong cost", func(c *world.Commit, p prepared) {
			v := c.Groups[0].Effects[0].(world.ResourceTransferred)
			v.Amount = world.IntegerAmount(9)
			c.Groups[0].Effects[0] = v
		}, nil, wire.StageUnknown},
		{"foreign skill owner", func(c *world.Commit, p prepared) {
			v := c.Groups[0].Effects[1].(world.CapabilityChanged)
			v.Entity = p.request.Target
			c.Groups[0].Effects[1] = v
		}, nil, wire.StageUnknown},
		{"additional target mutation", func(c *world.Commit, p prepared) {
			c.Groups[0].Facts = []world.Fact{{Subject: p.request.Owner, Property: world.PropertyTarget, Before: world.RefValue(world.Ref{}), After: world.RefValue(p.request.Target)}}
		}, nil, wire.StageUnknown},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			p := preparedTest(t, 13, lessonScene(s))
			c := commit(p, world.StageDone)
			c.Groups = []world.EffectGroup{learningGroup(c, p)}
			tc.mutate(&c, p)
			r := receiptTest(t, tr, p, c, tc.err)
			if r.Stage != tc.want {
				t.Fatalf("got %+v, want %v", r, tc.want)
			}
			if tc.err != nil && c.Outcome.Stage != world.StageDone {
				t.Fatal("durability rewrote in-memory outcome")
			}
			if r.Stage == wire.StageDone && len(r.Effects) != 2 {
				t.Fatalf("got %d effects, want full SP/skill set", len(r.Effects))
			}
			if r.Stage != wire.StageDone && r.EffectsPresent {
				t.Fatal("unknown retained partial effects")
			}
		})
	}
}
func TestSpeechAndChoiceNeedAttributedSpeech(t *testing.T) {
	for _, tc := range []struct {
		name   string
		verb   uint64
		speech bool
		want   wire.Stage
	}{
		{"talk speech", 5, true, wire.StageDone},
		{"talk without speech", 5, false, wire.StageUnknown},
		{"choose list only", 6, false, wire.StageUnknown},
		{"choose speech", 6, true, wire.StageDone},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tr, s := fixture()
			p := preparedTest(t, tc.verb, choiceScene(s))
			c := commit(p, world.StageDone)
			if tc.speech {
				c.Groups = []world.EffectGroup{group(c, world.SpeechCompleted{Speaker: p.request.Target, Listener: p.request.Owner, Text: "<p>Hello</p>"})}
			}
			r := receiptTest(t, tr, p, c, nil)
			if r.Stage != tc.want || tc.want == wire.StageDone && (!r.EffectsPresent || len(r.Effects) != 0) {
				t.Fatalf("got %+v, want %v with present empty effects only for speech", r, tc.want)
			}
		})
	}
}
func TestUnownedAndPartialRefusalsStayUnknown(t *testing.T) {
	tr, s := fixture()
	p := preparedTest(t, 2, s)
	for _, tc := range []struct {
		name   string
		mutate func(*world.Commit)
	}{
		{"owner", func(c *world.Commit) { c.Intent.ID.Owner.Gen++; c.Outcome.Intent = c.Intent.ID }},
		{"outcome identity", func(c *world.Commit) { c.Outcome.Intent.N++ }},
		{"verb", func(c *world.Commit) { c.Intent.Verb = world.VerbAttack }},
		{"deadline", func(c *world.Commit) { c.Intent.Deadline++ }},
		{"partial refusal", func(c *world.Commit) {
			c.Groups = []world.EffectGroup{group(*c, world.ExistenceChanged{Entity: p.request.Owner, Before: true})}
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c := commit(p, world.StageRefused)
			c.Outcome.Reason = world.ReasonState
			tc.mutate(&c)
			r := receiptTest(t, tr, p, c, nil)
			if r.Stage != wire.StageUnknown {
				t.Fatalf("got %+v, want unknown", r)
			}
		})
	}
}
