// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway_test

import (
	"fmt"
	"net"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
)

func ExampleStream() {
	a, b := net.Pipe()
	reader := gateway.Stream{Conn: a, Limit: 1024}
	writer := gateway.Stream{Conn: b, Limit: 1024}
	written := make(chan error, 1)
	go func() { written <- writer.Write([]byte("hello"), time.Now().Add(time.Second)) }()
	body, err := reader.Read(time.Now().Add(time.Second))
	writeErr := <-written
	fmt.Println(string(body), err, writeErr)
	fmt.Println(reader.Close(), writer.Close())
	// Output:
	// hello <nil> <nil>
	// <nil> <nil>
}
