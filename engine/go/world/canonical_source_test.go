// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"reflect"
	"testing"
	"testing/synctest"
	"time"
)

func TestCanonicalEventSourcesRemainDetached(t *testing.T) {
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
		if page.Dialogue.HTML == "" || page.Dialogue.Speaker.ID != teacher || len(page.Dialogue.Affordances) != 1 {
			t.Fatalf("page source: got %+v, want teacher and one choice", page)
		}
		if !skills.Complete || len(skills.Known) != 1 || skills.Known[0].Skill != c.Skills[0] {
			t.Fatalf("skill source: got %+v, want one complete effective skill", skills)
		}
		scene := captureTest(t, w, ctx, entry.Self.Entity)
		if len(scene.Dialogue.Affordances) != 1 {
			t.Fatalf("choices: got %+v, want one", scene.Dialogue.Affordances)
		}
		choice := scene.Dialogue.Affordances[0]
		if !affordanceAction(choice, fmtChoice(teacher)) {
			t.Fatalf("choice parameters: got %+v, want issued action", choice)
		}
		page.Dialogue.Affordances[0].Parameters[0].Value = LabelValue("changed")
		if after := captureTest(t, w, ctx, entry.Self.Entity); !reflect.DeepEqual(scene.Dialogue, after.Dialogue) {
			t.Fatal("page mutation changed live dialogue, want detached parameters")
		}
		commit := executeTest(t, w, ctx, Request{Owner: entry.Self.Entity, Verb: VerbChoose, Target: scene.Dialogue.Speaker, AffordanceID: choice.ID, Deadline: scene.End.Time + Time(time.Second)})
		if commit.Outcome.Stage != StageDone {
			t.Fatalf("choice outcome: got %+v, want done", commit.Outcome)
		}
		var list LearnList
		for _, event := range drainEvents(entry.Events) {
			if event, ok := event.(LearnList); ok {
				list = event
			}
		}
		if list.Dialogue.Speaker.ID != teacher || len(list.Dialogue.Affordances) == 0 {
			t.Fatalf("lesson source: got %+v, want teacher and issued lessons", list)
		}
		a := &list.Dialogue.Affordances[0]
		if len(a.Parameters) != 2 || len(a.Cost) != 1 {
			t.Fatalf("lesson authority: got %+v, want skill, level and cost", a)
		}
		before := captureTest(t, w, ctx, entry.Self.Entity)
		a.Parameters[0].Value = IntValue(999)
		a.Cost[0].Quantity = 999
		skills.Known[0].Skill.Level = 99
		after := captureTest(t, w, ctx, entry.Self.Entity)
		if !reflect.DeepEqual(before.Dialogue, after.Dialogue) || !reflect.DeepEqual(before.Skills, after.Skills) {
			t.Fatal("consumer mutation changed source state, want detached lesson and skill data")
		}
	})
}

func TestWorldHasOnlyCanonicalSourceAPI(t *testing.T) {
	for _, tc := range []struct {
		name   string
		typ    reflect.Type
		fields []string
	}{
		{"affordance", reflect.TypeFor[Affordance](), []string{"Params"}},
		{"page", reflect.TypeFor[DialoguePage](), []string{"NPCID", "HTML"}},
		{"lessons", reflect.TypeFor[LearnList](), []string{"NPCID", "Skills"}},
		{"skills", reflect.TypeFor[SkillsShown](), []string{"Skills"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			for _, field := range tc.fields {
				if _, ok := tc.typ.FieldByName(field); ok {
					t.Fatalf("got obsolete %s.%s, want only canonical fields", tc.typ, field)
				}
			}
		})
	}
	for _, method := range []string{"Leave", "Logout"} {
		t.Run(method, func(t *testing.T) {
			if _, ok := reflect.TypeFor[*World]().MethodByName(method); ok {
				t.Fatalf("got ID-based %s, want captured-ref departure only", method)
			}
		})
	}
}
