// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"errors"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
)

func (c *Catalog) loadGameplay(root string) error {
	var rows []dropRow
	for _, entry := range []struct {
		name string
		read func(row) error
	}{
		{"npc_template.csv", c.npcTemplateRow}, {"spawn.csv", c.spawnRow},
		{"skill_trainer_class.csv", c.skillTrainerRow},
		{"skill_tree.csv", func(r row) error {
			class := r.int("player_class_id")
			k := SkillLearn{SkillID: r.int("skill_id"), Level: r.int("skill_level"), MinLevel: r.int("min_level"), SP: r.int("sp")}
			if r.err != nil {
				return r.err
			}
			if _, ok := c.players[class]; !ok || k.SkillID <= 0 || k.Level <= 0 || k.MinLevel <= 0 || k.SP < 0 {
				return errors.New("invalid skill tree row")
			}
			for _, old := range c.skillTree[class] {
				if old.SkillID == k.SkillID && old.Level == k.Level {
					return errors.New("duplicate skill tree row")
				}
			}
			c.skillTree[class] = append(c.skillTree[class], k)
			return nil
		}},
		{"drop.csv", func(r row) error {
			d := dropRow{npc: r.int("npc_template_id"), category: r.int("category"), drop: Drop{ItemID: r.int("item_template_id"), Min: r.int64("min_count"), Max: r.int64("max_count"), Chance: r.int("chance")}}
			if r.err != nil {
				return r.err
			}
			if _, ok := c.npcs[d.npc]; !ok {
				return errors.New("drop without NPC")
			}
			if item, ok := c.items[d.drop.ItemID]; !ok || d.drop.Min <= 0 || d.drop.Max < d.drop.Min || d.drop.Chance <= 0 || d.drop.Chance > MaxDropChance || !item.Stackable() && d.drop.Max != 1 {
				return errors.New("invalid drop")
			}
			rows = append(rows, d)
			return nil
		}},
	} {
		if err := readCSV(filepath.Join(root, "catalog", entry.name), entry.read); err != nil {
			return fmt.Errorf("catalog: %s: %w", entry.name, err)
		}
	}
	c.sortSkillTree()
	for _, tree := range c.skillTree {
		for _, k := range tree {
			if _, ok := c.Skill(k.SkillID, k.Level); !ok {
				return errors.New("skill tree without exact XML level")
			}
		}
	}
	chances := map[[2]int32]int64{}
	for _, row := range rows {
		key := [2]int32{row.npc, row.category}
		chances[key] += int64(row.drop.Chance)
		if chances[key] > MaxDropChance {
			return errors.New("drop category chance out of range")
		}
	}
	c.buildDrops(rows)
	for _, cats := range c.drops {
		for _, cat := range cats {
			if cat.Chance < 1 || cat.Chance > MaxDropChance {
				return errors.New("drop category chance out of range")
			}
		}
	}
	if err := c.loadZones(filepath.Join(root, "data", "zone")); err != nil {
		return err
	}
	if err := c.loadMapRegions(filepath.Join(root, "data", "mapregion")); err != nil {
		return err
	}
	for _, dir := range []string{"trainer", "merchant"} {
		path := filepath.Join(root, "data", "html", dir)
		entries, err := os.ReadDir(path)
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return err
		}
		for _, e := range entries {
			if e.Type()&os.ModeSymlink != 0 {
				return errors.New("symlink in HTML")
			}
			if e.IsDir() || !strings.HasSuffix(e.Name(), ".htm") && !strings.HasSuffix(e.Name(), ".html") {
				continue
			}
			raw, err := os.ReadFile(filepath.Join(path, e.Name()))
			if err != nil {
				return err
			}
			c.html["data/html/"+dir+"/"+e.Name()] = CompactHTML(string(raw))
		}
	}
	return nil
}
func (r *row) int64(name string) int64 {
	s := r.str(name)
	n, err := strconv.ParseInt(s, 10, 64)
	if err != nil && r.err == nil {
		r.err = err
	}
	return n
}
func (c *Catalog) tree(class int32) []SkillLearn { return c.skillTree[class] }

// SkillCost returns the SP cost of the row for skill id at skillLevel in
// class's tree, if that row's minimum level is at most level. It does not check
// the known skill level, the character's SP or the trainer; callers do.
func (c *Catalog) SkillCost(class, id, skillLevel, level int32) (int32, bool) {
	for _, k := range c.tree(class) {
		if k.SkillID == id && k.Level == skillLevel && k.MinLevel <= level {
			return k.SP, true
		}
	}
	return 0, false
}
func (c *Catalog) skillTrainerRow(r row) error {
	npc, class := r.int("npc_template_id"), r.int("player_class_id")
	if r.err != nil {
		return r.err
	}
	if _, ok := c.npcs[npc]; !ok {
		return errors.New("trainer without NPC")
	}
	if _, ok := c.players[class]; !ok {
		return errors.New("trainer without class")
	}
	if c.teach[npc] == nil {
		c.teach[npc] = map[int32]bool{}
	}
	if c.teach[npc][class] {
		return errors.New("duplicate trainer class")
	}
	c.teach[npc][class] = true
	return nil
}

