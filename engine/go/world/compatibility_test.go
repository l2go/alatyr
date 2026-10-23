// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"
)

func TestLegacyObservationFieldsRemainCompatible(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		c := syntheticCharacter(1)
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		teacher := objectOf(t, w, ctx, 9102)
		drainEvents(entry.Events)
		if err := w.do(ctx, func(s *state) {
			p, n := s.players[1], s.npcs[teacher]
			w.sendPage(s, p, n, "page", "<a action=\"bypass -h "+fmtChoice(teacher)+"\">Learn</a>")
			w.markBroadcast(s, p, broadcastSkillList)
			w.tickBroadcast(s)
		}); err != nil {
			t.Fatal(err)
		}
		var page DialoguePage
		var skills SkillsShown
		for _, event := range drainEvents(entry.Events) {
			switch event := event.(type) {
			case DialoguePage:
				page = event
			case SkillsShown:
				skills = event
			}
		}
		if page.NPCID != teacher || page.HTML == "" || page.HTML != page.Dialogue.HTML || page.Dialogue.Speaker.ID != teacher {
			t.Fatalf("legacy page and source disagree: %+v", page)
		}
		if !skills.Complete || !slices.Equal(skills.Skills, c.Skills) || len(skills.Known) != len(skills.Skills) || skills.Known[0].Skill != skills.Skills[0] {
			t.Fatalf("legacy skill list and source disagree: %+v", skills)
		}
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		if len(scene.Dialogue.Affordances) != 1 {
			t.Fatalf("choices: got %+v", scene.Dialogue.Affordances)
		}
		choice := scene.Dialogue.Affordances[0]
		if len(choice.Params) != 0 || !affordanceAction(choice, fmtChoice(teacher)) {
			t.Fatalf("choice changed the legacy parameter shape: %+v", choice)
		}
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbChoose, Target: scene.Dialogue.Speaker, AffordanceID: choice.ID, Deadline: scene.End.Time + Time(time.Second)})
		if commit.Outcome.Stage != StageDone {
			t.Fatalf("choice with additive parameters: %+v", commit.Outcome)
		}
		var list LearnList
		for _, event := range drainEvents(entry.Events) {
			if event, ok := event.(LearnList); ok {
				list = event
			}
		}
		if list.NPCID != teacher || list.Dialogue.Speaker.ID != teacher || len(list.Skills) == 0 || len(list.Dialogue.Affordances) != len(list.Skills) {
			t.Fatalf("legacy lesson list and source disagree: %+v", list)
		}
		a := &list.Dialogue.Affordances[0]
		if !slices.Equal(a.Params, a.Parameters) || len(a.Params) != 2 {
			t.Fatalf("legacy lesson parameters: %+v", a)
		}
		// Legacy and complete parameter slices are independent, as are both
		// skill representations. None may alias live state or another form.
		before := captureTest(t, w, ctx, entry.Self.Entity)
		a.Params[0].Name = "changed"
		if a.Parameters[0].Name != "skill" {
			t.Fatal("legacy parameter projection aliases complete parameters")
		}
		skills.Skills[0].Level = 99
		if skills.Known[0].Skill.Level != 1 {
			t.Fatal("legacy skill list aliases source availability")
		}
		a.Parameters[0].Value = IntValue(999)
		after := captureTest(t, w, ctx, entry.Self.Entity)
		if !reflect.DeepEqual(before.Dialogue, after.Dialogue) || !reflect.DeepEqual(before.Skills, after.Skills) {
			t.Fatal("consumer changed source state through compatibility fields")
		}
	})
}

func TestLegacyLeaveSelectsCurrentIncarnation(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, store, ctx := startObservedWorld(t, &log)
		first := enterTest(t, w, ctx, syntheticCharacter(1))
		if err := w.Leave(ctx, first.Self.Entity.ID); err != nil {
			t.Fatal(err)
		}
		c := syntheticCharacter(1)
		c.Name = "Replacement"
		replacement := enterTest(t, w, ctx, c)
		log.reset()
		if err := w.Leave(ctx, first.Self.Entity.ID); err != nil {
			t.Fatal(err)
		}
		if store.saved.Name != c.Name {
			t.Fatalf("legacy save: got %q, want %q", store.saved.Name, c.Name)
		}
		commits := log.read()
		if len(commits) != 1 || commits[0].Intent.ID.Owner != replacement.Self.Entity || commits[0].Outcome.Stage != StageDone {
			t.Fatalf("legacy departure owner: got %+v", commits)
		}
		if err := w.Leave(ctx, first.Self.Entity.ID); err != nil {
			t.Fatalf("repeated absent legacy departure: %v", err)
		}
	})
}
