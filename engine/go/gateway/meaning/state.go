// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"fmt"
	"math"
	"slices"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Source explicitly describes the exclusive producers audited by a backend.
// A zero source grants no causal authority, even for a same-ID prompt reply.
type Source struct {
	// Dialogue asserts an audited exclusive dialogue producer.
	Dialogue bool
	// Pickup asserts an audited exclusive item transfer producer.
	Pickup bool
	// Learning asserts an audited exclusive skill-change producer.
	Learning bool
	// Return asserts an audited exclusive relocation and revival producer.
	Return bool
}

// Input is an owned backend update supplied only by trusted composition.
type Input struct {
	// Event is the backend fact; MS keeps its original time, independent of receipt.
	Event Event
	// Complete identifies explicitly completed projection domains.
	Complete []string
	// Incomplete revokes explicitly lost projection completeness. It also
	// disqualifies pending learning baselines in the self or skills domains.
	Incomplete []string
	// Source is supplied by an audited backend, never taken from a participant.
	Source Source
	// ReceivedAt is the backend receive time in gateway-relative milliseconds.
	// It is distinct from Event.MS and must not be in the actor's future.
	ReceivedAt int64
	// Completed is the participant attempt whose matching effect receipt was
	// validated by the trusted adapter. Zero supplies no delivery evidence.
	Completed uint64
	// Returned binds an audited return relocation to a handed-off rise attempt.
	// Unlike Completed, it does not assert a raw done receipt: rise has none.
	Returned uint64
	// ProposalBorn optionally supplies a conservative original proposal birth.
	// It never replaces the live receive time used for witness eligibility.
	ProposalBorn *int64
}

// Operation is the default captured backend step. Selection is a target helper,
// not an effect; adapters inspect it through OperationValue.
type Operation struct {
	// Command is the captured backend request; it must not be logged.
	Command Command
	// Selection distinguishes a target helper from the single effect write.
	Selection bool
}
type planned struct {
	plan                           Plan
	record                         wire.Record
	npc                            Ref
	baselineLevel                  *int64
	baselineSkill                  int32
	eligible, relocated, completed bool
	returned                       bool
	started                        bool
	owner                          *State
	riseProof                      wire.Record
}

// State is an actor-owned bounded projection implementing gateway.State.
// Construct it with NewState; operations must not run concurrently.
type State struct {
	projectionBytes     int
	backendPeaks        [9]int
	config              Config
	policy              Policy
	codec               *Codec
	observed            *Observations
	proposals           *Proposals
	actors              map[int32]Event
	motion              map[int32]Event
	dead                map[int32]bool
	vitals              map[int32]*Stats
	known               map[int32]Skill
	inventory           map[int32]Good
	quests              map[int32]QuestState
	retained            map[string]wire.Record
	complete            map[string]bool
	self                int32
	coin                *int64
	selected            Ref
	lastRef, lastTarget Ref
	plans               []*planned
}

// NewState constructs an actor-owned projection with explicit limits and trusted
// policy. A nil classifier grants no target capabilities. The caller must discard
// a State after an Apply error; the runtime closes its participant on that error.
func NewState(c Config, policy Policy) (*State, error) {
	if c.HistoryEntries < 1 || c.HistoryBytes < 1 || c.Attempts.Pending < 1 || c.Advertised[25] == 0 || c.Advertised[29] == 0 || c.Advertised[25] > math.MaxInt || c.Advertised[29] > math.MaxInt {
		return nil, ErrLimit
	}
	c.Attempts.Classes = slices.Clone(c.Attempts.Classes)
	codec, err := NewCodec(c)
	if err != nil {
		return nil, err
	}
	obs, err := NewObservations(c.Objects)
	if err != nil {
		return nil, err
	}
	p, err := NewProposals(c)
	if err != nil {
		return nil, err
	}
	return &State{config: c, policy: policy, codec: codec, observed: obs, proposals: p, actors: map[int32]Event{}, motion: map[int32]Event{}, dead: map[int32]bool{}, vitals: map[int32]*Stats{}, known: map[int32]Skill{}, inventory: map[int32]Good{}, quests: map[int32]QuestState{}, retained: map[string]wire.Record{}, complete: map[string]bool{}}, nil
}

