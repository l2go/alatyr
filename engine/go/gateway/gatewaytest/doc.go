// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

// Package gatewaytest drives the actual gateway participant boundary.
// Tests supply the same trusted factory and dictionary policy as their host.
// The driver records every decoded record in order; it does not filter facts,
// manufacture outcomes, or interpret a raw receipt as participant success.
package gatewaytest
