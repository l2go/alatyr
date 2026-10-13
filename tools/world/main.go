// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"flag"
	"fmt"
	"io"
	"os"

	"github.com/l2go/l2jfree/tools/world/internal/world"
)

const usage = `world renders catalog and data from keyed sources.

Usage:
  world render --world <dir> [--locale en]
  world check --world <dir> [--locale en]

render writes <dir>/catalog and <dir>/data from <dir>/src and <dir>/i18n/<locale>.tsv.
check exits 0 when those files match a fresh render, every key is defined and used,
and every text is printable ASCII.

--world is required. --locale defaults to en, and only en is accepted.
`

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, stdout, stderr io.Writer) int {
	if len(args) == 0 {
		fmt.Fprint(stdout, usage)
		return 0
	}
	for _, arg := range args {
		if arg == "-h" || arg == "--help" || arg == "help" {
			fmt.Fprint(stdout, usage)
			return 0
		}
	}
	cmd := args[0]
	if cmd != "render" && cmd != "check" {
		fmt.Fprintf(stderr, "world:1: unknown command %q\n", cmd)
		fmt.Fprint(stderr, usage)
		return 1
	}
	fs := flag.NewFlagSet(cmd, flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	dir := fs.String("world", "", "world directory")
	locale := fs.String("locale", "en", "locale, only en")
	if err := fs.Parse(args[1:]); err != nil || fs.NArg() != 0 || *dir == "" {
		fmt.Fprint(stderr, usage)
		return 1
	}
	var diags []world.Diagnostic
	if cmd == "render" {
		diags = world.Render(*dir, *locale)
	} else {
		diags = world.Check(*dir, *locale)
	}
	for _, d := range diags {
		fmt.Fprintln(stderr, d.String())
	}
	if len(diags) > 0 {
		return 1
	}
	return 0
}
