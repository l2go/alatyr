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
	"strconv"
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
func (n xmlNode) set(name string) string {
	for _, a := range n.Children {
		if a.XMLName.Local == "set" {
			k, _ := a.attr("name")
			if k == name {
				v, _ := a.attr("val")
				return v
			}
		}
	}
	return ""
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
			id64, err := strconv.ParseInt(idText, 10, 32)
			if err != nil {
				return err
			}
			id := int32(id64)
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
			// Active skill effects are not executed by this base.
			if n.set("operateType") != "OP_PASSIVE" {
				continue
			}
			levelsText, _ := n.attr("levels")
			levels, err := strconv.ParseInt(levelsText, 10, 32)
			if err != nil || levels < 1 || levels > 1000 {
				return errors.New("invalid passive levels")
			}
			tables := map[string][]string{}
			for _, ch := range n.Children {
				if ch.XMLName.Local == "table" {
					name, _ := ch.attr("name")
					if _, ok := tables[name]; ok {
						return errors.New("duplicate stat table")
					}
					tables[name] = strings.Fields(ch.Text)
				}
			}
			for level := int32(1); level <= int32(levels); level++ {
				key := [2]int32{id, level}
				if _, ok := c.passive[key]; ok {
					return errors.New("duplicate passive skill")
				}
				value := func(v string) (string, error) {
					if !strings.HasPrefix(v, "#") {
						return v, nil
					}
					t, ok := tables[v]
					if !ok || int(level) > len(t) {
						return "", errors.New("missing table level")
					}
					return t[level-1], nil
				}
				c.passive[key] = skillFuncs(n, value)
			}
		}
		return nil
	})
}
