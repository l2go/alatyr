// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func cursorTest(t *testing.T) *Cursor {
	t.Helper()
	c, err := NewCursor(testConfig())
	if err != nil {
		t.Fatal(err)
	}
	return c
}
func TestCursorRetiresSourceAndObservationLifetimes(t *testing.T) {
	c := cursorTest(t)
	view := View{Incarnations: map[int32]uint64{2: 10}, Events: []Input{{Event: Event{Kind: Appeared, ID: 2, Role: "creature"}}}, Ready: true}
	if _, err := c.Capture(view, 0); err != nil {
		t.Fatal(err)
	}
	old := c.ref(2)
	if _, err := c.Capture(view, 1); err != nil {
		t.Fatal(err)
	}
	if c.ref(2) != old {
		t.Fatal("unchanged source changed a reference lifetime")
	}
	// A capture cannot grant action authority to a replacement incarnation until
	// its checkpoint has published the new reference generation.
	view.Incarnations[2] = 11
	if _, ok := c.Environment(view, 1, nil).Observed.Lookup(2); ok {
		t.Fatal("unpublished incarnation acquired action authority")
	}
	if _, err := c.Capture(view, 2); err != nil {
		t.Fatal(err)
	}
	replacement := c.ref(2)
	if replacement.Gen <= old.Gen {
		t.Fatal("source replacement reused a reference")
	}
	if _, err := c.Capture(View{Incarnations: map[int32]uint64{}}, 3); err != nil {
		t.Fatal(err)
	}
	if _, err := c.Capture(view, 4); err != nil {
		t.Fatal(err)
	}
	if c.ref(2).Gen <= replacement.Gen {
		t.Fatal("re-entry reused a retired reference")
	}
	c.Reset()
	if _, err := c.Capture(view, 5); err != nil {
		t.Fatal(err)
	}
	if c.ref(2).Gen <= replacement.Gen {
		t.Fatal("resynchronization reset the generation counter")
	}
}

func TestCursorSnapshotCannotRenewProposalAuthority(t *testing.T) {
	c := cursorTest(t)
	actor := Input{Event: Event{Kind: Appeared, ID: 2, Role: "speaker"}}
	born := int64(10)
	spoke := Input{Token: 1, Event: Event{Kind: Spoke, ID: 2, Text: "Choose", Lines: []Line{{Label: "Lesson", Say: "saved-action"}}}, ReceivedAt: 10, ProposalBorn: &born}
	view := View{Incarnations: map[int32]uint64{2: 1}, Events: []Input{actor, spoke}, Ready: true}
	records, err := c.Capture(view, 10)
	if err != nil {
		t.Fatal(err)
	}
	first, err := EventValue(records[1])
	if err != nil {
		t.Fatal(err)
	}
	records, err = c.Capture(view, 500)
	if err != nil {
		t.Fatal(err)
	}
	again, err := EventValue(records[1])
	if err != nil {
		t.Fatal(err)
	}
	if first.Context.Dialog != again.Context.Dialog {
		t.Fatal("unchanged checkpoint reissued a proposal handle")
	}
	if _, _, reason := c.proposals.Choose(first.Context.Dialog, 1, 1009); reason != wire.ReasonNone {
		t.Fatalf("original authority expired early: %v", reason)
	}
	records, err = c.Capture(view, 1010)
	if err != nil {
		t.Fatal(err)
	}
	if _, _, reason := c.proposals.Choose(first.Context.Dialog, 1, 1010); reason != wire.ReasonStaleOffer {
		t.Fatal("checkpoint renewed the original proposal")
	}
	expired, err := EventValue(records[1])
	if err != nil {
		t.Fatal(err)
	}
	if len(expired.Context.Options) != 0 {
		t.Fatal("expired source retained executable options")
	}
}

func TestCursorProposalMetadataStaysWithinWindowBudget(t *testing.T) {
	c := cursorTest(t)
	if _, err := c.Record(Input{Event: Event{Kind: Appeared, ID: 2, Role: "speaker"}}, 1, 0); err != nil {
		t.Fatal(err)
	}
	for i := 1; i <= 32; i++ {
		in := Input{Token: uint64(i), Event: Event{Kind: Shop, ID: 2, List: int32(i), Goods: []Good{}, Complete: true}, ReceivedAt: int64(i)}
		if _, err := c.Record(in, 0, int64(i)); err != nil {
			t.Fatal(err)
		}
		if len(c.saved) > c.config.Windows || len(c.proposals.windows) > c.config.Windows {
			t.Fatal("proposal metadata exceeded the advertised window budget")
		}
	}
}

func TestCursorDepartingFactUsesTheRetiredReference(t *testing.T) {
	c := cursorTest(t)
	if _, err := c.Record(Input{Event: Event{Kind: Appeared, ID: 2, Role: "creature"}}, 1, 0); err != nil {
		t.Fatal(err)
	}
	old := c.ref(2)
	r, err := c.Record(Input{Event: Event{Kind: Gone, ID: 2}}, 0, 1)
	if err != nil {
		t.Fatal(err)
	}
	e, err := EventValue(r)
	if err != nil {
		t.Fatal(err)
	}
	if e.Context.Ref != old || c.ref(2).Gen != 0 {
		t.Fatal("departure lost its reference or left it usable")
	}
}
