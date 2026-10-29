// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestUseItemDeliveryCannotProveDone(t *testing.T) {
	c := Command{Key: 1, Verb: 14, Payload: []wire.Field{wire.S(64, true, 42)}}
	if err := validateCommand(c); err != nil {
		t.Fatal(err)
	}
	if err := checkReceipt(Receipt{Requested: 14, Effective: 14, Stage: wire.StageDone, EffectsPresent: true}, c); err == nil {
		t.Fatal("bare done accepted")
	}
	if err := checkReceipt(Receipt{Requested: 14, Effective: 14, Stage: wire.StageSent}, c); err != nil {
		t.Fatal(err)
	}
	c.Payload = append(c.Payload, wire.S(17, true, 7))
	if err := validateCommand(c); err == nil {
		t.Fatal("slot injection accepted")
	}
}
