// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package backend adapts an opened bridge.Port to the participant gateway.
// Receipts gate dictionary witnesses; they never supply participant Done.
// A Session owns one raw port across explicit recovery. Hosts bound their
// identity registry to at most 64 sessions and close it during shutdown.
package backend
