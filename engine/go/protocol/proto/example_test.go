// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package proto_test

import (
	"fmt"
	"log"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// One attempt goes from admission to a witnessed effect.
func ExampleSession() {
	s, err := proto.New(proto.Config{
		Results: 8, Pending: 2, IntentBytes: 64,
		EvidenceMS: 1000, QueueMS: 500, Classes: []string{"walk"},
	})
	if err != nil {
		log.Fatal(err)
	}
	const owner = 1
	grant, err := s.Take(owner)
	if err != nil {
		log.Fatal(err)
	}
	attempt := proto.Attempt{N: grant.NextN, Control: grant.Epoch, Verb: 1}
	if ad := s.Begin(owner, true, true, attempt, 0); !ad.Allocated {
		log.Fatal("attempt not admitted")
	}
	// The dictionary resolved the effect; this attempt waits for a witness.
	if _, err := s.Prepare(attempt.N, 1, "walk", true); err != nil {
		log.Fatal(err)
	}
	if err := s.Contact(attempt.N, 10); err != nil {
		log.Fatal(err)
	}
	sent, err := s.Sent(attempt.N, false)
	if err != nil {
		log.Fatal(err)
	}
	done, err := s.Done(attempt.N, 20)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(sent.Stage == wire.StageSent, done.Stage == wire.StageDone)
	// Output: true true
}
