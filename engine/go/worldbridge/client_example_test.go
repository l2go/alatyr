// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge_test

import (
	"context"
	"fmt"
	"path/filepath"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func ExampleOpen() {
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		fmt.Println(err)
		return
	}
	store := memstore.New()
	w := world.New(cat, store, world.WithReservedIDs([]int32{1}))
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			fmt.Println(err)
		}
	}()
	// This resolver stands for a trusted host's existing-character lookup.
	cfg := worldbridge.Config{World: w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) {
		return world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 1, CurHP: 50, CurMP: 20, CurCP: 10}, nil
	}}
	c, snapshot, err := worldbridge.Open(ctx, cfg, bridge.Identity{Account: "example", Character: "Walker"})
	if err != nil {
		fmt.Println(err)
		return
	}
	defer func() {
		if err := c.Close(); err != nil {
			fmt.Println(err)
		}
	}()
	var port bridge.Port = c
	fmt.Println("ready:", snapshot.Ready)
	n, err := port.Write(ctx, bridge.Command{Key: 1, Verb: 2, Payload: []wire.Field{wire.S(64, true, 1)}})
	if err != nil {
		fmt.Println(err)
		return
	}
	for u := range port.Updates() {
		for _, r := range u.Batch.Receipts {
			if r.N == n {
				fmt.Println("target committed:", r.Stage == wire.StageDone, len(r.Effects))
				if err := port.Close(); err != nil {
					fmt.Println(err)
					return
				}
				chars, err := store.Characters(ctx, "example")
				if err != nil {
					fmt.Println(err)
					return
				}
				fmt.Println("saved:", len(chars))
				return
			}
		}
	}
	// Output:
	// ready: true
	// target committed: true 1
	// saved: 1
}