// Observed returns a value copy of the latest observation for id, if present.
// The session loop owns State; calls must be serialized with all other operations.
func (s *State) Observed(id int32) (Observed, bool) {
	return s.observed.Lookup(id)
}

func mergeStats(a, b *Stats) *Stats {
	if b == nil {
		return a
	}
	if a == nil {
		a = &Stats{}
	}
	out := *a
	dst := []**int64{&out.HP, &out.MaxHP, &out.MP, &out.MaxMP, &out.Level, &out.XP, &out.SP}
	src := []*int64{b.HP, b.MaxHP, b.MP, b.MaxMP, b.Level, b.XP, b.SP}
	for i, v := range src {
		if v != nil {
			*dst[i] = Number(*v)
		}
	}
	return &out
}
func (s *State) context(e Event) FactContext {
	return FactContext{Seq: 1, Epoch: 1, Ref: s.observed.Reference(e.ID), Target: s.observed.Reference(e.Target), Origin: wire.ProvenanceUnclassified}
}

// Apply validates and retains an owned backend update; now is gateway-relative time.
// Invalid values return ErrValue, and invalid shapes or bounds return errors.
// After an error the runtime closes the participant without logging the value.
func (s *State) Apply(update gateway.Update, now int64) (gateway.Observation, error) {
	input, err := InputValue(update)
	if err != nil {
		return gateway.Observation{}, err
	}
	e := input.Event
	if input.ReceivedAt < 0 || input.ReceivedAt > now {
		return gateway.Observation{}, wire.ErrFrame
	}
	for _, domain := range input.Incomplete {
		switch domain {
		case "objects", "self", "inventory", "quests", "skills", "trade", "dialogue", "history":
			s.complete[domain] = false
			if domain == "self" || domain == "skills" {
				for _, p := range s.plans {
					p.eligible = false
				}
			}
		default:
			return gateway.Observation{}, wire.ErrFrame
		}
	}
	for _, domain := range input.Complete {
		switch domain {
		case "objects", "self", "inventory", "quests", "skills", "trade", "dialogue", "history":
			s.complete[domain] = true
		default:
			return gateway.Observation{}, wire.ErrFrame
		}
	}
	if e.Kind == "" {
		return gateway.Observation{}, nil
	}
	// The backend hands off owned values; retain only canonical bounded copies.
	id, target, err := s.observed.Apply(e)
	if err != nil {
		return gateway.Observation{}, err
	}
	s.lastRef, s.lastTarget = id, target
	ctx := s.context(e)
	ctx.Ref, ctx.Target = id, target
	if e.Kind == Spoke || e.Kind == Shop || e.Kind == Offer || e.Kind == Practice {
		born := input.ReceivedAt
		if input.ProposalBorn != nil {
			born = *input.ProposalBorn
			if born < 0 || born > input.ReceivedAt {
				return gateway.Observation{}, wire.ErrFrame
			}
		}
		ctx, err = s.proposals.Capture(e, id, input.Event.Complete || e.Kind == Spoke, born)
		if err != nil {
			return gateway.Observation{}, err
		}
	}
	r, err := EventRecord(e, ctx)
	if err != nil {
		return gateway.Observation{}, err
	}
	b, err := s.codec.Encode([]wire.Record{r})
	if err != nil {
		return gateway.Observation{}, err
	}
	records, err := s.codec.Decode(b)
	if err != nil {
		return gateway.Observation{}, err
	}
	r = records[0]
	d, err := EventValue(r)
	if err != nil {
		return gateway.Observation{}, err
	}
	public := d.Event
	switch e.Kind {
	case Appeared, Dropped:
		s.actors[e.ID] = public
		if e.Mine {
			s.self = e.ID
		}
		if e.Stats != nil {
			s.vitals[e.ID] = mergeStats(s.vitals[e.ID], e.Stats)
		}
	case Targeted:
		if e.ID == s.self {
			s.selected = target
		}
	case Moved, Stopped, Corrected:
		if _, visible := s.actors[e.ID]; visible {
			s.motion[e.ID] = public
		}
	case Died, Revived:
		if _, visible := s.actors[e.ID]; visible {
			s.dead[e.ID] = e.Kind == Died
		}
		if e.ID == s.self && e.Kind == Died {
			s.clearProposals()
		}
	case Gone:
		delete(s.actors, e.ID)
		delete(s.motion, e.ID)
		delete(s.dead, e.ID)
		delete(s.vitals, e.ID)
	case Took:
		delete(s.actors, e.Target)
		delete(s.motion, e.Target)
		delete(s.dead, e.Target)
		delete(s.vitals, e.Target)
	case Vitals:
		if e.ID == s.self {
			old := s.vitals[e.ID]
			if old == nil || e.Stats == nil || e.Stats.Level != nil && (old.Level == nil || *e.Stats.Level > *old.Level) {
				for _, p := range s.plans {
					p.eligible = false
				}
			}
		}
		if _, visible := s.actors[e.ID]; visible {
			s.vitals[e.ID] = mergeStats(s.vitals[e.ID], e.Stats)
		}
	case Carried, Inventory:
		if e.Kind == Carried && e.Goods != nil {
			clear(s.inventory)
			s.complete["inventory"] = true
		}
		for _, g := range e.Goods {
			if g.Change == 3 || g.Count == 0 {
				delete(s.inventory, g.Object)
				s.proposals.RemoveSaleObject(g.Object)
			} else {
				s.inventory[g.Object] = g
			}
		}
		if e.Coin != nil {
			s.coin = Number(*e.Coin)
		}
	case Known:
		if !input.Source.Learning {
			for _, p := range s.plans {
				for _, skill := range e.Skills {
					if skill.ID == p.plan.Command.Item {
						p.eligible = false
					}
				}
			}
		}
		if e.Complete && e.Skills != nil {
			clear(s.known)
			s.complete["skills"] = true
		}
		for _, v := range e.Skills {
			s.known[v.ID] = v
		}
	case Quests:
		if e.Complete && e.Quests != nil {
			clear(s.quests)
			s.complete["quests"] = true
		}
		for _, v := range e.Quests {
			s.quests[v.ID] = v
		}
	case Quest:
		s.quests[e.ID] = QuestState{ID: e.ID, Step: e.Amount, Name: e.Name, State: e.Text}
	case Spoke:
		s.retained[Spoke] = r
	case Shop, Offer, Practice:
		s.retained[fmt.Sprintf("%s:%d", e.Kind, e.List)] = r
	case History:
		history := e
		if len(history.Entries) > s.config.HistoryEntries {
			history.Entries = history.Entries[len(history.Entries)-s.config.HistoryEntries:]
		}
		for {
			candidate, err := EventRecord(history, ctx)
			if err != nil {
				return gateway.Observation{}, err
			}
			encoded, err := s.codec.Encode([]wire.Record{candidate})
			if err != nil {
				return gateway.Observation{}, err
			}
			if len(encoded) <= s.config.HistoryBytes {
				s.retained[History] = candidate
				break
			}
			if len(history.Entries) == 0 {
				delete(s.retained, History)
				break
			}
			history.Entries = history.Entries[1:]
		}

	}
	if len(s.known) > s.config.Wire.ListItems || len(s.inventory) > s.config.Wire.ListItems || len(s.quests) > s.config.Wire.ListItems {
		return gateway.Observation{}, ErrLimit
	}
	if len(s.retained) > s.config.Windows+2 { // Proposal handles, not semantic rows, determine eviction.
		s.pruneProposals(now)
	}
	if _, err = s.Snapshot(now); err != nil {
		return gateway.Observation{}, err
	}
	return gateway.Observation{Facts: []wire.Record{r}}, nil
}
func (s *State) clearProposals() {
	s.proposals.Invalidate()
	for k := range s.retained {
		if k != History {
			delete(s.retained, k)
		}
	}
}
func (s *State) pruneProposals(now int64) {
	valid := s.proposals.Handles(now)
	for key, r := range s.retained {
		if key == History {
			continue
		}
		d, err := EventValue(r)
		if err != nil {
			delete(s.retained, key)
			continue
		}
		id := d.Context.Dialog
		if id == 0 && len(d.Context.Offers) > 0 {
			id = d.Context.Offers[0]
		}
		if !valid[id] {
			delete(s.retained, key)
		}
	}
}

