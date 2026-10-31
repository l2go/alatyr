// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package meaning

import (
	"encoding/json"
	"os"
	"os/exec"
	"testing"

	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Synthetic limits are test inputs, not deployment defaults.
func testConfig() Config {
	c := Config{
		Wire:       wire.Limits{FrameBytes: 8192, StringBytes: 256, ListItems: 32, Depth: 4, Records: 16, Fields: 64, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts:   proto.Config{Results: 32, Pending: 8, IntentBytes: 2048, EvidenceMS: 1000, QueueMS: 1000, Classes: []string{"dialog", "take", "trade", "revive", "learn"}},
		ProposalMS: 1000, WindowBytes: 4096, Windows: 4, Objects: 8, HistoryEntries: 4, HistoryBytes: 2048,
	}
	for i := range c.Advertised {
		c.Advertised[i] = 65536
	}
	return c
}

func newState(t testing.TB, c Config, policy Policy) *State {
	t.Helper()
	s, err := NewState(c, policy)
	if err != nil {
		t.Fatal(err)
	}
	return s
}

// Fixtures must come from the exact selected published module, with no override.
// A missing module or fixture is a failure, never a skipped conformance check.
func protocolModuleDir(t testing.TB) string {
	t.Helper()
	const path = "github.com/l2go/alatyr/engine/go/protocol"
	const version = "v0.0.0-20261031113231-b145a2d5cf9d"
	cmd := exec.CommandContext(t.Context(), "go", "list", "-m", "-json", path)
	// Resolve the pinned module even when the surrounding test uses go.work.
	cmd.Env = append(os.Environ(), "GOWORK=off")
	raw, err := cmd.Output()
	if err != nil {
		t.Fatal(err)
	}
	var info struct {
		Path, Version, Dir string
		Replace            json.RawMessage
	}
	if err := json.Unmarshal(raw, &info); err != nil {
		t.Fatal(err)
	}
	if info.Path != path || info.Version != version || info.Dir == "" || len(info.Replace) != 0 {
		t.Fatal("expected the exact published protocol module without replacement")
	}
	return info.Dir
}
