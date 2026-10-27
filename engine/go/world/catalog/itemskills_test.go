// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestItemSkillReferences(t *testing.T) {
	for _, tc := range []struct {
		input string
		valid bool
	}{{"", true}, {"2031-1", true}, {" 3-2 ; 2031-1 ", true}, {"3", false}, {"3-0", false}, {"-1-1", false}, {"3-1;3-1", false}, {"2147483648-1", false}, {"3-1;", false}} {
		t.Run(tc.input, func(t *testing.T) {
			r := row{cols: map[string]int{"item_skills": 0}, rec: []string{tc.input}}
			refs := itemSkills(&r)
			if (r.err == nil) != tc.valid {
				t.Fatalf("references %q: %+v/%v", tc.input, refs, r.err)
			}
		})
	}
}

func TestWorldIdentityAndClassKinds(t *testing.T) {
	for _, tc := range []struct {
		input string
		valid bool
	}{{"world.id=meadow\nworld.class.0.kind=fighter\nworld.class.10.kind=mage", true}, {"world.id=\n", false}, {"world.id=a\nworld.id=b", false}, {"world.class.0.kind=unknown", false}, {"world.class.-1.kind=mage", false}, {"world.class.0.kind=fighter\nworld.class.0.kind=mage", false}} {
		t.Run(tc.input, func(t *testing.T) {
			root := t.TempDir()
			if err := os.WriteFile(filepath.Join(root, "world.properties"), []byte(tc.input), 0600); err != nil {
				t.Fatal(err)
			}
			c := &Catalog{}
			err := c.loadIdentity(root)
			if (err == nil) != tc.valid {
				t.Fatalf("identity: %v", err)
			}
			if tc.valid && (c.ID() != "meadow" || !reflect.DeepEqual(c.classKinds, map[int32]bool{0: false, 10: true})) {
				t.Fatal("identity/class semantics lost")
			}
		})
	}
}

func FuzzItemSkillReferences(f *testing.F) {
	for _, s := range []string{"", "2031-1", "3-1;3-1", "2147483648-1", "3-1;"} {
		f.Add(s)
	}
	f.Fuzz(func(t *testing.T, s string) {
		if len(s) > 4096 {
			t.Skip()
		}
		r := row{cols: map[string]int{"item_skills": 0}, rec: []string{s}}
		got := itemSkills(&r)
		if r.err != nil {
			return
		}
		seen := make(map[SkillRef]bool)
		for _, ref := range got {
			if ref.ID <= 0 || ref.Level <= 0 || seen[ref] {
				t.Fatalf("invalid accepted reference: %+v", ref)
			}
			seen[ref] = true
		}
	})
}
