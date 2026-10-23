// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestPinnedBridgeContracts(t *testing.T) {
	const path = "github.com/l2go/alatyr/engine/go/bridge"
	b, err := exec.CommandContext(t.Context(), "go", "list", "-m", "-json", path).Output()
	if err != nil {
		t.Fatal(err)
	}
	var m struct {
		Dir, Version string
		Main         bool
		Replace      json.RawMessage
	}
	if err = json.Unmarshal(b, &m); err != nil || m.Dir == "" || len(m.Replace) != 0 || (!m.Main && m.Version != "v0.0.0-20261023154142-3ca081d3eba3") {
		t.Fatal("missing or substituted bridge snapshot")
	}
	b, err = os.ReadFile(filepath.Join(m.Dir, "testdata", "contracts.json"))
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(b)
	if hex.EncodeToString(sum[:]) != "d857602fff6b54a8fcc2ad7aa940d80469d28a485a8db8151af07b67898458d0" {
		t.Fatal("bridge contract fixture changed")
	}
	var c struct{ Scenarios []struct{ ID string } }
	if err = json.Unmarshal(b, &c); err != nil || len(c.Scenarios) != 15 {
		t.Fatal("bridge scenario count", err)
	}
}
