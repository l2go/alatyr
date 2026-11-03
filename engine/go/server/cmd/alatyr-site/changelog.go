// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"errors"
	"html"
	"html/template"
	"regexp"
	"strings"
)

// Release is one CHANGELOG.md section.
type Release struct {
	Version string
	Date    string
	// Go marks the releases of the Go server, rendered in full.
	Go bool
	// Tagged says the release's tag exists, so its GitHub release can be linked.
	Tagged bool
	Blocks []Block
}

// Block is a heading, a paragraph or a list of the release text.
type Block struct {
	Heading string
	Text    template.HTML
	Items   []template.HTML
}

var releaseHeading = regexp.MustCompile(`^## \[([0-9][^\]]*)\](?: - (\d{4}-\d{2}-\d{2}))?$`)

// parseChangelog reads the released sections of a Keep a Changelog file. It
// understands the subset the file uses: section headings, paragraphs, one-line
// list items and inline code, emphasis and links.
func parseChangelog(text string) ([]Release, error) {
	var out []Release
	var cur *Release
	for _, line := range strings.Split(text, "\n") {
		if m := releaseHeading.FindStringSubmatch(line); m != nil {
			out = append(out, Release{Version: m[1], Date: m[2], Go: !strings.HasPrefix(m[1], "2.") && !strings.HasPrefix(m[1], "3.")})
			cur = &out[len(out)-1]
			continue
		}
		if strings.HasPrefix(line, "## ") || strings.HasPrefix(line, "[") {
			cur = nil // Unreleased, or the link definitions at the end.
			continue
		}
		if cur == nil || strings.TrimSpace(line) == "" {
			continue
		}
		switch {
		case strings.HasPrefix(line, "### "):
			cur.Blocks = append(cur.Blocks, Block{Heading: strings.TrimPrefix(line, "### ")})
		case strings.HasPrefix(line, "- "):
			item := inline(strings.TrimPrefix(line, "- "))
			if n := len(cur.Blocks); n > 0 && cur.Blocks[n-1].Items != nil {
				cur.Blocks[n-1].Items = append(cur.Blocks[n-1].Items, item)
			} else {
				cur.Blocks = append(cur.Blocks, Block{Items: []template.HTML{item}})
			}
		default:
			cur.Blocks = append(cur.Blocks, Block{Text: inline(line)})
		}
	}
	if len(out) == 0 {
		return nil, errors.New("changelog: no released section")
	}
	return out, nil
}

var (
	codeSpan = regexp.MustCompile("`([^`]+)`")
	strong   = regexp.MustCompile(`\*\*([^*]+)\*\*`)
	link     = regexp.MustCompile(`\[([^\]]+)\]\((https?://[^)\s]+)\)`)
)

// inline renders the inline Markdown the changelog uses. Text is escaped
// before any markup is added, and links must be absolute http(s) URLs.
func inline(s string) template.HTML {
	s = html.EscapeString(s)
	s = codeSpan.ReplaceAllString(s, "<code>$1</code>")
	s = strong.ReplaceAllString(s, "<strong>$1</strong>")
	s = link.ReplaceAllString(s, `<a href="$2">$1</a>`)
	return template.HTML(s)
}
