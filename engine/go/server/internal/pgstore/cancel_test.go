// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgstore

import (
	"context"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
)

// The store owns one leased connection and never reconnects. A request whose
// deadline passes inside a transaction must not take that connection with it:
// the 32-bot soak lost the database for the rest of the run when a login burst
// outlasted the request deadline, and every later save failed.
func TestCanceledRequestKeepsTheConnection(t *testing.T) {
	for _, tc := range []struct {
		name string
		run  func(*Store, context.Context) error
	}{
		{"write", func(s *Store, ctx context.Context) error {
			return s.write(ctx, func(tx pgx.Tx) error {
				_, err := tx.Exec(ctx, "SELECT pg_sleep(5)")
				return err
			})
		}},
		{"read", func(s *Store, ctx context.Context) error {
			_, err := s.Characters(ctx, "example") // blocks on the held lock until the deadline
			return err
		}},
		{"expired", func(s *Store, ctx context.Context) error {
			return s.write(ctx, func(tx pgx.Tx) error {
				<-ctx.Done() // the work outlives the caller without an in-flight query
				return nil
			})
		}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dsn := database(t)
			s := openTest(t, dsn)
			release := func() {}
			if tc.name == "read" {
				// Another session holds the table so the deadline passes mid-query.
				locker, err := pgx.Connect(t.Context(), dsn)
				if err != nil {
					t.Fatal(err)
				}
				defer locker.Close(context.Background())
				tx, err := locker.Begin(t.Context())
				if err != nil {
					t.Fatal(err)
				}
				if _, err := tx.Exec(t.Context(), "LOCK TABLE world.player IN ACCESS EXCLUSIVE MODE"); err != nil {
					t.Fatal(err)
				}
				release = func() { _ = tx.Rollback(context.Background()) }
				defer release()
			}
			ctx, cancel := context.WithTimeout(t.Context(), 100*time.Millisecond)
			defer cancel()
			if err := tc.run(s, ctx); err == nil {
				t.Fatal("the canceled request succeeded")
			}
			release()
			if err := s.Ping(t.Context()); err != nil {
				t.Fatalf("connection lost after a canceled %s: %v", tc.name, err)
			}
			if _, err := s.Characters(t.Context(), "example"); err != nil {
				t.Fatalf("store unusable after a canceled %s: %v", tc.name, err)
			}
		})
	}
}
