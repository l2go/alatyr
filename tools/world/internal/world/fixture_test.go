// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRenderFixtureMatchesGolden(t *testing.T) {
	dir := t.TempDir()
	copyFixture(t, "good", dir)
	if err := os.RemoveAll(filepath.Join(dir, "catalog")); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll(filepath.Join(dir, "data")); err != nil {
		t.Fatal(err)
	}
	if diags := Render(dir, "en"); len(diags) != 0 {
		t.Fatal(diags)
	}
	compareTrees(t, filepath.Join(fixturePath("good"), "catalog"), filepath.Join(dir, "catalog"))
	compareTrees(t, filepath.Join(fixturePath("good"), "data"), filepath.Join(dir, "data"))
}

func TestRenderIsDeterministic(t *testing.T) {
	a := t.TempDir()
	b := t.TempDir()
	copyFixture(t, "good", a)
	copyFixture(t, "good", b)
	for _, dir := range []string{a, b} {
		if err := os.RemoveAll(filepath.Join(dir, "catalog")); err != nil {
			t.Fatal(err)
		}
		if err := os.RemoveAll(filepath.Join(dir, "data")); err != nil {
			t.Fatal(err)
		}
	}
	if diags := Render(a, "en"); len(diags) != 0 {
		t.Fatal(diags)
	}
	first := readTree(t, a)
	if diags := Render(a, "en"); len(diags) != 0 {
		t.Fatal(diags)
	}
	second := readTree(t, a)
	if diags := Render(b, "en"); len(diags) != 0 {
		t.Fatal(diags)
	}
	third := readTree(t, b)
	if !treesEqual(first, second) || !treesEqual(first, third) {
		t.Fatal("two renders differ")
	}
}

func TestRenderStopsOnBrokenWorld(t *testing.T) {
	dir := t.TempDir()
	copyFixture(t, "bad-kind", dir)
	if err := os.RemoveAll(filepath.Join(dir, "catalog")); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll(filepath.Join(dir, "data")); err != nil {
		t.Fatal(err)
	}
	if diags := Render(dir, "en"); len(diags) == 0 {
		t.Fatal("expected errors")
	}
	if _, err := os.Stat(filepath.Join(dir, "catalog")); !os.IsNotExist(err) {
		t.Fatal("render wrote catalog")
	}
}

func TestCheckGoodWorld(t *testing.T) {
	expect(t, "good")
}

func TestCheckNonASCIIEn(t *testing.T) {
	expect(t, "non-ascii-en", "i18n/en.tsv:2: non-ASCII byte")
}

func TestCheckNonASCIISrc(t *testing.T) {
	expect(t, "non-ascii-src", "src/note.txt:1: non-ASCII byte")
}

func TestCheckNonASCIIBuilt(t *testing.T) {
	expect(t, "non-ascii-built",
		"data/seller.htm:1: byte differs from render",
		"data/seller.htm:1: non-ASCII byte",
	)
}

func TestCheckNonASCIIProperties(t *testing.T) {
	expect(t, "non-ascii-properties", "world.properties:1: non-ASCII byte")
}

func TestCheckBadHeader(t *testing.T) {
	expect(t, "bad-header", "i18n/en.tsv:1: header must be key, kind, text, note")
}

func TestCheckThreeFields(t *testing.T) {
	expect(t, "three-fields", "i18n/en.tsv:7: expected 4 fields")
}

func TestCheckDuplicateKey(t *testing.T) {
	expect(t, "duplicate-key", "i18n/en.tsv:7: duplicate key place.village.name")
}

func TestCheckBadKind(t *testing.T) {
	expect(t, "bad-kind", "i18n/en.tsv:7: kind must be proper or common")
}

func TestCheckEmptyText(t *testing.T) {
	expect(t, "empty-text", "i18n/en.tsv:7: empty text")
}

func TestCheckBadKey(t *testing.T) {
	expect(t, "bad-key", "i18n/en.tsv:7: key must match kind.slug.field")
}

func TestCheckUndefinedKeyCSV(t *testing.T) {
	expect(t, "undefined-csv", "src/catalog/shop.csv:2: undefined key item.missing.name")
}

