// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"html"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"
)

type option struct{ action, label string }
type page struct {
	text    string
	options []option
}

// parseHTML is a bounded presentation tokenizer, never a browser or executor.
// Unknown commands remain text. Even recognized action text grants no authority
// until it matches a world-issued affordance.
func parseHTML(input string) (page, error) {
	if len(input) > maxPayloadBytes {
		return page{}, errCapacity
	}
	if !utf8.ValidString(input) {
		return page{}, errSource
	}
	var p page
	var out strings.Builder
	var protected []bool
	write := func(s string, keep bool) {
		out.WriteString(s)
		for range len(s) {
			protected = append(protected, keep)
		}
	}
	var anchor *option
	var label strings.Builder
	pre := false
	emit := func(s string) {
		s = strings.ReplaceAll(entities(s), "\r\n", "\n")
		if !pre {
			var compact strings.Builder
			space := false
			for _, r := range s {
				if unicode.IsSpace(r) {
					if !space {
						compact.WriteByte(' ')
					}
					space = true
					continue
				}
				space = false
				compact.WriteRune(r)
			}
			s = compact.String()
		}
		write(s, pre)
		if anchor != nil {
			label.WriteString(s)
		}
	}
	for len(input) > 0 {
		i := strings.IndexByte(input, '<')
		if i < 0 {
			emit(input)
			break
		}
		emit(input[:i])
		input = input[i+1:]
		if strings.HasPrefix(input, "!--") {
			end := strings.Index(input, "-->")
			if end < 0 {
				return page{}, errSource
			}
			input = input[end+3:]
			continue
		}
		j := 0
		var quote byte
		for ; j < len(input); j++ {
			b := input[j]
			if quote != 0 {
				if b == quote {
					quote = 0
				}
				continue
			}
			if b == '"' || b == '\'' {
				quote = b
				continue
			}
			if b == '>' {
				break
			}
		}
		if j == len(input) || quote != 0 {
			return page{}, errSource
		}
		name, closing, attrs, err := tag(input[:j])
		if err != nil {
			return page{}, err
		}
		input = input[j+1:]
		if !closing && (name == "script" || name == "style") {
			end := closingTag(input, name)
			if end < 0 {
				return page{}, errSource
			}
			input = input[end:]
			continue
		}
		if name == "pre" {
			pre = !closing
		}
		if name == "br" || name == "br1" || closing && (name == "p" || name == "tr") {
			write("\n", pre)
		}
		if name == "a" && closing {
			if anchor != nil {
				anchor.label = strings.TrimSpace(label.String())
				p.options = append(p.options, *anchor)
				anchor = nil
				label.Reset()
			}
			continue
		}
		action, ok := attrs["action"]
		if name == "button" && !closing {
			emit(attrs["value"])
		}
		if closing || !ok || name != "a" && name != "button" {
			continue
		}
		action = strings.TrimSpace(action)
		const prefix = "bypass -h"
		if !strings.HasPrefix(action, prefix) || len(action) == len(prefix) || !asciiSpace(action[len(prefix)]) {
			continue
		}
		action = strings.TrimSpace(action[len(prefix):])
		if action == "" || strings.ContainsRune(action, '$') || strings.ContainsRune(action, 0) {
			continue
		}
		if name == "a" {
			if anchor != nil {
				return page{}, errSource
			}
			anchor = &option{action: action}
			label.Reset()
		} else {
			value := entities(attrs["value"])
			p.options = append(p.options, option{action: action, label: value})
		}
		if len(p.options) > maxRows {
			return page{}, errCapacity
		}
	}
	if anchor != nil {
		return page{}, errSource
	}
	raw := out.String()
	var cleaned strings.Builder
	for start := 0; start < len(raw); {
		end := strings.IndexByte(raw[start:], '\n')
		if end < 0 {
			end = len(raw)
		} else {
			end += start
		}
		left, right := start, end
		for left < right && raw[left] == ' ' && !protected[left] {
			left++
		}
		for right > left && raw[right-1] == ' ' && !protected[right-1] {
			right--
		}
		cleaned.WriteString(raw[left:right])
		if end < len(raw) {
			cleaned.WriteByte('\n')
		}
		start = end + 1
	}
	p.text = cleaned.String()
	// Strip layout-only edge newlines, not whitespace authored inside pre.
	if !strings.Contains(raw, "\n") || len(protected) == 0 || !protected[0] {
		p.text = strings.TrimLeft(p.text, "\n")
	}
	if len(protected) == 0 || !protected[len(protected)-1] {
		p.text = strings.TrimRight(p.text, "\n")
	}
	if len(p.text) > maxPayloadBytes || len(p.options) > maxRows {
		return page{}, errCapacity
	}
	return p, nil
}

