// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package hunt_test

import (
	"encoding/csv"
	"fmt"
	"os"
	"strconv"
	"strings"
	"testing"

	"github.com/l2go/alatyr/engine/go/world/hunt"
)

func TestJavaParityVectors(t *testing.T) {
	f, err := os.Open("testdata/parity.tsv")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	reader := csv.NewReader(f)
	reader.Comma = '\t'
	reader.Comment = '#'
	reader.FieldsPerRecord = 9
	rows, err := reader.ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) != 14 {
		t.Fatalf("reward vector count=%d, want 14", len(rows))
	}
	for _, row := range rows {
		t.Run(row[0], func(t *testing.T) {
			number := func(s string) int64 {
				n, err := strconv.ParseInt(s, 10, 64)
				if err != nil {
					t.Fatal(err)
				}
				return n
			}
			in := hunt.Snapshot{World: "test-world", Life: uint64(number(row[1])), Level: int32(number(row[2])), XP: number(row[3]), SP: int32(number(row[4])), Currency: 57}
			if coins := number(row[5]); coins > 0 {
				in.Drops = append(in.Drops, hunt.Drop{Item: 57, Count: coins})
			}
			if row[6] != "-" {
				for _, text := range strings.Split(row[6], ";") {
					v := strings.Split(text, ":")
					if len(v) != 5 {
						t.Fatal("side shape")
					}
					side := hunt.Side{ID: number(v[0]), Damage: uint64(number(v[1])), Level: int32(number(v[2])), CoinCursor: int(number(v[4]))}
					if v[3] != "-" {
						for _, text := range strings.Split(v[3], ",") {
							m := strings.Split(text, "@")
							if len(m) < 2 || len(m) > 3 {
								t.Fatal("member shape")
							}
							rate := 1.0
							if len(m) == 3 {
								rate, err = strconv.ParseFloat(m[2], 64)
								if err != nil {
									t.Fatal(err)
								}
							}
							side.Members = append(side.Members, hunt.Member{ID: int32(number(m[0])), Level: int32(number(m[1])), XPRate: rate})
						}
					}
					in.Sides = append(in.Sides, side)
				}
			}
			if row[7] != "-" {
				for _, text := range strings.Split(row[7], ";") {
					v := strings.Split(text, ":")
					if len(v) != 2 {
						t.Fatal("drop shape")
					}
					in.Drops = append(in.Drops, hunt.Drop{Item: int32(number(v[0])), Count: number(v[1])})
				}
			}
			out, err := hunt.Calculate(in)
			if err != nil {
				t.Fatal(err)
			}
			var sides, players, items []string
			for _, side := range out.Sides {
				sides = append(sides, fmt.Sprintf("S%d:%d:%d", side.ID, side.Coins, side.NextCursor))
				for _, p := range side.Players {
					players = append(players, fmt.Sprintf("P%d:%d:%d:%d", p.ID, p.XP, p.SP, p.Coins))
				}
			}
			for _, item := range out.Items {
				items = append(items, fmt.Sprintf("I%d:%d:%d:%t", item.Drop.Item, item.Drop.Count, item.Side, item.Unclaimed))
			}
			got := strings.Join(sides, ";") + "|" + strings.Join(players, ";") + "|" + strings.Join(items, ";")
			if got != row[8] {
				t.Fatalf("reward parity vector\ngot  %s\nwant %s", got, row[8])
			}
		})
	}
}
