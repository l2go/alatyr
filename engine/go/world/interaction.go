// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func (n *npc) npcInstance() bool {
	return n.tmpl.Role == catalog.Trainer || n.tmpl.Role == catalog.Folk
}
func (n *npc) trainer() bool { return n.tmpl.Role == catalog.Trainer }
func (w *World) talk(s *state, p *player, n *npc) bool {
	if !w.canInteract(p, n) {
		w.setIntention(s, p, order{intent: intentInteract, object: n.id, target: n.ref()})
		return false
	}
	w.tryAnimation(s, n, true, true)
	if n.trainer() {
		page := fmt.Sprintf("data/html/trainer/%d.htm", n.tmpl.ID)
		html, ok := w.cat.HTML(page)
		if ok {
			w.sendPage(s, p, n, page, strings.ReplaceAll(html, "%objectId%", strconv.Itoa(int(n.id))))
			return true
		}
	}
	return false
}

// Bypass executes only the trainer choice present in the last authored page.
func (w *World) Bypass(ctx context.Context, id int32, cmd string) error {
	var result error
	err := w.act(ctx, id, VerbChoose, func(s *state, p *player) {
		if !p.validBypass(cmd) {
			result = errors.New("world: choice not offered")
			s.finish(StageRefused)
			return
		}
		n, ok := s.npcs[p.lastFolk]
		if !ok || !n.trainer() || !w.canInteract(p, n) || p.teleporting {
			result = errors.New("world: trainer unavailable")
			s.finish(StageRefused)
			return
		}
		want := "npc_" + strconv.Itoa(int(n.id)) + "_SkillList"
		if cmd != want {
			result = errors.New("world: unsupported choice")
			s.finish(StageRefused)
			return
		}
		w.showSkillList(s, p, n)
	})
	if err != nil {
		return err
	}
	return result
}
func (w *World) showSkillList(s *state, p *player, n *npc) {
	if !w.cat.CanTeach(n.tmpl.ID, p.char.ClassID) {
		p.send(ActionIgnored{})
		return
	}
	s.change(p.ref(), PropertyLearning, boolValue(p.learning), IntValue(1))
	p.learning = true
	var skills []catalog.SkillLearn
	for _, k := range w.cat.AvailableSkills(p.char.ClassID, p.level(), p.skillLevel) {
		sk, ok := w.cat.Skill(k.SkillID, k.Level)
		if ok && (sk.Passive || sk.Type == "PDAM") {
			skills = append(skills, k)
		}
	}
	p.send(LearnList{NPCID: n.id, Skills: skills})
	w.issueLessons(s, p, n, skills)
}

// Learn reserves a trainer offer and spends SP in the loop. PutSkill executes
// on the caller; a failed write retains the learned skill for Leave to save.
// A host whose Store lacks PutSkill cannot learn a skill.
func (w *World) Learn(ctx context.Context, id, skillID, level int32) error {
	if _, ok := w.skillStore(); !ok {
		return errors.New("world: store cannot persist skills")
	}
	var learnt *Skill
	var result error
	var command IntentID
	err := w.act(ctx, id, VerbLearn, func(s *state, p *player) {
		command = s.cause().Intent
		learnt, result = w.learn(s, p, skillID, level)
	})
	if err != nil {
		return err
	}
	if result != nil {
		return result
	}
	if learnt == nil {
		return nil
	}
	return w.saveSkill(ctx, id, *learnt, command)
}

type skillStore interface {
	PutSkill(context.Context, int32, Skill) error
}

func (w *World) skillStore() (skillStore, bool) {
	store, ok := w.store.(skillStore)
	return store, ok
}

func (w *World) learn(s *state, p *player, skillID, level int32) (*Skill, error) {
	return w.learnFrom(s, p, s.npcs[p.lastFolk], skillID, level)
}

func (w *World) learnFrom(s *state, p *player, n *npc, skillID, level int32) (*Skill, error) {
	if _, ok := w.skillStore(); !ok {
		s.resolve(StageRefused, ReasonUnavailable)
		return nil, errors.New("world: store cannot persist skills")
	}
	if p.learningWrite {
		s.resolve(StageRefused, ReasonState)
		return nil, ErrSaving
	}
	if n == nil || !n.trainer() || !p.learning || !w.canInteract(p, n) || p.teleporting || !w.cat.CanTeach(n.tmpl.ID, p.char.ClassID) {
		s.resolve(StageRefused, ReasonState)
		return nil, errors.New("world: trainer offer unavailable")
	}
	sk, known := w.cat.Skill(skillID, level)
	cost, available := w.cat.SkillCost(p.char.ClassID, skillID, level, p.level())
	offered := false
	for _, k := range w.cat.AvailableSkills(p.char.ClassID, p.level(), p.skillLevel) {
		if k.SkillID == skillID && k.Level == level {
			offered = true
		}
	}
	if !known || !available || !offered || !sk.Passive && sk.Type != "PDAM" {
		s.resolve(StageRefused, ReasonState)
		return nil, errors.New("world: skill not offered")
	}
	if p.char.SP < cost {
		s.resolve(StageRefused, ReasonState)
		return nil, errors.New("world: insufficient sp")
	}
	k := Skill{ID: skillID, Level: level}
	oldSP, oldSkill := p.char.SP, p.skillLevel(skillID)
	w.putSkill(s, p, k)
	p.char.SP -= cost
	s.group([]Effect{
		ResourceTransferred{Resource: Resource{Kind: ResourceSP}, From: ResourceLocation{Entity: p.ref(), Place: PlaceCharacter}, To: ResourceLocation{Place: PlaceLearning}, Amount: IntegerAmount(int64(cost)), FromBalance: Balance{Before: IntegerAmount(int64(oldSP)), After: IntegerAmount(int64(p.char.SP))}},
		CapabilityChanged{Entity: p.ref(), Capability: CapabilitySkill, ID: skillID, Before: max(oldSkill, 0), After: level},
	})
	s.finish(StageDone)
	p.learningWrite = true
	p.send(SelfChanged{})
	w.showSkillList(s, p, n)
	return &k, nil
}

