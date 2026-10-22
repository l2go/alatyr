// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package catalog

import (
	"strings"
	"unicode"
)

var compactTags = func() []string {
	var out []string
	for _, t := range []string{"html", "title", "body", "br", "br1", "p", "table", "tr", "td"} {
		out = append(out, "<"+t+">", "</"+t+">", "<"+t+"/>", "<"+t+" />")
	}
	return out
}()
func CompactHTML(html string) string {
	s := strings.Map(func(r rune) rune {
		if javaWhitespace(r) {
			return ' '
		}
		return r
	}, html)
	s = replaceAll(s, "  ", " ")
	s = replaceAll(s, "< ", "<")
	s = replaceAll(s, " >", ">")
	for _, t := range compactTags {
		s = replaceAll(s, t+" ", t)
		s = replaceAll(s, " "+t, t)
	}
	s = replaceAll(s, "  ", " ")
	return strings.Trim(s, " ")
}
func replaceAll(s, pattern, value string) string {
	for from := 0; ; {
		i := strings.Index(s[from:], pattern)
		if i < 0 {
			return s
		}
		from += i
		s = s[:from] + value + s[from+len(pattern):]
	}
}
func javaWhitespace(r rune) bool {
	switch r {
	case '\u00a0', '\u2007', '\u202f':
		return false
	case '\t', '\n', '\v', '\f', '\r', '\u001c', '\u001d', '\u001e', '\u001f':
		return true
	}
	return unicode.In(r, unicode.Zs, unicode.Zl, unicode.Zp)
}
