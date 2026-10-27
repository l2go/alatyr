// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgstore_test

import (
	"context"
	"fmt"

	"github.com/l2go/alatyr/engine/go/server/internal/pgstore"
)

// Invalid connection configuration is rejected before any network operation.
// The returned error does not echo connection credentials.
func ExampleOpen() {
	_, err := pgstore.Open(context.Background(), "postgres://operator:password@%invalid/database")
	fmt.Println(err)
	// Output: pgstore: invalid database configuration
}
