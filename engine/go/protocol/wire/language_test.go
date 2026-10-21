// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import "testing"

func TestLanguageTags(t *testing.T) {
	for _, s := range []string{"en", "en-US", "zh-Hant-TW", "sl-rozaj-biske", "de-CH-1901", "en-US-u-ca-gregory", "x-private", "i-klingon", "en-x-a", "sr-Latn-BA"} {
		if !languageTag([]byte(s)) {
			t.Fatal("valid tag rejected", s)
		}
	}
	for _, s := range []string{"", "e", "en_US", "en--US", "en-", "x", "en-a", "en-u-ca-u-nu", "sl-rozaj-rozaj", "en-abcdefghij", "en US", "en-" + string([]byte{0}), "123"} {
		if languageTag([]byte(s)) {
			t.Fatal("invalid tag accepted", s)
		}
	}
}