func TestCheckUndefinedKeyXML(t *testing.T) {
	expect(t, "undefined-xml", "src/data/village.xml:1: undefined key place.missing.name")
}

func TestCheckUndefinedKeyHTML(t *testing.T) {
	expect(t, "undefined-html", "src/data/seller.htm:1: undefined key dialog.missing.prices")
}

func TestCheckUnusedKey(t *testing.T) {
	expect(t, "unused-key", "i18n/en.tsv:7: unused key place.spare.name")
}

func TestCheckExtraBuiltFile(t *testing.T) {
	expect(t, "extra-file", "catalog/extra.csv:1: file is not in the render")
}

func TestCheckMissingBuiltFile(t *testing.T) {
	expect(t, "missing-file", "data/seller.htm:1: file missing from the built world")
}

func TestCheckDifferentByte(t *testing.T) {
	expect(t, "different-byte", "data/village.xml:1: byte differs from render")
}

func TestCheckLeftoverAtKey(t *testing.T) {
	expect(t, "leftover-at",
		"catalog/shop.csv:2: byte differs from render",
		"catalog/shop.csv:2: unresolved @item.potion.name",
	)
}

func TestCheckLeftoverBraces(t *testing.T) {
	expect(t, "leftover-braces",
		"data/seller.htm:1: byte differs from render",
		"data/seller.htm:1: unresolved {count}",
		"data/seller.htm:1: unresolved {{",
	)
}

func TestCheckDoubledApostrophe(t *testing.T) {
	expect(t, "icu-apostrophe", "i18n/en.tsv:7: doubled apostrophe")
}

func TestCheckApostropheBeforeBrace(t *testing.T) {
	expect(t, "icu-apostrophe-brace", "i18n/en.tsv:7: apostrophe before '{'")
}

func TestCheckApostropheBeforeClosingBrace(t *testing.T) {
	expect(t, "icu-apostrophe-close", "i18n/en.tsv:7: apostrophe before '}'")
}

func TestCheckApostropheBeforeHash(t *testing.T) {
	expect(t, "icu-apostrophe-hash", "i18n/en.tsv:7: apostrophe before '#'")
}

func TestCheckLiteralBrace(t *testing.T) {
	expect(t, "icu-literal-brace", "i18n/en.tsv:7: literal '{'")
}

func TestCheckLiteralClosingBrace(t *testing.T) {
	expect(t, "icu-literal-close", "i18n/en.tsv:7: literal '}'")
}

func TestCheckPluralFew(t *testing.T) {
	expect(t, "icu-few", `i18n/en.tsv:7: unsupported plural branch "few"`)
}

func TestCheckPluralMissingOther(t *testing.T) {
	expect(t, "icu-no-other", "i18n/en.tsv:7: plural missing other")
}

func TestCheckUnknownArgumentType(t *testing.T) {
	expect(t, "icu-unknown-type", `i18n/en.tsv:7: unknown argument type "date"`)
}

func TestCheckExtraParameter(t *testing.T) {
	expect(t, "bind-extra", "src/text-bindings.tsv:7: extra parameter extra")
}

func TestCheckMissingParameter(t *testing.T) {
	expect(t, "bind-missing", "i18n/en.tsv:7: missing parameter name")
}

func TestCheckTextCycle(t *testing.T) {
	expect(t, "bind-cycle", "src/text-bindings.tsv:7: text cycle: alpha.loop.one -> beta.loop.two -> alpha.loop.one")
}

func TestCheckSelectorNoRow(t *testing.T) {
	expect(t, "bind-no-row", "src/text-bindings.tsv:2: selector matched 0 rows")
}

func TestCheckSelectorTwoRows(t *testing.T) {
	expect(t, "bind-two-rows", "src/text-bindings.tsv:2: selector matched 2 rows")
}

func TestCheckNonInteger(t *testing.T) {
	expect(t, "bind-non-integer", "src/text-bindings.tsv:2: value is not an integer")
}

