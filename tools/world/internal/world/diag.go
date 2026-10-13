// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// Diagnostic is one stable check or render failure.
// The text form is "path:line: message".
type Diagnostic struct {
	Path    string
	Line    int
	Message string
}

func (d Diagnostic) String() string {
	return d.Path + ":" + itoa(d.Line) + ": " + d.Message
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b [20]byte
	i := len(b)
	neg := n < 0
	if neg {
		n = -n
	}
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	if neg {
		i--
		b[i] = '-'
	}
	return string(b[i:])
}

var (
	keyPattern      = regexp.MustCompile(`^[a-z]+\.[a-z0-9-]+\.[a-z]+$`)
	argPattern      = regexp.MustCompile(`^[a-z][a-z0-9_]*$`)
	atKeyPattern    = regexp.MustCompile(`@[a-z]+\.[a-z0-9-]+\.[a-z]+`)
	braceArgPattern = regexp.MustCompile(`\{[a-z][a-z0-9_]*\}`)
)

func dedupeSort(in []Diagnostic) []Diagnostic {
	if len(in) == 0 {
		return nil
	}
	sort.Slice(in, func(i, j int) bool {
		if in[i].Path != in[j].Path {
			return in[i].Path < in[j].Path
		}
		if in[i].Line != in[j].Line {
			return in[i].Line < in[j].Line
		}
		return in[i].Message < in[j].Message
	})
	out := []Diagnostic{in[0]}
	for _, d := range in[1:] {
		last := out[len(out)-1]
		if d.Path != last.Path || d.Line != last.Line || d.Message != last.Message {
			out = append(out, d)
		}
	}
	return out
}

func lineAt(data []byte, offset int) int {
	if offset < 0 {
		offset = 0
	}
	if offset > len(data) {
		offset = len(data)
	}
	return 1 + bytes.Count(data[:offset], []byte{'\n'})
}

func firstDiffLine(got, want []byte) int {
	n := len(got)
	if len(want) < n {
		n = len(want)
	}
	for i := 0; i < n; i++ {
		if got[i] != want[i] {
			return lineAt(got, i)
		}
	}
	shorter := got
	if len(want) < len(got) {
		shorter = want
	}
	if len(shorter) == 0 {
		return 1
	}
	return lineAt(shorter, len(shorter))
}

func slashRel(root, path string) string {
	rel, err := filepath.Rel(root, path)
	if err != nil {
		return filepath.ToSlash(path)
	}
	return filepath.ToSlash(rel)
}

func readRel(root, rel string) ([]byte, error) {
	return os.ReadFile(filepath.Join(root, filepath.FromSlash(rel)))
}

// regularFiles lists files under root/rel. A missing directory yields no files
// and exists=false. rel "." lists the directory root itself, not its children.
func regularFiles(root, rel string) (files []string, exists bool, err error) {
	dir := root
	if rel != "." {
		dir = filepath.Join(root, filepath.FromSlash(rel))
	}
	info, statErr := os.Stat(dir)
	if statErr != nil {
		if os.IsNotExist(statErr) {
			return nil, false, nil
		}
		return nil, false, statErr
	}
	if !info.IsDir() {
		return nil, false, nil
	}
	walkErr := filepath.WalkDir(dir, func(path string, d os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() || !d.Type().IsRegular() {
			return nil
		}
		files = append(files, slashRel(root, path))
		return nil
	})
	if walkErr != nil {
		return nil, true, walkErr
	}
	sort.Strings(files)
	return files, true, nil
}

func asciiDiags(rel string, data []byte) []Diagnostic {
	tsv := strings.HasSuffix(rel, ".tsv")
	var out []Diagnostic
	line := 1
	bad := false
	report := func() {
		if bad {
			out = append(out, Diagnostic{Path: rel, Line: line, Message: "non-ASCII byte"})
			bad = false
		}
	}
	for i := 0; i < len(data); i++ {
		b := data[i]
		if b == '\n' {
			report()
			line++
			continue
		}
		if b == '\r' || (b == '\t' && tsv) || (b >= 0x20 && b <= 0x7e) {
			continue
		}
		bad = true
	}
	report()
	return out
}

func refKey(value string) (string, bool) {
	if len(value) < 2 || value[0] != '@' {
		return "", false
	}
	key := value[1:]
	if !keyPattern.MatchString(key) {
		return "", false
	}
	return key, true
}