func asciiSpace(b byte) bool { return b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f' }

func closingTag(input, name string) int {
	// Fold ASCII only: Unicode lowercasing can change byte lengths and make
	// a closing-tag index point into the original UTF-8 text instead of the tag.
	bytes := []byte(input)
	for i, b := range bytes {
		if b >= 'A' && b <= 'Z' {
			bytes[i] += 'a' - 'A'
		}
	}
	lower := string(bytes)
	for from := 0; from < len(lower); {
		i := strings.Index(lower[from:], "</"+name)
		if i < 0 {
			return -1
		}
		i += from
		after := i + 2 + len(name)
		if after < len(lower) && (lower[after] == '>' || asciiSpace(lower[after])) {
			return i
		}
		from = after
	}
	return -1
}
func tag(s string) (string, bool, map[string]string, error) {
	s = strings.TrimSpace(s)
	closing := strings.HasPrefix(s, "/")
	if closing {
		s = strings.TrimSpace(s[1:])
	}
	i := 0
	for i < len(s) && !asciiSpace(s[i]) && s[i] != '/' {
		i++
	}
	name := strings.ToLower(s[:i])
	s = s[i:]
	if name == "" {
		return "", false, nil, errSource
	}
	attrs := map[string]string{}
	for len(strings.TrimSpace(s)) > 0 {
		s = strings.TrimSpace(s)
		if s == "/" {
			break
		}
		i = 0
		for i < len(s) && !asciiSpace(s[i]) && s[i] != '=' {
			i++
		}
		key := strings.ToLower(s[:i])
		s = strings.TrimSpace(s[i:])
		if key == "" {
			return "", false, nil, errSource
		}
		if _, ok := attrs[key]; ok {
			return "", false, nil, errSource
		}
		value := ""
		if strings.HasPrefix(s, "=") {
			s = strings.TrimSpace(s[1:])
			if len(s) == 0 || s[0] != '"' && s[0] != '\'' {
				return "", false, nil, errSource
			}
			q := s[0]
			s = s[1:]
			j := strings.IndexByte(s, q)
			if j < 0 {
				return "", false, nil, errSource
			}
			value = s[:j]
			s = s[j+1:]
		}
		attrs[key] = value
	}
	return name, closing, attrs, nil
}

// entities deliberately recognizes only the profile's six named entities and
// valid numeric Unicode scalars, leaving other entity names as authored text.
func entities(s string) string {
	var out strings.Builder
	for len(s) > 0 {
		i := strings.IndexByte(s, '&')
		if i < 0 {
			out.WriteString(s)
			break
		}
		out.WriteString(s[:i])
		s = s[i:]
		j := strings.IndexByte(s, ';')
		if j < 0 {
			out.WriteString(s)
			break
		}
		entity := s[1:j]
		valid := false
		switch entity {
		case "amp", "lt", "gt", "quot", "apos", "nbsp":
			valid = true
		}
		if strings.HasPrefix(entity, "#") {
			v := entity[1:]
			base := 10
			if strings.HasPrefix(v, "x") || strings.HasPrefix(v, "X") {
				base = 16
				v = v[1:]
			}
			n, err := strconv.ParseUint(v, base, 32)
			if err == nil && n <= utf8.MaxRune && !(n >= 0xd800 && n <= 0xdfff) {
				out.WriteRune(rune(n))
				s = s[j+1:]
				continue
			}
		}
		if valid {
			out.WriteString(html.UnescapeString(s[:j+1]))
		} else {
			out.WriteString(s[:j+1])
		}
		s = s[j+1:]
	}
	return out.String()
}
