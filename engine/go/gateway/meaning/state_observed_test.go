// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
)

func TestStateObservedTracksLatestPosition(t *testing.T) {
	for _, kind := range []string{Moved, Stopped, Corrected} {
		for _, tc := range []struct {
			name string
			ms   [3]int64
		}{
			{"increasing-time", [3]int64{1, 2, 3}},
			{"same-millisecond", [3]int64{7, 7, 7}},
		} {
			t.Run(kind+"/"+tc.name, func(t *testing.T) {
				s := newState(t, testConfig(), Policy{})
				if got, ok := s.Observed(7); ok || got != (Observed{}) {
					t.Fatalf("before appearance: got (%+v, %t), want (zero, false)", got, ok)
				}
				steps := []struct {
					event Event
					want  Observed
				}{
					{Event{Kind: Appeared, ID: 7, Role: "opaque", X: 10, Y: 20, Z: 30}, Observed{Role: "opaque", X: 10, Y: 20, Z: 30}},
					{Event{Kind: kind, ID: 7, X: 40, Y: 50, Z: 60}, Observed{Role: "opaque", X: 40, Y: 50, Z: 60}},
					{Event{Kind: Appeared, ID: 7, Role: RoleUnknown, X: -10, Y: -20, Z: -30}, Observed{Role: RoleUnknown, X: -10, Y: -20, Z: -30}},
				}
				var ref Ref
				for i, step := range steps {
					step.event.MS = tc.ms[i]
					_, err := s.Apply(gateway.Update{Value: Input{Event: step.event, ReceivedAt: tc.ms[i]}}, tc.ms[i])
					if err != nil {
						t.Fatalf("apply step %d (%s): %v", i, step.event.Kind, err)
					}
					got, ok := s.Observed(7)
					if i == 0 {
						ref = got.Ref
						if ref.ID != 7 || ref.Gen == 0 {
							t.Fatalf("first reference: got %+v, want id 7 and a live generation", ref)
						}
					}
					step.want.Ref = ref
					if !ok || got != step.want {
						t.Fatalf("step %d (%s): got (%+v, %t), want (%+v, true)", i, step.event.Kind, got, ok, step.want)
					}
					got.X++
					got.Ref.Gen++
					if again, ok := s.Observed(7); !ok || again != step.want {
						t.Fatalf("caller mutation: got (%+v, %t), want (%+v, true)", again, ok, step.want)
					}
				}
				if _, err := s.Apply(gateway.Update{Value: Input{Event: Event{Kind: Gone, ID: 7, MS: tc.ms[2]}, ReceivedAt: tc.ms[2]}}, tc.ms[2]); err != nil {
					t.Fatal(err)
				}
				if got, ok := s.Observed(7); ok || got != (Observed{}) {
					t.Fatalf("after departure: got (%+v, %t), want (zero, false)", got, ok)
				}
			})
		}
	}
}
