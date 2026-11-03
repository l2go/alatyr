// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

//go:build !(js && wasm)

package main

import (
	"fmt"
	"os"
)

func main() {
	fmt.Fprintln(os.Stderr, "alatyr-live runs only as WebAssembly: GOOS=js GOARCH=wasm")
	os.Exit(2)
}
