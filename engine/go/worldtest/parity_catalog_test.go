// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"encoding/csv"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func parityNames() []string {
	return []string{"g01", "g02-target", "g02-receipt", "g03", "g04-buy", "g04-sell", "g05", "g06", "g07", "g08-before", "g08-after", "g09", "g10", "equip", "heal", "learning-next", "hunt-parties", "hunt-neutral", "death-save", "unknown-item"}
}

func parityCharacter(index, actor int) world.Character {
	base := int32(1501000100 + 100*index)
	char := world.Character{ObjectID: base + int32(actor), Account: "parity-" + parityNames()[index], Name: fmt.Sprintf("Parity%02dA%d", index, actor), ClassID: 0, Level: 2, Exp: 68, SP: 100, CurHP: 200, CurMP: 50, CurCP: 20, Vitality: 20000, X: -71057, Y: 257883, Z: -3104, Skills: []world.Skill{{ID: 3, Level: 1}}}
	if actor > 0 {
		char.Account += fmt.Sprintf("-a%d", actor)
	}
	item := base + 20 + int32(20*actor)
	char.Items = []world.Item{{ObjectID: item, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: item + 1, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: item + 2, TemplateID: 1060, Count: 3, Slot: -1}, {ObjectID: item + 3, TemplateID: 23, Count: 1, Slot: -1}, {ObjectID: item + 4, TemplateID: 28, Count: 1, Slot: -1}, {ObjectID: item + 5, TemplateID: 39, Count: 1, Slot: -1}}
	switch parityNames()[index] {
	case "g03":
		char.X, char.Y = -70357, 256863
		char.Items[3].Slot, char.Items[4].Slot, char.Items[5].Slot = catalog.SlotChest, catalog.SlotLegs, catalog.SlotFeet
	case "g04-buy", "g04-sell":
		char.Y = 258243
	case "g06":
		char.CurHP = 0
	case "death-save":
		char.X, char.Y = -70357, 256863
	case "heal":
		char.X, char.Y = -70357, 256863
		char.CurHP = 20
	case "learning-next":
		char.Level, char.Exp = 3, 363
	case "hunt-parties":
		char.X, char.Y = -60000, 260000
	case "hunt-neutral":
		char.X, char.Y = -59000, 260000
	case "unknown-item":
		char.Items = append(char.Items, world.Item{ObjectID: item + 6, TemplateID: 9999001, Count: 7, Slot: -1})
	}
	return char
}

// These three probes supplement, rather than replace, Okolitsa. Identical
// fixed table inputs make party/neutral balances comparable despite Java's
// intentionally nondeterministic ordinary drop-table RNG.
func writeParityCatalog(t *testing.T, root string) {
	t.Helper()
	appendRows := func(name string, rows []map[string]string) {
		path := filepath.Join(root, "catalog", name+".csv")
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		records, quoted := parityCSV(t, data)
		if len(records) < 2 {
			t.Fatalf("fixture table %s has no authored rows", name)
		}
		// COPY distinguishes an unquoted empty (NULL) from a quoted empty
		// string. Do not round-trip authored rows through csv.Writer.
		var out strings.Builder
		out.Write(data)
		if data[len(data)-1] != '\n' {
			out.WriteByte('\n')
		}
		key := "npc_template_id"
		if name == "npc_template" {
			key = "id"
		}
		column := -1
		for i, name := range records[0] {
			if name == key {
				column = i
			}
		}
		if column < 0 {
			t.Fatalf("fixture table %s has no %s", name, key)
		}
		template := -1
		for i, record := range records[1:] {
			if record[column] == "18003" {
				template = i + 1
				break
			}
		}
		if template < 0 {
			t.Fatalf("fixture table %s has no authored forager row", name)
		}
		for _, row := range rows {
			for key := range row {
				found := false
				for _, column := range records[0] {
					if column == key {
						found = true
						break
					}
				}
				if !found {
					t.Fatalf("fixture table %s has no column %s", name, key)
				}
			}
			for i, column := range records[0] {
				if i > 0 {
					out.WriteByte(',')
				}
				value := records[template][i]
				if replacement, ok := row[column]; ok {
					value = replacement
				}
				if value == "" && quoted[template][i] {
					out.WriteString(`""`)
					continue
				}
				// The standard encoder handles commas, quotes and line breaks;
				// quoted-empty metadata above preserves PostgreSQL NULL semantics.
				var cell strings.Builder
				writer := csv.NewWriter(&cell)
				if err := writer.Write([]string{value}); err != nil {
					t.Fatal(err)
				}
				writer.Flush()
				if err := writer.Error(); err != nil {
					t.Fatal(err)
				}
				out.WriteString(strings.TrimSuffix(cell.String(), "\n"))
			}
			out.WriteByte('\n')
		}
		if err := os.WriteFile(path, []byte(out.String()), 0600); err != nil {
			t.Fatal(err)
		}
	}
	var npcs, spawns, drops []map[string]string
	for i, x := range []int{-60000, -59000, -59960} {
		id := strconv.Itoa(910001 + i)
		npcs = append(npcs, map[string]string{"id": id, "client_template_id": id, "name": "Reward Probe", "max_hp": "100", "hp_regen": "0", "reward_exp": "100", "reward_sp": "20", "level": "1"})
		spawns = append(spawns, map[string]string{"spawn_group": "WORLD", "id": strconv.Itoa(1001 + i), "npc_template_id": id, "npc_count": "1", "x": strconv.Itoa(x), "y": "260000", "z": "-3104", "heading": "0", "respawn_delay_s": "10", "period_of_day": "ALWAYS"})
		for j, item := range []int{57, 1060, 4} {
			count := "1"
			if item == 57 {
				count = "13"
			}
			drops = append(drops, map[string]string{"npc_template_id": id, "item_template_id": strconv.Itoa(item), "category": strconv.Itoa(j), "min_count": count, "max_count": count, "chance": "1000000"})
		}
	}
	// Keep the authored forager table intact and append a distinct guaranteed
	// item category for the participant pickup witness. Currency-only pickup
	// is not certified by the reference bridge's receipt matcher.
	drops = append(drops, map[string]string{"npc_template_id": "18003", "item_template_id": "1060", "category": "3", "min_count": "1", "max_count": "1", "chance": "1000000"})
	appendRows("npc_template", npcs)
	appendRows("spawn", spawns)
	appendRows("drop", drops)
}

