// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning_test

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func ExampleNewState() {
	// This example supplies synthetic bounds, not production defaults.
	c := meaning.Config{
		Wire:     wire.Limits{FrameBytes: 8192, StringBytes: 256, ListItems: 32, Depth: 4, Records: 16, Fields: 64, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts: proto.Config{Pending: 8},
		Objects:  8, Windows: 4, WindowBytes: 4096, ProposalMS: 1000,
		HistoryEntries: 4, HistoryBytes: 2048,
	}
	c.Advertised[25], c.Advertised[29] = 65536, 65536
	s, err := meaning.NewState(c, meaning.Policy{})
	if err != nil {
		fmt.Println(err)
		return
	}
	_, err = s.Apply(gateway.Update{Value: meaning.Input{Event: meaning.Event{Kind: meaning.Appeared, ID: 1, Mine: true}}}, 0)
	if err != nil {
		fmt.Println(err)
		return
	}
	r, err := meaning.CommandRecord(meaning.Command{Kind: meaning.Walk, X: 10}, meaning.CommandContext{N: 1})
	if err != nil {
		fmt.Println(err)
		return
	}
	action, reason := s.Plan(r, 0)
	if reason != wire.ReasonNone {
		fmt.Println(reason)
		return
	}
	plan, err := s.Describe(action)
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(plan.Command.Kind, len(action.Steps))
	// Output: walk 1
}
