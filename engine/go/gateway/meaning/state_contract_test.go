// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"errors"
	"math"
	"reflect"
	"testing"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// These are discovered consumer method sets, not new exported abstractions.
type projection interface {
	Apply(gateway.Update, int64) (gateway.Observation, error)
	Snapshot(int64) (gateway.Snapshot, error)
	Invalidate()
}

func TestStateRequiresExplicitPositiveLimits(t *testing.T) {
	for _, change := range []func(*Config){
		func(c *Config) { c.Objects = 0 }, func(c *Config) { c.Windows = 0 },
		func(c *Config) { c.WindowBytes = 0 }, func(c *Config) { c.ProposalMS = 0 },
		func(c *Config) { c.HistoryEntries = 0 }, func(c *Config) { c.HistoryBytes = 0 },
		func(c *Config) { c.Attempts.Pending = 0 }, func(c *Config) { c.Advertised[25] = 0 },
		func(c *Config) { c.Advertised[29] = math.MaxUint64 },
	} {
		c := testConfig()
		change(&c)
		if _, err := NewState(c, Policy{}); !errors.Is(err, ErrLimit) {
			t.Fatalf("error = %v, want ErrLimit", err)
		}
	}
}

func TestWitnessRequiresAuditedSourceAndMatchingCompletion(t *testing.T) {
	for _, kind := range []string{Talk, Say, Take, Learn, Rise} {
		for _, authority := range []string{"none", "wrong_completion", "no_source", "audited"} {
			t.Run(kind+"/"+authority, func(t *testing.T) {
				s := newState(t, testConfig(), Policy{})
				for _, ev := range []Event{{Kind: Appeared, ID: 1, Mine: true}, {Kind: Vitals, ID: 1, Stats: &Stats{Level: Number(5)}}, {Kind: Appeared, ID: 7}, {Kind: Dropped, ID: 9}} {
					if err := apply(t, s, ev); err != nil {
						t.Fatal(err)
					}
				}
				p := &planned{owner: s, record: wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 42)}},
					plan: Plan{Command: Command{Kind: kind, Item: 3, Count: 2}, Item: s.observed.Reference(9)},
					npc:  s.observed.Reference(7), eligible: true, relocated: true, baselineLevel: Number(5)}
				a := &gateway.Action{Value: p, AfterCheckpoint: kind == Rise}
				input := Input{Completed: 42, Source: Source{Dialogue: true, Pickup: true, Learning: true, Return: true}}
				want := gateway.WitnessMatch
				switch kind {
				case Talk, Say:
					input.Event = Event{Kind: Spoke, ID: 7}
				case Take:
					input.Event = Event{Kind: Took, ID: 1, Target: 9}
				case Learn:
					input.Event = Event{Kind: Known, Complete: true, Skills: []Skill{{ID: 3, Level: 2}}}
					want = gateway.WitnessCandidate
				case Rise:
					input.Event = Event{Kind: Revived, ID: 1}
				}
				switch authority {
				case "none":
					input.Completed, input.Source = 0, Source{}
				case "wrong_completion":
					input.Completed = 41
				case "no_source":
					input.Source = Source{}
				}
				if authority != "audited" {
					want = gateway.WitnessNone
				}
				u := gateway.Update{Value: input}
				if _, err := s.Apply(u, 0); err != nil {
					t.Fatal(err)
				}
				if got, err := s.Witness(a, u); err != nil || got != want {
					t.Fatalf("witness = %v, error = %v, want %v", got, err, want)
				}
			})
		}
	}
}

