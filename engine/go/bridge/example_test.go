// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge_test

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
)

// A proposal expires at the earlier of two bounds: its lifetime plus one
// minute from the original Open, and one minute after it was received.
func ExampleProposalExpiry() {
	origin := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	received := origin.Add(5 * time.Minute)
	expiry, ok := bridge.ProposalExpiry(origin, received, 60000)
	fmt.Println(ok, expiry.Sub(origin))
	// Output: true 2m0s
}
