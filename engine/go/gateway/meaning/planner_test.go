// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"bytes"
	"errors"
	"math"
	"reflect"
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func classifyTest(o Observed) Capabilities {
	// Synthetic IDs are explicit test policy, never a production role lookup.
	return Capabilities{Strike: o.Ref.ID == 8, Talk: o.Ref.ID == 7, Pickup: o.Ref.ID == 9, Cast: o.Ref.ID == 7 || o.Ref.ID == 8}
}
func observations(t *testing.T) *Observations {
	t.Helper()
	o, err := NewObservations(8)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range []Event{
		{Kind: Appeared, ID: 1, Role: RolePlayer, X: 10, Y: 20, Z: 30, Mine: true},
		{Kind: Appeared, ID: 7, Role: "opaque", X: 100, Y: 200, Z: 300},
		{Kind: Appeared, ID: 8, Role: "opaque", X: 400, Y: 500, Z: 600},
		{Kind: Dropped, ID: 9, Role: RoleDrop, X: 11, Y: 22, Z: 33},
	} {
		if _, _, err := o.Apply(e); err != nil {
			t.Fatal(err)
		}
	}
	return o
}

func TestSavedProposalsResolveOnlyCapturedActions(t *testing.T) {
	o := observations(t)
	p, err := NewProposals(testConfig())
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		event Event
		want  Command
	}{
		{Event{Kind: Spoke, ID: 7, Lines: []Line{{Label: "Continue", Say: "saved-action"}}}, Command{Kind: Say, Target: 7, Text: "saved-action"}},
		{Event{Kind: Shop, List: 5, Goods: []Good{{ID: 30, Count: 10, Price: 30}}}, Command{Kind: Buy, List: 5, Item: 30, Count: 1}},
		{Event{Kind: Offer, List: 6, Goods: []Good{{Object: 101, ID: 30, Count: 10, Price: 20}}}, Command{Kind: Sell, List: 6, Target: 101, Item: 30, Count: 1}},
		{Event{Kind: Practice, List: 2, Goods: []Good{{ID: 3, Count: 2, Object: 3, Price: 40}}, Complete: true}, Command{Kind: Learn, List: 2, Item: 3, Count: 2}},
	} {
		t.Run(tc.event.Kind, func(t *testing.T) {
			ctx, err := p.Capture(tc.event, o.Reference(7), true, 1)
			if err != nil {
				t.Fatal(err)
			}
			fact, err := EventRecord(tc.event, ctx)
			if err != nil {
				t.Fatal(err)
			}
			encoded, err := codec(t).Encode([]wire.Record{fact})
			if err != nil || bytes.Contains(encoded, []byte("saved-action")) {
				t.Fatal("saved action escaped", err)
			}
			commandContext := CommandContext{N: 1, Control: 1, Dialog: ctx.Dialog, Option: 1, List: tc.event.List}
			if len(ctx.Offers) > 0 {
				commandContext.Offer = ctx.Offers[0]
				commandContext.Price = tc.event.Goods[0].Price
			}
			r, err := CommandRecord(tc.want, commandContext)
			if err != nil {
				t.Fatal(err)
			}
			// Mutating source slices cannot change the captured text or price.
			if len(tc.event.Lines) > 0 {
				tc.event.Lines[0].Say = "replacement"
			}
			if len(tc.event.Goods) > 0 {
				tc.event.Goods[0].Price++
			}
			plan, reason := PlanIntent(r, Environment{Observed: o, Proposals: p, Alive: true, Now: 1})
			if reason != wire.ReasonNone || plan.Command != tc.want {
				t.Fatalf("plan = %#v, reason = %v, want %#v", plan.Command, reason, tc.want)
			}
			if _, err := p.Capture(tc.event, o.Reference(7), true, 2); err != nil {
				t.Fatal(err)
			}
			if _, reason := PlanIntent(r, Environment{Observed: o, Proposals: p, Alive: true, Now: 2}); reason != wire.ReasonStaleOffer {
				t.Fatal("replaced handle retargeted", reason)
			}
		})
	}
}