// parityCSV retains the one piece of COPY metadata that encoding/csv drops:
// whether an empty field was quoted. FieldPos reports byte positions in input.
func parityCSV(t *testing.T, data []byte) ([][]string, [][]bool) {
	t.Helper()
	starts := []int{0}
	for i, b := range data {
		if b == '\n' {
			starts = append(starts, i+1)
		}
	}
	reader := csv.NewReader(bytes.NewReader(data))
	var records [][]string
	var quoted [][]bool
	for {
		record, err := reader.Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		flags := make([]bool, len(record))
		for i := range record {
			line, column := reader.FieldPos(i)
			offset := starts[line-1] + column - 1
			flags[i] = offset < len(data) && data[offset] == '"'
		}
		records = append(records, record)
		quoted = append(quoted, flags)
	}
	return records, quoted
}

func paritySQL() string {
	var out strings.Builder
	for i := range parityNames() {
		actors := 1
		if parityNames()[i] == "hunt-parties" {
			actors = 4
		}
		for a := range actors {
			c := parityCharacter(i, a)
			fmt.Fprintf(&out, "INSERT INTO login.account (name,password_hash) VALUES ('%s','2jmj7l5rSw0yVb/vlWAYkK/YBwk=');\n", c.Account)
			fmt.Fprintf(&out, "INSERT INTO world.player (id,account_name,name,race_id,active_class_id,base_class_id,level,exp,sp,max_hp,current_hp,max_mp,current_mp,x,y,z,vitality_points) VALUES (%d,'%s','%s',0,0,0,%d,%d,%d,200,%g,100,%g,%d,%d,%d,%d);\n", c.ObjectID, c.Account, c.Name, c.Level, c.Exp, c.SP, c.CurHP, c.CurMP, c.X, c.Y, c.Z, c.Vitality)
			for _, it := range c.Items {
				location, slot := "INVENTORY", int32(0)
				if it.Equipped() {
					location, slot = "PAPERDOLL", it.Slot
				}
				fmt.Fprintf(&out, "INSERT INTO world.item (id,item_template_id,owner_player_id,location,location_slot,count) VALUES (%d,%d,%d,'%s',%d,%d);\n", it.ObjectID, it.TemplateID, c.ObjectID, location, slot, it.Count)
			}
			for _, skill := range c.Skills {
				fmt.Fprintf(&out, "INSERT INTO world.player_skill (player_id,class_index,skill_id,skill_level) VALUES (%d,0,%d,%d);\n", c.ObjectID, skill.ID, skill.Level)
			}
		}
	}
	out.WriteString("INSERT INTO world.player_birthday (player_id,created_on,gift_claimed_year) SELECT id,CURRENT_DATE,EXTRACT(YEAR FROM CURRENT_DATE)::smallint FROM world.player WHERE id >= 1501000000 AND id < 1502000000;\n")
	out.WriteString("INSERT INTO world.player_recommendation_status (player_id,recommendations_left,recommendations_received,updated_at) SELECT id,3,0,CURRENT_TIMESTAMP FROM world.player WHERE id >= 1501000000 AND id < 1502000000;\n")
	return out.String()
}

func TestPrepareJavaParityFixtures(t *testing.T) {
	root, sql := os.Getenv("JAVA_PARITY_WORLD"), os.Getenv("JAVA_PARITY_SQL")
	if root == "" && sql == "" {
		t.Skip("only the disposable integration harness prepares Java fixtures")
	}
	if os.Getenv("CI") != "true" || root == "" || sql == "" {
		t.Fatal("fixture preparation requires the CI harness and both paths")
	}
	writeParityCatalog(t, root)
	if err := os.WriteFile(sql, []byte(paritySQL()), 0600); err != nil {
		t.Fatal(err)
	}
}
