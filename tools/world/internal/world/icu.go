// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// The formatter accepts a strict subset of ICU MessageFormat.
// An apostrophe is a literal character. A doubled apostrophe is rejected.
// An apostrophe immediately before '{' or '}', and before '#' inside a plural
// branch, is rejected: ICU4J opens a quoted span there. A literal brace and any
// plural branch other than "one" and "other" are rejected so the subset cannot
// disagree with ICU4J.

package world

import (
	"sort"
	"strconv"
	"strings"
)

type partKind int

const (
	partLit partKind = iota
	partArg
	partNum
	partPlural
	partHash
)

type part struct {
	kind     partKind
	lit      string
	name     string
	branches map[string][]part
}

type message struct {
	parts []part
	args  []string
}

type value struct {
	numeric bool
	text    string
	num     int64
}

type parser struct {
	s    string
	i    int
	args map[string]struct{}
	err  string
}

func parseTemplate(s string) (*message, string) {
	p := &parser{s: s, args: map[string]struct{}{}}
	parts := p.parseSequence(false)
	if p.err != "" {
		return nil, p.err
	}
	return &message{parts: parts, args: p.argNames()}, ""
}

func (p *parser) argNames() []string {
	names := make([]string, 0, len(p.args))
	for name := range p.args {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func (p *parser) parseSequence(inBranch bool) []part {
	var parts []part
	for p.i < len(p.s) && p.err == "" {
		c := p.s[p.i]
		if inBranch && c == '}' {
			return parts
		}
		switch c {
		case '\'':
			if p.i+1 < len(p.s) && p.s[p.i+1] == '\'' {
				p.err = "doubled apostrophe"
				return nil
			}
			if p.i+1 < len(p.s) {
				switch p.s[p.i+1] {
				case '{':
					p.err = "apostrophe before '{'"
					return nil
				case '}':
					p.err = "apostrophe before '}'"
					return nil
				case '#':
					if inBranch {
						p.err = "apostrophe before '#'"
						return nil
					}
				}
			}
			parts = append(parts, part{kind: partLit, lit: "'"})
			p.i++
		case '}':
			p.err = "literal '}'"
			return nil
		case '{':
			parts = append(parts, p.parsePlaceholder())
		case '#':
			if inBranch {
				parts = append(parts, part{kind: partHash})
				p.i++
				continue
			}
			parts = append(parts, p.literalRun(false))
		default:
			parts = append(parts, p.literalRun(inBranch))
		}
	}
	if p.err != "" {
		return nil
	}
	if inBranch {
		p.err = "plural branch is not closed"
		return nil
	}
	return parts
}

func (p *parser) literalRun(inBranch bool) part {
	j := p.i
	for j < len(p.s) {
		d := p.s[j]
		if d == '\'' || d == '{' || d == '}' || (inBranch && d == '#') {
			break
		}
		j++
	}
	lit := p.s[p.i:j]
	p.i = j
	return part{kind: partLit, lit: lit}
}

func (p *parser) parsePlaceholder() part {
	p.i++ // '{'
	p.skipSpace()
	name := p.readName()
	if name == "" {
		p.err = "literal '{'"
		return part{}
	}
	p.args[name] = struct{}{}
	p.skipSpace()
	if p.consume('}') {
		return part{kind: partArg, name: name}
	}
	if !p.consume(',') {
		p.err = "literal '{'"
		return part{}
	}
	p.skipSpace()
	typ := p.readWord()
	p.skipSpace()
	switch typ {
	case "number":
		return p.parseNumber(name)
	case "plural":
		return p.parsePlural(name)
	default:
		if typ == "" {
			p.err = "literal '{'"
		} else {
			p.err = "unknown argument type " + quote(typ)
		}
		return part{}
	}
}

func (p *parser) parseNumber(name string) part {
	if !p.consume(',') {
		p.err = "unknown argument type " + quote("number")
		return part{}
	}
	p.skipSpace()
	style := p.readWord()
	p.skipSpace()
	if style != "integer" {
		shown := style
		if shown == "" {
			shown = "number"
		}
		p.err = "unknown argument type " + quote(shown)
		return part{}
	}
	if !p.consume('}') {
		p.err = "literal '{'"
		return part{}
	}
	return part{kind: partNum, name: name}
}

func (p *parser) parsePlural(name string) part {
	if !p.consume(',') {
		p.err = "unknown argument type " + quote("plural")
		return part{}
	}
	branches := map[string][]part{}
	for p.err == "" {
		p.skipSpace()
		if p.consume('}') {
			if _, ok := branches["other"]; !ok {
				p.err = "plural missing other"
				return part{}
			}
			return part{kind: partPlural, name: name, branches: branches}
		}
		sel := p.readSelector()
		if sel == "" {
			p.err = "plural missing other"
			return part{}
		}
		if sel != "one" && sel != "other" {
			p.err = "unsupported plural branch " + quote(sel)
			return part{}
		}
		if _, dup := branches[sel]; dup {
			p.err = "duplicate plural branch " + quote(sel)
			return part{}
		}
		p.skipSpace()
		if !p.consume('{') {
			p.err = "plural branch is not closed"
			return part{}
		}
		body := p.parseSequence(true)
		if p.err != "" {
			return part{}
		}
		if !p.consume('}') {
			p.err = "plural branch is not closed"
			return part{}
		}
		branches[sel] = body
	}
	return part{}
}

func (p *parser) skipSpace() {
	for p.i < len(p.s) && p.s[p.i] == ' ' {
		p.i++
	}
}

func (p *parser) consume(c byte) bool {
	if p.i < len(p.s) && p.s[p.i] == c {
		p.i++
		return true
	}
	return false
}

func (p *parser) readName() string {
	if p.i >= len(p.s) || p.s[p.i] < 'a' || p.s[p.i] > 'z' {
		return ""
	}
	j := p.i + 1
	for j < len(p.s) {
		c := p.s[j]
		if (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' {
			j++
			continue
		}
		break
	}
	name := p.s[p.i:j]
	p.i = j
	return name
}

func (p *parser) readWord() string {
	if p.i >= len(p.s) || !isLetter(p.s[p.i]) {
		return ""
	}
	j := p.i + 1
	for j < len(p.s) && (isLetter(p.s[j]) || (p.s[j] >= '0' && p.s[j] <= '9')) {
		j++
	}
	word := p.s[p.i:j]
	p.i = j
	return word
}

func (p *parser) readSelector() string {
	if p.i < len(p.s) && p.s[p.i] == '=' {
		j := p.i + 1
		for j < len(p.s) && p.s[j] >= '0' && p.s[j] <= '9' {
			j++
		}
		if j == p.i+1 {
			return ""
		}
		sel := p.s[p.i:j]
		p.i = j
		return sel
	}
	return p.readWord()
}

func isLetter(c byte) bool {
	return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
}

func quote(s string) string {
	return `"` + s + `"`
}

func (m *message) apply(args map[string]value) (string, string) {
	return applyParts(m.parts, args, nil)
}

func applyParts(parts []part, args map[string]value, hash *int64) (string, string) {
	var b strings.Builder
	for _, p := range parts {
		switch p.kind {
		case partLit:
			b.WriteString(p.lit)
		case partHash:
			if hash == nil {
				return "", "unresolved #"
			}
			b.WriteString(formatInteger(*hash))
		case partArg:
			v, ok := args[p.name]
			if !ok {
				return "", "missing parameter " + p.name
			}
			if v.numeric {
				b.WriteString(formatInteger(v.num))
			} else {
				b.WriteString(v.text)
			}
		case partNum:
			v, ok := args[p.name]
			if !ok || !v.numeric {
				return "", "value is not an integer"
			}
			b.WriteString(formatInteger(v.num))
		case partPlural:
			v, ok := args[p.name]
			if !ok || !v.numeric {
				return "", "value is not an integer"
			}
			branch := p.branches["other"]
			if v.num == 1 {
				if one, ok := p.branches["one"]; ok {
					branch = one
				}
			}
			n := v.num
			s, err := applyParts(branch, args, &n)
			if err != "" {
				return "", err
			}
			b.WriteString(s)
		default:
			return "", "unknown message part"
		}
	}
	return b.String(), ""
}

// formatInteger prints n with ',' between thousands, the en grouping.
func formatInteger(n int64) string {
	s := strconv.FormatInt(n, 10)
	neg := ""
	if strings.HasPrefix(s, "-") {
		neg = "-"
		s = s[1:]
	}
	var b strings.Builder
	b.Grow(len(neg) + len(s) + len(s)/3)
	b.WriteString(neg)
	for i := 0; i < len(s); i++ {
		if i > 0 && (len(s)-i)%3 == 0 {
			b.WriteByte(',')
		}
		b.WriteByte(s[i])
	}
	return b.String()
}
