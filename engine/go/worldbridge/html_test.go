// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"errors"
	"reflect"
	"strings"
	"testing"
)

func TestHTMLIsTextNotAuthority(t *testing.T) {
	for _, tc := range []struct {
		name, input, want string
		options           int
		err               error
	}{
		{"format and entities", "<p>Hello &amp; world</p><br>Again", "Hello & world\n\nAgain", 0, nil},
		{"script removed", "<script>hidden</script><style>hidden</style>Hello", "Hello", 0, nil},
		{"UTF-8 script boundary", "<script>" + string(rune(0x212a)) + "</SCRIPT>Hello", "Hello", 0, nil},
		{"action", "<a action=\"bypass -h npc_2_SkillList\">Learn</a>", "Learn", 1, nil},
		{"variable", "<a action=\"bypass -h npc_2_$input\">Learn</a>", "Learn", 0, nil},
		{"unknown entity", "&notprofile;", "&notprofile;", 0, nil},
		{"pre whitespace", "<pre>  a\n b  </pre>", "  a\n b  ", 0, nil},
		{"numeric NUL is text", "a&#0;b", "a" + string(rune(0)) + "b", 0, nil},
		{"numeric line ending", "<pre>a&#13;&#10;b</pre>", "a\nb", 0, nil},
		{"unknown button remains text", "<button action=\"other\" value=\"Visible\">", "Visible", 0, nil},
		{"variable button remains text", "<button action=\"bypass -h $input\" value=\"Visible\">", "Visible", 0, nil},
		{"wrong script close", "<script>hidden</scriptx>", "", 0, errSource},
		{"duplicate action", "<button action=\"bypass -h a\" ACTION=\"bypass -h b\" value=\"Go\">", "", 0, errSource},
		{"truncated", "<a action=\"unterminated>", "", 0, errSource},
		{"oversized", strings.Repeat("x", maxPayloadBytes+1), "", 0, errCapacity},
	} {
		t.Run(tc.name, func(t *testing.T) {
			p, err := parseHTML(tc.input)
			if tc.err != nil {
				if !errors.Is(err, tc.err) || p.text != "" || len(p.options) != 0 {
					t.Fatalf("got %+v/%v, want atomic %v", p, err, tc.err)
				}
				return
			}
			if err != nil || p.text != tc.want || len(p.options) != tc.options {
				t.Fatalf("got %+v/%v, want %q and %d options", p, err, tc.want, tc.options)
			}
		})
	}
}
func FuzzParseHTML(f *testing.F) {
	f.Add("<p>Hello</p>")
	f.Add("<a action=\"bypass -h npc_2_SkillList\">Learn</a>")
	f.Add("<script>ignore</script>")
	f.Fuzz(func(t *testing.T, s string) {
		p, err := parseHTML(s)
		if err != nil {
			return
		}
		again, err := parseHTML(s)
		if err != nil || !reflect.DeepEqual(p, again) || len(p.text) > maxPayloadBytes {
			t.Fatal("presentation is unstable or exceeded its bound")
		}
	})
}
