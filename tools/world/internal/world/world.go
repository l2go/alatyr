// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"encoding/xml"
	"io"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

const (
	enHeader   = "key\tkind\ttext\tnote"
	bindHeader = "key\tparameter\tsource_kind\tsource\tselector\tfield"
)

type entry struct {
	key  string
	kind string
	text string
	note string
	line int
	msg  *message
}

type binding struct {
	key      string
	param    string
	kind     string
	source   string
	selector string
	field    string
	line     int
	ref      string
	num      int64
	numeric  bool
	resolved bool
}

type xnode struct {
	name     string
	attrs    map[string]string
	children []*xnode
}

type xmlStep struct {
	elem string
	attr string
	val  string
}

// Render writes catalog and data from src and i18n. It leaves the world
// untouched when any rule fails.
func Render(root, locale string) []Diagnostic {
	diags, files := analyze(root, locale)
	if len(diags) > 0 || files == nil {
		if len(diags) == 0 {
			return []Diagnostic{{Path: "catalog", Line: 1, Message: "cannot write the built world"}}
		}
		return diags
	}
	if err := writeTree(root, files); err != nil {
		return []Diagnostic{{Path: "catalog", Line: 1, Message: "cannot write the built world"}}
	}
	return nil
}

// Check reports every broken rule. The result is empty only when the world matches a fresh render.
func Check(root, locale string) []Diagnostic {
	diags, files := analyze(root, locale)
	if files != nil {
		diags = append(diags, diffBuilt(root, files)...)
	}
	diags = append(diags, leftovers(root)...)
	return dedupeSort(diags)
}

func analyze(root, locale string) ([]Diagnostic, map[string][]byte) {
	if locale != "en" {
		return []Diagnostic{{
			Path:    "i18n/" + locale + ".tsv",
			Line:    1,
			Message: "only locale en is supported",
		}}, nil
	}

	var block, soft []Diagnostic
	soft = append(soft, asciiWorld(root)...)

	entries, entryDiags, entriesOK := loadEntries(root)
	block = append(block, entryDiags...)
	binds, bindDiags, bindsOK := loadBindings(root)
	block = append(block, bindDiags...)
	block = append(block, requireFile(root, "world.properties")...)
	block = append(block, requireDir(root, "src/catalog")...)
	block = append(block, requireDir(root, "src/data")...)
	block = append(block, otherLocales(root)...)

	used := map[string]bool{}
	if entriesOK && bindsOK {
		block = append(block, resolveBindings(root, entries, binds)...)
		block = append(block, sourceRefs(root, entries, used)...)
		for _, b := range binds {
			if b.kind == "text" && b.ref != "" {
				used[b.ref] = true
			}
		}
		keys := make([]string, 0, len(entries))
		for key := range entries {
			keys = append(keys, key)
		}
		sort.Strings(keys)
		for _, key := range keys {
			if !used[key] {
				soft = append(soft, Diagnostic{
					Path:    "i18n/en.tsv",
					Line:    entries[key].line,
					Message: "unused key " + key,
				})
			}
		}
	}

	if len(block) > 0 {
		return dedupeSort(append(append([]Diagnostic{}, block...), soft...)), nil
	}

	texts, formatDiags := formatEntries(entries, binds)
	if len(formatDiags) > 0 {
		block = append(block, formatDiags...)
		return dedupeSort(append(append([]Diagnostic{}, block...), soft...)), nil
	}
	files, renderDiags := renderFiles(root, texts)
	if len(renderDiags) > 0 {
		block = append(block, renderDiags...)
		return dedupeSort(append(append([]Diagnostic{}, block...), soft...)), nil
	}
	return dedupeSort(soft), files
}

func asciiWorld(root string) []Diagnostic {
	var out []Diagnostic
	if data, err := readRel(root, "world.properties"); err == nil {
		out = append(out, asciiDiags("world.properties", data)...)
	}
	for _, rel := range []string{"i18n", "src", "catalog", "data"} {
		files, exists, err := regularFiles(root, rel)
		if err != nil || !exists {
			continue
		}
		for _, file := range files {
			data, err := readRel(root, file)
			if err != nil {
				continue
			}
			out = append(out, asciiDiags(file, data)...)
		}
	}
	return out
}