func TestLearnCandidateKeepsBaselineAndFailedStrategyRetainsNoPlan(t *testing.T) {
	s := newState(t, testConfig(), Policy{})
	for _, ev := range []Event{
		{Kind: Appeared, ID: 1, Mine: true}, {Kind: Vitals, ID: 1, Stats: &Stats{Level: Number(5)}},
		{Kind: Known, Complete: true, Skills: []Skill{{ID: 3, Level: 1}}},
	} {
		if err := apply(t, s, ev); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := s.Apply(gateway.Update{Value: Input{Complete: []string{"self"}}}, 0); err != nil {
		t.Fatal(err)
	}
	obs, err := s.Apply(gateway.Update{Value: Input{Event: Event{Kind: Practice, Complete: true, List: 2, Goods: []Good{{ID: 3, Count: 2, Object: 3}}}}}, 0)
	if err != nil {
		t.Fatal(err)
	}
	d, err := EventValue(obs.Facts[0])
	if err != nil {
		t.Fatal(err)
	}
	r, err := CommandRecord(Command{Kind: Learn}, CommandContext{N: 42, List: 2, Offer: d.Context.Offers[0]})
	if err != nil {
		t.Fatal(err)
	}
	s.policy.Steps = func(Plan) ([]gateway.Step, error) { return nil, errors.New("adapter: unsupported strategy") }
	if a, reason := s.Plan(r, 0); a != nil || reason != wire.ReasonInvalid || len(s.plans) != 0 {
		t.Fatal("failed strategy retained a plan", a, reason)
	}
	s.policy.Steps = nil
	a, reason := s.Plan(r, 0)
	if reason != wire.ReasonNone || !a.FullWindow {
		t.Fatal("missing full learning window", reason)
	}
	u := gateway.Update{Value: Input{Event: Event{Kind: Known, Complete: true, Skills: []Skill{{ID: 3, Level: 2}}}, Source: Source{Learning: true}, Completed: 42}}
	if _, err := s.Apply(u, 0); err != nil {
		t.Fatal(err)
	}
	if got, err := s.Witness(a, u); err != nil || got != gateway.WitnessCandidate {
		t.Fatal(got, err)
	}
	if valid, err := s.CandidateValid(a); err != nil || !valid {
		t.Fatal(valid, err)
	}
	if err := apply(t, s, Event{Kind: Vitals, ID: 1, Stats: &Stats{Level: Number(6)}}); err != nil {
		t.Fatal(err)
	}
	if valid, err := s.CandidateValid(a); err != nil || valid {
		t.Fatal("level-up did not exclude learning candidate", valid, err)
	}
}

func TestSnapshotOwnershipAndStorageAccounting(t *testing.T) {
	s := newState(t, testConfig(), Policy{})
	if err := apply(t, s, Event{Kind: Spoke, ID: 7, Text: "original", Lines: []Line{{Label: "Continue", Say: "saved"}}}); err != nil {
		t.Fatal(err)
	}
	snapshot, err := s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	for i, fact := range snapshot.Facts {
		if getU(fact.Fields, 1) == 11 {
			snapshot.Facts[i].Fields = nil
			value, ok := wire.Find(fact.Fields, 20)
			if !ok {
				t.Fatal("missing text")
			}
			text, _ := wire.Find(value.Fields, 1)
			text.Data[0] = 'X'
		}
	}
	again, err := s.Snapshot(0)
	if err != nil {
		t.Fatal(err)
	}
	for _, fact := range again.Facts {
		if getU(fact.Fields, 1) == 11 && stringText(fact.Fields, 20) != "original" {
			t.Fatal("snapshot aliases retained text")
		}
	}
	before := s.BackendStorageBytes()
	s.plans = append(s.plans, &planned{record: wire.Record{Fields: []wire.Field{wire.Data(1, true, "retained")}}})
	if s.BackendStorageBytes() <= before {
		t.Fatal("retained plan missing from accounting")
	}
}

type planning interface {
	Plan(wire.Record, int64) (*gateway.Action, wire.Reason)
	Validate(*gateway.Action, int64) wire.Reason
	Start(*gateway.Action) error
	Confirmed(*gateway.Action) (bool, error)
	Stop() *gateway.Step
}
type evidence interface {
	Witness(*gateway.Action, gateway.Update) (gateway.Witness, error)
	CandidateValid(*gateway.Action) (bool, error)
	Proof(*gateway.Action, int64) (wire.Record, error)
}

var (
	_ projection = (*State)(nil)
	_ planning   = (*State)(nil)
	_ evidence   = (*State)(nil)
)

func TestStateConsumerSplitPreservesElevenMethods(t *testing.T) {
	full := reflect.TypeFor[gateway.State]()
	sets := []reflect.Type{reflect.TypeFor[projection](), reflect.TypeFor[planning](), reflect.TypeFor[evidence]()}
	seen := make(map[string]bool)
	for _, set := range sets {
		for i := 0; i < set.NumMethod(); i++ {
			m := set.Method(i)
			got, ok := full.MethodByName(m.Name)
			if !ok || got.Type != m.Type || seen[m.Name] {
				t.Fatalf("consumer method %s is missing, changed, or duplicated", m.Name)
			}
			seen[m.Name] = true
		}
	}
	if full.NumMethod() != 11 || len(seen) != 11 {
		t.Fatalf("methods = %d, split = %d, want 11", full.NumMethod(), len(seen))
	}
}

func TestStateRejectsForeignValues(t *testing.T) {
	s, err := NewState(testConfig(), Policy{})
	if err != nil {
		t.Fatal(err)
	}
	other, err := NewState(testConfig(), Policy{})
	if err != nil {
		t.Fatal(err)
	}
	valid := &gateway.Action{Value: &planned{owner: s}}
	foreign := []*gateway.Action{nil, {}, {Value: "foreign"}, {Value: (*planned)(nil)}, {Value: &planned{owner: other}}}
	methods := []struct {
		name string
		call func(*gateway.Action) error
	}{
		{"Start", s.Start},
		{"Confirmed", func(a *gateway.Action) error { _, err := s.Confirmed(a); return err }},
		{"Witness", func(a *gateway.Action) error { _, err := s.Witness(a, gateway.Update{Value: Input{}}); return err }},
		{"CandidateValid", func(a *gateway.Action) error { _, err := s.CandidateValid(a); return err }},
		{"Proof", func(a *gateway.Action) error { _, err := s.Proof(a, 0); return err }},
		{"Describe", func(a *gateway.Action) error { _, err := s.Describe(a); return err }},
	}
	for _, method := range methods {
		t.Run(method.name, func(t *testing.T) {
			for i, a := range foreign {
				if err := method.call(a); !errors.Is(err, ErrValue) {
					t.Fatalf("value %d: error = %v, want ErrValue", i, err)
				}
			}
		})
	}
	for _, a := range foreign {
		if reason := s.Validate(a, 0); reason != wire.ReasonInvalid {
			t.Fatalf("Validate = %v, want Invalid", reason)
		}
	}
	for _, value := range []any{nil, "foreign", (*Input)(nil), &Input{}} {
		update := gateway.Update{Value: value}
		if _, err := s.Apply(update, 0); !errors.Is(err, ErrValue) {
			t.Fatalf("Apply: %v", err)
		}
		if _, err := s.Witness(valid, update); !errors.Is(err, ErrValue) {
			t.Fatalf("Witness Update: %v", err)
		}
		if _, err := InputValue(update); !errors.Is(err, ErrValue) {
			t.Fatalf("InputValue: %v", err)
		}
		if _, err := OperationValue(gateway.Step{Value: value}); !errors.Is(err, ErrValue) {
			t.Fatalf("OperationValue: %v", err)
		}
	}
	if err := s.Start(valid); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Confirmed(valid); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Witness(valid, gateway.Update{Value: Input{}}); err != nil {
		t.Fatal(err)
	}
	if _, err := s.CandidateValid(valid); err != nil {
		t.Fatal(err)
	}
	if _, err := OperationValue(gateway.Step{Value: Operation{}}); err != nil {
		t.Fatal(err)
	}
}

func TestLiveFactsKeepBackendTimeAndReceiveAge(t *testing.T) {
	s, err := NewState(testConfig(), Policy{})
	if err != nil {
		t.Fatal(err)
	}
	observation, err := s.Apply(gateway.Update{Value: Input{Event: Event{Kind: Appeared, ID: 1, MS: 999999}, ReceivedAt: 20}}, 23)
	if err != nil {
		t.Fatal(err)
	}
	d, err := EventValue(observation.Facts[0])
	if err != nil || d.Event.MS != 999999 {
		t.Fatalf("event time = %d, error = %v", d.Event.MS, err)
	}
	ev := Event{Kind: Shop, List: 4, Complete: true, Goods: []Good{{ID: 30, Price: 5, Count: 10}}}
	observation, err = s.Apply(gateway.Update{Value: Input{Event: ev, ReceivedAt: 20}}, 100)
	if err != nil {
		t.Fatal(err)
	}
	d, err = EventValue(observation.Facts[0])
	if err != nil {
		t.Fatal(err)
	}
	if _, reason := s.proposals.trade(Shop, 4, d.Context.Offers[0], 1, 5, 20+testConfig().ProposalMS); reason != wire.ReasonStaleOffer {
		t.Fatalf("delayed capture renewed proposal: %v", reason)
	}
	if _, err := s.Apply(gateway.Update{Value: Input{ReceivedAt: 101}}, 100); err == nil {
		t.Fatal("future receive time accepted")
	}
}

func TestReturnProofRetainsLiveFactAndCannotResurrectNewerDeath(t *testing.T) {
	s, err := NewState(testConfig(), Policy{})
	if err != nil {
		t.Fatal(err)
	}
	if err := apply(t, s, Event{Kind: Appeared, ID: 5, Mine: true}); err != nil {
		t.Fatal(err)
	}
	p := &planned{owner: s, plan: Plan{Command: Command{Kind: Rise}}, relocated: true, completed: true}
	a := &gateway.Action{AfterCheckpoint: true, Value: p}
	if valid, err := s.CandidateValid(a); err != nil || valid {
		t.Fatal("candidate without live proof", valid, err)
	}
	if _, err := s.Proof(a, 50); err == nil {
		t.Fatal("fabricated revival without a live record")
	}
	u := gateway.Update{Value: Input{Event: Event{Kind: Revived, ID: 5, MS: 17}, Source: Source{Return: true}}}
	if _, err := s.Apply(u, 20); err != nil {
		t.Fatal(err)
	}
	if witness, err := s.Witness(a, u); err != nil || witness != gateway.WitnessMatch {
		t.Fatal(witness, err)
	}
	if valid, err := s.CandidateValid(a); err != nil || !valid {
		t.Fatal(valid, err)
	}
	proof, err := s.Proof(a, 999)
	if err != nil || getS(proof.Fields, 3) != 17 {
		t.Fatal("live timestamp replaced", proof, err)
	}
	proof.Fields[0] = wire.U(1, true, 99)
	again, err := s.Proof(a, 1000)
	if err != nil || getU(again.Fields, 1) != 7 {
		t.Fatal("caller mutated retained proof", again, err)
	}
	u.Value = Input{Event: Event{Kind: Died, ID: 5}}
	if _, err := s.Apply(u, 20); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Witness(a, u); err != nil {
		t.Fatal(err)
	}
	if valid, err := s.CandidateValid(a); err != nil || valid {
		t.Fatal("later death undone", valid, err)
	}
}
