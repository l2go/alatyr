// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"fmt"
	"math"
)

// Point is an authored world coordinate.
type Point struct{ X, Y, Z int32 }

// Role identifies an authored NPC role without defining protocol capabilities.
type Role string

const (
	// Monster participates in combat and hunt rewards.
	Monster Role = "monster"
	// Trainer provides authored lessons.
	Trainer Role = "trainer"
	// Merchant is an authored role, not an implemented shop capability.
	Merchant Role = "merchant"
	// Folk is a non-combat NPC.
	Folk Role = "folk"
)

// NPCTemplate contains validated authored NPC statistics and presentation.
type NPCTemplate struct {
	ID, ClientID                   int32
	Name, Title                    string
	SendsServerName, SendsTitle    bool
	CollisionRadius, CollisionHigh float64
	Level                          int32
	Female                         bool
	Role                           Role
	AttackRange                    int32
	MaxHP, MaxMP                   float64
	HPRegen, MPRegen               float64
	STR, CON, DEX, INT, WIT, MEN   int32
	RewardExp, RewardSP            int32
	PAtk, PDef, MAtk, MDef         float64
	PAtkSpd, MAtkSpd               float64
	AggroRange                     int32
	RightHand, LeftHand, Armor     int32
	WalkSpeed, RunSpeed            float64
	AIType                         string
	DropsHerbs                     bool
}

// Spawn describes an authored NPC population and respawn interval.
type Spawn struct {
	Group        string
	ID           int32
	Area         string
	Count        int32
	NPC          int32
	Point        Point
	Heading      int32
	RespawnDelay int32
	PeriodOfDay  string
}

// NPC returns the exact authored template and whether it exists.
func (c *Catalog) NPC(id int32) (NPCTemplate, bool) {
	t, ok := c.npcs[id]
	return t, ok
}

// Spawns returns detached spawn records for the requested group.
func (c *Catalog) Spawns(group string) []Spawn {
	var out []Spawn
	for _, s := range c.spawns {
		if s.Group == group {
			out = append(out, s)
		}
	}
	return out
}

func (c *Catalog) npcTemplateRow(r row) error {
	t := NPCTemplate{
		ID: r.int("id"), ClientID: r.int("client_template_id"), Name: r.str("name"), Title: r.str("title"),
		SendsServerName: r.bool("sends_server_name"), SendsTitle: r.bool("sends_server_title"),
		CollisionRadius: r.float("collision_radius"), CollisionHigh: r.float("collision_height"),
		Level: r.int("level"), Female: r.str("sex") == "female",
		AttackRange: r.int("attack_range"), MaxHP: r.float("max_hp"), MaxMP: r.float("max_mp"),
		HPRegen: r.float("hp_regen"), MPRegen: r.float("mp_regen"),
		STR: r.int("strength"), CON: r.int("constitution"), DEX: r.int("dexterity"),
		INT: r.int("intelligence"), WIT: r.int("wit"), MEN: r.int("mental"),
		RewardExp: r.int("reward_exp"), RewardSP: r.int("reward_sp"),
		PAtk: r.float("p_atk"), PDef: r.float("p_def"), MAtk: r.float("m_atk"), MDef: r.float("m_def"),
		PAtkSpd: r.float("p_atk_speed"), MAtkSpd: r.float("m_atk_speed"), AggroRange: r.int("aggro_range"),
		RightHand: r.int("right_hand_item_template_id"), LeftHand: r.int("left_hand_item_template_id"),
		Armor: r.int("armor_item_template_id"), WalkSpeed: r.float("walk_speed"), RunSpeed: r.float("run_speed"),
		AIType: r.str("ai_type"), DropsHerbs: r.bool("drops_herbs"),
	}
	if r.err != nil {
		return r.err
	}
	for _, v := range []float64{t.MaxHP, t.MaxMP, t.PAtk, t.PDef, t.PAtkSpd, t.CollisionRadius, t.CollisionHigh, t.WalkSpeed, t.RunSpeed} {
		if v > math.MaxInt32 {
			return fmt.Errorf("catalog: npc value exceeds integer range")
		}
	}
	switch r.str("instance_type") {
	case "L2Monster":
		t.Role = Monster
	case "L2Trainer":
		t.Role = Trainer
	case "L2Merchant":
		t.Role = Merchant
	case "L2Npc":
		t.Role = Folk
	default:
		return fmt.Errorf("catalog: unsupported npc role")
	}
	if t.ID <= 0 || t.Level < 1 || t.MaxHP < 1 || t.MaxMP < 0 || t.PDef < 1 || t.PAtkSpd < 1 || t.PAtk < 0 || t.DEX < 0 || t.CollisionRadius < 0 || t.CollisionHigh < 0 || t.WalkSpeed < 1 || t.RunSpeed < 1 || t.RewardExp < 0 || t.RewardSP < 0 {
		return fmt.Errorf("catalog: invalid npc template %d", t.ID)
	}
	if _, dup := c.npcs[t.ID]; dup {
		return fmt.Errorf("catalog: duplicate npc %d", t.ID)
	}
	c.npcs[t.ID] = t
	return nil
}

func (c *Catalog) spawnRow(r row) error {
	s := Spawn{
		Group: r.str("spawn_group"), ID: r.int("id"), Area: r.str("area_name"), Count: r.int("npc_count"),
		NPC: r.int("npc_template_id"), Point: Point{r.int("x"), r.int("y"), r.int("z")},
		Heading: r.int("heading"), RespawnDelay: r.int("respawn_delay_s"), PeriodOfDay: r.str("period_of_day"),
	}
	if r.err != nil {
		return r.err
	}
	if _, ok := c.npcs[s.NPC]; !ok || s.Count < 1 || s.Count > 10000 || s.RespawnDelay < 0 || s.Heading < -1 || s.Heading > 65535 {
		return fmt.Errorf("invalid spawn %d", s.ID)
	}
	for _, old := range c.spawns {
		if old.ID == s.ID {
			return fmt.Errorf("duplicate spawn %d", s.ID)
		}
	}
	c.spawns = append(c.spawns, s)
	return nil
}
