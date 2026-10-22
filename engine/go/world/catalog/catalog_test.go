// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/xml"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestRenderedAuthoredStats(t *testing.T) {
	_, file, _, _ := runtime.Caller(0)
	// Read the repository world (CC-BY-SA-4.0), without copying its data.
	c, err := Load(filepath.Join(filepath.Dir(file), "../../../../world"))
	if err != nil {
		t.Fatal(err)
	}
	p, ok := c.Player(0)
	if !ok || p.Name == "" || p.RunSpd != 128 {
		t.Fatal("player template")
	}
	if _, ok = c.Player(10); ok {
		t.Fatal("invented class")
	}
	item, ok := c.Item(1)
	if !ok || item.PhysicalDamage != 17 {
		t.Fatal("item template")
	}
	fs := c.ItemFuncs(1)
	if len(fs) != 5 {
		t.Fatalf("CSV generated extra functions: %d", len(fs))
	}
	fs[0].Value = 999
	if c.ItemFuncs(1)[0].Value != 17 {
		t.Fatal("mutable catalog")
	}
	if fs = c.PassiveFuncs(257, 2); len(fs) != 1 || fs[0].Value != 6 {
		t.Fatal("passive table level")
	}
	if len(c.PassiveFuncs(2031, 1)) != 0 {
		t.Fatal("active healing effect treated as passive")
	}
}
func TestCSVRejectsDuplicateHeadersAndNonfiniteValues(t *testing.T) {
	for _, body := range []string{"n,n\n1,2\n", "n\nNaN\n", "n\n+Inf\n", "n\n1,2\n"} {
		path := filepath.Join(t.TempDir(), "data.csv")
		if err := os.WriteFile(path, []byte(body), 0600); err != nil {
			t.Fatal(err)
		}
		err := readCSV(path, func(r row) error { r.float("n"); return r.err })
		if err == nil {
			t.Fatalf("accepted invalid CSV")
		}
	}
}
func TestFunctionConditionsAndUnsupportedValues(t *testing.T) {
	var n xmlNode
	err := xml.Unmarshal([]byte("<skill><for><add order=\"0x40\" stat=\"pAtk\" val=\"4\"><using kind=\"Sword|Light\"/></add><div order=\"0x40\" stat=\"pAtk\" val=\"0\"/><add order=\"0x40\" stat=\"pAtk\" val=\"NaN\"/></for></skill>"), &n)
	if err != nil {
		t.Fatal(err)
	}
	fs := skillFuncs(n, func(v string) (string, error) { return v, nil })
	if len(fs) != 3 || fs[0].Cond == nil || !fs[1].Conditional || !fs[2].Conditional {
		t.Fatal("unsupported stat function not marked")
	}
	copied := cloneFuncs(fs)
	copied[0].Cond.Args[0].Kinds[0] = "changed"
	if strings.Join(fs[0].Cond.Args[0].Kinds, "") != "sword" {
		t.Fatal("condition alias")
	}
}
