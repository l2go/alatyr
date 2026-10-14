// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Substitution edits the source bytes in place. CSV, XML, and HTML are not
// parsed and written back, so bytes outside a key reference stay as they were,
// including CRLF and attribute order.

package world

import (
	"bytes"
	"strings"
)

type cell struct {
	text   string
	quoted bool
	line   int
	start  int
	end    int
}

type attrHit struct {
	value      string
	line       int
	start, end int
}

type htmlSpan struct {
	inner      string
	line       int
	start, end int
	valid      bool
}

func scanCSV(data []byte) ([][]cell, int, string) {
	var rows [][]cell
	var row []cell
	i := 0
	line := 1
	finish := func() {
		rows = append(rows, row)
		row = nil
	}
	for i <= len(data) {
		if i == len(data) {
			if len(row) > 0 {
				finish()
			}
			break
		}
		fieldLine := line
		start := i
		var text string
		quoted := false
		if data[i] == '"' {
			quoted = true
			i++
			var b strings.Builder
			closed := false
			for i < len(data) {
				if data[i] == '"' {
					if i+1 < len(data) && data[i+1] == '"' {
						b.WriteByte('"')
						i += 2
						continue
					}
					i++
					closed = true
					break
				}
				if data[i] == '\n' {
					line++
				}
				b.WriteByte(data[i])
				i++
			}
			if !closed {
				return nil, fieldLine, "unclosed quote"
			}
			text = b.String()
		} else {
			for i < len(data) && data[i] != ',' && data[i] != '\n' && data[i] != '\r' {
				i++
			}
			text = string(data[start:i])
		}
		row = append(row, cell{text: text, quoted: quoted, line: fieldLine, start: start, end: i})
		if i >= len(data) {
			finish()
			break
		}
		if data[i] == ',' {
			i++
			continue
		}
		if data[i] == '\r' && i+1 < len(data) && data[i+1] == '\n' {
			i += 2
			line++
			finish()
			continue
		}
		if data[i] == '\n' || data[i] == '\r' {
			i++
			line++
			finish()
			continue
		}
	}
	return rows, 0, ""
}

func applyCells(data []byte, rows [][]cell, replace func(c cell) (string, bool)) []byte {
	var out []byte
	pos := 0
	for _, row := range rows {
		for _, c := range row {
			repl, ok := replace(c)
			if !ok {
				continue
			}
			out = append(out, data[pos:c.start]...)
			out = append(out, repl...)
			pos = c.end
		}
	}
	out = append(out, data[pos:]...)
	return out
}

func quoteCSV(s string) string {
	if strings.ContainsAny(s, ",\"\r\n") {
		return `"` + strings.ReplaceAll(s, `"`, `""`) + `"`
	}
	return s
}

func escapeXML(s string) string {
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		switch s[i] {
		case '&':
			b.WriteString("&amp;")
		case '<':
			b.WriteString("&lt;")
		case '>':
			b.WriteString("&gt;")
		case '"':
			b.WriteString("&quot;")
		default:
			b.WriteByte(s[i])
		}
	}
	return b.String()
}

func escapeHTML(s string) string {
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		switch s[i] {
		case '&':
			b.WriteString("&amp;")
		case '<':
			b.WriteString("&lt;")
		case '>':
			b.WriteString("&gt;")
		default:
			b.WriteByte(s[i])
		}
	}
	return b.String()
}

func xmlAttrs(data []byte) []attrHit {
	var hits []attrHit
	i := 0
	for i < len(data) {
		if bytes.HasPrefix(data[i:], []byte("<!--")) {
			i = skipMark(data, i+4, []byte("-->"))
			continue
		}
		if bytes.HasPrefix(data[i:], []byte("<![CDATA[")) {
			i = skipMark(data, i+9, []byte("]]>"))
			continue
		}
		if bytes.HasPrefix(data[i:], []byte("<?")) {
			i = skipMark(data, i+2, []byte("?>"))
			continue
		}
		if data[i] == '<' && (i+1 >= len(data) || (data[i+1] != '/' && data[i+1] != '!')) {
			i++
			for i < len(data) && data[i] != '>' {
				if data[i] == '=' {
					j := i + 1
					for j < len(data) && (data[j] == ' ' || data[j] == '\n' || data[j] == '\r') {
						j++
					}
					if j < len(data) && data[j] == '"' {
						k := j + 1
						for k < len(data) && data[k] != '"' {
							k++
						}
						if k < len(data) && data[k] == '"' {
							hits = append(hits, attrHit{
								value: string(data[j+1 : k]),
								line:  lineAt(data, j+1),
								start: j + 1,
								end:   k,
							})
							i = k + 1
							continue
						}
					}
				}
				i++
			}
			if i < len(data) && data[i] == '>' {
				i++
			}
			continue
		}
		i++
	}
	return hits
}

