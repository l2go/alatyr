// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"encoding/json"
	"errors"
	"go/parser"
	"go/token"
	"io"
	"io/fs"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

func TestDependencyBoundary(t *testing.T) {
	const parent = "github.com/l2go/alatyr/engine/go"
	const own = parent + "/gateway"
	const protocol = "github.com/l2go/alatyr/engine/go/protocol"
	const version = "v0.0.0-20261023084050-3b52c8d267a0"
	const bridge = "github.com/l2go/alatyr/engine/go/bridge"
	modCmd := exec.CommandContext(t.Context(), "go", "mod", "edit", "-json")
	modJSON, err := modCmd.Output()
	if err != nil {
		t.Fatal(err)
	}
	var mod struct {
		Require []struct {
			Path, Version string
			Indirect      bool
		}
		Replace, Exclude, Tool []json.RawMessage
	}
	if err := json.Unmarshal(modJSON, &mod); err != nil {
		t.Fatal(err)
	}
	if len(mod.Require) != 1 || mod.Require[0].Path != protocol ||
		mod.Require[0].Version != version || mod.Require[0].Indirect ||
		len(mod.Replace)+len(mod.Exclude)+len(mod.Tool) != 0 {
		t.Fatal("module must require only the exact protocol snapshot, without overrides")
	}
	err = filepath.WalkDir(".", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		f, err := parser.ParseFile(token.NewFileSet(), path, nil, parser.ImportsOnly)
		if err != nil {
			return err
		}
		dir := filepath.ToSlash(filepath.Dir(path))
		ownPath := own
		if dir != "." {
			ownPath += "/" + dir
		}
		if !gatewayImportAllowed(dir, ownPath, false) {
			t.Errorf("unknown gateway package directory: %s", dir)
		}
		for _, im := range f.Imports {
			p, err := strconv.Unquote(im.Path.Value)
			if err != nil {
				return err
			}
			if strings.Contains(strings.Split(p, "/")[0], ".") && !gatewayImportAllowed(dir, p, strings.HasSuffix(path, "_test.go")) {
				t.Errorf("import outside gateway package boundary: %s", p)
			}
			if p == "C" {
				t.Error("foreign runtime dependency")
			}
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	cmd := exec.CommandContext(t.Context(), "go", "list", "-deps", "-test", "-json", "./...")
	out, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	if err = cmd.Start(); err != nil {
		t.Fatal(err)
	}
	dec := json.NewDecoder(out)
	count := 0
	for {
		var p struct {
			Standard   bool
			ImportPath string
			Module     *struct {
				Path, Version string
				Main          bool
				Replace       json.RawMessage
			}
		}
		err = dec.Decode(&p)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			waitErr := cmd.Wait()
			t.Fatal(errors.Join(err, waitErr))
		}
		if p.Standard {
			continue
		}
		if p.Module == nil {
			t.Errorf("dependency without module: %s", p.ImportPath)
			continue
		}
		if len(p.Module.Replace) != 0 {
			t.Error("replacement dependency")
		}
		switch p.Module.Path {
		case parent:
			path, _, _ := strings.Cut(p.ImportPath, " [")
			path = strings.TrimSuffix(strings.TrimSuffix(path, ".test"), "_test")
			if path != own && !strings.HasPrefix(path, own+"/") && path != bridge && !strings.HasPrefix(path, bridge+"/") {
				t.Errorf("package outside gateway boundary: %s", path)
			}
		case protocol:
			if p.ImportPath != protocol && !strings.HasPrefix(p.ImportPath, protocol+"/") {
				t.Errorf("unexpected protocol package: %s", p.ImportPath)
			}
			count++
			if !p.Module.Main && p.Module.Version != version {
				t.Error("protocol is not pinned")
			}
		default:
			t.Errorf("module outside gateway boundary: %s", p.Module.Path)
		}
	}
	if err = cmd.Wait(); err != nil {
		t.Fatal(err)
	}
	if count == 0 {
		t.Fatal("protocol dependency not inspected")
	}
}

// gatewayImportAllowed mirrors the package boundary independently of the CI
// guard, so the standalone package tests keep enforcing the runtime split.
func gatewayImportAllowed(dir, path string, test bool) bool {
	const root = "github.com/l2go/alatyr/engine/go/gateway"
	const bridge = "github.com/l2go/alatyr/engine/go/bridge"
	const protocol = "github.com/l2go/alatyr/engine/go/protocol"
	var allowed []string
	switch dir {
	case ".":
		allowed = []string{root}
	case "meaning":
		allowed = []string{root, root + "/meaning"}
	case "backend":
		allowed = []string{root, root + "/meaning", root + "/backend"}
		if test {
			allowed = append(allowed, root+"/fixture", root+"/gatewaytest")
		}
	case "fixture":
		allowed = []string{root + "/fixture"}
	case "gatewaytest":
		allowed = []string{root, root + "/meaning", root + "/backend", root + "/fixture", root + "/gatewaytest"}
	default:
		return false
	}
	for _, own := range allowed {
		if path == own {
			return true
		}
	}
	if path == protocol || strings.HasPrefix(path, protocol+"/") {
		return true
	}
	return (dir == "backend" || dir == "fixture" || dir == "gatewaytest") && (path == bridge || strings.HasPrefix(path, bridge+"/"))
}
