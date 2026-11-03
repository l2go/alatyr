// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"cmp"
	"slices"
)

const (
	// defaultInterest matches a participant gateway that observes 128
	// objects, one of which is the participant itself.
	defaultInterest = 127
	// interestStep quantizes distance so that small movements do not reorder
	// the interest set: an object displaces another only from a nearer step.
	interestStep = 250
	// maxDisplaced bounds how many unpinned objects one visibility pass may
	// replace, so a crowd cannot churn a player's view.
	maxDisplaced = 8
)

// WithInterest bounds how many objects one player is shown at once. A host
// derives it from the observation bound it advertises to participants, less
// the participant itself. Values below one retain the default of 127.
func WithInterest(objects int) Option {
	return func(w *World) {
		if objects > 0 {
			w.interest = objects
		}
	}
}

// interestRank orders a player's objects by importance: pinned objects first,
// then by distance step, then by ID. A smaller rank is more important.
type interestRank struct {
	unpinned bool
	step     int64
	id       int32
}

func compareRank(a, b interestRank) int {
	if a.unpinned != b.unpinned {
		if a.unpinned {
			return 1
		}
		return -1
	}
	return cmp.Or(cmp.Compare(a.step, b.step), cmp.Compare(a.id, b.id))
}

func (w *World) rank(s *state, p *player, id int32, pos Point) interestRank {
	return interestRank{unpinned: !w.pinned(s, p, id), step: int64(distance3D(p.pos, pos) / interestStep), id: id}
}

// pinned reports an object the player acts on or that acts on the player:
// its target, the object of its current action, its dialogue partner, its
// party, and anyone targeting, attacking or hating it.
func (w *World) pinned(s *state, p *player, id int32) bool {
	if id == 0 || id == p.id {
		return false
	}
	c := &p.creature
	if id == c.target || id == c.attacking || id == c.ai.target || id == c.ai.object || id == c.ai.follow || id == c.ai.pawn || id == p.dialogueSpeaker.ID {
		return true
	}
	if p.party != nil && slices.ContainsFunc(p.party.members, func(m *player) bool { return m.id == id }) {
		return true
	}
	a, ok := s.actor(id)
	if !ok {
		return false
	}
	o := a.base()
	if o.target == p.id || o.attacking == p.id || o.ai.target == p.id {
		return true
	}
	n, ok := a.(*npc)
	return ok && slices.ContainsFunc(n.hate, func(h hateEntry) bool { return h.id == p.id })
}

// admit is the only way into a player's interest set. When the set is full,
// the candidate enters only by displacing a less important unpinned object,
// whose ObjectGone is sent before admit returns. The caller sends the
// candidate's appearance after a true result.
func (w *World) admit(s *state, p *player, id int32, pos Point) bool {
	if p.known[id] {
		return true
	}
	if len(p.known) >= w.interest {
		candidate := w.rank(s, p, id, pos)
		if candidate.unpinned && p.displaced >= maxDisplaced {
			return false
		}
		worst, ok := w.leastImportant(s, p)
		if !ok || !displaces(candidate, worst) {
			return false
		}
		w.hide(s, p, worst.id, false)
		if candidate.unpinned {
			p.displaced++
		}
	}
	p.known[id] = true
	s.change(p.ref(), PropertyKnownObject, RefValue(Ref{}), RefValue(s.ref(id)))
	return true
}

// displaces applies the hysteresis: a pinned object displaces any unpinned
// one; an unpinned object displaces only one at least a whole step farther.
func displaces(candidate, worst interestRank) bool {
	if !worst.unpinned {
		return false
	}
	return !candidate.unpinned || candidate.step < worst.step
}

// leastImportant returns the player's least important unpinned object.
func (w *World) leastImportant(s *state, p *player) (interestRank, bool) {
	var worst interestRank
	found := false
	for id := range p.known {
		pos, _, _, ok := s.objectAt(id)
		if !ok {
			continue
		}
		r := w.rank(s, p, id, pos)
		if r.unpinned && (!found || compareRank(r, worst) > 0) {
			worst, found = r, true
		}
	}
	return worst, found
}

// sees reports whether an event about id may reach p.
func sees(p *player, id int32) bool { return id == p.id || p.known[id] }

// deliver sends an event about objects to one observer, keeping references
// closed over the observer's interest set. The event's subject and every
// object it acts on must be seen, or the event is not sent. A state reference,
// the target an actor holds or follows, may name an unseen object, as a scene
// capture does. Target lists and the optional dropper are cut to what is seen.
func (w *World) deliver(p *player, e Event) {
	switch v := e.(type) {
	case NPCSeen:
		if !sees(p, v.NPC.ObjectID) {
			return
		}
	case PlayerSeen:
		if !sees(p, v.Player.Char.ObjectID) {
			return
		}
	case AttackStarted:
		if !sees(p, v.AttackerID) || !sees(p, v.TargetID) {
			return
		}
	case DamageNotice:
		if !sees(p, v.AttackerID) || !sees(p, v.TargetID) {
			return
		}
	case SkillCast:
		if !sees(p, v.CasterID) || !sees(p, v.TargetID) {
			return
		}
	case SkillLaunched:
		if !sees(p, v.CasterID) {
			return
		}
		v.Targets = slices.DeleteFunc(slices.Clone(v.Targets), func(id int32) bool { return !sees(p, id) })
		e = v
	case ItemPicked:
		if !sees(p, v.PlayerID) || !sees(p, v.ObjectID) {
			return
		}
	case ItemDropped:
		if !sees(p, v.ObjectID) {
			return
		}
		if !sees(p, v.DropperID) {
			v.DropperID = 0
			e = v
		}
	case TargetChosen:
		if !sees(p, v.ObjectID) {
			return
		}
	case Following:
		if !sees(p, v.ObjectID) {
			return
		}
	default:
		if id, ok := subject(e); ok && !sees(p, id) {
			return
		}
	}
	p.send(e)
}

// subject returns the object an event without other references is about.
func subject(e Event) (int32, bool) {
	switch v := e.(type) {
	case Moving:
		return v.ObjectID, true
	case Stopped:
		return v.ObjectID, true
	case PositionObserved:
		return v.ObjectID, true
	case TargetCleared:
		return v.ObjectID, true
	case Vitals:
		return v.ObjectID, true
	case CombatStance:
		return v.ObjectID, true
	case GaitChanged:
		return v.ObjectID, true
	case PostureChanged:
		return v.ObjectID, true
	case Died:
		return v.ObjectID, true
	case Revived:
		return v.ObjectID, true
	case Animation:
		return v.ObjectID, true
	case SkillCanceled:
		return v.ObjectID, true
	}
	return 0, false
}

// candidate is an unknown object a visibility pass may admit.
type candidate struct {
	id   int32
	pos  Point
	rank interestRank
}

func sortCandidates(cs []candidate) {
	slices.SortFunc(cs, func(a, b candidate) int { return compareRank(a.rank, b.rank) })
}
