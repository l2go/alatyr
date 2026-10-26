// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"github.com/l2go/alatyr/engine/go/gateway"
	"testing"
)

func TestSnapshotRetiresMotionOnRepeatedAppearance(t *testing.T) {
	for _, kind := range []string{Moved, Stopped, Corrected} {
		for _, same := range []bool{false, true} {
			name := "later"
			if same {
				name = "same-millisecond"
			}
			t.Run(kind+"/"+name, func(t *testing.T) {
				s := newState(t, testConfig(), Policy{})
				events := []Event{{Kind: Appeared, ID: 7, X: 1}, {Kind: kind, ID: 7, X: 2, ToX: 20}, {Kind: Appeared, ID: 7, X: 3}}
				for i, e := range events {
					if !same {
						e.MS = int64(i)
					}
					if _, err := s.Apply(gateway.Update{Value: Input{Event: e, ReceivedAt: 2}}, 2); err != nil {
						t.Fatal(err)
					}
				}
				snapshot, err := s.Snapshot(2)
				if err != nil {
					t.Fatal(err)
				}
				seen := false
				for _, r := range snapshot.Facts {
					d, err := EventValue(r)
					if err != nil {
						t.Fatal(err)
					}
					if d.Event.ID != 7 {
						continue
					}
					if d.Event.Kind == Moved || d.Event.Kind == Stopped || d.Event.Kind == Corrected {
						t.Fatal("snapshot replayed superseded motion")
					}
					if d.Event.Kind == Appeared {
						seen = true
						if d.Event.X != 3 {
							t.Fatalf("position got %d, want 3", d.Event.X)
						}
					}
				}
				if !seen {
					t.Fatal("fresh actor missing")
				}
			})
		}
	}
}