func requireFile(root, rel string) []Diagnostic {
	info, err := os.Stat(filepath.Join(root, filepath.FromSlash(rel)))
	if err != nil || !info.Mode().IsRegular() {
		return []Diagnostic{{Path: rel, Line: 1, Message: "file is missing"}}
	}
	return nil
}

func requireDir(root, rel string) []Diagnostic {
	info, err := os.Stat(filepath.Join(root, filepath.FromSlash(rel)))
	if err != nil || !info.IsDir() {
		return []Diagnostic{{Path: rel, Line: 1, Message: "directory is missing"}}
	}
	return nil
}

func otherLocales(root string) []Diagnostic {
	dir := filepath.Join(root, "i18n")
	ents, err := os.ReadDir(dir)
	if err != nil {
		return []Diagnostic{{Path: "i18n", Line: 1, Message: "directory is missing"}}
	}
	var out []Diagnostic
	for _, ent := range ents {
		name := ent.Name()
		if name == "en.tsv" && !ent.IsDir() {
			continue
		}
		out = append(out, Diagnostic{Path: "i18n/" + name, Line: 1, Message: "only en.tsv is allowed"})
	}
	return out
}

func splitLines(data []byte) []string {
	if len(data) == 0 {
		return nil
	}
	parts := bytes.Split(data, []byte{'\n'})
	if len(parts) > 0 && len(parts[len(parts)-1]) == 0 {
		parts = parts[:len(parts)-1]
	}
	lines := make([]string, len(parts))
	for i, part := range parts {
		lines[i] = string(bytes.TrimSuffix(part, []byte{'\r'}))
	}
	return lines
}

func loadEntries(root string) (map[string]*entry, []Diagnostic, bool) {
	const rel = "i18n/en.tsv"
	data, err := readRel(root, rel)
	if err != nil {
		return nil, []Diagnostic{{Path: rel, Line: 1, Message: "file is missing"}}, false
	}
	lines := splitLines(data)
	var diags []Diagnostic
	if len(lines) == 0 || lines[0] != enHeader {
		line := 1
		diags = append(diags, Diagnostic{Path: rel, Line: line, Message: "header must be key, kind, text, note"})
		if len(lines) == 0 {
			return map[string]*entry{}, diags, true
		}
	}
	entries := map[string]*entry{}
	for i := 1; i < len(lines); i++ {
		lineNo := i + 1
		fields := strings.Split(lines[i], "\t")
		if len(fields) != 4 {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "expected 4 fields"})
			continue
		}
		key, kind, text := fields[0], fields[1], fields[2]
		if !keyPattern.MatchString(key) {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "key must match kind.slug.field"})
			continue
		}
		if kind != "proper" && kind != "common" {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "kind must be proper or common"})
			continue
		}
		if text == "" {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "empty text"})
			continue
		}
		if _, ok := entries[key]; ok {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "duplicate key " + key})
			continue
		}
		e := &entry{key: key, kind: kind, text: text, note: fields[3], line: lineNo}
		msg, errMsg := parseTemplate(text)
		if errMsg != "" {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: errMsg})
		} else {
			e.msg = msg
		}
		entries[key] = e
	}
	return entries, diags, true
}

func loadBindings(root string) ([]binding, []Diagnostic, bool) {
	const rel = "src/text-bindings.tsv"
	data, err := readRel(root, rel)
	if err != nil {
		return nil, []Diagnostic{{Path: rel, Line: 1, Message: "file is missing"}}, false
	}
	lines := splitLines(data)
	var diags []Diagnostic
	if len(lines) == 0 || lines[0] != bindHeader {
		diags = append(diags, Diagnostic{Path: rel, Line: 1, Message: "header must be key, parameter, source_kind, source, selector, field"})
		if len(lines) == 0 {
			return nil, diags, true
		}
	}
	var binds []binding
	for i := 1; i < len(lines); i++ {
		lineNo := i + 1
		fields := strings.Split(lines[i], "\t")
		if len(fields) != 6 {
			diags = append(diags, Diagnostic{Path: rel, Line: lineNo, Message: "expected 6 fields"})
			continue
		}
		binds = append(binds, binding{
			key:      fields[0],
			param:    fields[1],
			kind:     fields[2],
			source:   fields[3],
			selector: fields[4],
			field:    fields[5],
			line:     lineNo,
		})
	}
	return binds, diags, true
}

