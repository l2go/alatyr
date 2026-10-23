// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package itemlog_test

import (
	"fmt"
	"time"

	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

// A journal line is stamped in UTC with millisecond precision and ends in CRLF.
func ExampleLine() {
	at := time.Date(2026, time.March, 5, 14, 7, 9, 250_000_000, time.UTC)
	line := itemlog.Line(at, itemlog.Entry{Op: itemlog.Create, Process: "Loot", ObjectID: 7, Item: "Seed Token", Count: 2, Player: "Walker", Ref: itemlog.Null})
	fmt.Printf("%q\n", line)
	// Output: "05 Mar 14:07:09,250 [CREATE:Loot, item 7: Seed Token(2), player Walker, null]\r\n"
}
