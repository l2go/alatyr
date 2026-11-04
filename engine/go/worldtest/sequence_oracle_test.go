// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/worldlink"
	"math"
	"testing"
)

func TestSequenceWorldOracleRejectsMutations(t *testing.T) {
	valid := world.Character{CurHP: 10, MaxHP: 20, CurMP: 2, MaxMP: 3, Items: []world.Item{{ObjectID: 1, Count: 1}}}
	if err := checkCharacter(valid); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"negative-HP", "excess-MP", "NaN", "negative-count", "duplicate-row", "saved-state"} {
		t.Run(name, func(t *testing.T) {
			c := valid.Clone()
			switch name {
			case "negative-HP":
				c.CurHP = -1
			case "excess-MP":
				c.CurMP = 4
			case "NaN":
				c.CurHP = math.NaN()
			case "negative-count":
				c.Items[0].Count = -1
			case "duplicate-row":
				c.Items = append(c.Items, c.Items[0])
			case "saved-state":
				c.LastAccess = 1
				c.Items[0].Count = 2
				if err := checkSaved(valid, c); err == nil {
					t.Fatal("bad save escaped oracle")
				}
				return
			}
			if err := checkCharacter(c); err == nil {
				t.Fatal("mutated world state escaped oracle")
			}
		})
	}
	owner := world.Ref{ID: 1, Gen: 1}
	item := world.Ref{ID: 2, Gen: 1}
	transfer := world.ResourceTransferred{Resource: world.Resource{Kind: world.ResourceItem}, From: world.ResourceLocation{Entity: owner, Item: item, Place: world.PlaceInventory}, To: world.ResourceLocation{Place: world.PlaceConsumption}, Amount: world.IntegerAmount(1), FromBalance: world.Balance{Before: world.IntegerAmount(2), After: world.IntegerAmount(1)}}
	group := world.EffectGroup{Cause: world.Cause{Intent: world.IntentID{Owner: owner, N: 1}}, Effects: []world.Effect{transfer}}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group}}}); err != nil {
		t.Fatal(err)
	}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group, group}}}); err == nil {
		t.Fatal("double consumption escaped oracle")
	}
	transfer.FromBalance.After = world.IntegerAmount(math.MinInt64)
	group.Effects = []world.Effect{transfer}
	if err := checkEconomy([]world.Commit{{Groups: []world.EffectGroup{group}}}); err == nil {
		t.Fatal("balance overflow escaped oracle")
	}
}

func TestSourceOutcomeOracleRejectsMutations(t *testing.T) {
	result := wire.Record{Type: wire.Result, Fields: []wire.Field{wire.U(1, true, 7)}}
	intent := world.IntentID{Owner: world.Ref{ID: 1, Gen: 2}, N: 3}
	handoffs := []worldlink.Handoff{{N: 7, Intent: intent}}
	for _, name := range []string{"missing", "wrong-attempt", "wrong-incarnation", "unknown", "refused", "fact-only"} {
		t.Run(name, func(t *testing.T) {
			commit := world.Commit{Outcome: world.Outcome{Intent: intent, Stage: world.StageDone}}
			if err := checkCommittedResult(result, handoffs, []world.Commit{commit}); err != nil {
				t.Fatal(err)
			}
			switch name {
			case "missing":
				commit.Outcome.Intent = world.IntentID{}
			case "wrong-attempt":
				commit.Outcome.Intent.N++
			case "wrong-incarnation":
				commit.Outcome.Intent.Owner.Gen++
			case "unknown":
				commit.Outcome.Stage = world.StageUnknown
			case "refused":
				commit.Outcome.Stage = world.StageRefused
			case "fact-only":
				commit.Outcome = world.Outcome{}
				commit.Groups = []world.EffectGroup{{Cause: world.Cause{Intent: intent}}}
			}
			if err := checkCommittedResult(result, handoffs, []world.Commit{commit}); err == nil {
				t.Fatal("unproven Done escaped source oracle")
			}
		})
	}
}
