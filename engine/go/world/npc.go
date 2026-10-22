// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"time"

	"github.com/l2go/alatyr/engine/go/world/catalog"
)
const npcWatch = 1500
func playerWatch(known int) float64 {
	switch {
	case known <= 25:
		return 3400
	case known <= 35:
		return 2900
	case known <= 70:
		return 2300
	}
	return 1700
}
const (
	aiPeriod         = time.Second
	randomWalkRate   = 30
	maxDriftRange    = 200
	minSocial        = 6 * time.Second
	minMonsterAnim   = 5
	maxMonsterAnim   = 20
	minNPCAnim       = 10
	maxNPCAnim       = 20
	globalAggroStart = -10
	npcBaseCrit      = 38
)
type npc struct {
	creature
	tmpl  catalog.NPCTemplate
	spawn catalog.Spawn

	knownPlayers map[int32]bool
	aiTask      bool
	globalAggro int
	animating   bool
	lastAnim    time.Time
	animDelay   time.Duration
	hate []hateEntry
	overhit         bool
	overhitDamage   float64
	overhitAttacker int32
}
func (n *npc) takeOverhit(attacker int32, value float64) {
	if value <= 0 || !n.overhit {
		n.overhit = false
		return
	}
	beyond := -(n.hp - value)
	if beyond < 0 {
		n.overhit, n.overhitDamage, n.overhitAttacker = false, 0, 0
		return
	}
	n.overhit, n.overhitDamage, n.overhitAttacker = true, beyond, attacker
}

type hateEntry struct {
	id           int32
	damage, hate int64
}
func (n *npc) mob() bool { return n.tmpl.InstanceType == "L2Monster" }

func (n *npc) home() Point { return Point{n.spawn.Point.X, n.spawn.Point.Y, n.spawn.Point.Z} }

func (n *npc) base() *creature { return &n.creature }
func (n *npc) isPlayer() bool  { return false }

func (n *npc) moveSpeed() float64 {
	if n.running {
		return float64(int32(n.tmpl.RunSpeed))
	}
	return float64(int32(n.tmpl.WalkSpeed))
}

func (n *npc) collision() int32      { return int32(n.tmpl.CollisionRadius) }
func (n *npc) level() int32          { return n.tmpl.Level }
func (n *npc) maxHP() int32          { return int32(n.tmpl.MaxHP) }
func (n *npc) maxMP() int32          { return int32(n.tmpl.MaxMP) }
func (n *npc) pAtk() float64         { return float64(int32(n.tmpl.PAtk)) }
func (n *npc) pDef() float64         { return float64(int32(n.tmpl.PDef)) }
func (n *npc) pAtkSpd() int32        { return int32(n.tmpl.PAtkSpd) }
func (n *npc) attackRange() int32    { return n.tmpl.AttackRange }
func (n *npc) dex() int32            { return n.tmpl.DEX }
func (n *npc) critical() int32       { return npcBaseCrit }
func (n *npc) randomDamage() float64 { return 0.1 }
func (n *npc) accuracy() int32 {
	return int32(math.Sqrt(float64(n.tmpl.DEX))*6 + float64(n.tmpl.Level))
}
func (n *npc) evasion() int32 { return n.accuracy() }
func (n *npc) forgetDistance(int32) float64 {
	if n.mob() {
		return 2200
	}
	return 2 * npcWatch
}
func (n *npc) addHate(attacker int32, damage, hate int64) {
	for i := range n.hate {
		if n.hate[i].id == attacker {
			n.hate[i].damage += damage
			n.hate[i].hate += hate
			return
		}
	}
	n.hate = append(n.hate, hateEntry{id: attacker, damage: damage, hate: hate})
}
func (n *npc) mostHated() int32 {
	var best hateEntry
	for _, h := range n.hate {
		if h.hate > best.hate {
			best = h
		}
	}
	return best.id
}
func (w *World) spawnAll(s *state) {
	for _, sp := range w.cat.Spawns("WORLD") {
		t, ok := w.cat.NPC(sp.NPC)
		if !ok || sp.PeriodOfDay != "ALWAYS" {
			continue
		}
		for range max(sp.Count, 1) {
			w.spawnNPC(s, sp, t)
		}
	}
}
type NPCView struct {
	ObjectID        int32
	Template        catalog.NPCTemplate
	Pos             Point
	Heading         int32
	Attackable      bool
	Running         bool
	InCombat        bool
	Dead            bool
	MoveMul         float64
	AtkSpdMul       float32
	CollisionRadius float64
	CollisionHeight float64
}

