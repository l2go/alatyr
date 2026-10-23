// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 l2go

package wire_test

import (
	"bytes"
	"fmt"
	"log"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func exampleCodec() *wire.Codec {
	limits := wire.Limits{
		FrameBytes: 4096, StringBytes: 256, ListItems: 16, Depth: 4, Records: 8, Fields: 16,
		IdentifierBytes: 64, WorldBytes: 64, ClientBytes: 64,
	}
	c, err := wire.New(limits, wire.Envelopes(wire.Schema{1: wire.Required(wire.UInt)}, limits))
	if err != nil {
		log.Fatal(err)
	}
	return c
}

// A Codec writes and reads length-prefixed frames of records.
func ExampleCodec() {
	c := exampleCodec()
	var conn bytes.Buffer
	ping := wire.Record{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 7)}}
	if err := c.Write(&conn, []wire.Record{ping}); err != nil {
		log.Fatal(err)
	}
	records, err := c.Read(&conn)
	if err != nil {
		log.Fatal(err)
	}
	nonce, _ := wire.Find(records[0].Fields, 1)
	fmt.Println(records[0].Type == wire.Ping, nonce.U)
	// Output: true 7
}

// An unknown essential record kind is rejected, so a receiver never acts on
// meaning it does not understand.
func ExampleCodec_Decode_unsupported() {
	c := exampleCodec()
	_, err := c.Decode([]byte{100, 0}) // kind 100 is even and unknown
	fmt.Println(err)
	// Output: unsupported essential meaning
}
