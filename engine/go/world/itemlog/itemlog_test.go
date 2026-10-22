// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package itemlog

import (
	"strings"
	"testing"
	"time"
)

func TestSyntheticJournal(t *testing.T) {
	var b strings.Builder
	now := func() time.Time { return time.Date(2026, 1, 2, 3, 4, 5, 6000000, time.FixedZone("test", 3600)) }
	l := New(&b, now)
	l.Write(Entry{Op: Create, Process: "Loot", ObjectID: 7, Item: "Seed Token", Count: 2, Player: "Walker", Ref: Null})
	want := "02 Jan 02:04:05,006 [CREATE:Loot, item 7: Seed Token(2), player Walker, null]\r\n"
	if b.String() != want {
		t.Fatalf("%q", b.String())
	}
	l.Write(Entry{Op: Change, Process: "Consume"})
	Discard.Write(Entry{Op: Create})
	if b.String() != want {
		t.Fatal("discard or consume logged")
	}
}
