// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"errors"
	"slices"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Capabilities is a trusted classifier verdict, never participant-supplied data.
// A zero verdict grants no target action. Role strings remain opaque world labels.
type Capabilities struct {
	// Strike permits a hostile target to be struck without Force.
	Strike bool
	// Talk permits dialogue, or a forced strike, against this target.
	Talk bool
	// Pickup identifies a collectible target.
	Pickup bool
	// Cast permits this target for a skill action; skill checks remain independent.
	Cast bool
}

// Policy is installed by trusted composition before admission. The actor owns
// State; callbacks must not block or mutate the captured plan.
type Policy struct {
	// Classify grants capabilities from a validated observed value. Nil grants none.
	Classify func(Observed) Capabilities
	// Steps captures a backend strategy. Nil selects PlanSteps. Custom adapters
	// own their step values and must reject foreign values with errors on Write.
	Steps func(Plan) ([]gateway.Step, error)
}

// PlanSteps captures a target helper followed by one effect write. Local aim and
// focus perform no writes. The helper must be confirmed before the effect.
func PlanSteps(plan Plan) ([]gateway.Step, error) {
	if plan.Command.Kind == Aim || plan.Command.Kind == Focus {
		return nil, nil
	}
	if _, ok := VerbByCommand(plan.Command.Kind); !ok {
		return nil, wire.ErrUnsupported
	}
	var steps []gateway.Step
	if plan.Effect.ConfirmSelection {
		steps = append(steps, gateway.Step{Value: Operation{Command: Command{Kind: Talk, Target: plan.Target.ID}, Selection: true}, Await: true, Interferes: []string{"dialog"}})
	}
	step := gateway.Step{Value: Operation{Command: plan.Command}}
	if plan.Command.Kind == Say {
		step.Interferes = []string{"learn"}
	}
	return append(steps, step), nil
}

// ErrValue identifies a foreign, nil, or wrong-owner adapter value. It contains
// neither the value nor its concrete type and is safe to compare with errors.Is.
var ErrValue = errors.New("meaning: invalid adapter value")

// InputValue checks the only public Update.Value representation.
// A foreign or nil value returns ErrValue without exposing its contents or type.
func InputValue(update gateway.Update) (Input, error) {
	input, ok := update.Value.(Input)
	if !ok {
		return Input{}, ErrValue
	}
	return input, nil
}

// OperationValue checks the default strategy's Step.Value representation.
// A foreign or nil value returns ErrValue without exposing its contents or type.
func OperationValue(step gateway.Step) (Operation, error) {
	operation, ok := step.Value.(Operation)
	if !ok {
		return Operation{}, ErrValue
	}
	return operation, nil
}

func (s *State) actionValue(action *gateway.Action) (*planned, error) {
	if action == nil {
		return nil, ErrValue
	}
	p, ok := action.Value.(*planned)
	if !ok || p == nil || p.owner != s {
		return nil, ErrValue
	}
	return p, nil
}

// Describe returns the captured plan to a backend adapter. Invalid Action values
// return ErrValue instead of invoking a second resolution or panicking.
func (s *State) Describe(action *gateway.Action) (Plan, error) {
	p, err := s.actionValue(action)
	if err != nil {
		return Plan{}, err
	}
	return p.plan, nil
}

func cloneRecord(record wire.Record) wire.Record {
	record.Fields = cloneFields(record.Fields)
	return record
}
func cloneFields(fields []wire.Field) []wire.Field {
	fields = slices.Clone(fields)
	for i := range fields {
		fields[i].Value = cloneValue(fields[i].Value)
	}
	return fields
}
func cloneValue(value wire.Value) wire.Value {
	value.Data = slices.Clone(value.Data)
	value.Fields = cloneFields(value.Fields)
	value.Items = slices.Clone(value.Items)
	for i := range value.Items {
		value.Items[i] = cloneValue(value.Items[i])
	}
	return value
}

var _ gateway.State = (*State)(nil)
