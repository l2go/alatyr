// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import "net"

func pipe() (net.Conn, net.Conn) { return net.Pipe() }
