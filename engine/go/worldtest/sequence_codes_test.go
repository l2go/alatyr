// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
)

func TestSequenceDictionaryCodes(t *testing.T) {
	for _, tc := range []struct {
		code    uint64
		command string
	}{
		{verbWalk, meaning.Walk},
		{verbAim, meaning.Aim},
		{verbStrike, meaning.Strike},
		{verbTake, meaning.Take},
		{verbTalk, meaning.Talk},
		{verbChoose, meaning.Say},
		{verbBuy, meaning.Buy},
		{verbSell, meaning.Sell},
		{verbRise, meaning.Rise},
		{verbFocus, meaning.Focus},
		{verbEngage, meaning.Engage},
		{verbCast, meaning.Cast},
		{verbLearn, meaning.Learn},
		{verbUseItem, meaning.UseItem},
	} {
		v, ok := meaning.VerbByCommand(tc.command)
		if !ok || v.Code != tc.code {
			t.Fatalf("verb %s: got %d/%v, want %d", tc.command, v.Code, ok, tc.code)
		}
	}
	for _, tc := range []struct {
		code uint64
		name string
	}{
		{kindAppeared, meaning.Appeared},
		{kindMoved, meaning.Moved},
		{kindStopped, meaning.Stopped},
		{kindCorrected, meaning.Corrected},
		{kindStruck, meaning.Struck},
		{kindDied, meaning.Died},
		{kindRevived, meaning.Revived},
		{kindDropped, meaning.Dropped},
		{kindTook, meaning.Took},
		{kindGone, meaning.Gone},
		{kindSpoke, meaning.Spoke},
		{kindShop, meaning.Shop},
		{kindOffer, meaning.Offer},
		{kindCarried, meaning.Carried},
		{kindInventory, meaning.Inventory},
		{kindVitals, meaning.Vitals},
		{kindReward, meaning.Reward},
		{kindNotice, meaning.Notice},
		{kindHistory, meaning.History},
		{kindQuest, meaning.Quest},
		{kindQuests, meaning.Quests},
		{kindPractice, meaning.Practice},
		{kindKnown, meaning.Known},
		{kindTargeted, meaning.Targeted},
	} {
		k, ok := meaning.KindByName(tc.name)
		if !ok || k.Code != tc.code {
			t.Fatalf("kind %s: got %d/%v, want %d", tc.name, k.Code, ok, tc.code)
		}
	}
	for _, tc := range []struct {
		opcode int
		verb   uint64
	}{{opWalk, verbWalk}, {opAim, verbAim}, {opStrike, verbStrike}, {opTake, verbTake}, {opTalk, verbTalk}, {opChoose, verbChoose}, {opBuy, verbBuy}, {opSell, verbSell}, {opRise, verbRise}, {opFocus, verbFocus}, {opEngage, verbEngage}, {opCast, verbCast}, {opLearn, verbLearn}, {opUseItem, verbUseItem}} {
		if uint64(tc.opcode)+1 != tc.verb {
			t.Fatalf("opcode %d changed its verb %d", tc.opcode, tc.verb)
		}
	}
}
