// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"html/template"
	"os"
	"strings"
	"testing"
)

func TestChangelogReadsReleasedSections(t *testing.T) {
	text := "# Changelog\n\n## [Unreleased]\n\n- pending\n\n## [5.0.0] - 2026-11-03\n\nLead <b>.\n\n**Upgrade**\n\n- Use `x` and [docs](https://example.org/a?b=1&c=2).\n- Second.\n\n### Added\n\n- One.\n\n## [3.2.1] - 2026-10-23\n\n- Old.\n\n[Unreleased]: https://example.org\n"
	rs, err := parseChangelog(text)
	if err != nil {
		t.Fatal(err)
	}
	if len(rs) != 2 || rs[0].Version != "5.0.0" || rs[0].Date != "2026-11-03" || !rs[0].Go || rs[1].Go {
		t.Fatalf("%+v", rs)
	}
	b := rs[0].Blocks
	want := []Block{
		{Text: "Lead &lt;b&gt;."},
		{Text: "<strong>Upgrade</strong>"},
		{Items: []template.HTML{`Use <code>x</code> and <a href="https://example.org/a?b=1&amp;c=2">docs</a>.`, "Second."}},
		{Heading: "Added"},
		{Items: []template.HTML{"One."}},
	}
	if len(b) != len(want) {
		t.Fatalf("blocks %+v", b)
	}
	for i := range want {
		if b[i].Heading != want[i].Heading || b[i].Text != want[i].Text || strings.Join(htmls(b[i].Items), "|") != strings.Join(htmls(want[i].Items), "|") {
			t.Fatalf("block %d: %+v, want %+v", i, b[i], want[i])
		}
	}
}

func htmls(items []template.HTML) []string {
	var out []string
	for _, i := range items {
		out = append(out, string(i))
	}
	return out
}

// The repository's own changelog parses, and its newest release is the Go server.
func TestRepositoryChangelogParses(t *testing.T) {
	raw, err := os.ReadFile("../../../../../CHANGELOG.md")
	if err != nil {
		t.Fatal(err)
	}
	rs, err := parseChangelog(string(raw))
	if err != nil {
		t.Fatal(err)
	}
	if rs[0].Version != "5.0.0" || !rs[0].Go || len(rs[0].Blocks) == 0 {
		t.Fatalf("%+v", rs[0])
	}
}

func TestInlineDoesNotTrustMarkup(t *testing.T) {
	got := inline("[x](javascript:alert(1)) <script>")
	if strings.Contains(string(got), "<a") || strings.Contains(string(got), "<script>") {
		t.Fatalf("%s", got)
	}
}
