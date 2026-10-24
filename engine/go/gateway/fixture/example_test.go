// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package fixture_test

import (
	"context"
	"fmt"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/fixture"
)

func ExampleNew() {
	w, err := fixture.New(fixture.Config{})
	if err != nil {
		fmt.Println(err)
		return
	}
	defer w.Close()
	n, err := w.Write(context.Background(), bridge.Command{Key: 1, Verb: 9})
	if err != nil {
		fmt.Println(err)
		return
	}
	fmt.Println(n, len(w.Writes()))
	// Output: 1 1
}
