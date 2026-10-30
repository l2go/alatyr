// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"math/rand/v2"
	"sync"
	"testing"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func sequenceRun(t *testing.T, data []byte) []byte {
	t.Helper()
	var transcript []byte
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		policy := meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
			switch o.Role {
			case "tutor", "provisioner", "folk":
				return meaning.Capabilities{Talk: true, Cast: true}
			case "creature":
				return meaning.Capabilities{Strike: true, Cast: true}
			case meaning.RoleDrop:
				return meaning.Capabilities{Pickup: true}
			}
			return meaning.Capabilities{}
		}}
		var scheduled *SequencePort
		var mu sync.Mutex
		var commits []world.Commit
		r := newRigWithPort(t, cat, SequenceCharacter(data), allSources(), nil, policy, func(p bridge.Port) bridge.Port { scheduled = NewSequencePort(p); return scheduled }, world.WithCommits(func(c world.Commit) { mu.Lock(); commits = append(commits, c); mu.Unlock() }))
		scopes := []*backend.World{r.scope}
		transcript = ExerciseSequence(t, data, SequenceHost{
			World: r.w, Gateway: r.g, Config: r.c, Peer: r.p,
			Owner: scheduled.Port.(*worldbridge.Client).Owner,
			Resume: func() error {
				scope, state, err := r.session.Resume(t.Context())
				if err != nil {
					return err
				}
				r.mu.Lock()
				r.scope, r.state = scope, state
				r.mu.Unlock()
				scopes = append(scopes, scope)
				return nil
			},
			Saved: func() world.Character {
				chars, err := r.store.Characters(t.Context(), "example")
				if err != nil || len(chars) != 1 {
					t.Fatalf("saved characters: %v/%v", chars, err)
				}
				return chars[0]
			},
			Close: r.session.Close, Stop: r.stop, Port: scheduled, Handoffs: func() []backend.Handoff {
				r.mu.Lock()
				defer r.mu.Unlock()
				return append([]backend.Handoff(nil), r.handoffs...)
			}, Commits: func() []world.Commit { mu.Lock(); defer mu.Unlock(); return append([]world.Commit(nil), commits...) }, Scopes: func() []*backend.World { return scopes },
		})
	})
	return transcript
}

func TestGatewayReliabilitySeeds(t *testing.T) {
	for i, seed := range scenarioSeeds() {
		t.Run(fmt.Sprint(i), func(t *testing.T) { CompareSequence(t, sequenceRun(t, seed), sequenceRun(t, seed)) })
	}
}

func FuzzGatewaySequence(f *testing.F) {
	for _, seed := range SequenceSeeds() {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) > 193 {
			data = data[:193]
		}
		CompareSequence(t, sequenceRun(t, data), sequenceRun(t, data))
	})
}

func TestGeneratedGatewaySequences(t *testing.T) {
	rng := rand.New(rand.NewPCG(11, 29))
	for i := range 32 {
		data := make([]byte, 97)
		for j := range data {
			data[j] = byte(rng.Uint32())
		}
		t.Run(fmt.Sprint(i), func(t *testing.T) { CompareSequence(t, sequenceRun(t, data), sequenceRun(t, data)) })
	}
}

func TestResumeAfterLastLessonKeepsReadOnlyDialogue(t *testing.T) {
	program := []byte{0, 10, 0, 0, 5, 0, 0, 12, 0, 0, 20, 0, 0}
	CompareSequence(t, sequenceRun(t, program), sequenceRun(t, program))
}
