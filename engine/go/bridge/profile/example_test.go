// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package profile_test

import (
	"errors"
	"fmt"
	"strings"

	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// A frame round-trips through the profile codec, which checks every record
// against bridge/1 as well as the shared wire rules.
func ExampleCodec() {
	c, err := profile.NewProfileCodec()
	if err != nil {
		fmt.Println(err)
		return
	}
	frame, err := c.Encode([]wire.Record{{Type: wire.Ping, Fields: []wire.Field{wire.U(1, true, 7)}}})
	if err != nil {
		fmt.Println(err)
		return
	}
	records, err := c.Decode(frame)
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println("ping nonce:", profile.U(records[0], 1))
	// Output: ping nonce: 7
}

// An Open that asks only for a later profile version is well formed but
// unsupported; the codec refuses to encode it.
func ExampleCodec_Encode_unsupported() {
	c, err := profile.NewProfileCodec()
	if err != nil {
		fmt.Println(err)
		return
	}
	open := wire.Record{Type: profile.Open, Fields: []wire.Field{
		wire.U(1, true, 2), wire.U(2, true, 2),
		wire.Data(3, true, profile.Name),
		wire.Data(4, true, strings.Repeat("s", 32)),
		wire.Data(5, true, "account"), wire.Data(6, true, "Character"),
		wire.U(7, true, 1),
	}}
	_, err = c.Encode([]wire.Record{open})
	fmt.Println("unsupported:", errors.Is(err, wire.ErrUnsupported))
	// Output: unsupported: true
}
