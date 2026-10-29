// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

func TestParityWaitsForAsynchronousEvidenceWithinOriginalWindow(t *testing.T) {
	runWorld(t, func(t *testing.T, cat *catalog.Catalog) {
		delayed := false
		filter := func(u bridge.Update) bridge.Update {
			for _, receipt := range u.Batch.Receipts {
				if receipt.Requested == 5 && !delayed {
					delayed = true
					time.Sleep(1200 * time.Millisecond)
					break
				}
			}
			return u
		}
		base := newRigWithPolicy(t, cat, readyCharacter(), allSources(), filter, parityPolicy())
		r := &parityRig{rig: base, engine: "go", char: readyCharacter(), owner: base.port.Port.(*worldbridge.Client).Owner()}
		target := r.actor(30001)
		r.p.Send(r.intent(1, 11, r.ref(target.ID, 10)))
		r.outcome(1, wire.StageDone, wire.ReasonNone, wire.DeliveryBackend, 1)
		r.causalOrder(1, "speech")
		if !delayed {
			t.Fatal("test did not delay asynchronous evidence")
		}
	})
}

func TestParityLifeOrderDistinguishesHarmFromDeparture(t *testing.T) {
	makeFact := func(kind string, id int32, seq uint64) wire.Record {
		t.Helper()
		f, err := meaning.EventRecord(meaning.Event{Kind: kind, ID: id}, meaning.FactContext{Seq: seq, Epoch: 1, Ref: meaning.Ref{Gen: 1}})
		if err != nil {
			t.Fatal(err)
		}
		return f
	}
	struck := makeFact(meaning.Struck, 10, 1)
	appeared := makeFact(meaning.Appeared, 20, 2)
	gone, next, err := parityLifeOrder([]wire.Record{struck, appeared}, 10, 20)
	if err != nil || gone != -1 || next != 1 {
		t.Fatalf("harm became a departure: %d/%d/%v", gone, next, err)
	}
	gone, next, err = parityLifeOrder([]wire.Record{struck, makeFact(meaning.Gone, 10, 2), appeared}, 10, 20)
	if err != nil || gone != 1 || next != 2 {
		t.Fatalf("actual lifecycle order: %d/%d/%v", gone, next, err)
	}
	gone, next, err = parityLifeOrder([]wire.Record{makeFact(meaning.Appeared, 20, 1), makeFact(meaning.Gone, 10, 2), makeFact(meaning.Appeared, 20, 3)}, 10, 20)
	if err != nil || gone != 1 || next != 0 {
		t.Fatalf("later refresh concealed premature appearance: %d/%d/%v", gone, next, err)
	}

}
