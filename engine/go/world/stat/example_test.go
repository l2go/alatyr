// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package stat_test

import (
	"fmt"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/stat"
)

// The standard formula applies first; an authored function with a later Order
// then adds to its result. Walking speed follows running speed.
func ExamplePlayer() {
	p := stat.Player{Template: catalog.PlayerTemplate{RunSpd: 120, DEX: 30}, Level: 1}
	before := p.RunSpeed()
	p.Funcs = []catalog.ItemFunc{{Stat: string(stat.RunSpd), Op: "add", Order: 0x40, Value: 10}}
	fmt.Println(p.RunSpeed()-before, p.WalkSpeed() == p.RunSpeed()*70/100)
	// Output: 10 true
}
