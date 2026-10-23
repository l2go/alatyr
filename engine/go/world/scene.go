// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"slices"
	"time"
)

// Fence identifies a relocation within one incarnation. Zero precedes its
// first teleport. It is independent of Ref.Gen and any consumer session epoch.
type Fence uint64

// Domain identifies a source of observable state, not a protocol registry.
type Domain string

const (
	// DomainSelf contains the player's state.
	DomainSelf Domain = "self"
	// DomainObjects contains the currently known objects.
	DomainObjects Domain = "objects"
	// DomainInventory contains all carried inventory rows.
	DomainInventory Domain = "inventory"
	// DomainSkills contains effective skills and their reuse state.
	DomainSkills Domain = "skills"
	// DomainDialogue contains the current authored page and choices.
	DomainDialogue Domain = "dialogue"
	// DomainTrade contains trade state when that mechanic exists.
	DomainTrade Domain = "trade"
	// DomainQuests contains quest state when that mechanic exists.
	DomainQuests Domain = "quests"
)

// Coverage reports source completeness. Absent explicitly declares that the
// world has no mechanic for this domain; only that declaration proves an empty
// domain without capture. Complete is never inferred from an empty collection.
type Coverage struct {
	Domain           Domain
	Complete, Absent bool
}

// Marker bounds an atomic scene capture. Sequence is the source commitment
// boundary, not a new publication. End.Ready requires all domain coverage and
// completion of the current relocation. Seq is the last event publication,
// independent of Sequence. Capture does not allocate new intents or events.
type Marker struct {
	Sequence uint64
	Seq      uint64
	Time     Time
	Fence    Fence
	Ready    bool
}

// Observation is one currently known object with its captured incarnation.
// State is NPCSeen, PlayerSeen or ItemDropped. Movement is current state, not
// a replay of arrivals or attacks. Targets may require host-side redaction.
type Observation struct {
	Entity      Ref
	State       Event
	Target      Ref
	Moving      bool
	Destination Point
}

// Carried is one complete inventory row and its incarnation.
type Carried struct {
	Entity Ref
	Item   Item
}

// KnownSkill combines an effective skill with its runtime availability.
type KnownSkill struct {
	Skill             Skill
	Passive, Disabled bool
	ReuseUntil        Time
}

// Dialogue is current authored presentation and its original affordances.
// HTML is presentation, not an executable choice or a new utterance.
type Dialogue struct {
	Speaker     Ref
	Page, HTML  string
	Affordances []Affordance
}

// Scene is a detached, atomic capture of the observer's actual known scene.
// Begin precedes its data and End is the final marker. It contains no outcomes,
// effects or replayed live events and never renews an affordance's authority.
// Consumers must inspect Coverage and End.Ready rather than assume completeness.
type Scene struct {
	Begin       Marker
	Self        Self
	Target      Ref
	CombatUntil Time
	Moving      bool
	Destination Point
	Objects     []Observation
	Inventory   []Carried
	Skills      []KnownSkill
	Dialogue    Dialogue
	Coverage    []Coverage
	End         Marker
}

// Capture reads all supported observable sources in a single owning-loop turn.
// It does not refresh visibility, issue choices, apply effects or clear reuse.
func (w *World) Capture(ctx context.Context, owner Ref) (Scene, error) {
	var scene Scene
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[owner.ID]
		if !ok || p.ref() != owner {
			result = ErrNotPresent
			return
		}
		scene = w.capture(s, p)
	})
	if err != nil {
		return Scene{}, err
	}
	return scene, result
}

// Subscribe atomically replaces the stream and captures the same live player.
// It works after overflow without reentering or replaying commands. It cannot
// restore a departed incarnation or a stopped world. Pending old notifications
// are flushed before the barrier and are not delivered as new live state.
func (w *World) Subscribe(ctx context.Context, owner Ref) (Scene, Subscription, error) {
	var scene Scene
	var subscription Subscription
	var result error
	err := w.do(ctx, func(s *state) {
		p, ok := s.players[owner.ID]
		if !ok || p.ref() != owner {
			result = ErrNotPresent
			return
		}
		w.flush(s)
		p.stream.close(CloseReplaced)
		p.stream = newEventStream()
		p.slow = false
		scene = w.capture(s, p)
		subscription = p.stream.subscription()
	})
	if err != nil {
		return Scene{}, Subscription{}, err
	}
	return scene, subscription, result
}

