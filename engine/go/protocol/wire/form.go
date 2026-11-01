// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

// FormField is the optional alatyr/1 Fact string naming an NPC's visual form.
// It is an opaque world label, never a capability or a closed list of creatures.
const FormField uint32 = 36

// FormSpec returns an independent optional string specification. The field is
// nonessential so older codecs skip it. Empty text and tokens outside
// [a-z][a-z0-9_]{0,31} are invalid frames, not unknown forms.
func FormSpec() Spec {
	s := Optional(String)
	s.MaxBytes = 32
	s.Token = true
	return s
}

// ValidForm reports whether s is a form token. Empty is not a token.
func ValidForm(s string) bool { return formToken([]byte(s)) }

func formToken(raw []byte) bool {
	if len(raw) < 1 || len(raw) > 32 {
		return false
	}
	if raw[0] < 'a' || raw[0] > 'z' {
		return false
	}
	for _, b := range raw[1:] {
		switch {
		case b >= 'a' && b <= 'z', b >= '0' && b <= '9', b == '_':
		default:
			return false
		}
	}
	return true
}
