// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgstore

import (
	"context"
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/l2go/alatyr/engine/go/world"
)

// readCharacters uses one repeatable-read transaction for every loaded collection.
func readCharacters(ctx context.Context, tx pgx.Tx, account string) ([]world.Character, error) {
	rows, err := tx.Query(ctx, `SELECT p.id, p.account_name, p.name, p.active_class_id, p.base_class_id, p.race_id,
			p.is_female, p.hair_style, p.hair_color, p.face, p.level, p.exp, p.sp,
			p.max_hp, p.max_cp, p.max_mp, p.current_hp, p.current_cp, p.current_mp,
			p.x, p.y, p.z, p.heading, p.vitality_points, coalesce(r.recommendations_left, 3), p.last_access_at, p.death_penalty_level
		FROM player p LEFT JOIN player_recommendation_status r ON r.player_id = p.id
		WHERE p.account_name = $1 ORDER BY p.id`, account)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []world.Character
	byID := map[int32]int{}
	for rows.Next() {
		var c world.Character
		var maxHP, maxCP, maxMP, curHP, curCP, curMP int32
		var last *time.Time
		if err := rows.Scan(&c.ObjectID, &c.Account, &c.Name, &c.ClassID, &c.BaseClass, &c.Race,
			&c.Female, &c.HairStyle, &c.HairColor, &c.Face, &c.Level, &c.Exp, &c.SP,
			&maxHP, &maxCP, &maxMP, &curHP, &curCP, &curMP,
			&c.X, &c.Y, &c.Z, &c.Heading, &c.Vitality, &c.RecommendationsLeft, &last, &c.DeathPenalty); err != nil {
			return nil, err
		}
		c.MaxHP, c.MaxCP, c.MaxMP = float64(maxHP), float64(maxCP), float64(maxMP)
		c.CurHP, c.CurCP, c.CurMP = float64(curHP), float64(curCP), float64(curMP)
		if last != nil {
			c.LastAccess = last.UnixMilli()
		}
		byID[c.ObjectID] = len(out)
		out = append(out, c)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if len(out) == 0 {
		return nil, nil
	}
	rows, err = tx.Query(ctx, `SELECT i.owner_player_id, i.id, i.item_template_id, i.count, i.location, i.location_slot
		FROM item i JOIN player p ON p.id = i.owner_player_id
		WHERE p.account_name = $1 AND i.location IN ('INVENTORY', 'PAPERDOLL') ORDER BY i.id`, account)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var owner int32
		var it world.Item
		var loc string
		var slot int32
		if err := rows.Scan(&owner, &it.ObjectID, &it.TemplateID, &it.Count, &loc, &slot); err != nil {
			return nil, err
		}
		it.Slot = -1
		if loc == "PAPERDOLL" {
			it.Slot = slot
		}
		c := &out[byID[owner]]
		c.Items = append(c.Items, it)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if err := readKnown(ctx, tx, account, out, byID); err != nil {
		return nil, err
	}
	return out, nil
}

// readKnown reads what the characters know of their first class slot —
// skills and shortcuts — with their quest variables and colors
// without inventing a color row when none has been saved.
func readKnown(ctx context.Context, tx pgx.Tx, account string, out []world.Character, byID map[int32]int) error {
	for i := range out {
		out[i].NameColor, out[i].TitleColor = 0xffffff, 0xffff77
	}
	var owner int32
	each := func(sql string, scans []any, add func(c *world.Character) error) error {
		rows, err := tx.Query(ctx, sql, account)
		if err != nil {
			return err
		}
		_, err = pgx.ForEachRow(rows, append([]any{&owner}, scans...), func() error { return add(&out[byID[owner]]) })
		return err
	}
	var k world.Skill
	if err := each(`SELECT k.player_id, k.skill_id, k.skill_level FROM player_skill k JOIN player p ON p.id = k.player_id
		WHERE p.account_name = $1 AND k.class_index = 0 ORDER BY k.player_id, k.skill_id`,
		[]any{&k.ID, &k.Level}, func(c *world.Character) error {
			c.Skills = append(c.Skills, k)
			return nil
		}); err != nil {
		return err
	}
	var sc world.Shortcut
	if err := each(`SELECT k.player_id, k.page, k.slot, k.shortcut_type_id, k.target_id, k.level FROM player_shortcut k
		JOIN player p ON p.id = k.player_id
		WHERE p.account_name = $1 AND k.class_index = 0 ORDER BY k.player_id, k.page, k.slot`,
		[]any{&sc.Page, &sc.Slot, &sc.Type, &sc.ID, &sc.Level}, func(c *world.Character) error {
			c.Shortcuts = append(c.Shortcuts, sc)
			return nil
		}); err != nil {
		return err
	}
	var q world.QuestVar
	if err := each(`SELECT q.player_id, q.quest_name, q.variable_name, q.value FROM player_quest_variable q
		JOIN player p ON p.id = q.player_id
		WHERE p.account_name = $1 ORDER BY q.player_id, q.quest_name, q.variable_name`,
		[]any{&q.Quest, &q.Name, &q.Value}, func(c *world.Character) error {
			c.QuestVars = append(c.QuestVars, q)
			return nil
		}); err != nil {
		return err
	}
	var name, title string
	return each(`SELECT k.player_id, k.name_color, k.title_color FROM player_name_title_color k
		JOIN player p ON p.id = k.player_id WHERE p.account_name = $1`,
		[]any{&name, &title}, func(c *world.Character) error {
			n, err := strconv.ParseInt(name, 16, 32)
			if err != nil {
				return fmt.Errorf("name color %q of %d: %w", name, c.ObjectID, err)
			}
			t, err := strconv.ParseInt(title, 16, 32)
			if err != nil {
				return fmt.Errorf("title color %q of %d: %w", title, c.ObjectID, err)
			}
			c.NameColor, c.TitleColor = int32(n), int32(t)
			return nil
		})
}

// PutCharacter creates or updates the character, its recommendation status,
// its creation day, its skills, shortcuts, quest variables and colors, and
// its items in the inventory and on the paperdoll, in one transaction.
// Vitals are rounded to retain compatibility with integer database columns;
// the items of the character that it no longer has are deleted.
func (s *Store) PutCharacter(ctx context.Context, c world.Character) error {
	if err := validateCharacter(c); err != nil {
		return err
	}
	return s.write(ctx, func(tx pgx.Tx) error {
		var last *time.Time
		if c.LastAccess != 0 {
			t := time.UnixMilli(c.LastAccess)
			last = &t
		}
		round := func(v float64) int32 { return int32(math.Round(v)) }
		tag, err := tx.Exec(ctx, `INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id,
				is_female, face, hair_style, hair_color, level, exp, sp,
				max_hp, current_hp, max_cp, current_cp, max_mp, current_mp,
				x, y, z, heading, last_access_at, vitality_points, death_penalty_level)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24, $25, $26)
			ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, race_id = EXCLUDED.race_id,
				active_class_id = EXCLUDED.active_class_id, base_class_id = EXCLUDED.base_class_id,
				is_female = EXCLUDED.is_female, face = EXCLUDED.face, hair_style = EXCLUDED.hair_style,
				hair_color = EXCLUDED.hair_color, level = EXCLUDED.level, exp = EXCLUDED.exp, sp = EXCLUDED.sp,
				max_hp = EXCLUDED.max_hp, current_hp = EXCLUDED.current_hp, max_cp = EXCLUDED.max_cp,
				current_cp = EXCLUDED.current_cp, max_mp = EXCLUDED.max_mp, current_mp = EXCLUDED.current_mp,
				x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z, heading = EXCLUDED.heading,
				last_access_at = EXCLUDED.last_access_at, vitality_points = EXCLUDED.vitality_points,
				death_penalty_level = EXCLUDED.death_penalty_level
            WHERE player.account_name = EXCLUDED.account_name`,
			c.ObjectID, c.Account, c.Name, c.Race, c.ClassID, c.BaseClass,
			c.Female, c.Face, c.HairStyle, c.HairColor, c.Level, c.Exp, c.SP,
			round(c.MaxHP), round(c.CurHP), round(c.MaxCP), round(c.CurCP), round(c.MaxMP), round(c.CurMP),
			c.X, c.Y, c.Z, c.Heading, last, c.Vitality, c.DeathPenalty)
		if err != nil {
			return err
		}
		if tag.RowsAffected() != 1 {
			return errors.New("pgstore: character ownership conflict")
		}
		if _, err := tx.Exec(ctx, `INSERT INTO player_recommendation_status (player_id, recommendations_left, updated_at)
			VALUES ($1, $2, $3)
			ON CONFLICT (player_id) DO UPDATE SET recommendations_left = EXCLUDED.recommendations_left`,
			c.ObjectID, c.RecommendationsLeft, time.Now()); err != nil {
			return err
		}
		// Preserve an existing creation date.
		now := time.Now()
		if _, err := tx.Exec(ctx, `INSERT INTO player_birthday (player_id, gift_claimed_year, created_on)
			VALUES ($1, $2, $3) ON CONFLICT (player_id) DO NOTHING`,
			c.ObjectID, now.Year(), now); err != nil {
			return err
		}
		if err := putKnown(ctx, tx, c); err != nil {
			return err
		}
		ids := make([]int32, 0, len(c.Items))
		for _, it := range c.Items {
			loc, slot := "INVENTORY", int32(0)
			if it.Equipped() {
				loc, slot = "PAPERDOLL", it.Slot
			}
			if tag, err := tx.Exec(ctx, `INSERT INTO item (id, item_template_id, owner_player_id, location, location_slot, count)
				VALUES ($1, $2, $3, $4, $5, $6)
				ON CONFLICT (id) DO UPDATE SET item_template_id = EXCLUDED.item_template_id,
					owner_player_id = EXCLUDED.owner_player_id, owner_clan_id = NULL, owner_pet_id = NULL,
					location_slot = CASE WHEN item.location='INVENTORY' AND EXCLUDED.location='INVENTORY'
                        THEN item.location_slot ELSE EXCLUDED.location_slot END,
                    location = EXCLUDED.location, count = EXCLUDED.count
                WHERE item.owner_player_id = EXCLUDED.owner_player_id
                AND item.location IN ('INVENTORY', 'PAPERDOLL')`,
				it.ObjectID, it.TemplateID, c.ObjectID, loc, slot, it.Count); err != nil {
				return err
			} else if tag.RowsAffected() != 1 {
				return errors.New("pgstore: item identity conflict")
			}
			ids = append(ids, it.ObjectID)
		}
		_, err = tx.Exec(ctx, `DELETE FROM item WHERE owner_player_id = $1
			AND location IN ('INVENTORY', 'PAPERDOLL') AND NOT (id = ANY($2))`, c.ObjectID, ids)
		return err
	})
}

// putKnown replaces only the loaded base-class collections. Other class slots
// and unsupported player tables remain untouched.
func putKnown(ctx context.Context, tx pgx.Tx, c world.Character) error {
	skills := make([]int32, 0, len(c.Skills))
	for _, k := range c.Skills {
		skills = append(skills, k.ID)
	}
	if _, err := tx.Exec(ctx, `DELETE FROM player_skill WHERE player_id = $1 AND class_index = 0 AND NOT (skill_id = ANY($2))`,
		c.ObjectID, skills); err != nil {
		return err
	}
	keys := make([]int32, 0, len(c.Shortcuts))
	for _, k := range c.Shortcuts {
		keys = append(keys, k.Page*12+k.Slot)
	}
	if _, err := tx.Exec(ctx, `DELETE FROM player_shortcut WHERE player_id = $1 AND class_index = 0
		AND NOT (page * 12 + slot = ANY($2))`, c.ObjectID, keys); err != nil {
		return err
	}
	quests := make([]string, 0, len(c.QuestVars))
	vars := make([]string, 0, len(c.QuestVars))
	for _, q := range c.QuestVars {
		quests, vars = append(quests, q.Quest), append(vars, q.Name)
	}
	if _, err := tx.Exec(ctx, `DELETE FROM player_quest_variable WHERE player_id = $1
		AND (quest_name, variable_name) NOT IN (SELECT * FROM unnest($2::text[], $3::text[]))`,
		c.ObjectID, quests, vars); err != nil {
		return err
	}
	for _, k := range c.Skills {
		if _, err := tx.Exec(ctx, `INSERT INTO player_skill (player_id, class_index, skill_id, skill_level)
			VALUES ($1, 0, $2, $3) ON CONFLICT (player_id, class_index, skill_id) DO UPDATE SET skill_level = EXCLUDED.skill_level`,
			c.ObjectID, k.ID, k.Level); err != nil {
			return err
		}
	}
	for _, k := range c.Shortcuts {
		if _, err := tx.Exec(ctx, `INSERT INTO player_shortcut (player_id, class_index, page, slot, shortcut_type_id, target_id, level)
			VALUES ($1, 0, $2, $3, $4, $5, $6) ON CONFLICT (player_id, class_index, page, slot)
			DO UPDATE SET shortcut_type_id = EXCLUDED.shortcut_type_id, target_id = EXCLUDED.target_id, level = EXCLUDED.level`,
			c.ObjectID, k.Page, k.Slot, k.Type, k.ID, k.Level); err != nil {
			return err
		}
	}
	for _, q := range c.QuestVars {
		if _, err := tx.Exec(ctx, `INSERT INTO player_quest_variable (player_id, quest_name, variable_name, value)
			VALUES ($1, $2, $3, $4) ON CONFLICT (player_id, quest_name, variable_name) DO UPDATE SET value = EXCLUDED.value`,
			c.ObjectID, q.Quest, q.Name, q.Value); err != nil {
			return err
		}
	}
	_, err := tx.Exec(ctx, `INSERT INTO player_name_title_color (player_id, name_color, title_color) VALUES ($1, $2, $3)
		ON CONFLICT (player_id) DO UPDATE SET name_color = EXCLUDED.name_color, title_color = EXCLUDED.title_color`,
		c.ObjectID, fmt.Sprintf("%06x", c.NameColor), fmt.Sprintf("%06x", c.TitleColor))
	return err
}

func validateCharacter(c world.Character) error {
	if c.ObjectID <= 0 || c.Level < 1 || c.SP < 0 || c.Exp < 0 || c.ClassID != c.BaseClass {
		return errors.New("pgstore: invalid base-class character")
	}
	for _, name := range []string{c.Account, c.Name} {
		if len(name) < 1 || len(name) > 128 || !utf8.ValidString(name) || strings.ContainsRune(name, 0) {
			return errors.New("pgstore: invalid character identity")
		}
	}
	for _, value := range []float64{c.MaxHP, c.CurHP, c.MaxMP, c.CurMP, c.MaxCP, c.CurCP} {
		if math.IsNaN(value) || math.IsInf(value, 0) || value < 0 || math.Round(value) > math.MaxInt32 {
			return errors.New("pgstore: invalid vital balance")
		}
	}
	ids := map[int32]bool{c.ObjectID: true}
	for _, it := range c.Items {
		if it.ObjectID <= 0 || it.TemplateID <= 0 || it.Count <= 0 || ids[it.ObjectID] {
			return errors.New("pgstore: invalid item identity or balance")
		}
		ids[it.ObjectID] = true
	}
	skills := make(map[int32]bool)
	for _, k := range c.Skills {
		if k.ID <= 0 || k.Level <= 0 || skills[k.ID] {
			return errors.New("pgstore: invalid skill")
		}
		skills[k.ID] = true
	}
	shortcuts := make(map[[2]int32]bool)
	for _, k := range c.Shortcuts {
		key := [2]int32{k.Page, k.Slot}
		if k.Page < 0 || k.Slot < 0 || k.Slot >= 12 || shortcuts[key] {
			return errors.New("pgstore: invalid shortcut")
		}
		shortcuts[key] = true
	}
	quests := make(map[[2]string]bool)
	for _, q := range c.QuestVars {
		key := [2]string{q.Quest, q.Name}
		if quests[key] {
			return errors.New("pgstore: duplicate variable")
		}
		quests[key] = true
	}
	return nil
}