// Snapshot returns independently owned current facts and incomplete domain names.
// It never renews proposal lifetimes or fabricates causal outcomes.
func (s *State) Snapshot(now int64) (gateway.Snapshot, error) {
	s.pruneProposals(now)
	out := make([]wire.Record, 0, len(s.actors)*2+8)
	appendEvent := func(e Event) error {
		r, err := EventRecord(e, s.context(e))
		if err != nil {
			return err
		}
		out = append(out, r)
		return nil
	}
	ids := make([]int32, 0, len(s.actors))
	for id := range s.actors {
		ids = append(ids, id)
	}
	slices.Sort(ids)
	for _, id := range ids {
		e := s.actors[id]
		e.Kind = Appeared
		e.Stats = s.vitals[id]
		if err := appendEvent(e); err != nil {
			return gateway.Snapshot{}, err
		}
		if v, ok := s.motion[id]; ok {
			if err := appendEvent(v); err != nil {
				return gateway.Snapshot{}, err
			}
		}
		if s.dead[id] {
			if err := appendEvent(Event{Kind: Died, ID: id, MS: now}); err != nil {
				return gateway.Snapshot{}, err
			}
		}
	}
	if v := s.vitals[s.self]; v != nil {
		if err := appendEvent(Event{Kind: Vitals, ID: s.self, Stats: v, MS: now}); err != nil {
			return gateway.Snapshot{}, err
		}
	}
	inv := Event{Kind: Carried, Coin: s.coin, MS: now}
	if s.complete["inventory"] {
		inv.Goods = []Good{}
	}
	for _, v := range s.inventory {
		inv.Goods = append(inv.Goods, v)
	}
	slices.SortFunc(inv.Goods, func(a, b Good) int { return cmp32(a.Object, b.Object) })
	if err := appendEvent(inv); err != nil {
		return gateway.Snapshot{}, err
	}
	known := Event{Kind: Known, Complete: s.complete["skills"], MS: now}
	if known.Complete {
		known.Skills = []Skill{}
	}
	for _, v := range s.known {
		known.Skills = append(known.Skills, v)
	}
	slices.SortFunc(known.Skills, func(a, b Skill) int { return cmp32(a.ID, b.ID) })
	if err := appendEvent(known); err != nil {
		return gateway.Snapshot{}, err
	}
	quests := Event{Kind: Quests, Complete: s.complete["quests"], MS: now}
	if quests.Complete {
		quests.Quests = []QuestState{}
	}
	for _, v := range s.quests {
		quests.Quests = append(quests.Quests, v)
	}
	slices.SortFunc(quests.Quests, func(a, b QuestState) int { return cmp32(a.ID, b.ID) })
	if err := appendEvent(quests); err != nil {
		return gateway.Snapshot{}, err
	}
	keys := make([]string, 0, len(s.retained))
	for key := range s.retained {
		keys = append(keys, key)
	}
	slices.Sort(keys)
	for _, key := range keys {
		r := s.retained[key]
		out = append(out, r)
	}
	used := 0
	for _, r := range out {
		b, err := s.codec.Encode([]wire.Record{r})
		if err != nil {
			return gateway.Snapshot{}, err
		}
		if len(b) > int(s.config.Advertised[25])-used || len(b) > int(s.config.Advertised[29])-used {
			return gateway.Snapshot{}, ErrLimit
		}
		used += len(b)
	}
	s.projectionBytes = used
	var incomplete []string
	for _, domain := range []string{"objects", "self", "inventory", "quests", "skills", "trade", "dialogue"} {
		if !s.complete[domain] {
			incomplete = append(incomplete, domain)
		}
	}
	if _, ok := s.actors[s.self]; !ok || s.self == 0 || s.vitals[s.self] == nil {
		if !slices.Contains(incomplete, "self") {
			incomplete = append(incomplete, "self")
		}
	}
	for i := range out {
		out[i] = cloneRecord(out[i])
	}
	return gateway.Snapshot{Facts: out, Ready: len(incomplete) == 0, Domains: incomplete}, nil
}
func cmp32(a, b int32) int {
	if a < b {
		return -1
	}
	if a > b {
		return 1
	}
	return 0
}
func (s *State) environment(now int64) Environment {
	_, exists := s.actors[s.self]
	return Environment{Observed: s.observed, Proposals: s.proposals, Known: s.known, Alive: exists && !s.dead[s.self], Dead: exists && s.dead[s.self], Self: s.self, Now: now, Classify: s.policy.Classify}
}

