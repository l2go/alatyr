// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Command alatyr-live runs the authored world and its gateway as WebAssembly in
// a browser worker, with a visitor the page controls and an agent that plays by
// itself, both speaking alatyr/1 to the same world. Build it with
// GOOS=js GOARCH=wasm. The worker script loads it with Go's wasm_exec.js and
// passes the rendered world as a zip archive.
package main