func resolveBindings(root string, entries map[string]*entry, binds []binding) []Diagnostic {
	var diags []Diagnostic
	seen := map[string]int{}
	byKey := map[string][]int{}
	for i := range binds {
		b := &binds[i]
		rel := "src/text-bindings.tsv"
		if !keyPattern.MatchString(b.key) {
			diags = append(diags, Diagnostic{Path: rel, Line: b.line, Message: "key must match kind.slug.field"})
			continue
		}
		if !argPattern.MatchString(b.param) {
			diags = append(diags, Diagnostic{Path: rel, Line: b.line, Message: "invalid parameter name"})
			continue
		}
		id := b.key + "\x00" + b.param
		if _, ok := seen[id]; ok {
			diags = append(diags, Diagnostic{Path: rel, Line: b.line, Message: "duplicate parameter " + b.param})
			continue
		}
		seen[id] = b.line
		if _, ok := entries[b.key]; !ok {
			diags = append(diags, Diagnostic{Path: rel, Line: b.line, Message: "undefined key " + b.key})
			continue
		}
		switch b.kind {
		case "text":
			diags = append(diags, resolveText(entries, b)...)
		case "csv-number":
			diags = append(diags, resolveCSV(root, b)...)
		case "xml-number":
			diags = append(diags, resolveXML(root, b)...)
		default:
			diags = append(diags, Diagnostic{Path: rel, Line: b.line, Message: "unknown source kind " + quote(b.kind)})
			continue
		}
		byKey[b.key] = append(byKey[b.key], i)
	}
	diags = append(diags, matchParams(entries, binds, byKey)...)
	diags = append(diags, textCycles(binds)...)
	return diags
}

func resolveText(entries map[string]*entry, b *binding) []Diagnostic {
	rel := "src/text-bindings.tsv"
	if b.selector != "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "text binding must have an empty selector"}}
	}
	if b.field != "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "text binding must have an empty field"}}
	}
	key, ok := refKey(b.source)
	if !ok {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "source must be @key"}}
	}
	b.ref = key
	if _, defined := entries[key]; !defined {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "undefined key " + key}}
	}
	b.resolved = true
	return nil
}

func resolveCSV(root string, b *binding) []Diagnostic {
	rel := "src/text-bindings.tsv"
	srcRel, ok := cleanSource(b.source)
	if !ok || !strings.HasPrefix(srcRel, "catalog/") || !strings.HasSuffix(srcRel, ".csv") {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "source path escapes src"}}
	}
	if b.selector == "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "empty selector"}}
	}
	if b.field == "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "field is empty"}}
	}
	eqs, errMsg := parseCSVSelector(b.selector)
	if errMsg != "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: errMsg}}
	}
	data, err := readRel(root, path.Join("src", srcRel))
	if err != nil {
		return []Diagnostic{{Path: "src/" + srcRel, Line: 1, Message: "file is missing"}}
	}
	rows, line, scanErr := scanCSV(data)
	if scanErr != "" {
		return []Diagnostic{{Path: "src/" + srcRel, Line: line, Message: scanErr}}
	}
	if len(rows) == 0 {
		return []Diagnostic{{Path: "src/" + srcRel, Line: 1, Message: "CSV has no header"}}
	}
	header := map[string]int{}
	for i, c := range rows[0] {
		if _, dup := header[c.text]; dup {
			return []Diagnostic{{Path: "src/" + srcRel, Line: c.line, Message: "duplicate column " + c.text}}
		}
		header[c.text] = i
	}
	cols := append([]string{}, b.field)
	for _, eq := range eqs {
		cols = append(cols, eq.name)
	}
	sort.Strings(cols)
	for _, name := range cols {
		if _, ok := header[name]; !ok {
			return []Diagnostic{{Path: rel, Line: b.line, Message: "unknown column " + name}}
		}
	}
	var hits []int
	for r := 1; r < len(rows); r++ {
		if rowMatches(rows[r], header, eqs) {
			hits = append(hits, r)
		}
	}
	if len(hits) == 0 {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "selector matched 0 rows"}}
	}
	if len(hits) != 1 {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "selector matched " + itoa(len(hits)) + " rows"}}
	}
	row := rows[hits[0]]
	idx := header[b.field]
	if idx >= len(row) {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "value is not an integer"}}
	}
	n, ok := parseInt(row[idx].text)
	if !ok {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "value is not an integer"}}
	}
	b.num = n
	b.numeric = true
	b.resolved = true
	return nil
}

