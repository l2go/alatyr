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
	j, err := NewJournal(JournalConfig{Directory: dir, Bytes: 1024, FileBytes: 512, Age: time.Hour, Cleanup: time.Second, Queued: 8})
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
	k, err := NewJournal(JournalConfig{Directory: blocked, Bytes: 1024, FileBytes: 512, Age: time.Hour, Cleanup: time.Second, Queued: 8})
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

func TestJournalConfigValidation(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*JournalConfig)
	}{
		{"queue", func(c *JournalConfig) { c.Queued = 0 }},
		{"total-bytes", func(c *JournalConfig) { c.Bytes = 0 }},
		{"file-bytes", func(c *JournalConfig) { c.FileBytes = 0 }},
		{"file-over-total", func(c *JournalConfig) { c.FileBytes = c.Bytes + 1 }},
		{"age", func(c *JournalConfig) { c.Age = 0 }},
		{"cleanup", func(c *JournalConfig) { c.Cleanup = 0 }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c := JournalConfig{Directory: filepath.Join(t.TempDir(), "journal"), Bytes: 1024,
				FileBytes: 512, Age: time.Hour, Cleanup: time.Second, Queued: 8}
			tc.mutate(&c)
			j, err := NewJournal(c)
			if err == nil {
				t.Error("NewJournal accepted invalid limits")
				if err := j.Close(); err != nil {
					t.Error(err)
				}
			}
			if _, err := os.Stat(c.Directory); !os.IsNotExist(err) {
				t.Fatalf("invalid configuration touched directory: %v", err)
			}
		})
	}
}