// Plan consumes a structurally validated Intent and captures one backend strategy.
// Unsupported, stale, or disallowed actions return an explicit refusal.
func (s *State) Plan(r wire.Record, now int64) (*gateway.Action, wire.Reason) {
	plan, reason := PlanIntent(r, s.environment(now))
	if reason != wire.ReasonNone {
		return nil, reason
	}
	p := &planned{plan: plan, record: cloneRecord(r), owner: s}
	if plan.Command.Kind == Say {
		_, p.npc, _ = s.proposals.Choose(plan.Dialog, getU(r.Fields, 13), now)
	} else {
		p.npc = plan.Target
	}
	a := &gateway.Action{Effective: plan.Effect.Effective, Class: plan.Effect.Class, Waiting: plan.Effect.Waiting, Value: p, Local: plan.Command.Kind == Aim || plan.Command.Kind == Focus}
	if a.Local {
		return a, wire.ReasonNone
	}
	strategy := s.policy.Steps
	if strategy == nil {
		strategy = PlanSteps
	}
	steps, err := strategy(plan)
	if err != nil {
		return nil, wire.ReasonInvalid
	}
	if plan.Command.Kind == Learn {
		p.eligible = s.complete["skills"] && s.complete["self"]
		if stats := s.vitals[s.self]; stats != nil && stats.Level != nil {
			p.baselineLevel = Number(*stats.Level)
		} else {
			p.eligible = false
		}
		p.baselineSkill = s.known[plan.Command.Item].Level
		if int64(p.baselineSkill) >= plan.Command.Count {
			p.eligible = false
		}
		a.FullWindow = true
		// Only pending learn plans need level-change exclusions; bounded by attempts.
		s.plans = slices.DeleteFunc(s.plans, func(old *planned) bool { return old == nil || !old.eligible })
		if len(s.plans) >= s.config.Attempts.Pending {
			s.plans = s.plans[1:]
		}
		s.plans = append(s.plans, p)
	}
	a.SurviveRelocation = plan.Command.Kind == Rise
	a.AfterCheckpoint = a.SurviveRelocation
	a.Steps = slices.Clone(steps)
	for i := range a.Steps {
		a.Steps[i].Interferes = slices.Clone(a.Steps[i].Interferes)
	}
	return a, wire.ReasonNone
}

