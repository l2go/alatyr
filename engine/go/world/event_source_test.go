// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"testing/synctest"
	"time"
)

func TestEveryEventHasPublicationTimeAndSequence(t *testing.T) {
	values := []Event{
		Announcement{},
		NPCSeen{},
		PlayerSeen{},
		ObjectGone{},
		SelfChanged{},
		PositionObserved{},
		TargetChosen{},
		TargetDetails{},
		TargetCleared{},
		Vitals{},
		AttackStarted{},
		DamageNotice{},
		CombatStance{},
		GaitChanged{},
		PostureChanged{},
		Died{},
		Animation{},
		Following{},
		ZoneChanged{},
		Teleported{},
		Revived{},
		DialoguePage{},
		LearnList{},
		SkillsShown{},
		SkillCast{},
		SkillLaunched{},
		SkillCanceled{},
		SkillReuse{},
		Rewarded{},
		LevelChanged{},
		ItemDropped{},
		ItemPicked{},
		InventoryChanged{},
		ActionIgnored{},
		Moving{},
		Stopped{},
		LeftWorld{},
		GameClock{},
		AffordancesChanged{},
	}
	w := New(nil, nil)
	p := &player{stream: newEventStream()}
	for _, value := range values {
		t.Run(reflect.TypeOf(value).Name(), func(t *testing.T) {
			if value.Time() != 0 || value.Seq() != 0 {
				t.Fatal("synthetic event claims a publication")
			}
			p.deliver(w.s, value)
			got := <-p.stream.events
			if reflect.TypeOf(got) != reflect.TypeOf(value) || got.Time() < 0 || got.Time() > w.s.now() || got.Seq() != w.s.eventSeq {
				t.Fatalf("publication: got %#v, sequence %d", got, w.s.eventSeq)
			}
		})
	}
	if w.s.eventSeq != uint64(len(values)) {
		t.Fatalf("sequence: got %d, want %d", w.s.eventSeq, len(values))
	}
}

func TestDelayedEventsRetainTheirIssuedSource(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		c := syntheticCharacter(1)
		c.Skills = []Skill{{ID: 9201, Level: 1}}
		entry := enterTest(t, w, ctx, c)
		teacher := objectOf(t, w, ctx, 9102)
		drainEvents(entry.Events)
		publish := func(page string, disabled bool) {
			t.Helper()
			if err := w.do(ctx, func(s *state) {
				p, n := s.players[1], s.npcs[teacher]
				w.sendPage(s, p, n, page, "<a action=\"bypass -h "+fmtChoice(teacher)+"\">"+page+"</a>")
				if disabled {
					p.reuse = make(map[int32]reuseStamp)
					p.reuse[9201] = reuseStamp{until: time.Now().Add(time.Second)}
				}
				w.markBroadcast(s, p, broadcastSkillList)
				w.tickBroadcast(s)
			}); err != nil {
				t.Fatal(err)
			}
		}
		publish("A", false)
		advanceVirtual(10 * time.Millisecond)
		publish("B", true)
		latest := captureTest(t, w, ctx, entry.Self.Entity)
		advanceVirtual(10 * time.Millisecond)
		var pages []DialoguePage
		var skills []SkillsShown
		var previous uint64
		for _, event := range drainEvents(entry.Events) {
			if event.Seq() <= previous || event.Seq() > latest.End.Seq || event.Time() > latest.End.Time {
				t.Fatalf("queued marker changed: time %v, seq %d, previous %d", event.Time(), event.Seq(), previous)
			}
			previous = event.Seq()
			switch event := event.(type) {
			case DialoguePage:
				pages = append(pages, event)
			case SkillsShown:
				skills = append(skills, event)
			}
		}
		if len(pages) != 2 || pages[0].Dialogue.Page != "A" || pages[1].Dialogue.Page != "B" || pages[0].Time() >= pages[1].Time() {
			t.Fatalf("delayed pages: got %+v", pages)
		}
		a, b := pages[0].Dialogue.Affordances, pages[1].Dialogue.Affordances
		if len(a) != 1 || len(b) != 1 || a[0].ID == b[0].ID || !affordanceAction(a[0], fmtChoice(teacher)) || !reflect.DeepEqual(b, latest.Dialogue.Affordances) {
			t.Fatalf("page authority: A %+v, B %+v, latest %+v", a, b, latest.Dialogue)
		}
		if len(skills) != 2 || !skills[0].Complete || !skills[1].Complete || skills[0].Skills[0].Disabled || !skills[1].Skills[0].Disabled {
			t.Fatalf("runtime flags: got %+v", skills)
		}
		pages[1].Dialogue.Affordances[0].Params[0].Name = "changed"
		skills[1].Skills[0].Skill.Level = 99
		after := captureTest(t, w, ctx, entry.Self.Entity)
		if !reflect.DeepEqual(after.Dialogue, latest.Dialogue) || !reflect.DeepEqual(after.Skills, latest.Skills) {
			t.Fatal("consumer mutated live authority or skills")
		}
	})
}

