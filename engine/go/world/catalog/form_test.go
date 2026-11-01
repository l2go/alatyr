// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/csv"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func npcRow(fields map[string]string) row {
	base := map[string]string{"id": "1", "instance_type": "L2Monster", "level": "1", "max_hp": "1", "p_def": "1", "p_atk_speed": "1", "walk_speed": "1", "run_speed": "1", "client_class": ""}
	for name, value := range fields {
		base[name] = value
	}
	optional := []string{"client_template_id", "name", "title", "sends_server_name", "sends_server_title", "collision_radius", "collision_height", "sex", "attack_range", "max_mp", "hp_regen", "mp_regen", "strength", "constitution", "dexterity", "intelligence", "wit", "mental", "reward_exp", "reward_sp", "p_atk", "m_atk", "m_def", "m_atk_speed", "aggro_range", "right_hand_item_template_id", "left_hand_item_template_id", "armor_item_template_id", "ai_type", "drops_herbs"}
	cols := map[string]int{}
	var rec []string
	for name, value := range base {
		cols[name] = len(rec)
		rec = append(rec, value)
	}
	for _, name := range optional {
		if _, ok := cols[name]; ok {
			continue
		}
		cols[name] = len(rec)
		rec = append(rec, "")
	}
	return row{cols: cols, rec: rec}
}

func TestNPCFormGrammar(t *testing.T) {
	for _, token := range []string{"", "a", "field_wolf", "a1", "a_b", strings.Repeat("a", 32)} {
		t.Run("ok/"+token, func(t *testing.T) {
			c := &Catalog{npcs: map[int32]NPCTemplate{}}
			if err := c.npcTemplateRow(npcRow(map[string]string{"client_class": token})); err != nil {
				t.Fatal(err)
			}
			if c.npcs[1].Form != token {
				t.Fatalf("form = %q", c.npcs[1].Form)
			}
		})
	}
	for _, token := range []string{"A", "Wolf", "1a", "_a", "a-b", "a b", strings.Repeat("a", 33), "field_wolf!"} {
		t.Run("bad/"+token, func(t *testing.T) {
			c := &Catalog{npcs: map[int32]NPCTemplate{}}
			err := c.npcTemplateRow(npcRow(map[string]string{"client_class": token}))
			if err == nil || !strings.Contains(err.Error(), "form") {
				t.Fatalf("err = %v", err)
			}
			if _, ok := c.npcs[1]; ok {
				t.Fatal("invalid form stored")
			}
		})
	}
	c := &Catalog{npcs: map[int32]NPCTemplate{}}
	err := c.npcTemplateRow(npcRow(map[string]string{"max_hp": "0", "client_class": "Wolf"}))
	if err == nil || !strings.Contains(err.Error(), "invalid npc template") {
		t.Fatalf("stat error lost: %v", err)
	}
	err = c.npcTemplateRow(npcRow(nil))
	if err != nil {
		t.Fatal(err)
	}
	err = c.npcTemplateRow(npcRow(map[string]string{"client_class": "Wolf"}))
	if err == nil || !strings.Contains(err.Error(), "duplicate") {
		t.Fatalf("duplicate error lost: %v", err)
	}
	missing := npcRow(nil)
	delete(missing.cols, "client_class")
	if err := c.npcTemplateRow(missing); err == nil || !strings.Contains(err.Error(), "client_class") {
		t.Fatalf("missing column: %v", err)
	}
}

func TestLoadRejectsNPCFormOutsideTheGrammar(t *testing.T) {
	root := t.TempDir()
	if err := os.CopyFS(root, os.DirFS(filepath.Join("..", "..", "..", "..", "world"))); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(root, "catalog", "npc_template.csv")
	f, err := os.Open(path)
	if err != nil {
		t.Fatal(err)
	}
	rows, err := csv.NewReader(f).ReadAll()
	closeErr := f.Close()
	if err != nil || closeErr != nil {
		t.Fatal(err, closeErr)
	}
	form := -1
	for i, name := range rows[0] {
		if name == "client_class" {
			form = i
		}
	}
	if form < 0 || rows[1][0] != "30001" {
		t.Fatal("npc template header")
	}
	rows[1][form] = "Wolf"
	f, err = os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	w := csv.NewWriter(f)
	w.WriteAll(rows)
	err = w.Error()
	closeErr = f.Close()
	if err != nil || closeErr != nil {
		t.Fatal(err, closeErr)
	}
	if _, err := Load(root); err == nil || !strings.Contains(err.Error(), "form") {
		t.Fatalf("load: %v", err)
	}
}
