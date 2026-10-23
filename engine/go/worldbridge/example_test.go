// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

// The package intentionally has no public client yet. This example demonstrates
// source translation without claiming a session or executing a wire effect.
func Example() {
	tr, s := fixture()
	p, err := prepare(command(2), s, s.End.Time)
	if err != nil {
		fmt.Println(err)
		return
	}
	c := commit(p, world.StageDone)
	g := group(c)
	g.Facts = []world.Fact{{Subject: p.request.Owner, Property: world.PropertyTarget, Before: world.RefValue(world.Ref{}), After: world.RefValue(p.request.Target)}}
	c.Groups = []world.EffectGroup{g}
	r, err := tr.receipt(p, c, nil, 1, 1, time.Unix(1, 0))
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println("target changed:", r.Stage == wire.StageDone, len(r.Effects))
	c.Groups = nil
	r, err = tr.receipt(p, c, nil, 2, 1, time.Unix(1, 0))
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println("unchanged target:", r.Stage == wire.StageUnknown)
	// Output:
	// target changed: true 1
	// unchanged target: true
}