func TestMultiplePagesInOneTurnKeepSeparateAuthority(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		var log commitLog
		w, _, ctx := startObservedWorld(t, &log)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		teacher := objectOf(t, w, ctx, 9102)
		drainEvents(entry.Events)
		if err := w.do(ctx, func(s *state) {
			p, n := s.players[1], s.npcs[teacher]
			w.sendPage(s, p, n, "A", "<a action=\"bypass -h "+fmtChoice(teacher)+"\">A</a>")
			w.showSkillList(s, p, n)
			w.sendPage(s, p, n, "B", "<p>B</p>")
		}); err != nil {
			t.Fatal(err)
		}
		var pages []DialoguePage
		var lessons []LearnList
		for _, event := range drainEvents(entry.Events) {
			switch event := event.(type) {
			case DialoguePage:
				pages = append(pages, event)
			case LearnList:
				lessons = append(lessons, event)
			}
		}
		if len(pages) != 2 || len(lessons) != 1 || len(pages[0].Dialogue.Affordances) != 1 || len(pages[1].Dialogue.Affordances) != 0 {
			t.Fatalf("turn sources: pages %+v, lessons %+v", pages, lessons)
		}
		list := lessons[0]
		if list.Dialogue.Page != "A" || len(list.Skills) != len(list.Dialogue.Affordances) || len(list.Skills) == 0 || list.Seq() <= pages[0].Seq() || list.Seq() >= pages[1].Seq() {
			t.Fatalf("lesson source: got %+v", list)
		}
		list.Dialogue.Affordances[0].Cost[0].Quantity = 999
		list.Skills[0].SP = 999
		if scene := captureTest(t, w, ctx, entry.Self.Entity); scene.Dialogue.Page != "B" || len(scene.Dialogue.Affordances) != 0 {
			t.Fatalf("stale lesson replaced live page: %+v", scene.Dialogue)
		}
	})
}

func TestSkillAvailabilityUsesPublicationTime(t *testing.T) {
	for _, tc := range []struct {
		name     string
		at       Time
		disabled bool
	}{
		{"before expiry", 9, true}, {"at expiry", 10, false}, {"after expiry", 11, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			source := SkillsShown{Complete: true, Skills: []KnownSkill{{Skill: Skill{ID: 1, Level: 1}, Disabled: true, ReuseUntil: 10}}}
			got := publishEvent(source, stamp{time: tc.at, seq: 1}).(SkillsShown)
			if got.Skills[0].Disabled != tc.disabled || got.Time() != tc.at || !source.Skills[0].Disabled {
				t.Fatalf("availability: got %+v, want disabled %v without aliasing", got, tc.disabled)
			}
		})
	}
}

func TestEventOverflowIsBoundedAndInactiveFlushDoesNotPublish(t *testing.T) {
	w := New(nil, nil)
	p := &player{stream: newEventStream()}
	w.s.players[1] = p
	for range cap(p.stream.events) + 1 {
		p.send(ActionIgnored{})
	}
	w.flush(w.s)
	if len(p.stream.events) != 64 || w.s.eventSeq != 64 || <-p.stream.end != CloseOverflow || !p.slow {
		t.Fatalf("overflow: buffered %d, seq %d, slow %v", len(p.stream.events), w.s.eventSeq, p.slow)
	}
	for range 100 {
		p.send(ActionIgnored{})
		w.flush(w.s)
	}
	if w.s.eventSeq != 64 || len(p.outbox) != 0 {
		t.Fatal("inactive stream continued publication")
	}
}

func TestEventSequenceExhaustionReturnsWorldError(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		w := New(syntheticCatalog(t), &testStore{})
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		drainEvents(entry.Events)
		err := w.do(ctx, func(s *state) {
			s.eventSeq = ^uint64(0)
			s.players[1].send(ActionIgnored{})
		})
		if !errors.Is(err, ErrExhausted) || len(drainEvents(entry.Events)) != 0 {
			t.Fatalf("exhaustion: got %v, want error without publication", err)
		}
		if reason := <-entry.Subscription.End; reason != CloseStop {
			t.Fatalf("exhaustion close: got %v, want stop", reason)
		}
		if err := <-done; !errors.Is(err, ErrExhausted) {
			t.Fatalf("Run: got %v, want exhaustion", err)
		}
	})
}
