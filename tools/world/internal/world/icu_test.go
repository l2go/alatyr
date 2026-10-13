// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import "testing"

func TestFormatIntegerGroupsThousands(t *testing.T) {
	if got := formatInteger(1234); got != "1,234" {
		t.Fatalf("got %s", got)
	}
	if got := formatInteger(0); got != "0" {
		t.Fatalf("got %s", got)
	}
	if got := formatInteger(12); got != "12" {
		t.Fatalf("got %s", got)
	}
	if got := formatInteger(1000000); got != "1,000,000" {
		t.Fatalf("got %s", got)
	}
}

func TestPluralSelectsOneForExactlyOne(t *testing.T) {
	got := formatPlural(t, 1)
	if got != "1 coin" {
		t.Fatalf("got %s", got)
	}
}

func TestPluralSelectsOtherForZero(t *testing.T) {
	got := formatPlural(t, 0)
	if got != "0 coins" {
		t.Fatalf("got %s", got)
	}
}

func TestPluralSelectsOtherForTwelve(t *testing.T) {
	got := formatPlural(t, 12)
	if got != "12 coins" {
		t.Fatalf("got %s", got)
	}
}

func TestPlainNumberUsesGrouping(t *testing.T) {
	msg, errMsg := parseTemplate("{count}")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := msg.apply(map[string]value{"count": {numeric: true, num: 1234}})
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	if got != "1,234" {
		t.Fatalf("got %s", got)
	}
}

func TestPaymentPhrase(t *testing.T) {
	amount, errMsg := parseTemplate("{count, plural, one {# {currency} coin} other {# {currency} coins}}")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := amount.apply(map[string]value{
		"count":    {numeric: true, num: 12},
		"currency": {text: "Mark"},
	})
	if errMsg != "" || got != "12 Mark coins" {
		t.Fatalf("%s %s", got, errMsg)
	}
	line, errMsg := parseTemplate("{potion} costs {payment} in {village}.")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg = line.apply(map[string]value{
		"potion":  {text: "Test Potion"},
		"payment": {text: got},
		"village": {text: "Testvale"},
	})
	if errMsg != "" || got != "Test Potion costs 12 Mark coins in Testvale." {
		t.Fatalf("%s %s", got, errMsg)
	}
}

func TestDoubledApostropheRejected(t *testing.T) {
	_, errMsg := parseTemplate("It''s")
	if errMsg != "doubled apostrophe" {
		t.Fatalf("%s", errMsg)
	}
}

func TestApostropheBeforeBraceRejected(t *testing.T) {
	_, errMsg := parseTemplate("Say '{name}' aloud")
	if errMsg != "apostrophe before '{'" {
		t.Fatalf("%s", errMsg)
	}
}

func TestApostropheBeforeClosingBraceRejected(t *testing.T) {
	_, errMsg := parseTemplate("Close '}")
	if errMsg != "apostrophe before '}'" {
		t.Fatalf("%s", errMsg)
	}
}

func TestApostropheBeforeHashInBranchRejected(t *testing.T) {
	_, errMsg := parseTemplate("{count, plural, one {'# coin} other {# coins}}")
	if errMsg != "apostrophe before '#'" {
		t.Fatalf("%s", errMsg)
	}
}

func TestLiteralBraceRejected(t *testing.T) {
	_, errMsg := parseTemplate("Open { brace")
	if errMsg != "literal '{'" {
		t.Fatalf("%s", errMsg)
	}
}

func TestLiteralClosingBraceRejected(t *testing.T) {
	_, errMsg := parseTemplate("Close } brace")
	if errMsg != "literal '}'" {
		t.Fatalf("%s", errMsg)
	}
}

func TestPluralFewRejected(t *testing.T) {
	_, errMsg := parseTemplate("{count, plural, few {# x} other {# x}}")
	if errMsg != `unsupported plural branch "few"` {
		t.Fatalf("%s", errMsg)
	}
}

func TestPluralMissingOtherRejected(t *testing.T) {
	_, errMsg := parseTemplate("{count, plural, one {# x}}")
	if errMsg != "plural missing other" {
		t.Fatalf("%s", errMsg)
	}
}

func TestUnknownArgumentTypeRejected(t *testing.T) {
	_, errMsg := parseTemplate("{count, date, short}")
	if errMsg != `unknown argument type "date"` {
		t.Fatalf("%s", errMsg)
	}
}

func TestSingleApostropheIsLiteral(t *testing.T) {
	msg, errMsg := parseTemplate("It's fine")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := msg.apply(nil)
	if errMsg != "" || got != "It's fine" {
		t.Fatalf("%s %s", got, errMsg)
	}
}

func TestTrailingApostropheIsLiteral(t *testing.T) {
	msg, errMsg := parseTemplate("neighbours'")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := msg.apply(nil)
	if errMsg != "" || got != "neighbours'" {
		t.Fatalf("%s %s", got, errMsg)
	}
}

func TestApostropheBeforeHashOutsideBranchIsLiteral(t *testing.T) {
	msg, errMsg := parseTemplate("See '#")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := msg.apply(nil)
	if errMsg != "" || got != "See '#" {
		t.Fatalf("%s %s", got, errMsg)
	}
}

func formatPlural(t *testing.T, n int64) string {
	t.Helper()
	msg, errMsg := parseTemplate("{count, plural, one {# coin} other {# coins}}")
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got, errMsg := msg.apply(map[string]value{"count": {numeric: true, num: n}})
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	return got
}
