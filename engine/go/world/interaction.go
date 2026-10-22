// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"slices"
	"strings"
)

func (w *World) canInteract(p *player, n *npc) bool {
	if p.casting || p.dead || p.sitting {
		return false
	}
	return distance3DSq(p.pos, n.pos) <= interactionDistance*interactionDistance
}

func distance3DSq(a, b Point) float64 {
	dx, dy, dz := float64(a.X-b.X), float64(a.Y-b.Y), float64(a.Z-b.Z)
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
		p.send(ActionFailed{})
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
		p.send(ActionFailed{})
		return
	}
	n, ok := s.npcs[c.ai.target]
	if !ok {
		w.setIntention(s, p, order{intent: intentActive})
		p.send(ActionFailed{})
		return
	}
	if w.maybeMoveToPawn(s, p, n.id, 36) {
		p.send(ActionFailed{})
		return
	}
	w.npcOnAction(s, p, n)
	w.setIntention(s, p, order{intent: intentIdle})
}
