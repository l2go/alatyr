// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world_test

import (
	"context"
	"fmt"
	"path/filepath"
	"runtime"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func ExampleWorld_Execute() {
	_, file, _, _ := runtime.Caller(0)
	cat, err := catalog.Load(filepath.Join(filepath.Dir(file), "../../../world"))
	if err != nil {
		fmt.Println(err)
		return
	}
	ctx, cancel := context.WithCancel(context.Background())
	w := world.New(cat, memstore.New())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			fmt.Println(err)
		}
	}()
	entry, err := w.Enter(ctx, world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 1, CurHP: 50, CurMP: 20, CurCP: 10})
	if err != nil {
		fmt.Println(err)
		return
	}
	defer func() {
		if err := w.LeaveRef(ctx, entry.Self.Entity); err != nil {
			fmt.Println(err)
		}
	}()
	commit, err := w.Execute(ctx, world.Request{Owner: entry.Self.Entity, Verb: world.VerbSelect, Target: entry.Self.Entity})
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println("selection committed:", commit.Outcome.Stage == world.StageDone)
	for _, group := range commit.Groups {
		for _, fact := range group.Facts {
			if fact.Property == world.PropertyTarget {
				target, ok := fact.After.Ref()
				fmt.Println("target:", target.ID, ok)
			}
		}
		fmt.Println("resource effects:", len(group.Effects))
	}
	scene, err := w.Capture(ctx, entry.Self.Entity)
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println("scene ready:", scene.End.Ready)
	// Output:
	// selection committed: true
	// target: 1 true
	// resource effects: 0
	// scene ready: true
}
