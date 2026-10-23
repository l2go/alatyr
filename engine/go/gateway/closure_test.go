// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"context"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type closingWorld struct{ closed int }

func (w *closingWorld) Updates() <-chan Update            { return nil }
func (w *closingWorld) Write(context.Context, Step) error { panic("closed control must not write") }
func (w *closingWorld) Close() error                      { w.closed++; return nil }
func TestFailedControlReleaseClosesWorldOnce(t *testing.T) {
	attempts, err := proto.New(proto.Config{Results: 4, Pending: 1, IntentBytes: 256, EvidenceMS: 10, QueueMS: 10})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := attempts.Take(1); err != nil {
		t.Fatal(err)
	}
	attempts.Close()
	world := &closingWorld{}
	g := &Gateway{}
	s := &game{gate: g, attempts: attempts, world: world, conns: map[uint64]*connection{}}
	s.drop(&connection{id: 1}, wire.CloseNormal)
	s.fail(wire.CloseInternal)
	if !s.closed || world.closed != 1 {
		t.Fatal(s.closed, world.closed)
	}
}
