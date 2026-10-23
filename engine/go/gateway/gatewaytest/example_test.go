// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"fmt"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
)

func ExampleConfig() {
	c := gatewaytest.Config()
	fmt.Println(c.Attempts.Pending, c.Attempts.EvidenceMS)
	// Output: 8 5000
}
