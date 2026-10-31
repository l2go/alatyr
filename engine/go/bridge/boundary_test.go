// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

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
	const own = parent + "/bridge"
	const protocol = "github.com/l2go/alatyr/engine/go/protocol"
	const version = "v0.0.0-20261031085835-fd03dfdaa563"
	err := filepath.WalkDir(".", func(path string, d fs.DirEntry, err error) error {
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
		for _, im := range f.Imports {
			p, err := strconv.Unquote(im.Path.Value)
			if err != nil {
				return err
			}
			if strings.Contains(strings.Split(p, "/")[0], ".") && !strings.HasPrefix(p, own+"/") && !strings.HasPrefix(p, protocol+"/") && p != own && p != protocol {
				t.Errorf("import outside bridge boundary: %s", p)
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
			if path != own && !strings.HasPrefix(path, own+"/") {
				t.Errorf("package outside bridge boundary: %s", path)
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
			t.Errorf("module outside bridge boundary: %s", p.Module.Path)
		}
	}
	if err = cmd.Wait(); err != nil {
		t.Fatal(err)
	}
	if count == 0 {
		t.Fatal("protocol dependency not inspected")
	}
}