// CanTeach reports whether NPC template npc trains class.
func (c *Catalog) CanTeach(npc, class int32) bool { return c.teach[npc][class] }

// HTML returns the authored page at its world-relative path.
func (c *Catalog) HTML(path string) (string, bool) { v, ok := c.html[path]; return v, ok }

// Skill returns a copy of skill id at level.
func (c *Catalog) Skill(id, level int32) (Skill, bool) {
	v, ok := c.skills[id].levels[level]
	v.Funcs = cloneFuncs(v.Funcs)
	v.WeaponsAllowed = slices.Clone(v.WeaponsAllowed)
	return v, ok
}
func javaDecode(v string) (int32, error) { n, err := strconv.ParseInt(v, 0, 32); return int32(n), err }
func javaBool(v string) bool             { return strings.EqualFold(v, "true") }
func skillOf(id, level int32, set map[string]string) (Skill, error) {
	s := Skill{ID: id, Level: level, DisplayID: id, Operate: set["operateType"], Type: set["skillType"], Target: set["target"], SendToClient: true, EffectRange: -1, SSBoost: 2, CanBeReflected: true}
	if s.Operate != "OP_ACTIVE" && s.Operate != "OP_PASSIVE" && s.Operate != "OP_TOGGLE" || s.Type == "" || s.Target == "" {
		return s, errors.New("missing skill semantics")
	}
	s.Passive = s.Operate == "OP_PASSIVE"
	if s.Type == "PDAM" {
		s.State = SkillOffensive
	} else {
		s.State = SkillPositive
	}
	for _, v := range []struct {
		key string
		dst *int32
	}{
		{"displayId", &s.DisplayID}, {"mpConsume", &s.MPConsume}, {"mpInitialConsume", &s.MPInitialConsume},
		{"hpConsume", &s.HPConsume}, {"hitTime", &s.HitTime}, {"coolTime", &s.CoolTime}, {"reuseDelay", &s.ReuseDelay},
		{"castRange", &s.CastRange}, {"effectRange", &s.EffectRange}, {"baseCritRate", &s.BaseCritRate},
		{"lethal1", &s.Lethal1}, {"lethal2", &s.Lethal2}, {"needCharges", &s.NeededCharges},
		{"itemConsumeCount", &s.ItemConsume}, {"soulConsumeCount", &s.SoulCount},
		{"itemConsumeId", &s.ItemConsumeID},
	} {
		if raw, ok := set[v.key]; ok {
			n, err := javaDecode(raw)
			if err != nil {
				return s, err
			}
			*v.dst = n
		}
	}
	for _, v := range []struct {
		key string
		dst *bool
	}{
		{"sendToClient", &s.SendToClient}, {"isMagic", &s.Magic}, {"overHit", &s.OverHit},
		{"staticHitTime", &s.StaticHitTime}, {"staticReuse", &s.StaticReuse}, {"ignoreShld", &s.IgnoreShld},
		{"ignoreResists", &s.IgnoreResists}, {"canBeReflected", &s.CanBeReflected},
		{"isItem", &s.Item}, {"isPotion", &s.Potion},
	} {
		if raw, ok := set[v.key]; ok {
			if !strings.EqualFold(raw, "true") && !strings.EqualFold(raw, "false") {
				return s, errors.New("invalid skill boolean")
			}
			*v.dst = javaBool(raw)
		}
	}
	for _, v := range []struct {
		key string
		dst *float64
	}{{"power", &s.Power}, {"SSBoost", &s.SSBoost}} {
		if raw, ok := set[v.key]; ok {
			n, err := strconv.ParseFloat(raw, 64)
			if err != nil || math.IsNaN(n) || math.IsInf(n, 0) {
				return s, errors.New("invalid skill number")
			}
			*v.dst = float64(float32(n))
			if math.IsInf(*v.dst, 0) {
				return s, errors.New("skill value exceeds float range")
			}
		}
	}
	if raw := set["weaponsAllowed"]; raw != "" && raw != "0" {
		return s, errors.New("numeric weapon restrictions are unsupported; use weaponKinds")
	}
	if raw := set["weaponKinds"]; raw != "" {
		for v := range strings.SplitSeq(raw, ",") {
			kind := strings.ToLower(strings.TrimSpace(v))
			if kind == "" {
				return s, errors.New("empty weapon kind")
			}
			s.WeaponsAllowed = append(s.WeaponsAllowed, kind)
		}
	}
	if s.ItemConsume < 0 || s.ItemConsumeID < 0 || s.MPConsume < 0 || s.MPInitialConsume < 0 || int64(s.MPConsume)+int64(s.MPInitialConsume) > math.MaxInt32 || s.HPConsume < 0 || s.HitTime < 0 || s.CoolTime < 0 || s.ReuseDelay < 0 || s.Power < 0 || s.BaseCritRate < 0 || s.CastRange < -1 || s.EffectRange < -1 {
		return s, errors.New("invalid skill range or cost")
	}
	return s, nil
}
