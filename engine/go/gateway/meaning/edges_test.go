// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"bytes"
	"errors"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func TestUnknownKindAndEssentialIntentAcrossTransports(t *testing.T) {
	c := codec(t)
	raw, e := wire.New(testConfig().Wire, Registry(testConfig()))
	if e != nil {
		t.Fatal(e)
	}
	fact := wire.Record{Type: wire.Fact, Fields: []wire.Field{wire.U(1, true, 99), wire.U(2, true, 1), wire.S(3, true, 0), wire.U(4, true, 1), wire.Data(5, true, "skills"), wire.U(6, true, 0)}}
	for _, stream := range []bool{false, true} {
		var buf bytes.Buffer
		var records []wire.Record
		var err error
		if stream {
			err = raw.Write(&buf, []wire.Record{fact})
			if err == nil {
				records, err = c.Read(&buf)
			}
		} else {
			var b []byte
			b, err = raw.Encode([]wire.Record{fact})
			if err == nil {
				records, err = c.Decode(b)
			}
		}
		if err != nil || len(records) != 0 {
			t.Fatal(records, err)
		}
	}
	fact.Fields[5] = wire.U(6, true, 1)
	b, e := raw.Encode([]wire.Record{fact})
	if e != nil {
		t.Fatal(e)
	}
	records, e := c.Decode(b)
	if e != nil || len(records) != 1 || !records[0].Unsupported {
		t.Fatal(records, e)
	}
	b = []byte{10, 9, 24, 1, 40, 1, 56, 1, 184, 12, 0}
	records, e = c.Decode(b)
	if e != nil {
		t.Fatal(e)
	}
	if _, reason := PlanIntent(records[0], Environment{Alive: true}); reason != wire.ReasonUnknownEssential {
		t.Fatal(reason)
	}
}
func TestDepthListAndWindowCaps(t *testing.T) {
	config := testConfig()
	shallow := config
	shallow.Wire.Depth = 2
	c, e := NewCodec(shallow)
	if e != nil {
		t.Fatal(e)
	}
	r, e := EventRecord(Event{Kind: Spoke, ID: 7, Lines: []Line{{Label: "Go"}}}, FactContext{Seq: 1, Epoch: 1, Dialog: 1, Options: []Option{{1, "Go"}}})
	if e != nil {
		t.Fatal(e)
	}
	if _, e = c.Encode([]wire.Record{r}); !errors.Is(e, wire.ErrFrame) {
		t.Fatal("depth cap", e)
	}
	config.Windows = 2
	p, e := NewProposals(config)
	if e != nil {
		t.Fatal(e)
	}
	var first uint64
	for i := int32(1); i <= 3; i++ {
		ev := Event{Kind: Shop, List: i, Goods: []Good{{ID: 30, Count: 1, Price: 1}}}
		ctx, e := p.Capture(ev, Ref{}, true, 0)
		if e != nil {
			t.Fatal(e)
		}
		if i == 1 {
			first = ctx.Offers[0]
		}
	}
	if len(p.windows) != 2 {
		t.Fatal("window cap")
	}
	if _, reason := p.trade(Shop, 1, first, 1, 1, 0); reason != wire.ReasonStaleOffer {
		t.Fatal("evicted window usable")
	}
	tooMany := Event{Kind: Shop, Goods: make([]Good, config.Wire.ListItems+1)}
	if _, e = p.Capture(tooMany, Ref{}, true, 0); e != ErrLimit {
		t.Fatal(e)
	}
	config.WindowBytes = 1
	p, _ = NewProposals(config)
	if _, e = p.Capture(Event{Kind: Shop}, Ref{}, true, 0); e != ErrLimit {
		t.Fatal("window bytes", e)
	}
}
func TestSemanticRefusalDoesNotPoisonValidFrame(t *testing.T) {
	c := codec(t)
	first, e := CommandRecord(Command{Kind: Walk, X: 1, Y: 2, Z: 3}, CommandContext{N: 1, Control: 1})
	if e != nil {
		t.Fatal(e)
	}
	first.Fields = append(first.Fields, wire.Obj(10, true, refValue(Ref{7, 1}).Fields))
	second, e := CommandRecord(Command{Kind: Walk, X: 4, Y: 5, Z: 6}, CommandContext{N: 2, Control: 1})
	if e != nil {
		t.Fatal(e)
	}
	b, e := c.Encode([]wire.Record{first, second})
	if e != nil {
		t.Fatal(e)
	}
	records, e := c.Decode(b)
	if e != nil || len(records) != 2 {
		t.Fatal(records, e)
	}
	if _, reason := PlanIntent(records[0], Environment{Alive: true}); reason != wire.ReasonInvalid {
		t.Fatal(reason)
	}
	if plan, reason := PlanIntent(records[1], Environment{Alive: true}); reason != wire.ReasonNone || plan.Command != (Command{Kind: Walk, X: 4, Y: 5, Z: 6}) {
		t.Fatal(plan, reason)
	}
}
