// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"reflect"
	"slices"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestIssuedEventsDoNotBorrowLaterSceneAuthority(t *testing.T) {
	tr, s := fixture()
	s.Skills = []world.KnownSkill{{Skill: world.Skill{ID: 301, Level: 1}}}
	choice, lessons := choiceScene(s), lessonScene(s)
	later := lessons
	later.Dialogue = world.Dialogue{Speaker: choice.Dialogue.Speaker, Page: "B", HTML: "<p>B</p>"}
	later.Skills = slices.Clone(s.Skills)
	later.Skills[0].Disabled = true
	for _, tc := range []struct {
		name   string
		event  world.Event
		source world.Scene
	}{
		{"page", world.DialoguePage{Dialogue: choice.Dialogue}, choice},
		{"lessons", world.LearnList{Dialogue: lessons.Dialogue}, lessons},
		{"skills", world.SkillsShown{Known: s.Skills, Complete: true}, s},
	} {
		t.Run(tc.name, func(t *testing.T) {
			want, err := tr.events(tc.event, tc.source, 0)
			if err != nil || len(want) == 0 {
				t.Fatalf("original source: got %+v, %v", want, err)
			}
			got, err := tr.events(tc.event, later, 0)
			if err != nil || !reflect.DeepEqual(got, want) {
				t.Fatalf("delayed translation: got %+v/%v, want %+v", got, err, want)
			}
			if _, _, err := factRecords(got, 1, 1, tc.source.End.Time, 0); err != nil {
				t.Fatalf("raw facts: %v", err)
			}
		})
	}
	if fs, err := tr.events(world.SkillsShown{Known: s.Skills}, later, 0); !errors.Is(err, errSource) || len(fs) != 0 {
		t.Fatalf("incomplete source: got %+v/%v, want no facts", fs, err)
	}
	if fs, err := tr.events(world.AffordancesChanged{Affordances: choice.Dialogue.Affordances}, later, 0); err != nil || len(fs) != 0 {
		t.Fatalf("superseded refresh: got %+v/%v, want no repeated facts", fs, err)
	}
}

func TestChooseBindsExactActionParameter(t *testing.T) {
	_, s := fixture()
	s = choiceScene(s)
	s.Dialogue.HTML = strings.Replace(s.Dialogue.HTML, "</body>", "<a action=\"bypass -h npc_2_Describe\">Describe</a></body>", 1)
	second := s.Dialogue.Affordances[0]
	second.ID = 12
	second.Parameters = []world.Parameter{{Name: "action", Value: world.LabelValue("npc_2_Describe")}}
	// Both synthetic options have the same target; order grants no authority.
	s.Dialogue.Affordances = append([]world.Affordance{second}, s.Dialogue.Affordances...)
	for _, tc := range []struct {
		action string
		id     uint64
	}{
		{"npc_2_SkillList", 10}, {"npc_2_Describe", 12},
	} {
		t.Run(tc.action, func(t *testing.T) {
			c := command(6)
			c.Payload[1] = wire.Data(68, true, tc.action)
			p, err := prepare(c, s, s.End.Time)
			if err != nil || p.refusal != "" || p.request.AffordanceID != tc.id {
				t.Fatalf("binding: got %+v/%v, want affordance %d", p, err, tc.id)
			}
		})
	}
}

func TestChooseRejectsMissingOrAmbiguousActionBinding(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*world.Dialogue)
	}{
		{"missing parameter", func(d *world.Dialogue) { d.Affordances[0].Parameters = nil }},
		{"wrong name", func(d *world.Dialogue) { d.Affordances[0].Parameters[0].Name = "other" }},
		{"wrong type", func(d *world.Dialogue) { d.Affordances[0].Parameters[0].Value = world.IntValue(1) }},
		{"unmatched action", func(d *world.Dialogue) { d.Affordances[0].Parameters[0].Value = world.LabelValue("other") }},
		{"two affordances", func(d *world.Dialogue) {
			a := d.Affordances[0]
			a.ID++
			d.Affordances = append(d.Affordances, a)
		}},
		{"two page options", func(d *world.Dialogue) {
			d.HTML = strings.Replace(d.HTML, "</body>", "<a action=\"bypass -h npc_2_SkillList\">Again</a></body>", 1)
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, s := fixture()
			s = choiceScene(s)
			tc.mutate(&s.Dialogue)
			p, err := prepare(command(6), s, s.End.Time)
			if !errors.Is(err, errSource) || p.request.AffordanceID != 0 {
				t.Fatalf("binding: got %+v/%v, want rejected source without authority", p, err)
			}
		})
	}
}
