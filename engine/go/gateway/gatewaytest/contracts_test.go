// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"context"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/fixture"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

type rig struct {
	t       *testing.T
	c       meaning.Config
	g       *gateway.Gateway
	p       *gatewaytest.Peer
	peers   []*gatewaytest.Peer
	worlds  []*fixture.World
	origin  time.Time
	current []meaning.Input
}

func initial() []meaning.Input {
	es := []meaning.Event{
		{Kind: meaning.Appeared, ID: 1, Mine: true, Role: meaning.RolePlayer},
		{Kind: meaning.Appeared, ID: 2, Role: "speaker"},
		{Kind: meaning.Dropped, ID: 9, Role: meaning.RoleDrop},
		{Kind: meaning.Vitals, ID: 1, Stats: &meaning.Stats{HP: meaning.Number(100), MaxHP: meaning.Number(100), Level: meaning.Number(1)}},
		{Kind: meaning.Carried, Goods: []meaning.Good{}, Coin: meaning.Number(100)},
		{Kind: meaning.Known, Complete: true, Skills: []meaning.Skill{}},
		{Kind: meaning.Quests, Complete: true, Quests: []meaning.QuestState{}},
	}
	out := []meaning.Input{}
	for _, e := range es {
		out = append(out, meaning.Input{Event: e})
	}
	return append(out, meaning.Input{Complete: []string{"objects", "self", "inventory", "skills", "quests", "dialogue", "trade"}})
}
func runBoth(t *testing.T, f func(*rig), _ bool) {
	t.Helper()
	synctest.Test(t, func(t *testing.T) {
		r := &rig{t: t, c: gatewaytest.Config(), origin: time.Now()}
		r.g = gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) {
			w, err := fixture.New(fixture.Config{Meaning: r.c, Origin: r.origin, Initial: append(initial(), r.current...), Policy: meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
				return meaning.Capabilities{Talk: o.Role == "speaker", Pickup: o.Role == meaning.RoleDrop}
			}}})
			if err != nil {
				return nil, nil, err
			}
			r.worlds = append(r.worlds, w)
			return w, w.Projection(), nil
		}, r.origin)
		r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
		r.peers = append(r.peers, r.p)
		f(r)
	})
}
func (r *rig) sync() {
	synctest.Wait()
	for _, p := range r.peers {
		p.Drain()
	}
	synctest.Wait()
	for _, p := range r.peers {
		p.Drain()
	}
}
func (r *rig) emit(e meaning.Event) {
	r.current = append(r.current, meaning.Input{Event: e})
	for _, w := range r.worlds {
		if err := w.Emit(r.t.Context(), meaning.Input{Event: e}); err != nil {
			r.t.Fatal(err)
		}
	}
	r.sync()
}
func (r *rig) intent(n, verb uint64, fields ...wire.Field) wire.Record {
	return wire.Record{Type: wire.Intent, Fields: append([]wire.Field{wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, r.p.Control)}, fields...)}
}
func (r *rig) ref(id int32, field uint32) wire.Field {
	for i := len(r.p.Records) - 1; i >= 0; i-- {
		rec := r.p.Records[i]
		if rec.Type != wire.Fact {
			continue
		}
		e, err := meaning.EventValue(rec)
		if err != nil {
			r.t.Fatal(err)
		}
		if e.Event.ID == id {
			return wire.Obj(field, true, []wire.Field{wire.S(1, true, int64(id)), wire.U(2, true, e.Context.Ref.Gen)})
		}
	}
	r.t.Fatal("missing reference")
	return wire.Field{}
}

func TestTypedCommitCompletesAttemptWithoutAnInferredWitness(t *testing.T) {
	runBoth(t, func(r *rig) {
		r.p.Send(r.intent(1, 5, r.ref(2, 10)))
		r.p.Result(1, wire.StageSent)
		if err := r.worlds[0].Resolve(r.t.Context(), 1, wire.StageDone, wire.ReasonNone); err != nil {
			r.t.Fatal(err)
		}
		r.p.Result(1, wire.StageDone)
		original := r.intent(2, 1, wire.At(11, true, 10, 20, 30))
		r.p.Send(original)
		r.p.Result(2, wire.StageSent)
		before := len(r.worlds[0].Writes())
		r.p.Send(original)
		r.sync()
		if len(r.worlds[0].Writes()) != before {
			r.t.Fatal("duplicate reexecuted a command")
		}
	}, false)
}
func TestWorldRefusalPreservesAbsenceOfEffects(t *testing.T) {
	runBoth(t, func(r *rig) {
		r.p.Send(r.intent(1, 5, r.ref(2, 10)))
		r.p.Result(1, wire.StageSent)
		if err := r.worlds[0].Resolve(r.t.Context(), 1, wire.StageRefused, wire.ReasonWorld); err != nil {
			r.t.Fatal(err)
		}
		result := r.p.Result(1, wire.StageRefused)
		if gatewaytest.U(result, 7) != uint64(wire.DeliveryNone) {
			r.t.Fatal("refusal implied effects")
		}
	}, false)
}
func TestMalformedFrameCannotExecuteAPrefix(t *testing.T) {
	runBoth(t, func(r *rig) {
		codec, err := meaning.NewCodec(r.c)
		if err != nil {
			r.t.Fatal(err)
		}
		b, err := codec.Encode([]wire.Record{r.intent(1, 1, wire.At(11, true, 1, 2, 3)), r.intent(2, 1, wire.At(11, true, 4, 5, 6))})
		if err != nil {
			r.t.Fatal(err)
		}
		r.p.Raw(b[:len(b)-1])
		r.sync()
		for _, w := range r.worlds[0].Writes() {
			if w.N != 0 {
				r.t.Fatal("malformed frame executed a participant command")
			}
		}
	}, false)
}
