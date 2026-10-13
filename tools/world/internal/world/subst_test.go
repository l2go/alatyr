// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"bytes"
	"testing"
)

func TestCSVQuotesCommaAndQuote(t *testing.T) {
	got := quoteCSV(`A & B < C > "D, E`)
	want := `"A & B < C > ""D, E"`
	if got != want {
		t.Fatalf("%s", got)
	}
}

func TestCSVKeepsCRLF(t *testing.T) {
	in := []byte("shop_id,item\r\n1,@item.potion.name\r\n")
	out, diags, _, errMsg := substituteCSV(in, map[string]string{"item.potion.name": "Test Potion"})
	if errMsg != "" || len(diags) != 0 {
		t.Fatalf("%s %v", errMsg, diags)
	}
	want := []byte("shop_id,item\r\n1,Test Potion\r\n")
	if !bytes.Equal(out, want) {
		t.Fatalf("%q", out)
	}
}

func TestQuotedCSVCellIsNotAKey(t *testing.T) {
	in := []byte("\"@item.potion.name\"\n")
	out, diags, _, errMsg := substituteCSV(in, map[string]string{"item.potion.name": "Nope"})
	if errMsg != "" || len(diags) != 0 {
		t.Fatalf("%s %v", errMsg, diags)
	}
	if !bytes.Equal(out, in) {
		t.Fatalf("%q", out)
	}
}

func TestAtSignInsideLongerCellIsLiteral(t *testing.T) {
	in := []byte("keep mail@local\n")
	out, _, _, errMsg := substituteCSV(in, map[string]string{})
	if errMsg != "" || !bytes.Equal(out, in) {
		t.Fatalf("%s %q", errMsg, out)
	}
}

func TestXMLEscapesAttributeAndKeepsOrder(t *testing.T) {
	in := []byte("<village  id=\"1\" title=\"Lane @local\" name=\"@item.potion.name\" />\n")
	out, diags := substituteXML(in, map[string]string{
		"item.potion.name": `A & B < C > "D, E`,
	})
	if len(diags) != 0 {
		t.Fatal(diags)
	}
	want := "<village  id=\"1\" title=\"Lane @local\" name=\"A &amp; B &lt; C &gt; &quot;D, E\" />\n"
	if string(out) != want {
		t.Fatalf("%s", out)
	}
}

func TestHTMLEscapesAndDoesNotRescan(t *testing.T) {
	out, diags := substituteHTML([]byte("{{item.potion.name}}"), map[string]string{
		"item.potion.name": `A & B < C > "D, E`,
	})
	if len(diags) != 0 {
		t.Fatal(diags)
	}
	want := `A &amp; B &lt; C &gt; "D, E`
	if string(out) != want {
		t.Fatalf("%s", out)
	}

	again, diags := substituteHTML([]byte("{{item.potion.name}}"), map[string]string{
		"item.potion.name": "{{item.potion.name}}",
	})
	if len(diags) != 0 || string(again) != "{{item.potion.name}}" {
		t.Fatalf("%s %v", again, diags)
	}
}
