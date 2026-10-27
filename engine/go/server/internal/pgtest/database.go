// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgtest

import (
	"context"
	"net/url"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
)

// Database creates a disposable PostgreSQL 18 database and registers cleanup.
func Database(t *testing.T) string {
	t.Helper()
	dsn := os.Getenv("ALATYR_TEST_POSTGRES_DSN")
	if dsn == "" {
		if os.Getenv("ALATYR_REQUIRE_POSTGRES") == "1" {
			t.Fatal("PostgreSQL is required but ALATYR_TEST_POSTGRES_DSN is not set")
		}
		t.Skip("ALATYR_TEST_POSTGRES_DSN is not set")
	}
	uri, err := url.Parse(dsn)
	if err != nil || uri.Scheme != "postgres" && uri.Scheme != "postgresql" {
		t.Fatal("test DSN must be a PostgreSQL URL")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	admin, err := pgx.Connect(ctx, dsn)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := admin.Close(ctx); err != nil {
			t.Error(err)
		}
	})
	var version int
	if err := admin.QueryRow(ctx, "SELECT current_setting('server_version_num')::integer").Scan(&version); err != nil || version/10000 != 18 {
		t.Fatalf("PostgreSQL version: got %d/%v, want 18", version, err)
	}
	var name string
	if err := admin.QueryRow(ctx, "SELECT 'alatyr_test_' || pg_backend_pid() || '_' || floor(extract(epoch FROM clock_timestamp())*1000000)::bigint").Scan(&name); err != nil {
		t.Fatal(err)
	}
	if _, err := admin.Exec(ctx, "CREATE DATABASE "+pgx.Identifier{name}.Sanitize()); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if _, err := admin.Exec(ctx, "DROP DATABASE "+pgx.Identifier{name}.Sanitize()+" WITH (FORCE)"); err != nil {
			t.Error(err)
		}
	})
	uri.Path = "/" + name
	return uri.String()
}
