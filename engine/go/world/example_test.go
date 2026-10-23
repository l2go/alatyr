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

func ExampleWorld_Enter() {
	_, file, _, _ := runtime.Caller(0)
	cat, err := catalog.Load(filepath.Join(filepath.Dir(file), "../../../world"))
	if err != nil {
		fmt.Println(err)
		return
	}
	ctx, cancel := context.WithCancel(context.Background())
	w := world.New(cat, memstore.New(), world.WithCommits(func(c world.Commit) {
		if c.Intent.Verb != world.VerbEnter {
			return
		}
		fmt.Println("enter committed:", c.Outcome.Stage == world.StageDone)
		for _, group := range c.Groups {
			for _, effect := range group.Effects {
				if e, ok := effect.(world.ExistenceChanged); ok {
					fmt.Println("exists:", e.Entity.ID, e.After)
				}
			}
		}
	}))
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			fmt.Println(err)
		}
	}()
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 1, CurHP: 50, CurMP: 20, CurCP: 10}
	entry, err := w.Enter(ctx, c)
	if err != nil {
		fmt.Println(err)
		return
	}
	if err := w.Leave(ctx, entry.Self.Entity); err != nil {
		fmt.Println(err)
	}
	// Output:
	// enter committed: true
	// exists: 1 true
}

func ExampleWorld_Learn() {
	_, file, _, _ := runtime.Caller(0)
	cat, err := catalog.Load(filepath.Join(filepath.Dir(file), "../../../world"))
	if err != nil {
		fmt.Println(err)
		return
	}
	ctx, cancel := context.WithCancel(context.Background())
	w := world.New(cat, memstore.New(), world.WithCommits(func(c world.Commit) {
		if c.Intent.Verb != world.VerbLearn {
			return
		}
		fmt.Println("learn committed:", c.Outcome.Stage == world.StageDone)
		for _, group := range c.Groups {
			for _, effect := range group.Effects {
				switch e := effect.(type) {
				case world.ResourceTransferred:
					before, beforeOK := e.FromBalance.Before.Integer()
					after, afterOK := e.FromBalance.After.Integer()
					if beforeOK && afterOK {
						fmt.Println("SP:", before, "->", after)
					}
				case world.CapabilityChanged:
					fmt.Println("skill:", e.ID, e.Before, "->", e.After)
				}
			}
		}
	}), world.WithDurability(func(d world.Durability) {
		fmt.Println("skill saved:", d.Err == nil)
	}))
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			fmt.Println(err)
		}
	}()
	c := world.Character{ObjectID: 1, Account: "example", Name: "Learner", ClassID: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104}
	entry, err := w.Enter(ctx, c)
	if err != nil {
		fmt.Println(err)
		return
	}
	var teacher int32
findTeacher:
	for {
		select {
		case event, ok := <-entry.Events:
			if !ok {
				break findTeacher
			}
			if seen, ok := event.(world.NPCSeen); ok && seen.NPC.Template.ID == 30001 {
				teacher = seen.NPC.ObjectID
			}
		default:
			break findTeacher
		}
	}
	for range 2 {
		if err := w.Action(ctx, c.ObjectID, teacher); err != nil {
			fmt.Println(err)
			return
		}
	}
	if err := w.Bypass(ctx, c.ObjectID, fmt.Sprintf("npc_%d_SkillList", teacher)); err != nil {
		fmt.Println(err)
		return
	}
	if err := w.Learn(ctx, c.ObjectID, 257, 1); err != nil {
		fmt.Println(err)
		return
	}
	if err := w.Leave(ctx, entry.Self.Entity); err != nil {
		fmt.Println(err)
	}
	// Output:
	// learn committed: true
	// SP: 20 -> 8
	// skill: 257 0 -> 1
	// skill saved: true
}