func resolveXML(root string, b *binding) []Diagnostic {
	rel := "src/text-bindings.tsv"
	srcRel, ok := cleanSource(b.source)
	if !ok || !strings.HasPrefix(srcRel, "data/") || !strings.HasSuffix(srcRel, ".xml") {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "source path escapes src"}}
	}
	if b.selector == "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "empty selector"}}
	}
	if b.field == "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "field is empty"}}
	}
	steps, errMsg := parseXMLSelector(b.selector)
	if errMsg != "" {
		return []Diagnostic{{Path: rel, Line: b.line, Message: errMsg}}
	}
	data, err := readRel(root, path.Join("src", srcRel))
	if err != nil {
		return []Diagnostic{{Path: "src/" + srcRel, Line: 1, Message: "file is missing"}}
	}
	rootNode, err := parseXMLTree(data)
	if err != nil {
		return []Diagnostic{{Path: "src/" + srcRel, Line: 1, Message: "invalid XML"}}
	}
	hits := selectNodes(rootNode, steps)
	if len(hits) == 0 {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "selector matched 0 elements"}}
	}
	if len(hits) != 1 {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "selector matched " + itoa(len(hits)) + " elements"}}
	}
	raw, ok := hits[0].attrs[b.field]
	if !ok {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "unknown attribute " + b.field}}
	}
	n, ok := parseInt(raw)
	if !ok {
		return []Diagnostic{{Path: rel, Line: b.line, Message: "value is not an integer"}}
	}
	b.num = n
	b.numeric = true
	b.resolved = true
	return nil
}

func matchParams(entries map[string]*entry, binds []binding, byKey map[string][]int) []Diagnostic {
	var diags []Diagnostic
	keys := make([]string, 0, len(entries))
	for key := range entries {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		e := entries[key]
		if e.msg == nil {
			continue
		}
		want := map[string]bool{}
		for _, name := range e.msg.args {
			want[name] = true
		}
		got := map[string]binding{}
		for _, idx := range byKey[key] {
			b := binds[idx]
			got[b.param] = b
		}
		var extra []string
		for name := range got {
			if !want[name] {
				extra = append(extra, name)
			}
		}
		sort.Strings(extra)
		for _, name := range extra {
			diags = append(diags, Diagnostic{
				Path:    "src/text-bindings.tsv",
				Line:    got[name].line,
				Message: "extra parameter " + name,
			})
		}
		var missing []string
		for name := range want {
			if _, ok := got[name]; !ok {
				missing = append(missing, name)
			}
		}
		sort.Strings(missing)
		for _, name := range missing {
			diags = append(diags, Diagnostic{
				Path:    "i18n/en.tsv",
				Line:    e.line,
				Message: "missing parameter " + name,
			})
		}
	}
	return diags
}

func textCycles(binds []binding) []Diagnostic {
	graph := map[string][]string{}
	edgeLine := map[string]int{}
	for _, b := range binds {
		if b.kind != "text" || b.ref == "" {
			continue
		}
		graph[b.key] = append(graph[b.key], b.ref)
		edge := b.key + "\x00" + b.ref
		if line, ok := edgeLine[edge]; !ok || b.line < line {
			edgeLine[edge] = b.line
		}
	}
	nodes := make([]string, 0, len(graph))
	for node := range graph {
		nodes = append(nodes, node)
		sort.Strings(graph[node])
	}
	sort.Strings(nodes)
	color := map[string]int{}
	var diags []Diagnostic
	var stack []string
	var dfs func(string)
	dfs = func(node string) {
		color[node] = 1
		stack = append(stack, node)
		for _, next := range graph[node] {
			if color[next] == 1 {
				i := 0
				for i < len(stack) && stack[i] != next {
					i++
				}
				cyc := append([]string{}, stack[i:]...)
				cyc = append(cyc, next)
				from, to := cyc[0], cyc[1]
				diags = append(diags, Diagnostic{
					Path:    "src/text-bindings.tsv",
					Line:    edgeLine[from+"\x00"+to],
					Message: "text cycle: " + strings.Join(cyc, " -> "),
				})
				continue
			}
			if color[next] == 0 {
				dfs(next)
			}
		}
		stack = stack[:len(stack)-1]
		color[node] = 2
	}
	for _, node := range nodes {
		if color[node] == 0 {
			dfs(node)
		}
	}
	return diags
}

