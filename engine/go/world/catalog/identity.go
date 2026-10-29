// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"bufio"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// ID returns the authored world.id. Synthetic catalogs may omit a passport.
func (c *Catalog) ID() string { return c.id }

func (c *Catalog) loadIdentity(root string) error {
	f, err := os.Open(filepath.Join(root, "world.properties"))
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	defer f.Close()
	scan := bufio.NewScanner(f)
	for scan.Scan() {
		line := strings.TrimSpace(scan.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		key, value, ok := strings.Cut(line, "=")
		key, value = strings.TrimSpace(key), strings.TrimSpace(value)
		if ok && key == "world.id" {
			value = strings.TrimSpace(value)
			if value == "" || len(value) > 1024 || c.id != "" {
				return errors.New("catalog: invalid world identity")
			}
			c.id = value
		}
		if class, found := strings.CutPrefix(key, "world.class."); ok && found && strings.HasSuffix(class, ".kind") {
			n, err := strconv.ParseInt(strings.TrimSuffix(class, ".kind"), 10, 32)
			if err != nil || n < 0 || value != "fighter" && value != "mage" {
				return errors.New("catalog: invalid class kind")
			}
			if _, exists := c.classKinds[int32(n)]; exists {
				return errors.New("catalog: duplicate class kind")
			}
			if c.classKinds == nil {
				c.classKinds = make(map[int32]bool)
			}
			c.classKinds[int32(n)] = value == "mage"
		}
	}
	return scan.Err()
}