func TestTrustedClassificationAndSkillRefusals(t *testing.T) {
	o := observations(t)
	for _, kind := range []string{Talk, Strike, Take, Cast, Engage} {
		ctx := CommandContext{N: 1, Target: o.Reference(8), Item: o.Reference(9), SkillLevel: 1}
		target := int32(8)
		if kind == Take {
			target = 9
		}
		r, err := CommandRecord(Command{Kind: kind, Target: target, Item: 3}, ctx)
		if err != nil {
			t.Fatal(err)
		}
		if _, reason := PlanIntent(r, Environment{Observed: o, Alive: true}); reason != wire.ReasonNoAction {
			t.Fatalf("nil classifier granted %s: %v", kind, reason)
		}
	}
	r, err := CommandRecord(Command{Kind: Cast, Target: 9, Item: 3}, CommandContext{N: 1, Target: o.Reference(9), SkillLevel: 1})
	if err != nil {
		t.Fatal(err)
	}
	if _, reason := PlanIntent(r, Environment{Observed: o, Alive: true, Classify: classifyTest}); reason != wire.ReasonNoAction {
		t.Fatal("pickup became skill target", reason)
	}
	r, err = CommandRecord(Command{Kind: Cast, Target: 8, Item: 3}, CommandContext{N: 1, Target: o.Reference(8), SkillLevel: 1})
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		skill  Skill
		reason wire.Reason
	}{
		{Skill{}, wire.ReasonSkillUnknown}, {Skill{ID: 3, Level: 2}, wire.ReasonSkillUnknown},
		{Skill{ID: 3, Level: 1, Passive: true}, wire.ReasonSkillPassive}, {Skill{ID: 3, Level: 1, Disabled: true}, wire.ReasonSkillDisabled},
	} {
		env := Environment{Observed: o, Alive: true, Classify: classifyTest, Known: map[int32]Skill{3: tc.skill}}
		if _, reason := PlanIntent(r, env); reason != tc.reason {
			t.Fatalf("skill refusal = %v, want %v", reason, tc.reason)
		}
	}
	old := o.Reference(8)
	if _, _, err := o.Apply(Event{Kind: Gone, ID: 8}); err != nil {
		t.Fatal(err)
	}
	if _, _, err := o.Apply(Event{Kind: Appeared, ID: 8}); err != nil {
		t.Fatal(err)
	}
	if o.Valid(old) {
		t.Fatal("recycled target lifetime accepted")
	}
}

func TestNeutralStrategyUsesOneEffectAndOwnsMetadata(t *testing.T) {
	steps, err := PlanSteps(Plan{Command: Command{Kind: Take, Target: 9}})
	if err != nil || len(steps) != 1 {
		t.Fatal("pickup is not one effect", steps, err)
	}
	op, err := OperationValue(steps[0])
	if err != nil || op.Selection || op.Command.Kind != Take {
		t.Fatal(op, err)
	}
	for _, kind := range []string{Aim, Focus} {
		if steps, err := PlanSteps(Plan{Command: Command{Kind: kind}}); err != nil || len(steps) != 0 {
			t.Fatal("local action wrote a backend step", steps, err)
		}
	}
	steps, err = PlanSteps(Plan{Command: Command{Kind: Cast}, Effect: Effect{ConfirmSelection: true}})
	if err != nil || len(steps) != 2 || !steps[0].Await || steps[1].Await {
		t.Fatal(steps, err)
	}
	op, err = OperationValue(steps[0])
	if err != nil || !op.Selection {
		t.Fatal(op, err)
	}
	custom := []gateway.Step{{Value: "adapter-owned", Interferes: []string{"learn"}}}
	s := newState(t, testConfig(), Policy{Steps: func(Plan) ([]gateway.Step, error) { return custom, nil }})
	if err := apply(t, s, Event{Kind: Appeared, ID: 1, Mine: true}); err != nil {
		t.Fatal(err)
	}
	r, err := CommandRecord(Command{Kind: Walk}, CommandContext{N: 1})
	if err != nil {
		t.Fatal(err)
	}
	a, reason := s.Plan(r, 0)
	if reason != wire.ReasonNone {
		t.Fatal(reason)
	}
	custom[0].Value, custom[0].Interferes[0] = "changed", "changed"
	if a.Steps[0].Value != "adapter-owned" || a.Steps[0].Interferes[0] != "learn" {
		t.Fatal("retained strategy aliases caller metadata")
	}
	r.Fields[1] = wire.U(2, true, 99)
	if reason := s.Validate(a, 0); reason != wire.ReasonNone {
		t.Fatal("captured intent aliases caller", reason)
	}
}