type colEq struct {
	name string
	num  int64
}

func parseCSVSelector(s string) ([]colEq, string) {
	parts := strings.Split(s, ";")
	out := make([]colEq, 0, len(parts))
	for _, part := range parts {
		name, raw, ok := strings.Cut(part, "=")
		if !ok || name == "" {
			return nil, "malformed selector"
		}
		n, ok := parseInt(raw)
		if !ok {
			return nil, "selector value is not an integer"
		}
		out = append(out, colEq{name: name, num: n})
	}
	return out, ""
}

func rowMatches(row []cell, header map[string]int, eqs []colEq) bool {
	for _, eq := range eqs {
		idx := header[eq.name]
		if idx >= len(row) {
			return false
		}
		n, ok := parseInt(row[idx].text)
		if !ok || n != eq.num {
			return false
		}
	}
	return true
}

func parseXMLSelector(s string) ([]xmlStep, string) {
	parts := strings.Split(s, "/")
	steps := make([]xmlStep, 0, len(parts))
	for _, part := range parts {
		lb := strings.IndexByte(part, '[')
		rb := strings.LastIndexByte(part, ']')
		if lb <= 0 || rb != len(part)-1 || rb <= lb {
			return nil, "malformed selector"
		}
		elem := part[:lb]
		attr, val, ok := strings.Cut(part[lb+1:rb], "=")
		if !ok || elem == "" || attr == "" || val == "" {
			return nil, "malformed selector"
		}
		steps = append(steps, xmlStep{elem: elem, attr: attr, val: val})
	}
	return steps, ""
}

func parseXMLTree(data []byte) (*xnode, error) {
	dec := xml.NewDecoder(bytes.NewReader(data))
	var stack []*xnode
	var root *xnode
	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, err
		}
		switch t := tok.(type) {
		case xml.StartElement:
			n := &xnode{name: t.Name.Local, attrs: map[string]string{}}
			for _, a := range t.Attr {
				n.attrs[a.Name.Local] = a.Value
			}
			if len(stack) == 0 {
				root = n
			} else {
				parent := stack[len(stack)-1]
				parent.children = append(parent.children, n)
			}
			stack = append(stack, n)
		case xml.EndElement:
			if len(stack) > 0 {
				stack = stack[:len(stack)-1]
			}
		}
	}
	if root == nil {
		return nil, io.ErrUnexpectedEOF
	}
	return root, nil
}

func selectNodes(root *xnode, steps []xmlStep) []*xnode {
	var starts []*xnode
	var walk func(*xnode)
	walk = func(n *xnode) {
		if matchStep(n, steps[0]) {
			starts = append(starts, n)
		}
		for _, child := range n.children {
			walk(child)
		}
	}
	walk(root)
	var out []*xnode
	for _, start := range starts {
		out = append(out, followSteps(start, steps[1:])...)
	}
	return out
}

func matchStep(n *xnode, step xmlStep) bool {
	val, ok := n.attrs[step.attr]
	return ok && n.name == step.elem && val == step.val
}

func followSteps(n *xnode, steps []xmlStep) []*xnode {
	cur := []*xnode{n}
	for _, step := range steps {
		var next []*xnode
		for _, node := range cur {
			for _, child := range node.children {
				if matchStep(child, step) {
					next = append(next, child)
				}
			}
		}
		cur = next
	}
	return cur
}

func cleanSource(rel string) (string, bool) {
	if rel == "" || strings.HasPrefix(rel, "/") || strings.Contains(rel, "\\") {
		return "", false
	}
	clean := path.Clean(rel)
	if clean == "." || clean == ".." || strings.HasPrefix(clean, "../") {
		return "", false
	}
	return clean, true
}

