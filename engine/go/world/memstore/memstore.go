// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package memstore

import (
	"context"
	"github.com/l2go/alatyr/engine/go/world"
	"slices"
	"sync"
)

func (s *Store) Characters(_ context.Context, account string) ([]world.Character, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []world.Character
	for _, c := range s.chars {
		if c.Account == account {
			out = append(out, c.Clone())
		}
	}
	return out, nil
}

func (s *Store) PutCharacter(_ context.Context, c world.Character) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if i := slices.IndexFunc(s.chars, func(o world.Character) bool { return o.ObjectID == c.ObjectID }); i >= 0 {
		s.chars[i] = c.Clone()
		return nil
	}
	s.chars = append(s.chars, c.Clone())
	return nil
}

func (s *Store) PutSkill(_ context.Context, objectID int32, k world.Skill) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	i := slices.IndexFunc(s.chars, func(o world.Character) bool { return o.ObjectID == objectID })
	if i < 0 {
		return nil
	}
	c := &s.chars[i]
	if j := slices.IndexFunc(c.Skills, func(o world.Skill) bool { return o.ID == k.ID }); j >= 0 {
		c.Skills[j] = k
		return nil
	}
	c.Skills = append(c.Skills, k)
	return nil
}
