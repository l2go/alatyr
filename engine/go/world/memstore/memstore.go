// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package memstore

import (
	"context"
	"slices"
	"sync"

	"github.com/l2go/alatyr/engine/go/world"
)

// Store keeps detached character snapshots in memory. The zero value is empty
// and ready to use.
type Store struct {
	mu    sync.Mutex
	chars []world.Character
}

// New returns an empty Store.
func New() *Store { return &Store{} }

// Characters returns detached copies of the account's characters in the order
// they were first stored. It never fails.
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

// PutCharacter stores a detached copy of c, replacing any character with the
// same ObjectID. It never fails.
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

// PutSkill records skill k for the stored character objectID, replacing a
// skill with the same ID. A character that has not been stored is ignored.
// It never fails.
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