func (w *World) saveSkill(ctx context.Context, id int32, k Skill, command IntentID) error {
	store, ok := w.skillStore()
	if !ok {
		return errors.New("world: store cannot persist skills")
	}
	saveErr := store.PutSkill(ctx, id, k)
	if w.durability != nil {
		w.durability(Durability{Intent: command, Time: Time(time.Since(w.started)), Err: saveErr})
	}
	clearErr := w.do(context.Background(), func(s *state) {
		if p, ok := s.players[id]; ok {
			p.learningWrite = false
		}
	})
	if saveErr != nil {
		return fmt.Errorf("world: save skill: %w", errors.Join(saveErr, clearErr))
	}
	return clearErr
}

func (w *World) canInteract(p *player, n *npc) bool {
	if p.casting || p.dead || p.sitting {
		return false
	}
	return distance3DSq(p.pos, n.pos) <= interactionDistance*interactionDistance
}

func distance3DSq(a, b Point) float64 {
	dx, dy, dz := (float64(a.X) - float64(b.X)), (float64(a.Y) - float64(b.Y)), (float64(a.Z) - float64(b.Z))
	return dx*dx + dy*dy + dz*dz
}

func (w *World) sendPage(s *state, p *player, n *npc, page, html string) {
	s.change(p.ref(), PropertyDialogue, LabelValue(p.dialogue), LabelValue(page))
	p.dialogue = page
	p.dialogueHTML = html
	p.dialogueSpeaker = n.ref()
	p.bypass, p.bypassPrefix = bypassCache(html)
	s.group([]Effect{SpeechCompleted{Speaker: n.ref(), Listener: p.ref(), Text: html}})
	p.send(DialoguePage{NPCID: n.id, HTML: html})
	w.issueChoices(s, p, n)
}

func bypassCache(html string) (exact, prefix []string) {
	exact, prefix = []string{}, []string{}
	for i := 0; i < len(html); i++ {
		start := strings.Index(html[i:], "bypass -h")
		if start < 0 {
			break
		}
		start += i
		finish := strings.IndexByte(html[start:], '"')
		if finish < 0 {
			break
		}
		finish += start
		start += 10
		i = start
		if start > finish {
			continue
		}
		if d := strings.IndexByte(html[start:], '$'); d >= 0 && start+d > 0 && start+d < finish {
			prefix = append(prefix, strings.TrimSpace(html[start:start+d]))
		} else {
			exact = append(exact, strings.TrimSpace(html[start:finish]))
		}
	}
	return exact, prefix
}

func (p *player) validBypass(cmd string) bool {
	if slices.Contains(p.bypass, cmd) {
		return true
	}
	for _, b := range p.bypassPrefix {
		if strings.HasPrefix(cmd, b) {
			return true
		}
	}
	return false
}

func (w *World) onIntentionInteract(s *state, p *player, object int32) {
	c := p.base()
	if c.ai.intention == intentRest {
		p.send(ActionIgnored{})
		return
	}
	w.clientStopAutoAttack(s, p)
	if c.ai.intention != intentInteract {
		w.changeIntention(s, p, intentInteract, object, Point{})
		c.ai.target = object
		w.moveToPawn(s, p, object, 60)
	}
}

func (w *World) thinkInteract(s *state, p *player) {
	c := p.base()
	if c.casting {
		p.send(ActionIgnored{})
		return
	}
	n, ok := s.npcs[c.ai.target]
	if !ok {
		w.setIntention(s, p, order{intent: intentActive})
		p.send(ActionIgnored{})
		return
	}
	if w.maybeMoveToPawn(s, p, n.id, 36) {
		p.send(ActionIgnored{})
		return
	}
	p.lastFolk = n.id
	w.talk(s, p, n)
	w.setIntention(s, p, order{intent: intentIdle})
}

const interactionDistance = 150
