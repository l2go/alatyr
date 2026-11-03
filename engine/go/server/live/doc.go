// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package live hosts the authored Go world and its participant gateway in one
// process, without a network or a database, and lets participants join it over
// in-memory alatyr/1 streams. It composes the world exactly as the server does,
// through goworld, so a participant here gets the same outcomes and the same
// bounded view. The browser host in cmd/alatyr-live runs it as WebAssembly.
//
// A Session is a minimal alatyr/1 participant: it says Hello, follows the
// checkpoint and live facts, answers pings, and turns an Action into one
// numbered intent. It reports what it sees and every result through a
// callback. An Agent drives a Session by itself.
package live
