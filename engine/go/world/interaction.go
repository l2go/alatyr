// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"context"
	"errors"
	"fmt"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"slices"
	"strconv"
	"strings"
)

func (n *npc) npcInstance() bool {
	return n.tmpl.Role == catalog.Trainer || n.tmpl.Role == catalog.Folk
}
func (n *npc) trainer() bool { return n.tmpl.Role == catalog.Trainer }
func (w *World) talk(s *state, p *player, n *npc) {
	if !w.canInteract(p, n) {
		w.setIntention(s, p, order{intent: intentInteract, object: n.id})
		return
	}
	w.tryAnimation(s, n, true, true)
	if n.trainer() {
		html, ok := w.cat.HTML(fmt.Sprintf("data/html/trainer/%d.htm", n.tmpl.ID))
		if ok {
			w.sendPage(p, n, strings.ReplaceAll(html, "%objectId%", strconv.Itoa(int(n.id))))
		}
	}
}

// Bypass executes only the trainer choice present in the last authored page.
func (w *World) Bypass(ctx context.Context, id int32, cmd string) error {
	var result error
	err := w.act(ctx, id, func(s *state, p *player) {
		if !p.validBypass(cmd) {
			result = errors.New("world: choice not offered")
			return
		}
		n, ok := s.npcs[p.lastFolk]
		if !ok || !n.trainer() || !w.canInteract(p, n) || p.teleporting {
			result = errors.New("world: trainer unavailable")
			return
		}
		want := "npc_" + strconv.Itoa(int(n.id)) + "_SkillList"
		if cmd != want {
			result = errors.New("world: unsupported choice")
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
		p.send(RequestIgnored{})
		return
	}
	p.learning = true
	var skills []catalog.SkillLearn
	for _, k := range w.cat.AvailableSkills(p.char.ClassID, p.level(), p.skillLevel) {
		sk, ok := w.cat.Skill(k.SkillID, k.Level)
		if ok && (sk.Passive || sk.Type == "PDAM") {
			skills = append(skills, k)
		}
	}
	p.send(LearnList{NPCID: n.id, Skills: skills})
}

// Learn reserves a trainer offer and spends SP in the loop. PutSkill executes
// on the caller; a failed write retains the learned skill for Leave to save.
// A host whose Store lacks PutSkill cannot learn a skill.
func (w *World) Learn(ctx context.Context, id, skillID, level int32) error {
	store, ok := w.store.(interface {
		PutSkill(context.Context, int32, Skill) error
	})
	if !ok {
		return errors.New("world: store cannot persist skills")
	}
	var learnt *Skill
	var result error
	err := w.act(ctx, id, func(s *state, p *player) {
		if p.learningWrite {
			result = ErrSaving
			return
		}
		n, ok := s.npcs[p.lastFolk]
		if !ok || !n.trainer() || !p.learning || !w.canInteract(p, n) || p.teleporting || !w.cat.CanTeach(n.tmpl.ID, p.char.ClassID) {
			result = errors.New("world: trainer offer unavailable")
			return
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
			result = errors.New("world: skill not offered")
			return
		}
		if p.char.SP < cost {
			result = errors.New("world: insufficient SP")
			return
		}
		k := Skill{ID: skillID, Level: level}
		learnt = &k
		w.addSkill(s, p, k)
		p.char.SP -= cost
		p.learningWrite = true
		p.send(SelfInfo{})
		w.showSkillList(s, p, n)
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
	saveErr := store.PutSkill(ctx, id, *learnt)
	clearErr := w.do(context.Background(), func(s *state) {
		if p, ok := s.players[id]; ok {
			p.learningWrite = false
		}
	})
	if saveErr != nil {
		return fmt.Errorf("world: save skill: %w", saveErr)
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

func (w *World) sendPage(p *player, n *npc, html string) {
	p.bypass, p.bypassPrefix = bypassCache(html)
	p.send(NPCPage{NPCID: n.id, HTML: html})
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
		p.send(RequestIgnored{})
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
		p.send(RequestIgnored{})
		return
	}
	n, ok := s.npcs[c.ai.target]
	if !ok {
		w.setIntention(s, p, order{intent: intentActive})
		p.send(RequestIgnored{})
		return
	}
	if w.maybeMoveToPawn(s, p, n.id, 36) {
		p.send(RequestIgnored{})
		return
	}
	w.npcOnAction(s, p, n)
	w.setIntention(s, p, order{intent: intentIdle})
}

const interactionDistance = 150
