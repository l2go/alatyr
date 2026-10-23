// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package memstore_test

import (
	"context"
	"fmt"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

// The store keeps detached copies: changing a value after storing it does not
// change what the store returns.
func ExampleStore() {
	ctx := context.Background()
	var s memstore.Store
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 1}
	if err := s.PutCharacter(ctx, c); err != nil {
		fmt.Println(err)
		return
	}
	c.Level = 9
	if err := s.PutSkill(ctx, 1, world.Skill{ID: 3, Level: 1}); err != nil {
		fmt.Println(err)
		return
	}
	stored, err := s.Characters(ctx, "example")
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(len(stored), stored[0].Name, stored[0].Level, len(stored[0].Skills))
	// Output: 1 Walker 1 1
}
