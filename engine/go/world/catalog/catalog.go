// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/csv"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strconv"
)

type PlayerTemplate struct {
	Name                             string
	ClassID                          int32
	STR, CON, DEX, INT, WIT, MEN     int32
	PAtk, PDef, MAtk, MDef           float64
	AtkSpd, CastSpd                  float64
	Accuracy, Crit, Evasion, RunSpd  float64
	MaxLoad                          float64
	CanCraft                         bool
	CollisionRadius, CollisionHeight [2]float64

	BaseHP, HPPerLevel, HPIncrement float64
	BaseCP, CPPerLevel, CPIncrement float64
	BaseMP, MPPerLevel, MPIncrement float64
	ClassBaseLevel                  int32
}

type ItemKind uint8

const (
	Weapon ItemKind = iota + 1
	Armor
	EtcItem
)

type ItemTemplate struct {
	ID           int32
	Name         string
	Kind         ItemKind
	BodyPartName string
	Weight       int32

	WeaponType                        string
	PhysicalDamage, RandomDamage      int32
	CritRate, AccuracyMod, EvasionMod int32
	AttackSpeed, MagicDamage          int32
	SoulshotCount, SpiritshotCount    int32

	ArmorType           string
	PDef, MDef, MPBonus int32
	ArmorEvasionMod     int32

	EtcType     string
	ConsumeType string
}

type row struct {
	cols map[string]int
	rec  []string
	err  error
}

func (r *row) str(name string) string {
	i, ok := r.cols[name]
	if !ok {
		if r.err == nil {
			r.err = fmt.Errorf("no column %q", name)
		}
		return ""
	}
	return r.rec[i]
}

func (r *row) int(name string) int32 {
	s := r.str(name)
	if s == "" {
		return 0
	}
	v, err := strconv.ParseInt(s, 10, 32)
	if err != nil && r.err == nil {
		r.err = fmt.Errorf("column %q: %w", name, err)
	}
	return int32(v)
}

func (r *row) float(name string) float64 {
	s := r.str(name)
	if s == "" {
		return 0
	}
	v, err := strconv.ParseFloat(s, 64)
	if (err != nil || math.IsNaN(v) || math.IsInf(v, 0)) && r.err == nil {
		r.err = fmt.Errorf("column %q: invalid finite number", name)
	}
	return v
}

func (r *row) bool(name string) bool {
	s := r.str(name)
	v, err := strconv.ParseBool(s)
	if err != nil && s != "" && r.err == nil {
		r.err = fmt.Errorf("column %q: %w", name, err)
	}
	return v
}

func readCSV(path string, each func(row) error) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	cr := csv.NewReader(f)
	head, err := cr.Read()
	if err != nil {
		return err
	}
	cols := make(map[string]int, len(head))
	for i, h := range head {
		if h == "" {
			return errors.New("empty column")
		}
		if _, ok := cols[h]; ok {
			return errors.New("duplicate column")
		}
		cols[h] = i
	}
	for line := 2; ; line++ {
		rec, err := cr.Read()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return err
		}
		if err := each(row{cols: cols, rec: rec}); err != nil {
			return fmt.Errorf("line %d: %w", line, err)
		}
	}
}

func (c *Catalog) playerRow(r row) error {
	t := PlayerTemplate{
		ClassID: r.int("player_class_id"), Name: r.str("class_name"),
		STR: r.int("base_strength"), CON: r.int("base_constitution"), DEX: r.int("base_dexterity"),
		INT: r.int("base_intelligence"), WIT: r.int("base_wit"), MEN: r.int("base_mental"),
		PAtk: r.float("base_physical_attack"), PDef: r.float("base_physical_defense"),
		MAtk: r.float("base_magic_attack"), MDef: r.float("base_magic_defense"),
		AtkSpd: r.float("base_attack_speed"), CastSpd: r.float("base_casting_speed"),
		Accuracy: r.float("base_accuracy"), Crit: r.float("base_critical_rate"),
		Evasion: r.float("base_evasion"), RunSpd: r.float("base_run_speed"),
		MaxLoad:         r.float("base_max_load"),
		CanCraft:        r.bool("can_craft"),
		CollisionRadius: [2]float64{r.float("male_collision_radius"), r.float("female_collision_radius")},
		CollisionHeight: [2]float64{r.float("male_collision_height"), r.float("female_collision_height")},
	}
	if r.err != nil {
		return r.err
	}
	if _, ok := c.players[t.ClassID]; ok {
		return fmt.Errorf("duplicate player template %d", t.ClassID)
	}
	if t.RunSpd <= 0 {
		return errors.New("nonpositive run speed")
	}
	c.players[t.ClassID] = t
	return nil
}

