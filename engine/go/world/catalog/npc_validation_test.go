// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import "testing"

func TestNPCRejectsMissingAndSubIntegerStats(t *testing.T) {
	for _, column := range []string{"max_hp", "p_def", "p_atk_speed", "walk_speed", "run_speed"} {
		for _, value := range []string{"", "0", "0.5"} {
			t.Run(column+"="+value, func(t *testing.T) {
				fields := map[string]string{"id": "1", "instance_type": "L2Monster", "level": "1", "max_hp": "1", "p_def": "1", "p_atk_speed": "1", "walk_speed": "1", "run_speed": "1"}
				fields[column] = value
				cols := map[string]int{}
				var rec []string
				for name, v := range fields {
					cols[name] = len(rec)
					rec = append(rec, v)
				}
				// Populate optional columns with empty values, as rendered CSV does.
				for _, name := range []string{"client_template_id", "name", "title", "sends_server_name", "sends_server_title", "collision_radius", "collision_height", "sex", "attack_range", "max_mp", "hp_regen", "mp_regen", "strength", "constitution", "dexterity", "intelligence", "wit", "mental", "reward_exp", "reward_sp", "p_atk", "m_atk", "m_def", "m_atk_speed", "aggro_range", "right_hand_item_template_id", "left_hand_item_template_id", "armor_item_template_id", "ai_type", "drops_herbs"} {
					cols[name] = len(rec)
					rec = append(rec, "")
				}
				c := &Catalog{npcs: map[int32]NPCTemplate{}}
				if err := c.npcTemplateRow(row{cols: cols, rec: rec}); err == nil {
					t.Fatalf("accepted %s=%q", column, value)
				}
			})
		}
	}
}
