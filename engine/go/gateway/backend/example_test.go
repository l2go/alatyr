// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend_test

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/fixture"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func ExampleNew() {
	// The raw constructor returns the complete snapshot before admission.
	fact := func(n, kind uint64, domain string, fields ...wire.Field) wire.Record {
		return wire.Record{Type: wire.Fact, Fields: append([]wire.Field{wire.U(1, true, kind), wire.U(2, true, n), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, domain), wire.U(6, true, 1)}, fields...)}
	}
	snapshot := bridge.Snapshot{GatewayEpoch: 1, Ready: true, FenceSeq: 4, Facts: []wire.Record{
		fact(1, 1, "objects", wire.S(64, true, 1), wire.U(19, true, 1), wire.Obj(25, true, []wire.Field{wire.S(1, true, 100)})),
		fact(2, 14, "inventory", wire.Items(24, true, []wire.Value{})),
		fact(3, 23, "skills", wire.U(31, true, 1), wire.Items(30, true, []wire.Value{})),
		fact(4, 21, "quests", wire.U(31, true, 1), wire.Items(29, true, []wire.Value{})),
	}}
	port, err := fixture.New(fixture.Config{})
	if err != nil {
		fmt.Println(err)
		return
	}
	session, _, state, err := backend.New(port, snapshot, backend.Config{Meaning: gatewaytest.Config(), Origin: time.Now()})
	if err != nil {
		fmt.Println(err)
		if closeErr := port.Close(); closeErr != nil {
			fmt.Println(closeErr)
		}
		return
	}
	projection, err := state.Snapshot(0)
	if err != nil {
		fmt.Println(err)
	} else {
		fmt.Println(projection.Ready)
	}
	if err := session.Close(); err != nil {
		fmt.Println(err)
	}
	// Output: true
}
