// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
)

// projectionSteps changes every input of an actor's snapshot facts: its
// entries, the references of it and its target as they are renewed, and the
// width of the death time.
func projectionSteps() []Event {
	steps := []Event{{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer, Name: "self"}, {Kind: Vitals, ID: 1, Stats: &Stats{HP: Number(90), MaxHP: Number(100)}}}
	for id := int32(2); id <= 6; id++ {
		steps = append(steps, Event{Kind: Appeared, ID: id, Role: "creature", Name: "wolf", Stats: &Stats{HP: Number(int64(id))}})
	}
	return append(steps,
		Event{Kind: Moved, ID: 5, X: 1, ToX: 50, Speed: 120},
		Event{Kind: Targeted, ID: 1, Target: 5},
		Event{Kind: Moved, ID: 6, X: 2, ToX: 60, Speed: 120, Target: 5},
		Event{Kind: Vitals, ID: 5, Stats: &Stats{HP: Number(1)}},
		Event{Kind: Died, ID: 5},
		Event{Kind: Stopped, ID: 6, X: 30},
		// A renewed observation of the target changes the follower's facts.
		Event{Kind: Gone, ID: 5},
		Event{Kind: Appeared, ID: 5, Role: "creature", Name: "boar"},
		Event{Kind: Dropped, ID: 12, Role: RoleDrop, X: 5},
		Event{Kind: Took, ID: 1, Target: 12},
		Event{Kind: Carried, Goods: []Good{{Object: 100, ID: 57, Count: 10}}, Coin: Number(10)},
		Event{Kind: Known, Complete: true, Skills: []Skill{{ID: 3, Level: 2}}},
		Event{Kind: Quest, ID: 4, Amount: 1, Name: "first", Text: "started"},
		Event{Kind: Died, ID: 6},
		Event{Kind: Revived, ID: 6},
		Event{Kind: Died, ID: 4},
		Event{Kind: Gone, ID: 3},
		Event{Kind: Corrected, ID: 2, X: 7, ToX: 7},
	)
}

func applyAt(s *State, e Event, now int64) error {
	e.MS = now
	_, err := s.Apply(gateway.Update{Value: Input{Event: e, ReceivedAt: now}}, now)
	return err
}

func TestUpdateProjectionCheckMatchesSnapshot(t *testing.T) {
	s := newState(t, testConfig(), Policy{})
	type step struct {
		e   Event
		now int64
	}
	var steps []step
	for i, e := range projectionSteps() {
		steps = append(steps, step{e, int64(i)})
	}
	// Dead actors stay untouched while the death time grows past one and two
	// varint bytes, then the projection is invalidated and rebuilt.
	for _, now := range []int64{63, 64, 64, 8191, 8192, 1 << 20} {
		steps = append(steps, step{Event{Kind: Moved, ID: 1, X: int32(now), ToX: int32(now + 10), Speed: 100}, now})
	}
	steps = append(steps, step{Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer}, 1 << 21}, step{Event{Kind: Appeared, ID: 4, Role: "creature"}, 1 << 21})
	for i, st := range steps {
		if i == len(steps)-2 {
			s.Invalidate()
		}
		if err := applyAt(s, st.e, st.now); err != nil {
			t.Fatalf("step %d %s: %v", i, st.e.Kind, err)
		}
		checked := s.projectionBytes
		if _, err := s.Snapshot(st.now); err != nil {
			t.Fatal(err)
		}
		if checked != s.projectionBytes {
			t.Fatalf("step %d %s at %d: update counted %d bytes, snapshot %d", i, st.e.Kind, st.now, checked, s.projectionBytes)
		}
	}
}

func TestUpdateFailsExactlyWhenSnapshotWouldExceed(t *testing.T) {
	steps := projectionSteps()
	measure := newState(t, testConfig(), Policy{})
	sizes := make([]int, len(steps))
	for i, e := range steps {
		if err := applyAt(measure, e, 64); err != nil {
			t.Fatal(err)
		}
		if _, err := measure.Snapshot(64); err != nil {
			t.Fatal(err)
		}
		sizes[i] = measure.projectionBytes
	}
	for _, field := range []int{25, 29} {
		c := testConfig()
		limit := sizes[len(sizes)/2]
		c.Advertised[field] = uint64(limit)
		s := newState(t, c, Policy{})
		for i, e := range steps {
			err := applyAt(s, e, 64)
			if sizes[i] <= limit && err != nil {
				t.Fatalf("field %d step %d: %d bytes within %d failed: %v", field+1, i, sizes[i], limit, err)
			}
			if sizes[i] > limit {
				if !errors.Is(err, ErrLimit) {
					t.Fatalf("field %d step %d: %d bytes over %d got %v", field+1, i, sizes[i], limit, err)
				}
				break
			}
		}
	}
}

// One update re-encoded the whole projection, so its cost grew with every
// visible actor and a crowded scene allocated gigabytes per second.
func TestUpdateCostDoesNotGrowWithVisibleActors(t *testing.T) {
	allocs := func(actors int) float64 {
		c := testConfig()
		c.Objects = 128
		s := newState(t, c, Policy{})
		if err := applyAt(s, Event{Kind: Appeared, ID: 1, Mine: true, Role: RolePlayer}, 1); err != nil {
			t.Fatal(err)
		}
		for id := int32(2); id <= int32(actors); id++ {
			if err := applyAt(s, Event{Kind: Appeared, ID: id, Role: "creature", Name: "wolf"}, 1); err != nil {
				t.Fatal(err)
			}
		}
		x := int32(0)
		return testing.AllocsPerRun(50, func() {
			x++
			if err := applyAt(s, Event{Kind: Moved, ID: 2, X: x, ToX: x + 10, Speed: 100}, 1); err != nil {
				t.Fatal(err)
			}
		})
	}
	few, many := allocs(2), allocs(100)
	if many > few+10 {
		t.Fatalf("one update allocated %.0f times with 100 visible actors, %.0f with 2", many, few)
	}
}
