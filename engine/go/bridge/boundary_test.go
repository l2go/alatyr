// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"encoding/json"
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
	const own = "github.com/l2go/alatyr/engine/go/bridge"
	const protocol = "github.com/l2go/l2jfree/engine/go/protocol"
	const version = "v0.0.0-20261021214322-a0349830aa3d"
	err := filepath.WalkDir(".", func(path string, d fs.DirEntry, e error) error {
		if e != nil {
			return e
		}
		if d.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		f, e := parser.ParseFile(token.NewFileSet(), path, nil, parser.ImportsOnly)
		if e != nil {
			return e
		}
		for _, im := range f.Imports {
			p, e := strconv.Unquote(im.Path.Value)
			if e != nil {
				return e
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
	cmd := exec.Command("go", "list", "-deps", "-test", "-json", "./...")
	out, e := cmd.StdoutPipe()
	if e != nil {
		t.Fatal(e)
	}
	if e = cmd.Start(); e != nil {
		t.Fatal(e)
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
		e = dec.Decode(&p)
		if e == io.EOF {
			break
		}
		if e != nil {
			cmd.Wait()
			t.Fatal(e)
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
		case own:
		case protocol:
			count++
			if !p.Module.Main && p.Module.Version != version {
				t.Error("protocol is not pinned")
			}
		default:
			t.Errorf("module outside bridge boundary: %s", p.Module.Path)
		}
	}
	if e = cmd.Wait(); e != nil {
		t.Fatal(e)
	}
	if count == 0 {
		t.Fatal("protocol dependency not inspected")
	}
}
