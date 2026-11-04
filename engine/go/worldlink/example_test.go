// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldlink_test

import (
	"context"
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

func ExampleOpen() {
	cat, err := catalog.Load("../../../world")
	if err != nil {
		fmt.Println(err)
		return
	}
	w := world.New(cat, memstore.New(), world.WithReservedIDs([]int32{1}))
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			fmt.Println(err)
		}
	}()
	character := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, CurHP: 100, X: -71057, Y: 257883, Z: -3104}
	// Applications supply their shared host profile; this example uses explicit
	// synthetic test bounds and a detached in-memory character loader.
	source, state, err := worldlink.Open(ctx, worldlink.Config{
		World: w, Catalog: cat, Meaning: gatewaytest.Config(), Origin: time.Now(),
		Load: func(context.Context, worldlink.Identity) (world.Character, error) { return character.Clone(), nil },
	}, worldlink.Identity{Account: character.Account, Character: character.Name})
	if err != nil {
		fmt.Println(err)
		return
	}
	defer func() {
		if err := source.Close(); err != nil {
			fmt.Println(err)
		}
	}()
	snapshot, err := state.Snapshot(0)
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(source.Owner().ID, snapshot.Ready)
	// Output: 1 true
}
