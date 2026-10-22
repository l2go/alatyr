// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"encoding/xml"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

type xmlNode struct {
	XMLName  xml.Name
	Attrs    []xml.Attr `xml:",any,attr"`
	Text     string     `xml:",chardata"`
	Children []xmlNode  `xml:",any"`
}

func (n xmlNode) attr(name string) (string, bool) {
	for _, a := range n.Attrs {
		if a.Name.Local == name {
			return a.Value, true
		}
	}
	return "", false
}

// encoding/xml does not fetch the external document type or expand entities.
func (c *Catalog) loadStatXML(dir, kind string) error {
	return filepath.WalkDir(dir, func(path string, d fs.DirEntry, err error) error {
		if errors.Is(err, fs.ErrNotExist) && path == dir && kind == "etcitem" {
			return nil
		}
		if err != nil {
			return err
		}
		if d.Type()&os.ModeSymlink != 0 {
			return errors.New("symlink in stat XML")
		}
		if d.IsDir() || !strings.HasSuffix(d.Name(), ".xml") {
			return nil
		}
		b, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		var root xmlNode
		if err = xml.Unmarshal(b, &root); err != nil {
			return err
		}
		if root.XMLName.Local != "list" {
			return errors.New("expected list XML")
		}
		for _, n := range root.Children {
			if kind == "skills" && n.XMLName.Local != "skill" || kind != "skills" && n.XMLName.Local != "item" {
				continue
			}
			idText, _ := n.attr("id")
			id32, err := javaDecode(idText)
			if err != nil {
				return err
			}
			id := id32
			if kind != "skills" {
				t, ok := c.items[id]
				if !ok {
					return fmt.Errorf("item XML without template %d", id)
				}
				expected := map[string]ItemKind{"weapon": Weapon, "armor": Armor, "etcitem": EtcItem}[kind]
				if t.Kind != expected {
					return fmt.Errorf("item XML kind mismatch %d", id)
				}
				if _, ok := c.itemFuncs[id]; ok {
					return fmt.Errorf("duplicate item XML %d", id)
				}
				c.itemFuncs[id] = skillFuncs(n, func(v string) (string, error) { return v, nil })
				continue
			}
			id, levels, err := parseSkill(n)
			if err != nil {
				return err
			}
			if _, dup := c.skills[id]; dup {
				return errors.New("duplicate skill XML")
			}
			c.skills[id] = levels
			for level, sk := range levels.levels {
				if sk.Passive {
					c.passive[[2]int32{id, level}] = cloneFuncs(sk.Funcs)
				}
			}
		}
		return nil
	})
}
