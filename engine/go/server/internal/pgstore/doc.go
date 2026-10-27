// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package pgstore persists detached Go-world snapshots in PostgreSQL.
// One connection holds the server lease and serializes context-bounded I/O.
// Losing that connection cannot silently reacquire ownership or resume writes.
package pgstore