func TestCheckEscaping(t *testing.T) {
	expect(t, "escaping")
	csv := readFixture(t, "escaping", "catalog/shop.csv")
	xml := readFixture(t, "escaping", "data/village.xml")
	html := readFixture(t, "escaping", "data/seller.htm")
	if !bytes.Contains(csv, []byte(`"A & B < C > ""D, E"`)) {
		t.Fatalf("csv %s", csv)
	}
	if !bytes.Contains(xml, []byte(`A &amp; B &lt; C &gt; &quot;D, E`)) {
		t.Fatalf("xml %s", xml)
	}
	if !bytes.Contains(html, []byte(`A &amp; B &lt; C &gt; "D, E`)) {
		t.Fatalf("html %s", html)
	}
}

func TestCheckBytesPreserved(t *testing.T) {
	expect(t, "bytes")
	csv := readFixture(t, "bytes", "catalog/shop.csv")
	if !bytes.Contains(csv, []byte("\r\n")) || !bytes.Contains(csv, []byte("keep mail@local")) {
		t.Fatalf("%q", csv)
	}
	xml := string(readFixture(t, "bytes", "data/village.xml"))
	want := "<village  id=\"1\" title=\"Lane @local\" name=\"Testvale\" />\n"
	if xml != want {
		t.Fatalf("%s", xml)
	}
}

func TestCheckNumbers(t *testing.T) {
	expect(t, "numbers")
	html := string(readFixture(t, "numbers", "data/label.htm"))
	want := "<html><body>1 coin|0 coins|12 coins|1,234|1,234</body></html>\n"
	if html != want {
		t.Fatalf("%s", html)
	}
}

func TestCheckXMLNumber(t *testing.T) {
	expect(t, "xml-number")
	html := string(readFixture(t, "xml-number", "data/label.htm"))
	if html != "<html><body>Guard 3</body></html>\n" {
		t.Fatalf("%s", html)
	}
}

func TestCheckXMLSelectorNone(t *testing.T) {
	expect(t, "bind-xml-none", "src/text-bindings.tsv:2: selector matched 0 elements")
}

func TestCheckXMLSelectorTwo(t *testing.T) {
	expect(t, "bind-xml-two", "src/text-bindings.tsv:2: selector matched 2 elements")
}

func TestCheckXMLNonInteger(t *testing.T) {
	expect(t, "bind-xml-non-integer", "src/text-bindings.tsv:2: value is not an integer")
}

func TestRejectsOtherLocale(t *testing.T) {
	diags := Check(fixturePath("good"), "fr")
	if len(diags) != 1 || diags[0].String() != "i18n/fr.tsv:1: only locale en is supported" {
		t.Fatal(diags)
	}
}

func expect(t *testing.T, name string, lines ...string) {
	t.Helper()
	diags := Check(fixturePath(name), "en")
	var have []string
	for _, d := range diags {
		have = append(have, d.String())
	}
	if strings.Join(have, "\n") != strings.Join(lines, "\n") {
		t.Fatalf("got:\n%s\nwant:\n%s", strings.Join(have, "\n"), strings.Join(lines, "\n"))
	}
}

func fixturePath(name string) string {
	return filepath.Join("..", "..", "testdata", name)
}

func readFixture(t *testing.T, name, rel string) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(fixturePath(name), rel))
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func copyFixture(t *testing.T, name, dst string) {
	t.Helper()
	src := fixturePath(name)
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

func compareTrees(t *testing.T, want, got string) {
	t.Helper()
	w := readTree(t, want)
	g := readTree(t, got)
	if !treesEqual(w, g) {
		t.Fatalf("want %v\ngot %v", keysOf(w), keysOf(g))
	}
}

func readTree(t *testing.T, root string) map[string][]byte {
	t.Helper()
	out := map[string][]byte{}
	err := filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		body, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		out[filepath.ToSlash(rel)] = body
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	return out
}

func treesEqual(a, b map[string][]byte) bool {
	if len(a) != len(b) {
		return false
	}
	for k, v := range a {
		if !bytes.Equal(v, b[k]) {
			return false
		}
	}
	return true
}

func keysOf(m map[string][]byte) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}
