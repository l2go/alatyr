// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package gateway binds bounded participant sessions to replaceable worlds.
// Each session owns a loop that serializes State operations and World writes.
// Policy and backend implementations are supplied by the caller; this package
// does not define a game dictionary, connect to a world, or start a server.
// Callers must close the Gateway to stop and join its session and transport loops.
package gateway
