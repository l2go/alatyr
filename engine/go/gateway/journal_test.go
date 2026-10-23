// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestDiagnosticBoundsAndFailureIsolation(t *testing.T) {
	dir := t.TempDir()
	j, err := NewJournal(dir, 1024, 512, time.Hour, time.Second, 8)
	if err != nil {
		t.Fatal(err)
	}
	old := filepath.Join(dir, "events-00000000000000000001-00000001.jsonl")
	if err := os.WriteFile(old, []byte("old"), 0600); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 100; i++ {
		j.Record(Summary{Session: 1, N: uint64(i)})
	}
	if err := j.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(old); !os.IsNotExist(err) {
		t.Fatal("expired records kept", err)
	}
	files, total, err := j.files()
	if err != nil || total > 1024 {
		t.Fatal(total, err)
	}
	for _, file := range files {
		if file.size > 512 {
			t.Fatal(file)
		}
		b, err := os.ReadFile(file.path)
		if err != nil {
			t.Fatal(err)
		}
		if strings.Contains(string(b), "private") || strings.Contains(string(b), "password") {
			t.Fatal(string(b))
		}
		info, err := os.Stat(file.path)
		if err != nil {
			t.Fatal(err)
		}
		if info.Mode().Perm() != 0600 {
			t.Fatal(info.Mode())
		}
	}
	blocked := filepath.Join(t.TempDir(), "broken")
	k, err := NewJournal(blocked, 1024, 512, time.Hour, time.Second, 8)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(blocked); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(blocked, []byte("not a directory"), 0600); err != nil {
		t.Fatal(err)
	}
	k.Record(Summary{N: 1})
	if err := k.Close(); err == nil {
		t.Fatal("Close must report the disk error")
	}
	if k.Errors() == 0 {
		t.Fatal("missing diagnostic failure count")
	}
}