func (w *World) capture(s *state, p *player) Scene {
	now := time.Now()
	marker := Marker{Sequence: s.commitSeq, Seq: s.eventSeq, Time: s.at(now), Fence: p.fence}
	scene := Scene{Begin: marker, Self: w.selfOf(p), Target: s.ref(p.target), CombatUntil: s.at(s.stance[p.id])}
	scene.Moving = p.move != nil
	if scene.Moving {
		scene.Destination = p.move.dest
	}
	objectsComplete := true
	ids := make([]int32, 0, len(p.known))
	for id := range p.known {
		ids = append(ids, id)
	}
	slices.Sort(ids)
	for _, id := range ids {
		o := Observation{Entity: s.ref(id)}
		objectsComplete = objectsComplete && o.Entity != (Ref{})
		if a, ok := s.actor(id); ok {
			c := a.base()
			o.Target = s.ref(c.target)
			o.Moving = c.move != nil
			if o.Moving {
				o.Destination = c.move.dest
			}
			switch v := a.(type) {
			case *npc:
				o.State = NPCSeen{NPC: v.view(now)}
			case *player:
				o.State = PlayerSeen{Player: w.selfOf(v)}
			}
		} else if g, ok := s.ground[id]; ok {
			t, known := w.cat.Item(g.templateID)
			objectsComplete = objectsComplete && known
			o.State = ItemDropped{ObjectID: g.id, TemplateID: g.templateID, Count: g.count, Pos: g.pos, Stackable: t.Stackable()}
		} else {
			objectsComplete = false
			continue
		}
		scene.Objects = append(scene.Objects, o)
	}
	inventoryComplete := true
	for _, it := range p.char.Items {
		r := p.itemRefs[it.ObjectID]
		_, known := w.cat.Item(it.TemplateID)
		inventoryComplete = inventoryComplete && r != (Ref{}) && known
		scene.Inventory = append(scene.Inventory, Carried{Entity: r, Item: it})
	}
	var skillsComplete bool
	scene.Skills, skillsComplete = w.knownSkills(s, p, now)
	scene.Dialogue = w.dialogueOf(p)
	dialogueComplete := p.dialogue == "" || p.dialogueSpeaker != (Ref{}) && p.dialogueHTML != ""
	scene.Coverage = []Coverage{
		{Domain: DomainSelf, Complete: true},
		{Domain: DomainObjects, Complete: objectsComplete},
		{Domain: DomainInventory, Complete: inventoryComplete},
		{Domain: DomainSkills, Complete: skillsComplete},
		{Domain: DomainDialogue, Complete: dialogueComplete},
		// These declarations must be removed when either mechanic is introduced.
		// No event or command path currently implements trade or quests.
		{Domain: DomainTrade, Complete: true, Absent: true},
		{Domain: DomainQuests, Complete: true, Absent: true},
	}
	marker.Ready = !p.teleporting
	for _, coverage := range scene.Coverage {
		marker.Ready = marker.Ready && coverage.Complete
	}
	scene.End = marker
	return scene
}

func (w *World) knownSkills(s *state, p *player, now time.Time) ([]KnownSkill, bool) {
	var skills []KnownSkill
	complete := true
	for _, k := range p.shownSkills() {
		sk, known := w.cat.Skill(k.ID, k.Level)
		complete = complete && known
		r := p.reuse[k.ID]
		skills = append(skills, KnownSkill{Skill: k, Passive: sk.Passive, Disabled: r.until.After(now), ReuseUntil: s.at(r.until)})
	}
	return skills, complete
}

func (w *World) dialogueOf(p *player) Dialogue {
	return Dialogue{Speaker: p.dialogueSpeaker, Page: p.dialogue, HTML: p.dialogueHTML, Affordances: cloneAffordances(p.affordances)}
}

func cloneDialogue(d Dialogue) Dialogue {
	d.Affordances = cloneAffordances(d.Affordances)
	return d
}
