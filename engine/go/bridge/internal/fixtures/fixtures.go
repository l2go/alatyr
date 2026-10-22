// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package fixtures locates the single conformance source in the pinned module.
package fixtures

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

type Vector struct{ ID, Transport, Hex, Expected string }

func Load(t testing.TB, profile bool) []Vector {
	t.Helper()
	output, e := exec.Command("go", "list", "-m", "-json", "github.com/l2go/l2jfree/engine/go/protocol").Output()
	if e != nil {
		t.Fatal(e)
	}
	var module struct{ Dir string }
	if e = json.Unmarshal(output, &module); e != nil || module.Dir == "" {
		t.Fatal("missing protocol module directory")
	}
	path := filepath.Join(module.Dir, "testdata", "vectors.json")
	size := 5645
	digest := "cf2c431fec2ec7bd01850eaba2a682af75015a6beceb552a0df5024039ba5545"
	if profile {
		path = filepath.Join(module.Dir, "testdata", "bridge-v1", "vectors.json")
		size = 3269
		digest = "de3d5c23c4250ee1eceaafe942f5714338c1b79043aa713ece9912dd0d9f22bc"
	}
	data, e := os.ReadFile(path)
	if e != nil {
		t.Fatal(e)
	}
	sum := sha256.Sum256(data)
	if len(data) != size || hex.EncodeToString(sum[:]) != digest {
		t.Fatal("shared fixture bytes changed")
	}
	var file struct{ Vectors []Vector }
	if e = json.Unmarshal(data, &file); e != nil {
		t.Fatal(e)
	}
	want := 28
	if profile {
		want = 16
	}
	if len(file.Vectors) != want {
		t.Fatal("fixture count")
	}
	return file.Vectors
}
func Bytes(t testing.TB, v Vector) []byte {
	t.Helper()
	var b []byte
	for i := 0; i < len(v.Hex); {
		if v.Hex[i] == ' ' {
			i++
			continue
		}
		if i+2 > len(v.Hex) {
			t.Fatal("hex length")
		}
		x, e := hex.DecodeString(v.Hex[i : i+2])
		if e != nil {
			t.Fatal(e)
		}
		b = append(b, x...)
		i += 2
	}
	return b
}