func parseInt(s string) (int64, bool) {
	if s == "" || s[0] == '+' {
		return 0, false
	}
	n, err := strconv.ParseInt(s, 10, 64)
	if err != nil {
		return 0, false
	}
	return n, true
}

func sourceRefs(root string, entries map[string]*entry, used map[string]bool) []Diagnostic {
	var diags []Diagnostic
	note := func(rel, key string, line int) {
		if _, ok := entries[key]; !ok {
			diags = append(diags, Diagnostic{Path: rel, Line: line, Message: "undefined key " + key})
			return
		}
		used[key] = true
	}
	for _, top := range []string{"src/catalog", "src/data"} {
		files, exists, err := regularFiles(root, top)
		if err != nil || !exists {
			continue
		}
		for _, rel := range files {
			data, err := readRel(root, rel)
			if err != nil {
				diags = append(diags, Diagnostic{Path: rel, Line: 1, Message: "cannot read"})
				continue
			}
			switch {
			case strings.HasPrefix(rel, "src/catalog/") && strings.HasSuffix(rel, ".csv"):
				rows, line, errMsg := scanCSV(data)
				if errMsg != "" {
					diags = append(diags, Diagnostic{Path: rel, Line: line, Message: errMsg})
					continue
				}
				for _, row := range rows {
					for _, c := range row {
						if c.quoted {
							continue
						}
						key, ok := refKey(c.text)
						if !ok {
							continue
						}
						note(rel, key, c.line)
					}
				}
			case strings.HasPrefix(rel, "src/data/") && strings.HasSuffix(rel, ".xml"):
				for _, hit := range xmlAttrs(data) {
					key, ok := refKey(hit.value)
					if !ok {
						continue
					}
					note(rel, key, hit.line)
				}
			case strings.HasPrefix(rel, "src/data/") && strings.HasSuffix(rel, ".htm"):
				for _, sp := range htmlSpans(data) {
					if !sp.valid {
						diags = append(diags, Diagnostic{Path: rel, Line: sp.line, Message: "invalid text reference"})
						continue
					}
					note(rel, sp.inner, sp.line)
				}
			}
		}
	}
	return diags
}

func formatEntries(entries map[string]*entry, binds []binding) (map[string]string, []Diagnostic) {
	byKey := map[string][]binding{}
	for _, b := range binds {
		if !b.resolved && b.kind != "text" {
			continue
		}
		byKey[b.key] = append(byKey[b.key], b)
	}
	done := map[string]string{}
	visiting := map[string]bool{}
	var diags []Diagnostic
	var eval func(string) (string, bool)
	eval = func(key string) (string, bool) {
		if text, ok := done[key]; ok {
			return text, true
		}
		e, ok := entries[key]
		if !ok || e.msg == nil {
			return "", false
		}
		if visiting[key] {
			diags = append(diags, Diagnostic{Path: "i18n/en.tsv", Line: e.line, Message: "text cycle: " + key})
			return "", false
		}
		visiting[key] = true
		defer func() { visiting[key] = false }()
		args := map[string]value{}
		for _, b := range byKey[key] {
			if b.kind == "text" {
				text, ok := eval(b.ref)
				if !ok {
					return "", false
				}
				args[b.param] = value{text: text}
				continue
			}
			if !b.numeric {
				diags = append(diags, Diagnostic{Path: "src/text-bindings.tsv", Line: b.line, Message: "value is not an integer"})
				return "", false
			}
			args[b.param] = value{numeric: true, num: b.num}
		}
		text, errMsg := e.msg.apply(args)
		if errMsg != "" {
			diags = append(diags, Diagnostic{Path: "i18n/en.tsv", Line: e.line, Message: errMsg})
			return "", false
		}
		done[key] = text
		return text, true
	}
	keys := make([]string, 0, len(entries))
	for key := range entries {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		eval(key)
	}
	if len(diags) > 0 {
		return nil, diags
	}
	return done, nil
}

