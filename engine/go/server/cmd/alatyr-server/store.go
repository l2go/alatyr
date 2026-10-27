// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"sync"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// Hidden templates remain durable but cannot acquire live references or affect
// stats. The host merges them back into every detached write, outside Run.
// This keeps catalog compatibility out of the world's gameplay rules.
type hidden struct {
	items  []world.Item
	skills []world.Skill
}
type characterStore struct {
	store   Store
	catalog *catalog.Catalog
	mu      sync.Mutex
	hidden  map[int32]hidden
}

func (s *characterStore) load(c world.Character) world.Character {
	c = c.Clone()
	var h hidden
	items := c.Items[:0]
	for _, it := range c.Items {
		if _, ok := s.catalog.Item(it.TemplateID); ok {
			items = append(items, it)
		} else {
			h.items = append(h.items, it)
		}
	}
	skills := c.Skills[:0]
	for _, k := range c.Skills {
		if _, ok := s.catalog.Skill(k.ID, k.Level); ok {
			skills = append(skills, k)
		} else {
			h.skills = append(h.skills, k)
		}
	}
	c.Items = items
	c.Skills = skills
	s.mu.Lock()
	s.hidden[c.ObjectID] = h
	s.mu.Unlock()
	return c
}
func (s *characterStore) restore(c world.Character) world.Character {
	c = c.Clone()
	s.mu.Lock()
	h := s.hidden[c.ObjectID]
	live := make(map[int32]bool, len(c.Skills))
	for _, k := range c.Skills {
		live[k.ID] = true
	}
	retained := make([]world.Skill, 0, len(h.skills))
	for _, k := range h.skills {
		if !live[k.ID] {
			retained = append(retained, k)
		}
	}
	h.skills = retained
	s.hidden[c.ObjectID] = h
	s.mu.Unlock()
	c.Items = append(c.Items, h.items...)
	c.Skills = append(c.Skills, h.skills...)
	return c
}
func (s *characterStore) PutCharacter(ctx context.Context, c world.Character) error {
	return s.store.PutCharacter(ctx, s.restore(c))
}
func (s *characterStore) PutLearning(ctx context.Context, c world.Character) error {
	return s.store.PutLearning(ctx, s.restore(c))
}