func (c *Catalog) levelRow(r row) error {
	id := r.int("player_class_id")
	t, ok := c.players[id]
	if !ok {
		return fmt.Errorf("level gain of class %d without a player template", id)
	}
	t.BaseHP, t.HPPerLevel, t.HPIncrement = r.float("base_hp_max"), r.float("hp_per_level"), r.float("hp_per_level_increment")
	t.BaseCP, t.CPPerLevel, t.CPIncrement = r.float("base_cp_max"), r.float("cp_per_level"), r.float("cp_per_level_increment")
	t.BaseMP, t.MPPerLevel, t.MPIncrement = r.float("base_mp_max"), r.float("mp_per_level"), r.float("mp_per_level_increment")
	t.ClassBaseLevel = r.int("class_base_level")
	if r.err != nil {
		return r.err
	}
	c.players[id] = t
	return nil
}

func (c *Catalog) addItem(t ItemTemplate, err error) error {
	if err != nil {
		return err
	}
	if t.ID <= 0 || t.Weight < 0 || t.RandomDamage < 0 || t.RandomDamage > 100 {
		return fmt.Errorf("invalid item %d", t.ID)
	}
	if _, dup := c.items[t.ID]; dup {
		return fmt.Errorf("item %d twice", t.ID)
	}
	c.items[t.ID] = t
	return nil
}

type Catalog struct {
	players    map[int32]PlayerTemplate
	items      map[int32]ItemTemplate
	itemFuncs  map[int32][]ItemFunc
	passive    map[[2]int32][]ItemFunc
	npcs       map[int32]NPCTemplate
	spawns     []Spawn
	drops      map[int32][]*DropCategory
	skillTree  map[int32][]SkillLearn
	teach      map[int32]map[int32]bool
	skills     map[int32]skillLevels
	html       map[string]string
	zones      []zone
	restarts   map[int32]Restart
	mapRegions []mapRegion
}

func (c *Catalog) Player(id int32) (PlayerTemplate, bool) { t, ok := c.players[id]; return t, ok }
func (c *Catalog) Item(id int32) (ItemTemplate, bool)     { t, ok := c.items[id]; return t, ok }
func (c *Catalog) PassiveFuncs(id, level int32) []ItemFunc {
	return cloneFuncs(c.passive[[2]int32{id, level}])
}

// Load reads the rendered world root. Source authoring files are never opened.
// Templates, spawns, learning, drops and geometry come from the same root.
func Load(root string) (*Catalog, error) {
	c := &Catalog{players: map[int32]PlayerTemplate{}, items: map[int32]ItemTemplate{}, itemFuncs: map[int32][]ItemFunc{}, passive: map[[2]int32][]ItemFunc{}}
	c.npcs = map[int32]NPCTemplate{}
	c.drops = map[int32][]*DropCategory{}
	c.skillTree = map[int32][]SkillLearn{}
	c.teach = map[int32]map[int32]bool{}
	c.skills = map[int32]skillLevels{}
	c.html = map[string]string{}
	for _, s := range []struct {
		file string
		row  func(row) error
	}{
		{"player_template.csv", c.playerRow}, {"level_up_gain.csv", c.levelRow},
		{"weapon_template.csv", c.weaponRow}, {"armor_template.csv", c.etcArmorRow}, {"etc_item_template.csv", c.etcRow},
	} {
		if err := readCSV(filepath.Join(root, "catalog", s.file), s.row); err != nil {
			return nil, fmt.Errorf("catalog: %s: %w", s.file, err)
		}
	}
	for _, kind := range []string{"weapon", "armor", "etcitem", "skills"} {
		if err := c.loadStatXML(filepath.Join(root, "data", "stats", kind), kind); err != nil {
			return nil, fmt.Errorf("catalog: %s XML: %w", kind, err)
		}
	}
	if err := c.loadGameplay(root); err != nil {
		return nil, err
	}
	return c, nil
}
func (c *Catalog) weaponRow(r row) error {
	t := ItemTemplate{ID: r.int("id"), Name: r.str("name"), Kind: Weapon, BodyPartName: r.str("body_part"), Weight: r.int("weight"),
		WeaponType: r.str("weapon_type"), PhysicalDamage: r.int("physical_damage"), MagicDamage: r.int("magic_damage"),
		RandomDamage: r.int("random_damage"), CritRate: r.int("critical_rate"), AccuracyMod: r.int("accuracy_modifier"),
		EvasionMod: r.int("evasion_modifier"), AttackSpeed: r.int("attack_speed"), SoulshotCount: r.int("soulshot_count"), SpiritshotCount: r.int("spiritshot_count")}
	return c.addItem(t, r.err)
}
func (c *Catalog) etcArmorRow(r row) error {
	t := ItemTemplate{ID: r.int("id"), Name: r.str("name"), Kind: Armor, BodyPartName: r.str("body_part"), Weight: r.int("weight"),
		ArmorType: r.str("armor_type"), PDef: r.int("physical_defense"), MDef: r.int("magic_defense"), ArmorEvasionMod: r.int("evasion_modifier"), MPBonus: r.int("mp_bonus")}
	return c.addItem(t, r.err)
}
func (c *Catalog) etcRow(r row) error {
	t := ItemTemplate{ID: r.int("id"), Name: r.str("name"), Kind: EtcItem, Weight: r.int("weight"), EtcType: r.str("item_type"), ConsumeType: r.str("consume_type")}
	return c.addItem(t, r.err)
}
