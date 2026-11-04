// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestReconnectDuringSourceTransition(t *testing.T) {
	for _, transition := range []string{"attempt", "teleport", "death"} {
		t.Run(transition, func(t *testing.T) {
			runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
				var receiver *SequenceReceiver
				r := newRigWithPort(t, cat, readyCharacter(), allSources(), nil, parityPolicy(), func(w gateway.World) gateway.World {
					receiver = NewSequenceReceiver(w)
					return receiver
				})
				owner, session := r.session.Owner(), r.p.Session
				original := r.intent(1, 5, r.ref(r.actor(30001).ID, 10))
				if transition == "attempt" {
					r.p.Send(original)
					r.p.Result(1, wire.StageSent)
				} else if transition == "teleport" {
					if _, err := r.w.Execute(t.Context(), world.Request{Owner: owner, Verb: world.VerbTeleport, Position: world.Point{X: -71047, Y: 257883, Z: -3104}}); err != nil {
						t.Fatal(err)
					}
				} else if err := r.w.Harm(t.Context(), world.Ref{}, owner, 1e6); err != nil {
					t.Fatal(err)
				}
				// The socket is lost before the gateway consumes the source transition.
				r.reconnect()
				for receiver.Flush() {
					r.sync()
				}
				r.sync()
				if r.p.Session != session || r.session.Owner() != owner || !r.p.Ready {
					t.Fatalf("reconnect replaced scope: session=%d owner=%+v ready=%v", r.p.Session, r.session.Owner(), r.p.Ready)
				}
				if transition == "attempt" {
					r.p.Result(1, wire.StageDone)
					r.p.Send(original)
					r.p.Result(1, wire.StageDone)
					r.sync()
					if participantCommands(r.port.commands()) != 1 {
						t.Fatal("replay reexecuted the interrupted attempt")
					}
				}
				scene, err := r.w.Capture(t.Context(), owner)
				if err != nil || scene.Self.Teleporting || scene.Self.Dead != (transition == "death") {
					t.Fatalf("reconnected source=%+v/%v", scene.Self, err)
				}
			})
		})
	}
}

func TestReconnectWhileAsynchronousTalkIsPending(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		char := readyCharacter()
		char.Y = 258243 - 400
		r := newRig(t, cat, char, allSources(), nil)
		owner, session := r.session.Owner(), r.p.Session
		request := r.intent(1, 5, r.ref(r.actor(30002).ID, 10))
		r.p.Send(request)
		r.p.Result(1, wire.StageSent)
		r.reconnect()
		// Loss of control stops the old approach. Its unresolved attempt keeps
		// the original evidence deadline; reconnect does not invent completion.
		r.advance(6 * time.Second)
		r.p.Result(1, wire.StageUnknown)
		writes := participantCommands(r.port.commands())
		r.p.Send(request)
		r.p.Result(1, wire.StageUnknown)
		r.sync()
		if r.session.Owner() != owner || r.p.Session != session || participantCommands(r.port.commands()) != writes {
			t.Fatal("pending reconnect replaced scope or reexecuted the attempt")
		}
	})
}