// Validate rechecks current permission and lifetimes without retargeting a plan.
// Invalid Action values return ReasonInvalid rather than panic.
func (s *State) Validate(a *gateway.Action, now int64) wire.Reason {
	p, err := s.actionValue(a)
	if err != nil {
		return wire.ReasonInvalid
	}
	again, reason := PlanIntent(p.record, s.environment(now))
	if reason != wire.ReasonNone {
		return reason
	}
	if again.Effect.Effective != p.plan.Effect.Effective {
		return wire.ReasonInvalid
	}
	return wire.ReasonNone
}

// Confirmed is called before a selection helper or its effect is written.
// Foreign or nil Action values return ErrValue; the runtime closes the participant
// with an internal reason without logging the value or error text.
func (s *State) Confirmed(a *gateway.Action) (bool, error) {
	p, err := s.actionValue(a)
	if err != nil {
		return false, err
	}
	return s.selected == p.plan.Target, nil
}

// Witness is called for eligible actions on a live observation. Only audited
// producers and matching effect receipts grant causal authority. Rise instead
// requires an attributable handed-off return, a live revival, and checkpoint drain.
// Foreign Action or Update values return ErrValue; the runtime closes the
// participant with an internal reason without logging values or error text.
// Invalid live revival shapes or bounds return a codec error with the same close policy.
func (s *State) Witness(a *gateway.Action, u gateway.Update) (gateway.Witness, error) {
	p, err := s.actionValue(a)
	if err != nil {
		return gateway.WitnessNone, err
	}
	input, err := InputValue(u)
	if err != nil {
		return gateway.WitnessNone, err
	}
	if input.Completed != 0 && input.Completed == getU(p.record.Fields, 1) {
		p.completed = true
	}
	e := input.Event
	switch p.plan.Command.Kind {
	case Talk, Say:
		if p.completed && input.Source.Dialogue && e.Kind == Spoke && s.lastRef == p.npc {
			return gateway.WitnessMatch, nil
		}
	case Take:
		if p.completed && input.Source.Pickup && e.Kind == Took && e.ID == s.self && s.lastTarget == p.plan.Item {
			return gateway.WitnessMatch, nil
		}
	case Learn:
		if p.completed && e.Kind == Known && input.Source.Learning && p.eligible {
			for _, skill := range e.Skills {
				if skill.ID == p.plan.Command.Item && int64(skill.Level) >= p.plan.Command.Count {
					return gateway.WitnessCandidate, nil
				}
			}
		}
	case Rise:
		if u.Relocate {
			p.relocated = input.Source.Return
			p.returned = input.Returned != 0 && input.Returned == getU(p.record.Fields, 1)
			p.riseProof = wire.Record{}
		}
		if e.Kind == Died && e.ID == s.self {
			p.relocated = false
		}
		if input.Source.Return && e.Kind == Revived && e.ID == s.self && p.relocated {
			proof, err := EventRecord(e, s.context(e))
			if err != nil {
				return gateway.WitnessNone, err
			}
			if _, err := s.codec.Encode([]wire.Record{proof}); err != nil {
				return gateway.WitnessNone, err
			}
			p.riseProof = cloneRecord(proof)
			if p.completed || p.returned {
				return gateway.WitnessMatch, nil
			}
		}
	}
	return gateway.WitnessNone, nil
}

