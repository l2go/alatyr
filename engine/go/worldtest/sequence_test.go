// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"math/rand/v2"
	"path/filepath"
	"slices"
	"sync"
	"testing"
	"testing/synctest"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

func sequenceCatalog(t testing.TB) *catalog.Catalog {
	t.Helper()
	cat, err := catalog.Load(filepath.Join("..", "..", "..", "world"))
	if err != nil {
		t.Fatal(err)
	}
	return cat
}

func sequenceRun(t *testing.T, cat *catalog.Catalog, data []byte) []byte {
	t.Helper()
	var transcript []byte
	synctest.Test(t, func(t *testing.T) {
		var mu sync.Mutex
		var commits []world.Commit
		var scheduled *SequenceReceiver
		r := newRigWithPort(t, cat, SequenceCharacter(data), allSources(), nil, parityPolicy(), func(w gateway.World) gateway.World { scheduled = NewSequenceReceiver(w); return scheduled }, world.WithCommits(func(c world.Commit) { mu.Lock(); commits = append(commits, c); mu.Unlock() }))

		transcript = ExerciseSequence(t, data, SequenceHost{Flush: scheduled.Flush, World: r.w, Gateway: r.g, Config: r.c, Peer: r.p, Owner: r.port.Port.Owner,
			Saved: func() world.Character {
				chars, err := r.store.Characters(t.Context(), "example")
				if err != nil || len(chars) != 1 {
					t.Fatal("saved character", err)
				}
				return chars[0]
			},
			Close: r.session.Close, Stop: r.stop, Handoffs: func() []worldlink.Handoff { r.mu.Lock(); defer r.mu.Unlock(); return slices.Clone(r.handoffs) }, Commits: func() []world.Commit { mu.Lock(); defer mu.Unlock(); return slices.Clone(commits) },
		})
	})
	return transcript
}

func TestGatewayReliabilitySeeds(t *testing.T) {
	cat := sequenceCatalog(t)
	for i, seed := range scenarioSeeds() {
		t.Run(fmt.Sprint(i), func(t *testing.T) {
			t.Parallel()
			CompareSequence(t, sequenceRun(t, cat, seed), sequenceRun(t, cat, seed))
		})
	}
}

func FuzzGatewaySequence(f *testing.F) {
	cat := sequenceCatalog(f)
	for _, seed := range SequenceSeeds() {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, data []byte) {
		t.Parallel()
		if len(data) > 193 {
			data = data[:193]
		}
		CompareSequence(t, sequenceRun(t, cat, data), sequenceRun(t, cat, data))
	})
}

func TestGeneratedGatewaySequences(t *testing.T) {
	cat := sequenceCatalog(t)
	rng := rand.New(rand.NewPCG(11, 29))
	for i := range 32 {
		data := make([]byte, 97)
		for j := range data {
			data[j] = byte(rng.Uint32())
		}
		t.Run(fmt.Sprint(i), func(t *testing.T) {
			t.Parallel()
			CompareSequence(t, sequenceRun(t, cat, data), sequenceRun(t, cat, data))
		})
	}
}

func TestResumeAfterLastLessonKeepsReadOnlyDialogue(t *testing.T) {
	t.Parallel()
	cat := sequenceCatalog(t)
	program := []byte{0, opEngage, 0, 0, opChoose, 0, 0, opLearn, 0, 0, opResume, 0, 0}
	CompareSequence(t, sequenceRun(t, cat, program), sequenceRun(t, cat, program))
}
