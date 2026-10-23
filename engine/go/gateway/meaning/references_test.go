// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import "testing"

func TestReferencesRefreshRemovalAndBoundedChurn(t *testing.T) {
	o, e := NewObservations(2)
	if e != nil {
		t.Fatal(e)
	}
	first, _, e := o.Apply(Event{Kind: Appeared, ID: 7})
	if e != nil {
		t.Fatal(e)
	}
	refresh, _, _ := o.Apply(Event{Kind: Appeared, ID: 7})
	if first != refresh {
		t.Fatal("refresh changed generation")
	}
	for i := 0; i < 10000; i++ {
		o.Apply(Event{Kind: Gone, ID: 7})
		if _, _, e = o.Apply(Event{Kind: Appeared, ID: 7}); e != nil {
			t.Fatal(e)
		}
	}
	if o.Valid(first) || o.Len() != 1 {
		t.Fatal("recycled observation reused")
	}
	o.Apply(Event{Kind: Dropped, ID: 9})
	_, item, e := o.Apply(Event{Kind: Took, ID: 7, Target: 9})
	if e != nil || item.Gen == 0 || o.Valid(item) {
		t.Fatal("departing reference lost or retained", item, e)
	}
	o.Reset()
	if _, _, e = o.Apply(Event{Kind: Appeared, ID: 1}); e != nil {
		t.Fatal(e)
	}
	o.Apply(Event{Kind: Appeared, ID: 2})
	if _, _, e = o.Apply(Event{Kind: Appeared, ID: 3}); e != ErrLimit {
		t.Fatal(e)
	}
}