func applySpans(data []byte, replace func(start, end int) (string, bool), spans [][2]int) []byte {
	var out []byte
	pos := 0
	for _, sp := range spans {
		repl, ok := replace(sp[0], sp[1])
		if !ok {
			continue
		}
		out = append(out, data[pos:sp[0]]...)
		out = append(out, repl...)
		pos = sp[1]
	}
	out = append(out, data[pos:]...)
	return out
}

func htmlSpans(data []byte) []htmlSpan {
	var spans []htmlSpan
	for i := 0; i+1 < len(data); {
		if data[i] != '{' || data[i+1] != '{' {
			i++
			continue
		}
		j := i + 2
		for j < len(data) && data[j] != '}' && data[j] != '\n' && data[j] != '\r' {
			j++
		}
		if j+1 < len(data) && data[j] == '}' && data[j+1] == '}' {
			inner := string(data[i+2 : j])
			spans = append(spans, htmlSpan{
				inner: inner,
				line:  lineAt(data, i),
				start: i,
				end:   j + 2,
				valid: keyPattern.MatchString(inner),
			})
			i = j + 2
			continue
		}
		spans = append(spans, htmlSpan{
			line:  lineAt(data, i),
			start: i,
			end:   i + 2,
			valid: false,
		})
		i += 2
	}
	return spans
}

func skipMark(data []byte, from int, mark []byte) int {
	rel := bytes.Index(data[from:], mark)
	if rel < 0 {
		return len(data)
	}
	return from + rel + len(mark)
}

func substituteCSV(data []byte, texts map[string]string) ([]byte, []Diagnostic, int, string) {
	rows, line, errMsg := scanCSV(data)
	if errMsg != "" {
		return nil, nil, line, errMsg
	}
	var diags []Diagnostic
	out := applyCells(data, rows, func(c cell) (string, bool) {
		if c.quoted {
			return "", false
		}
		key, ok := refKey(c.text)
		if !ok {
			return "", false
		}
		text, ok := texts[key]
		if !ok {
			diags = append(diags, Diagnostic{Line: c.line, Message: "undefined key " + key})
			return "", false
		}
		return quoteCSV(text), true
	})
	return out, diags, 0, ""
}

func substituteXML(data []byte, texts map[string]string) ([]byte, []Diagnostic) {
	hits := xmlAttrs(data)
	var diags []Diagnostic
	spans := make([][2]int, len(hits))
	for i, h := range hits {
		spans[i] = [2]int{h.start, h.end}
	}
	out := applySpans(data, func(start, end int) (string, bool) {
		value := string(data[start:end])
		key, ok := refKey(value)
		if !ok {
			return "", false
		}
		text, ok := texts[key]
		if !ok {
			diags = append(diags, Diagnostic{Line: lineAt(data, start), Message: "undefined key " + key})
			return "", false
		}
		return escapeXML(text), true
	}, spans)
	return out, diags
}

func substituteHTML(data []byte, texts map[string]string) ([]byte, []Diagnostic) {
	spans := htmlSpans(data)
	var diags []Diagnostic
	pairs := make([][2]int, len(spans))
	for i, sp := range spans {
		pairs[i] = [2]int{sp.start, sp.end}
		if !sp.valid {
			diags = append(diags, Diagnostic{Line: sp.line, Message: "invalid text reference"})
		}
	}
	out := applySpans(data, func(start, end int) (string, bool) {
		for _, sp := range spans {
			if sp.start != start || sp.end != end {
				continue
			}
			if !sp.valid {
				return "", false
			}
			text, ok := texts[sp.inner]
			if !ok {
				diags = append(diags, Diagnostic{Line: sp.line, Message: "undefined key " + sp.inner})
				return "", false
			}
			return escapeHTML(text), true
		}
		return "", false
	}, pairs)
	return out, diags
}
