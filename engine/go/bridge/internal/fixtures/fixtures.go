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

// Vector is one shared conformance vector.
type Vector struct{ ID, Transport, Hex, Expected string }

// Load reads the core vectors, or the bridge/1 vectors when profile is set,
// from the pinned protocol module and fails t unless their bytes match the
// recorded size and digest.
func Load(t testing.TB, profile bool) []Vector {
	t.Helper()
	output, err := exec.Command("go", "list", "-m", "-json", "github.com/l2go/alatyr/engine/go/protocol").Output()
	if err != nil {
		t.Fatal(err)
	}
	var module struct{ Dir string }
	if err = json.Unmarshal(output, &module); err != nil || module.Dir == "" {
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
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	if len(data) != size || hex.EncodeToString(sum[:]) != digest {
		t.Fatal("shared fixture bytes changed")
	}
	var file struct{ Vectors []Vector }
	if err = json.Unmarshal(data, &file); err != nil {
		t.Fatal(err)
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

// Bytes decodes the space-separated hex of v.
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
		x, err := hex.DecodeString(v.Hex[i : i+2])
		if err != nil {
			t.Fatal(err)
		}
		b = append(b, x...)
		i += 2
	}
	return b
}