func TestProposalAndReferenceOverflowRejectBeforeMutation(t *testing.T) {
	p, err := NewProposals(testConfig())
	if err != nil {
		t.Fatal(err)
	}
	p.next = math.MaxUint64
	if _, err := p.Capture(Event{Kind: Spoke}, Ref{}, true, 0); !errors.Is(err, ErrLimit) || len(p.windows) != 0 {
		t.Fatal("proposal handle wrapped", err)
	}
	o, err := NewObservations(1)
	if err != nil {
		t.Fatal(err)
	}
	o.generation = math.MaxUint64
	if _, _, err := o.Apply(Event{Kind: Appeared, ID: 1}); !errors.Is(err, ErrLimit) || o.Len() != 0 {
		t.Fatal("reference generation wrapped", err)
	}
}

func TestEveryNonproposalVerbAndKnownSkillWithoutPractice(t *testing.T) {
	o := observations(t)
	env := Environment{Observed: o, Alive: true, Known: map[int32]Skill{3: {ID: 3, Level: 2}}, Self: 1, Classify: classifyTest}
	commands := []Command{
		{Kind: Walk, X: 123, Y: -456, Z: 789}, {Kind: Aim, Target: 8}, {Kind: Focus, Target: 8},
		{Kind: Strike, Target: 8}, {Kind: Strike, Target: 7, Force: true}, {Kind: Take, Target: 9, X: 11, Y: 22, Z: 33},
		{Kind: Talk, Target: 7, X: 100, Y: 200, Z: 300}, {Kind: Engage, Target: 8}, {Kind: Engage, Target: 7},
		{Kind: Engage, Target: 7, Force: true}, {Kind: Cast, Target: 8, Item: 3, Force: true}, {Kind: Rise},
	}
	for _, cmd := range commands {
		t.Run(cmd.Kind, func(t *testing.T) {
			localEnv := env
			if cmd.Kind == Rise {
				localEnv.Alive = false
				localEnv.Dead = true
			}
			ctx := CommandContext{N: 1, Control: 1, Target: o.Reference(cmd.Target), Item: o.Reference(cmd.Target), SkillLevel: 2}
			r, err := CommandRecord(cmd, ctx)
			if err != nil {
				t.Fatal(err)
			}
			encoded, e := codec(t).Encode([]wire.Record{r})
			if e != nil {
				t.Fatal(e)
			}
			records, e := codec(t).Decode(encoded)
			if e != nil {
				t.Fatal(e)
			}
			plan, reason := PlanIntent(records[0], localEnv)
			if reason != wire.ReasonNone {
				t.Fatal(reason)
			}
			want := cmd
			if cmd.Kind == Engage {
				if cmd.Target == 7 && !cmd.Force {
					want.Kind = Talk
					want.X, want.Y, want.Z = 100, 200, 300
					if plan.Effect.Class != "dialog" || !plan.Effect.Waiting {
						t.Fatal(plan)
					}
				} else {
					want.Kind = Strike
				}
				if !plan.Effect.ConfirmSelection || plan.Effect.Requested != 11 {
					t.Fatal(plan)
				}
			}
			if !reflect.DeepEqual(plan.Command, want) {
				t.Fatalf("%#v != %#v", plan.Command, want)
			}
		})
	}
}

func TestUseItemOwnedRowAndShape(t *testing.T) {
	env := Environment{Alive: true, Inventory: map[int32]Good{42: {Object: 42, ID: 4, Count: 1}}}
	record, err := CommandRecord(Command{Kind: UseItem, Target: 42}, CommandContext{N: 1, Control: 1})
	if err != nil {
		t.Fatal(err)
	}
	plan, reason := PlanIntent(record, env)
	if reason != wire.ReasonNone || plan.Command.Target != 42 || plan.Effect.Class != "trade" || !plan.Effect.Waiting {
		t.Fatalf("plan=%+v reason=%v", plan, reason)
	}
	for _, tc := range []struct {
		name   string
		fields []wire.Field
		alive  bool
		want   wire.Reason
	}{
		{"missing", nil, true, wire.ReasonInvalid},
		{"foreign", []wire.Field{wire.S(21, true, 99)}, true, wire.ReasonStaleTarget},
		{"zero", []wire.Field{wire.S(21, true, 0)}, true, wire.ReasonStaleTarget},
		{"slot injection", []wire.Field{wire.S(21, true, 42), wire.S(17, true, 7)}, true, wire.ReasonInvalid},
		{"dead", []wire.Field{wire.S(21, true, 42)}, false, wire.ReasonNotAlive},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := record
			r.Fields = append(append([]wire.Field(nil), record.Fields[:3]...), tc.fields...)
			env.Alive = tc.alive
			if _, got := PlanIntent(r, env); got != tc.want {
				t.Fatalf("reason=%v want %v", got, tc.want)
			}
		})
	}
}
