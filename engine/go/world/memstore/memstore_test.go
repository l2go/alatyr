// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package memstore

import (
	"github.com/l2go/alatyr/engine/go/world"
	"sync"
	"testing"
)

func TestDetachedConcurrentSnapshots(t *testing.T) {
	s := New()
	c := world.Character{ObjectID: 1, Account: "test", Items: []world.Item{{Count: 1}}, Skills: []world.Skill{{ID: 3, Level: 1}}, Shortcuts: []world.Shortcut{{ID: 3}}, QuestVars: []world.QuestVar{{Value: "saved"}}}
	if err := s.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	c.Items[0].Count = 9
	c.Shortcuts[0].ID = 9
	c.QuestVars[0].Value = "changed"
	var wg sync.WaitGroup
	for range 4 {
		wg.Go(func() {
			for range 10 {
				s.PutSkill(t.Context(), 1, world.Skill{ID: 3, Level: 2})
				cs, _ := s.Characters(t.Context(), "test")
				cs[0].Skills[0].Level = 99
			}
		})
	}
	wg.Wait()
	cs, err := s.Characters(t.Context(), "test")
	if err != nil {
		t.Fatal(err)
	}
	if cs[0].Items[0].Count != 1 || cs[0].Shortcuts[0].ID != 3 || cs[0].QuestVars[0].Value != "saved" || cs[0].Skills[0].Level != 2 {
		t.Fatal("alias or lost update")
	}
}
