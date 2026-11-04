// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type directScheduleWorld struct{ *scheduleWorld }

func (*directScheduleWorld) Notify() <-chan struct{} { return nil }
func (*directScheduleWorld) Receive() (Update, bool) { return Update{}, false }
func (*directScheduleWorld) Capacity() int           { return 8 }

type sharedFenceState struct{ *testState }

func (*sharedFenceState) Snapshot(int64) (Snapshot, error) {
	r := closeFact()
	set(&r, 1, wire.U(1, true, 20))
	return Snapshot{Ready: true, Shared: true, Facts: []wire.Record{r}}, nil
}
func (*sharedFenceState) Apply(Update, int64) (Observation, error) {
	return Observation{Facts: []wire.Record{closeFact(), closeFact()}}, nil
}

func TestSharedCheckpointFencesInFlightFactsForEveryConnection(t *testing.T) {
	r, live, _ := newFencedPeers(t)
	r.game.gate.cfg.Codec = closeTestCodec(t)
	r.game.state = &sharedFenceState{&testState{}}
	r.game.world = &directScheduleWorld{r.world}
	r.conn.queue, r.conn.snapshot = nil, nil
	r.conn.bytes, r.conn.active = 0, r.game.gate.cfg.OutBytes
	if !r.game.observe(Update{}) {
		t.Fatal("observation failed")
	}
	for _, c := range []*connection{r.conn, live} {
		if len(c.queue) != 0 {
			t.Fatal("obsolete facts escaped after the newer checkpoint")
		}
		facts := 0
		for _, frame := range c.snapshot {
			rs, err := r.game.gate.cfg.Codec.Decode(frame)
			if err != nil {
				t.Fatal(err)
			}
			for _, record := range rs {
				if record.Type == wire.Fact {
					facts++
					if uintField(record, 1) != 20 {
						t.Fatal("checkpoint contains obsolete in-flight state")
					}
				}
			}
		}
		if facts != 1 {
			t.Fatalf("checkpoint facts=%d, want 1", facts)
		}
	}
}

type replenishedWorld struct {
	*scheduleWorld
	calls int
}

func (*replenishedWorld) Notify() <-chan struct{}   { return nil }
func (w *replenishedWorld) Receive() (Update, bool) { w.calls++; return Update{}, true }
func (*replenishedWorld) Capacity() int             { return 3 }

func TestContinuousPublicationsCannotStarveExpiry(t *testing.T) {
	r := scheduleNewRig(t)
	w := &replenishedWorld{scheduleWorld: r.world}
	r.game.world, r.game.state = w, &testState{}
	r.conn.ready, r.conn.transfer = true, false
	_, control := r.game.attempts.Controller()
	r.game.intent(r.conn, testIntent(2, 4, control))

	// Use the already prepared local test action as a contacted attempt that
	// has not been marked Sent. Expire still owns its original deadline.
	if err := r.game.attempts.Contact(2, r.game.gate.now()); err != nil {
		t.Fatal(err)
	}
	r.now = r.now.Add(time.Duration(r.game.gate.cfg.Attempts.EvidenceMS) * time.Millisecond)
	w.calls = 0
	if r.game.drainWorld() {
		r.game.expire()
	}
	e, _ := r.game.attempts.Lookup(2)
	if w.calls != w.Capacity() || !e.Terminal || e.Result.Stage != wire.StageUnknown {
		t.Fatalf("polls=%d entry=%+v", w.calls, e)
	}
}
