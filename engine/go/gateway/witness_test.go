// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway_test

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
)

func TestWitnessNamesAndValues(t *testing.T) {
	for _, tc := range []struct {
		name string
		got  gateway.Witness
		want gateway.Witness
	}{
		{"none", gateway.WitnessNone, 0},
		{"match", gateway.WitnessMatch, 1},
		{"candidate", gateway.WitnessCandidate, 2},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if tc.got != tc.want {
				t.Fatalf("witness = %d, want %d", tc.got, tc.want)
			}
		})
	}
}