func (n *npc) view(time.Time) NPCView {
	return NPCView{
		ObjectID: n.id, Template: n.tmpl, Pos: n.pos, Heading: n.heading,
		Attackable: n.mob(), Running: n.running, InCombat: n.ai.autoAttacking, Dead: n.dead,
		MoveMul:   1,
		AtkSpdMul: float32(1.1 * n.tmpl.PAtkSpd / n.tmpl.PAtkSpd),
		CollisionRadius: math.Trunc(n.tmpl.CollisionRadius), CollisionHeight: math.Trunc(n.tmpl.CollisionHigh),
	}
}
func (w *World) learnPlayer(s *state, n *npc, p *player) {
	if n.dead || distance3D(n.pos, p.pos) > npcWatch || n.knownPlayers[p.id] {
		return
	}
	n.knownPlayers[p.id] = true
	if n.mob() && n.ai.intention == intentIdle {
		w.setIntention(s, n, order{intent: intentActive})
	}
}
func (w *World) forgetPlayer(s *state, n *npc, id int32) {
	delete(n.knownPlayers, id)
	delete(n.listeners, id)
	if n.mob() {
		w.notifyForget(s, n, id)
		if len(n.knownPlayers) == 0 && n.ai.intention == intentActive {
			w.setIntention(s, n, order{intent: intentIdle})
			if n.ai.intention == intentIdle {
				n.animating = false
			}
		}
	}
}

func (w *World) startAITask(s *state, n *npc) {
	if !n.aiTask {
		n.aiTask = true
		s.thinkers = append(s.thinkers, n.id)
	}
}

func (w *World) stopAITask(s *state, n *npc) { n.aiTask = false }
func (w *World) think(s *state, n *npc) {
	if n.globalAggro < 0 {
		n.globalAggro++
	}
	if n.move != nil || s.rng.IntN(randomWalkRate) != 0 {
		return
	}
	home := n.home()
	dest := home
	if distance3D(n.pos, home) <= 2*maxDriftRange {
		dest = Point{
			X: home.X + int32(s.rng.IntN(2*maxDriftRange)) - maxDriftRange,
			Y: home.Y + int32(s.rng.IntN(2*maxDriftRange)) - maxDriftRange,
			Z: n.pos.Z,
		}
	}
	w.aiMoveTo(s, n, dest)
}
func (w *World) thinkAttackMob(s *state, n *npc) {
	if n.ai.attackTimeout < s.ticks && n.running {
		w.setRunning(s, n, false)
		n.ai.attackTimeout = maxAttackTimeout + s.ticks
	}
	t, ok := s.actor(n.ai.object)
	if !ok || t.base().dead || n.ai.attackTimeout < s.ticks {
		if ok {
			n.stopHating(t.base().id)
		}
		w.setIntention(s, n, order{intent: intentActive})
		w.setRunning(s, n, false)
		return
	}
	if n.attackingNow(time.Now()) {
		return
	}
	n.target = t.base().id
	dx, dy := float64(n.pos.X-t.base().pos.X), float64(n.pos.Y-t.base().pos.Y)
	dist2 := dx*dx + dy*dy
	rng := float64(n.attackRange() + n.collision() + t.collision())
	if t.base().move != nil {
		rng += 50
	}
	if dist2 > rng*rng {
		if t.base().move != nil {
			rng -= 100
		}
		w.moveToPawn(s, n, t.base().id, max(int32(rng), 5))
		return
	}
	n.ai.attackTimeout = maxAttackTimeout + s.ticks
	w.clientStopMoving(s, n)
	w.doAttack(s, n, t)
}
func (n *npc) stopHating(id int32) {
	for i := range n.hate {
		if n.hate[i].id == id {
			n.hate[i].hate = 0
		}
	}
}
func (w *World) tickAI(s *state) {
	kept := s.thinkers[:0]
	for _, id := range s.thinkers {
		n, ok := s.npcs[id]
		if !ok || !n.aiTask {
			continue
		}
		w.notifyThink(s, n)
		kept = append(kept, id)
	}
	s.thinkers = kept
	s.after(aiPeriod, w.tickAI)
}
func (w *World) tickAnimation(s *state) {
	kept := s.animators[:0]
	for _, id := range s.animators {
		n, ok := s.npcs[id]
		if !ok || !n.animating {
			continue
		}
		if !w.tryAnimation(s, n, false, false) {
			n.animating = false
			continue
		}
		kept = append(kept, id)
	}
	s.animators = kept
	s.after(aiPeriod, w.tickAnimation)
}
func (w *World) activate(s *state, k regionKey) {
	r := s.region(k)
	if r.active {
		return
	}
	r.active = true
	for _, id := range r.objects {
		if n, ok := s.npcs[id]; ok && !n.mob() {
			w.tryAnimation(s, n, false, true)
		}
	}
}
func (w *World) setRunning(s *state, a actor, running bool) {
	c := a.base()
	if c.running == running {
		return
	}
	c.running = running
	w.broadcastToKnown(s, a, MoveType{ObjectID: c.id, Running: running})
	w.markBroadcast(s, a, broadcastFullInfo)
}