func renderFiles(root string, texts map[string]string) (map[string][]byte, []Diagnostic) {
	out := map[string][]byte{}
	var diags []Diagnostic
	for _, top := range []string{"src/catalog", "src/data"} {
		files, exists, err := regularFiles(root, top)
		if err != nil || !exists {
			diags = append(diags, Diagnostic{Path: top, Line: 1, Message: "directory is missing"})
			continue
		}
		for _, rel := range files {
			data, err := readRel(root, rel)
			if err != nil {
				diags = append(diags, Diagnostic{Path: rel, Line: 1, Message: "cannot read"})
				continue
			}
			dest := strings.TrimPrefix(rel, "src/")
			var rendered []byte
			var part []Diagnostic
			switch {
			case strings.HasPrefix(rel, "src/catalog/") && strings.HasSuffix(rel, ".csv"):
				var line int
				var errMsg string
				rendered, part, line, errMsg = substituteCSV(data, texts)
				if errMsg != "" {
					diags = append(diags, Diagnostic{Path: rel, Line: line, Message: errMsg})
				}
			case strings.HasPrefix(rel, "src/data/") && strings.HasSuffix(rel, ".xml"):
				rendered, part = substituteXML(data, texts)
			case strings.HasPrefix(rel, "src/data/") && strings.HasSuffix(rel, ".htm"):
				rendered, part = substituteHTML(data, texts)
			default:
				rendered = append([]byte{}, data...)
			}
			for i := range part {
				if part[i].Path == "" {
					part[i].Path = rel
				}
			}
			diags = append(diags, part...)
			out[dest] = rendered
		}
	}
	if len(diags) > 0 {
		return nil, diags
	}
	return out, nil
}

func writeTree(root string, files map[string][]byte) error {
	for _, top := range []string{"catalog", "data"} {
		if err := os.RemoveAll(filepath.Join(root, top)); err != nil {
			return err
		}
	}
	paths := make([]string, 0, len(files))
	for rel := range files {
		paths = append(paths, rel)
	}
	sort.Strings(paths)
	for _, rel := range paths {
		dest := filepath.Join(root, filepath.FromSlash(rel))
		if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
			return err
		}
		if err := os.WriteFile(dest, files[rel], 0o644); err != nil {
			return err
		}
	}
	return nil
}

func diffBuilt(root string, rendered map[string][]byte) []Diagnostic {
	built := map[string][]byte{}
	for _, top := range []string{"catalog", "data"} {
		files, exists, err := regularFiles(root, top)
		if err != nil || !exists {
			continue
		}
		for _, rel := range files {
			data, err := readRel(root, rel)
			if err != nil {
				continue
			}
			built[rel] = data
		}
	}
	var diags []Diagnostic
	for rel, want := range rendered {
		got, ok := built[rel]
		if !ok {
			diags = append(diags, Diagnostic{Path: rel, Line: 1, Message: "file missing from the built world"})
			continue
		}
		if !bytes.Equal(got, want) {
			diags = append(diags, Diagnostic{Path: rel, Line: firstDiffLine(got, want), Message: "byte differs from render"})
		}
	}
	for rel := range built {
		if _, ok := rendered[rel]; !ok {
			diags = append(diags, Diagnostic{Path: rel, Line: 1, Message: "file is not in the render"})
		}
	}
	return diags
}

func leftovers(root string) []Diagnostic {
	var diags []Diagnostic
	for _, top := range []string{"catalog", "data"} {
		files, exists, err := regularFiles(root, top)
		if err != nil || !exists {
			continue
		}
		for _, rel := range files {
			data, err := readRel(root, rel)
			if err != nil {
				continue
			}
			diags = append(diags, leftoverDiags(rel, data)...)
		}
	}
	return diags
}

func leftoverDiags(rel string, data []byte) []Diagnostic {
	var diags []Diagnostic
	lines := bytes.Split(data, []byte{'\n'})
	for i, chunk := range lines {
		line := i + 1
		if m := atKeyPattern.Find(chunk); m != nil {
			diags = append(diags, Diagnostic{Path: rel, Line: line, Message: "unresolved " + string(m)})
		}
		if bytes.Contains(chunk, []byte("{{")) {
			diags = append(diags, Diagnostic{Path: rel, Line: line, Message: "unresolved {{"})
		}
		if m := braceArgPattern.Find(chunk); m != nil {
			diags = append(diags, Diagnostic{Path: rel, Line: line, Message: "unresolved " + string(m)})
		}
	}
	return diags
}
