// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestHelpDescribesBothCommands(t *testing.T) {
	var out bytes.Buffer
	if code := run([]string{"--help"}, &out, io.Discard); code != 0 {
		t.Fatalf("code %d", code)
	}
	text := out.String()
	if !strings.Contains(text, "render") || !strings.Contains(text, "check") || !strings.Contains(text, "--world") {
		t.Fatalf("%s", text)
	}
}

func TestWorldFlagIsRequired(t *testing.T) {
	if code := run([]string{"check"}, io.Discard, io.Discard); code != 1 {
		t.Fatalf("code %d", code)
	}
}

func TestCheckCommandOnGoodWorld(t *testing.T) {
	var errb bytes.Buffer
	code := run([]string{"check", "--world", "testdata/good"}, io.Discard, &errb)
	if code != 0 {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
}

func TestCheckCommandOnBrokenWorld(t *testing.T) {
	var errb bytes.Buffer
	code := run([]string{"check", "--world", "testdata/bad-kind"}, io.Discard, &errb)
	if code != 1 {
		t.Fatalf("code %d", code)
	}
	if !strings.Contains(errb.String(), "i18n/en.tsv:7: kind must be proper or common") {
		t.Fatalf("%s", errb.String())
	}
}

func TestRenderCommand(t *testing.T) {
	dir := t.TempDir()
	copyTree(t, "testdata/good", dir)
	if err := os.RemoveAll(filepath.Join(dir, "catalog")); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll(filepath.Join(dir, "data")); err != nil {
		t.Fatal(err)
	}
	var errb bytes.Buffer
	code := run([]string{"render", "--world", dir, "--locale", "en"}, io.Discard, &errb)
	if code != 0 {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
	got, err := os.ReadFile(filepath.Join(dir, "data", "seller.htm"))
	if err != nil {
		t.Fatal(err)
	}
	want, err := os.ReadFile(filepath.Join("testdata", "good", "data", "seller.htm"))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, want) {
		t.Fatalf("%s", got)
	}
}

func TestHelpFlagAfterTheCommand(t *testing.T) {
	var out, errb bytes.Buffer
	if code := run([]string{"check", "--help"}, &out, &errb); code != 0 {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
	if !strings.Contains(out.String(), "render") || !strings.Contains(out.String(), "check") {
		t.Fatalf("%s", out.String())
	}
}

func TestLocaleHelpIsRejected(t *testing.T) {
	var out, errb bytes.Buffer
	code := run([]string{"check", "--world", "testdata/good", "--locale", "help"}, &out, &errb)
	if code != 1 {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
	if strings.Contains(out.String(), "Usage:") || strings.Contains(errb.String(), "renders catalog") {
		t.Fatalf("usage\n%s%s", out.String(), errb.String())
	}
	if !strings.Contains(errb.String(), "i18n/help.tsv:1: only locale en is supported") {
		t.Fatalf("%s", errb.String())
	}
}

func TestWorldNamedHelpIsChecked(t *testing.T) {
	var out, errb bytes.Buffer
	code := run([]string{"check", "--world", "help"}, &out, &errb)
	if code != 1 {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
	if strings.Contains(out.String(), "renders catalog") || strings.Contains(errb.String(), "renders catalog") {
		t.Fatalf("usage\n%s%s", out.String(), errb.String())
	}
	if !strings.Contains(errb.String(), "file is missing") {
		t.Fatalf("%s", errb.String())
	}
}

func TestRejectsOtherLocaleOnTheCommand(t *testing.T) {
	var errb bytes.Buffer
	code := run([]string{"check", "--world", "testdata/good", "--locale", "fr"}, io.Discard, &errb)
	if code != 1 || !strings.Contains(errb.String(), "only locale en is supported") {
		t.Fatalf("code %d\n%s", code, errb.String())
	}
}

func TestModuleHasNoRequire(t *testing.T) {
	data, err := os.ReadFile("go.mod")
	if err != nil {
		t.Fatal(err)
	}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "require" || strings.HasPrefix(line, "require ") || strings.HasPrefix(line, "require\t") {
			t.Fatalf("go.mod requires a module: %s", line)
		}
	}
	if !strings.Contains(string(data), "go 1.27.1\n") || !strings.Contains(string(data), "toolchain go1.27.3\n") {
		t.Fatalf("%s", data)
	}
}

func copyTree(t *testing.T, src, dst string) {
	t.Helper()
	err := filepath.WalkDir(src, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(src, path)
		if err != nil {
			return err
		}
		target := filepath.Join(dst, rel)
		if d.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		body, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		return os.WriteFile(target, body, 0o644)
	})
	if err != nil {
		t.Fatal(err)
	}
}
