// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// A stat directory that is itself a symlink is refused, as filepath.WalkDir
// refused it before the catalog read through fs.FS.
func TestStatXMLRootSymlinkIsRefused(t *testing.T) {
	root := t.TempDir()
	target := t.TempDir()
	if err := os.WriteFile(filepath.Join(target, "external.xml"), []byte(`<list/>`), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, filepath.Join(root, "weapon")); err != nil {
		t.Fatal(err)
	}
	c := &Catalog{}
	if err := c.loadStatXML(os.DirFS(root), "weapon", "weapon"); err == nil || !strings.Contains(err.Error(), "symlink") {
		t.Fatalf("a symlinked stat directory was read: %v", err)
	}
}
