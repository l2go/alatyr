// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend

import (
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestEmptyDialogueNeedsNoProposalBirth(t *testing.T) {
	for _, tc := range []struct {
		name, kind string
		lines      []meaning.Line
		valid      bool
	}{
		{"known-empty", meaning.Spoke, []meaning.Line{}, true},
		{"unknown-actions", meaning.Spoke, nil, false},
		{"saved-choice", meaning.Spoke, []meaning.Line{{Label: "choice", Say: "saved"}}, false},
		{"shop", meaning.Shop, nil, false}, {"offer", meaning.Offer, nil, false}, {"practice", meaning.Practice, nil, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, w, _, _ := setup(t)
			in, err := w.state.eventInput(wire.Record{Type: wire.Fact}, meaning.Event{Kind: tc.kind, Lines: tc.lines}, 100)
			if tc.valid {
				if err != nil || in.ProposalBorn != nil {
					t.Fatalf("read-only page: got %+v/%v, want no birth", in, err)
				}
			} else if !errors.Is(err, wire.ErrFrame) {
				t.Fatalf("missing proposal birth: got %v, want frame error", err)
			}
		})
	}
}
