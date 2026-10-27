// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"testing"
)

func TestParityCatalogMatchesJavaCopySchema(t *testing.T) {
	source := filepath.Join("..", "..", "..", "world", "catalog")
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, "catalog"), 0700); err != nil {
		t.Fatal(err)
	}
	if err := os.CopyFS(filepath.Join(root, "catalog"), os.DirFS(source)); err != nil {
		t.Fatal(err)
	}
	ddl, err := os.ReadFile(filepath.Join("..", "..", "java", "game", "src", "main", "resources", "db", "catalog", "02_npc_spawn.sql"))
	if err != nil {
		t.Fatal(err)
	}
	writeParityCatalog(t, root)
	tables := make(map[string][][]string)
	for _, tc := range []struct {
		name    string
		added   int
		changed string
	}{
		{"npc_template", 3, "id client_template_id name max_hp hp_regen reward_exp reward_sp level"},
		{"spawn", 3, "spawn_group id npc_template_id npc_count x y z heading respawn_delay_s period_of_day"},
		{"drop", 9, "npc_template_id item_template_id category min_count max_count chance"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			original, err := os.ReadFile(filepath.Join(source, tc.name+".csv"))
			if err != nil {
				t.Fatal(err)
			}
			data, err := os.ReadFile(filepath.Join(root, "catalog", tc.name+".csv"))
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.HasPrefix(data, original) {
				t.Fatal("fixture rewrote authored bytes, including COPY NULL/empty distinctions")
			}
			rows, quoted := parityCSV(t, data)
			before, _ := parityCSV(t, original)
			if len(rows) != len(before)+tc.added {
				t.Fatalf("added rows=%d, want %d", len(rows)-len(before), tc.added)
			}
			tables[tc.name] = rows
			// Read the actual Java DDL, not a second handwritten list of required fields.
			block := regexp.MustCompile(`(?s)CREATE UNLOGGED TABLE ` + tc.name + ` \((.*?)\n\);`).FindSubmatch(ddl)
			if len(block) != 2 {
				t.Fatal("Java catalog table missing")
			}
			var columns, declarations []string
			for line := range strings.SplitSeq(string(block[1]), "\n") {
				line = strings.TrimSpace(line)
				if line == "" || strings.HasPrefix(line, "PRIMARY KEY ") || strings.HasPrefix(line, "CHECK ") {
					continue
				}
				fields := strings.Fields(line)
				columns = append(columns, fields[0])
				declarations = append(declarations, line)
			}
			if !reflect.DeepEqual(rows[0], columns) {
				t.Fatalf("COPY header differs from complete Java schema:\ngot %v\nwant %v", rows[0], columns)
			}
			for rowIndex, row := range rows[1:] {
				for i, value := range row {
					decl := declarations[i]
					null := value == "" && !quoted[rowIndex+1][i]
					required := strings.Contains(decl, " NOT NULL") || strings.Contains(decl, " PRIMARY KEY")
					if null {
						if required {
							t.Fatalf("COPY row %d column %s becomes NULL despite %s", rowIndex+2, columns[i], decl)
						}
						continue
					}
					typ := strings.TrimSuffix(strings.Fields(decl)[1], ",")
					switch {
					case typ == "text": // A quoted empty string is valid text, never SQL NULL.
					case typ == "boolean":
						if value != "true" && value != "false" {
							t.Fatalf("invalid boolean %s=%q", columns[i], value)
						}
					case typ == "integer" || typ == "smallint":
						bits := 32
						if typ == "smallint" {
							bits = 16
						}
						n, err := strconv.ParseInt(value, 10, bits)
						if err != nil {
							t.Fatalf("invalid %s %s=%q", typ, columns[i], value)
						}
						if strings.Contains(decl, ">= 0") && n < 0 || strings.Contains(decl, ">= -1") && n < -1 {
							t.Fatalf("CHECK violation %s=%d", columns[i], n)
						}
					case strings.HasPrefix(typ, "numeric("):
						n, err := strconv.ParseFloat(value, 64)
						if err != nil || math.IsNaN(n) || math.IsInf(n, 0) || strings.Contains(decl, ">= 0") && n < 0 {
							t.Fatalf("invalid numeric %s=%q", columns[i], value)
						}
					default:
						t.Fatalf("unhandled Java column type %s", typ)
					}
				}
			}
			// Every unspecified field of a probe comes from the complete authored row,
			// including nullable fields and quoted empty NOT NULL text.
			key := "npc_template_id"
			if tc.name == "npc_template" {
				key = "id"
			}
			index := 0
			for i, c := range columns {
				if c == key {
					index = i
				}
			}
			template := 0
			for i, row := range before[1:] {
				if row[index] == "18003" {
					template = i + 1
					break
				}
			}
			if template == 0 {
				t.Fatal("authored forager row missing")
			}
			_, beforeQuoted := parityCSV(t, original)
			changed := strings.Fields(tc.changed)
			for j := len(before); j < len(rows); j++ {
				for i, column := range columns {
					if slices.Contains(changed, column) {
						continue
					}
					if rows[j][i] != before[template][i] || rows[j][i] == "" && quoted[j][i] != beforeQuoted[template][i] {
						t.Fatalf("probe row %d did not inherit %s", j, column)
					}
				}
			}
		})
	}
	if t.Failed() {
		return // A schema failure can leave a table absent from the cross-table snapshot.
	}
	// Primary keys, cross-table references, enum checks and drop intervals of all
	// generated rows must also remain valid, rather than merely avoiding NULL.
	ids := make(map[string]bool)
	seen := make(map[string]bool)
	for _, row := range tables["npc_template"][1:] {
		if ids[row[0]] {
			t.Fatal("duplicate NPC id")
		}
		ids[row[0]] = true
	}
	for _, row := range tables["npc_template"][1:] {
		if !ids[row[1]] {
			t.Fatal("missing client template reference")
		}
	}
	for _, row := range tables["spawn"][1:] {
		key := row[0] + ":" + row[1]
		if seen[key] || !ids[row[2]] || row[0] != "WORLD" || row[9] != "ALWAYS" {
			t.Fatalf("spawn key/reference/enum: %v", row)
		}
		seen[key] = true
	}
	items := make(map[string]bool)
	for _, name := range []string{"weapon_template", "armor_template", "etc_item_template"} {
		data, err := os.ReadFile(filepath.Join(source, name+".csv"))
		if err != nil {
			t.Fatal(err)
		}
		rows, _ := parityCSV(t, data)
		for _, row := range rows[1:] {
			items[row[0]] = true
		}
	}
	clear(seen)
	for _, row := range tables["drop"][1:] {
		key := strings.Join(row[:3], ":")
		lo, _ := strconv.ParseInt(row[3], 10, 64)
		hi, _ := strconv.ParseInt(row[4], 10, 64)
		if seen[key] || !ids[row[0]] || !items[row[1]] || hi < lo {
			t.Fatalf("drop key/reference/interval: %v", row)
		}
		seen[key] = true
	}
}

func TestParityCSVRetainsQuotedEmptyMetadata(t *testing.T) {
	rows, quoted := parityCSV(t, []byte("a,b,c\n,\"\",\"comma, quote \"\"x\"\"\"\n"))
	if !reflect.DeepEqual(rows[1], []string{"", "", `comma, quote "x"`}) || !reflect.DeepEqual(quoted[1], []bool{false, true, true}) {
		t.Fatalf("COPY metadata lost: %v/%v", rows, quoted)
	}
}
