// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"slices"
	"time"

	"github.com/l2go/alatyr/engine/go/world"
)

// observeDecay adds two controlled world players, independently of the socket
// permissions of the participant under test. The lower ID selects the NPC; the
// higher ID must receive that target clearing before its own disappearance.
// No observation faults on the participant port can hide this source invariant.
func (s *sequencer) observeDecay(n world.NPCView) {
	var entries []world.Entry
	defer func() {
		for _, entry := range entries {
			if err := s.h.World.Leave(s.t.Context(), entry.Self.Entity); err != nil {
				s.t.Error(err)
			}
		}
		s.settle()
	}()
	for _, id := range []int32{2, 3} {
		c := SequenceCharacter(nil)
		c.ObjectID, c.Account, c.Name = id, "sequence-observers", fmt.Sprintf("Observer%d", id)
		c.X, c.Y, c.Z = n.Pos.X, n.Pos.Y, n.Pos.Z
		c.Items, c.Skills = nil, nil
		entry, err := s.h.World.Enter(s.t.Context(), c)
		if err != nil {
			s.t.Fatal(err)
		}
		entries = append(entries, entry)
		// A dense initial scene can overflow the legacy entry notifications.
		// Capture it atomically and begin a fresh stream before selecting the
		// target; no event under assertion is discarded or resubscribed.
		scene, subscription, err := s.h.World.Subscribe(s.t.Context(), entry.Self.Entity)
		if err != nil {
			s.t.Fatal(err)
		}
		if !slices.ContainsFunc(scene.Objects, func(o world.Observation) bool { return o.Entity == n.Entity }) {
			s.t.Fatal("decay observer does not know the NPC")
		}
		entry.Events, entry.Subscription = subscription.Events, subscription
		entries[len(entries)-1] = entry
	}
	commit, err := s.h.World.Execute(s.t.Context(), world.Request{Owner: entries[0].Self.Entity, Verb: world.VerbSelect, Target: n.Entity})
	if err != nil || commit.Outcome.Stage != world.StageDone {
		s.t.Fatalf("decay target selection: got %v/%v, want Done", commit.Outcome, err)
	}
	var got []string
	read := func(record bool) {
		for i, entry := range entries {
		reading:
			for {
				select {
				case e, ok := <-entry.Events:
					if !ok {
						reason := <-entry.Subscription.End
						s.t.Fatalf("decay observer %d closed while recording=%v: reason %d", i, record, reason)
					}
					if !record || i != 1 {
						continue
					}
					switch v := e.(type) {
					case world.TargetCleared:
						if v.ObjectID == entries[0].Self.Entity.ID {
							got = append(got, "clear")
						}
					case world.ObjectGone:
						if v.ObjectID == n.ObjectID {
							got = append(got, "gone")
						}
					}
				default:
					break reading
				}
			}
		}
	}
	s.settle()
	read(false)
	if err := s.h.World.Harm(s.t.Context(), s.h.Owner(), n.Entity, 1e6); err != nil {
		s.t.Fatal(err)
	}
	s.settle()
	read(true)
	// Decay runs after 8.5 seconds on the one-second decay timer. Drain both
	// source streams every second while the normal participant pumps settle.
	for range 10 {
		s.advance(time.Second)
		read(true)
	}
	if want := []string{"clear", "gone"}; !slices.Equal(got, want) {
		s.t.Fatalf("decay observer order: got %v, want %v", got, want)
	}
}
