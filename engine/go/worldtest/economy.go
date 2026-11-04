// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"fmt"
	"math"

	"github.com/l2go/alatyr/engine/go/world"
)

// Economy checks commitments as they arrive: a refused intent that still
// changed the world, in either order, a negative or mismatched resource
// balance, or an item consumed twice for one intent. A zero Window remembers
// every intent; a positive one remembers the latest Window refused intents,
// intents with effects and consumptions, so a long run keeps flat memory.
type Economy struct {
	Window   int
	used     recent[consumption]
	refused  recent[world.IntentID]
	effected recent[world.IntentID]
}

type consumption struct {
	intent world.IntentID
	item   world.Ref
}

// recent is a set that forgets its oldest key beyond limit when limit > 0.
type recent[K comparable] struct {
	seen  map[K]bool
	order []K
	next  int
}

func (r *recent[K]) has(k K) bool { return r.seen[k] }

func (r *recent[K]) add(k K, limit int) {
	if r.seen[k] {
		return
	}
	if r.seen == nil {
		r.seen = map[K]bool{}
	}
	r.seen[k] = true
	if limit <= 0 {
		return
	}
	if len(r.order) < limit {
		r.order = append(r.order, k)
		return
	}
	delete(r.seen, r.order[r.next])
	r.order[r.next] = k
	r.next = (r.next + 1) % limit
}

// Observe applies one commitment.
func (e *Economy) Observe(c world.Commit) error {
	if c.Outcome.Stage == world.StageRefused || c.Outcome.Stage == world.StageExpired {
		if c.Intent.ID.N == 0 || c.Outcome.Intent != c.Intent.ID || len(c.Groups) != 0 {
			return fmt.Errorf("refused world intent %v changed the world or lost its identity", c.Intent.ID)
		}
		if e.effected.has(c.Intent.ID) {
			return fmt.Errorf("refused world intent %v changed the world earlier", c.Intent.ID)
		}
		e.refused.add(c.Intent.ID, e.Window)
	}
	for _, g := range c.Groups {
		if e.refused.has(g.Cause.Intent) {
			return fmt.Errorf("refused world intent %v produced a deferred effect", g.Cause.Intent)
		}
		if g.Cause.Intent.N != 0 {
			e.effected.add(g.Cause.Intent, e.Window)
		}
		for _, effect := range g.Effects {
			moved, ok := effect.(world.ResourceTransferred)
			if !ok {
				continue
			}
			amount, integer := moved.Amount.Integer()
			if !integer {
				continue
			}
			if amount < 0 {
				return fmt.Errorf("negative resource transfer")
			}
			for _, side := range []struct {
				location world.ResourceLocation
				balance  world.Balance
				debit    bool
			}{{moved.From, moved.FromBalance, true}, {moved.To, moved.ToBalance, false}} {
				if side.location.Entity == (world.Ref{}) {
					continue
				}
				before, okBefore := side.balance.Before.Integer()
				after, okAfter := side.balance.After.Integer()
				if !okBefore || !okAfter || before < 0 || after < 0 {
					return fmt.Errorf("invalid integer resource balance")
				}
				if side.debit {
					if before < amount || before-amount != after {
						return fmt.Errorf("resource debit underflow or mismatch")
					}
				} else if after < before || after-before != amount {
					return fmt.Errorf("resource credit overflow or mismatch")
				}
			}
			if moved.To.Place == world.PlaceConsumption && moved.Resource.Kind == world.ResourceItem {
				key := consumption{g.Cause.Intent, moved.From.Item}
				if e.used.has(key) || amount != 1 {
					return fmt.Errorf("item consumed more than once per intent")
				}
				e.used.add(key, e.Window)
			}
		}
	}
	return nil
}

// CheckCharacter reports invalid vitals, negative progression or inventory rows.
func CheckCharacter(c world.Character) error { return checkCharacter(c) }
func checkCharacter(c world.Character) error {
	for _, v := range [][2]float64{{c.CurHP, c.MaxHP}, {c.CurMP, c.MaxMP}} {
		if math.IsNaN(v[0]) || math.IsInf(v[0], 0) || math.IsNaN(v[1]) || math.IsInf(v[1], 0) || v[1] < 0 || v[0] < 0 || v[0] > v[1] {
			return fmt.Errorf("vital outside [0,max]: %v", v)
		}
	}
	seen := map[int32]bool{}
	for _, it := range c.Items {
		if it.Count < 0 || seen[it.ObjectID] {
			return fmt.Errorf("invalid inventory row")
		}
		seen[it.ObjectID] = true
	}
	if c.SP < 0 || c.Exp < 0 {
		return fmt.Errorf("negative progression balance")
	}
	return nil
}
func checkEconomy(commits []world.Commit) error {
	e := Economy{}
	for _, c := range commits {
		if err := e.Observe(c); err != nil {
			return err
		}
	}
	return nil
}
