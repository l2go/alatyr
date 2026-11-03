// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Command alatyr-site builds the project site from the repository: the pages
// and notes under site/, the alatyr/1 reference from the participant
// dictionary, the releases from CHANGELOG.md, and the rendered world the live
// demo loads. Facts that live in code are generated, never written by hand.
//
//	alatyr-site -root ../../../.. -out _site
//
// The live demo's WebAssembly and Go's wasm_exec.js are built and copied into
// live/ by the caller.
package main