// CandidateValid runs before a retained witness is committed. Foreign or nil
// Action values return ErrValue, not a negative verdict; the runtime closes the
// participant with an internal reason without logging values or error text.
func (s *State) CandidateValid(a *gateway.Action) (bool, error) {
	p, err := s.actionValue(a)
	if err != nil {
		return false, err
	}
	if a.AfterCheckpoint {
		_, exists := s.actors[s.self]
		return p.relocated && (p.completed || p.returned) && p.riseProof.Type == wire.Fact && exists && !s.dead[s.self], nil
	}
	stats := s.vitals[s.self]
	return p.completed && p.eligible && p.baselineLevel != nil && stats != nil && stats.Level != nil && *stats.Level == *p.baselineLevel && int64(s.known[p.plan.Command.Item].Level) >= p.plan.Command.Count, nil
}

// Proof returns an independently owned retained live revival or current skill fact.
// Invalid Action values return ErrValue; the runtime closes the participant.
// A missing live revival is an error, never a fabricated snapshot revival.
func (s *State) Proof(a *gateway.Action, now int64) (wire.Record, error) {
	p, err := s.actionValue(a)
	if err != nil {
		return wire.Record{}, err
	}
	if a.AfterCheckpoint {
		if p.riseProof.Type != wire.Fact {
			return wire.Record{}, wire.ErrFrame
		}
		return cloneRecord(p.riseProof), nil
	}
	e := Event{Kind: Known, MS: now, Complete: s.complete["skills"], Skills: []Skill{}}
	for _, v := range s.known {
		e.Skills = append(e.Skills, v)
	}
	slices.SortFunc(e.Skills, func(a, b Skill) int { return cmp32(a.ID, b.ID) })
	return EventRecord(e, s.context(e))
}

// Invalidate retires spatial projection and proposals but preserves lifetime counters.
func (s *State) Invalidate() {
	s.clearProposals()
	s.observed.Reset()
	clear(s.actors)
	clear(s.motion)
	clear(s.dead)
	clear(s.vitals)
	clear(s.known)
	clear(s.inventory)
	clear(s.quests)
	clear(s.complete)
	s.selected = Ref{}
}

// Stop derives a fresh guarded movement step, or nil when no live self is observed.
func (s *State) Stop() *gateway.Step {
	v, ok := s.observed.Lookup(s.self)
	if !ok || s.dead[s.self] {
		return nil
	}
	return &gateway.Step{Value: Operation{Command: Command{Kind: Walk, X: v.X, Y: v.Y, Z: v.Z}}}
}

// Start is called before the first backend step. Foreign or nil Action values
// return ErrValue; the runtime closes the participant with an internal reason
// without logging the value or error text.
func (s *State) Start(a *gateway.Action) error {
	p, err := s.actionValue(a)
	if err != nil {
		return err
	}
	if !p.started && p.plan.Command.Kind == Talk {
		s.clearProposals()
	}
	p.started = true
	return nil
}
