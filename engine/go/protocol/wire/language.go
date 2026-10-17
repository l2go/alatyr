// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire

import "strings"

// languageTag checks well-formed tags, not membership in a language registry.
func languageTag(raw []byte) bool {
	tag := strings.ToLower(string(raw))
	switch tag {
	case "art-lojban", "cel-gaulish", "en-gb-oed", "i-ami", "i-bnn", "i-default", "i-enochian", "i-hak", "i-klingon", "i-lux", "i-mingo", "i-navajo", "i-pwn", "i-tao", "i-tay", "i-tsu", "no-bok", "no-nyn", "sgn-be-fr", "sgn-be-nl", "sgn-ch-de", "zh-guoyu", "zh-hakka", "zh-min", "zh-min-nan", "zh-xiang":
		return true
	}
	parts := strings.Split(tag, "-")
	alpha := func(s string) bool {
		for _, b := range []byte(s) {
			if b < 'a' || b > 'z' {
				return false
			}
		}
		return s != ""
	}
	numeric := func(s string) bool {
		for _, b := range []byte(s) {
			if b < '0' || b > '9' {
				return false
			}
		}
		return s != ""
	}
	alnum := func(s string) bool {
		for _, b := range []byte(s) {
			if !(b >= 'a' && b <= 'z' || b >= '0' && b <= '9') {
				return false
			}
		}
		return s != ""
	}
	private := func(i int) bool {
		if i >= len(parts) {
			return false
		}
		for _, s := range parts[i:] {
			if len(s) > 8 || !alnum(s) {
				return false
			}
		}
		return true
	}
	if parts[0] == "x" {
		return private(1)
	}
	if len(parts[0]) < 2 || len(parts[0]) > 8 || !alpha(parts[0]) {
		return false
	}
	i := 1
	if len(parts[0]) <= 3 {
		for n := 0; n < 3 && i < len(parts) && len(parts[i]) == 3 && alpha(parts[i]); n++ {
			i++
		}
	}
	if i < len(parts) && len(parts[i]) == 4 && alpha(parts[i]) {
		i++
	}
	if i < len(parts) && (len(parts[i]) == 2 && alpha(parts[i]) || len(parts[i]) == 3 && numeric(parts[i])) {
		i++
	}
	variants := map[string]bool{}
	for i < len(parts) {
		s := parts[i]
		if !(len(s) >= 5 && len(s) <= 8 && alnum(s) || len(s) == 4 && s[0] >= '0' && s[0] <= '9' && alnum(s)) {
			break
		}
		if variants[s] {
			return false
		}
		variants[s] = true
		i++
	}
	extensions := map[string]bool{}
	for i < len(parts) {
		s := parts[i]
		if s == "x" {
			return private(i + 1)
		}
		if len(s) != 1 || !alnum(s) || extensions[s] {
			return false
		}
		extensions[s] = true
		i++
		start := i
		for i < len(parts) && len(parts[i]) >= 2 && len(parts[i]) <= 8 && alnum(parts[i]) {
			i++
		}
		if i == start {
			return false
		}
	}
	return true
}
