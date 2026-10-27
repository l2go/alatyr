// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package pgstore

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/l2go/alatyr/engine/go/world"
)

// Store owns one database connection and its lifetime advisory lease. Construct
// it with Open and close it after all world departures have completed.
// Methods are safe for concurrent use; database I/O never runs in World.Run.
type Store struct {
	conn     *pgx.Conn
	gate     chan struct{}
	once     sync.Once
	closed   bool
	closeErr error
}

// Open acquires exclusive Go-server ownership and returns the leased storage connection. The caller applies migrations
// before reading or writing player state. It never reconnects after failure.
// Java servers and other writers must be stopped before opening this store.
func Open(ctx context.Context, dsn string) (*Store, error) {
	cfg, err := pgx.ParseConfig(dsn)
	if err != nil {
		return nil, errors.New("pgstore: invalid database configuration")
	}
	cfg.RuntimeParams["search_path"] = "world,public"
	cfg.RuntimeParams["application_name"] = "alatyr-go"
	conn, err := pgx.ConnectConfig(ctx, cfg)
	if err != nil {
		return nil, errors.New("pgstore: connect failed")
	}
	s := &Store{conn: conn, gate: make(chan struct{}, 1)}
	var owned bool
	if err = conn.QueryRow(ctx, "SELECT pg_try_advisory_lock(726304190151)").Scan(&owned); err == nil && !owned {
		err = errors.New("database already owned")
	}
	if err != nil {
		return nil, errors.Join(fmt.Errorf("pgstore: open: %w", err), s.Close())
	}
	return s, nil
}

func (s *Store) acquire(ctx context.Context) error {
	if s.gate == nil {
		return errors.New("pgstore: uninitialized")
	}
	select {
	case s.gate <- struct{}{}:
	case <-ctx.Done():
		return ctx.Err()
	}
	if err := ctx.Err(); err != nil {
		<-s.gate
		return err
	}
	if s.closed {
		<-s.gate
		return errors.New("pgstore: closed")
	}
	return nil
}
func (s *Store) release() { <-s.gate }

// Close releases the lease and connection once. The returned error is stable.
func (s *Store) Close() error {
	s.once.Do(func() {
		if s.gate == nil {
			return
		}
		s.gate <- struct{}{}
		defer s.release()
		s.closed = true
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		s.closeErr = s.conn.Close(ctx)
	})
	return s.closeErr
}

// Ping verifies the same leased connection used for every read and write.
func (s *Store) Ping(ctx context.Context) error {
	if err := s.acquire(ctx); err != nil {
		return err
	}
	defer s.release()
	return s.conn.Ping(ctx)
}

func (s *Store) write(ctx context.Context, f func(pgx.Tx) error) error {
	if err := s.acquire(ctx); err != nil {
		return err
	}
	defer s.release()
	if err := pgx.BeginFunc(ctx, s.conn, f); err != nil {
		return fmt.Errorf("pgstore: transaction: %w", err)
	}
	return nil
}

// Characters returns one consistent, detached account snapshot, including
// unknown item templates and skills. Unsupported database tables are not read.
func (s *Store) Characters(ctx context.Context, account string) ([]world.Character, error) {
	if err := s.acquire(ctx); err != nil {
		return nil, err
	}
	defer s.release()
	var chars []world.Character
	err := pgx.BeginTxFunc(ctx, s.conn, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly}, func(tx pgx.Tx) error {
		var err error
		chars, err = readCharacters(ctx, tx, account)
		return err
	})
	if err != nil {
		return nil, fmt.Errorf("pgstore: characters: %w", err)
	}
	return chars, nil
}

// PutLearning writes all learned base-class skills and the captured SP balance
// together. It sets an absolute balance, so an ambiguous commit can be retried
// with the same snapshot without charging twice. World owns incarnation fencing.
func (s *Store) PutLearning(ctx context.Context, c world.Character) error {
	if err := validateCharacter(c); err != nil {
		return err
	}
	return s.write(ctx, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, "UPDATE player SET sp=$2 WHERE id=$1 AND account_name=$3", c.ObjectID, c.SP, c.Account)
		if err != nil {
			return err
		}
		if tag.RowsAffected() != 1 {
			return errors.New("pgstore: learning owner missing")
		}
		for _, k := range c.Skills {
			if _, err := tx.Exec(ctx, `INSERT INTO player_skill (player_id,class_index,skill_id,skill_level) VALUES ($1,0,$2,$3)
    ON CONFLICT (player_id,class_index,skill_id) DO UPDATE SET skill_level=EXCLUDED.skill_level`, c.ObjectID, k.ID, k.Level); err != nil {
				return err
			}
		}
		return nil
	})
}

// UsedIDs returns every persisted object identity, including unsupported Java
// tables when present, so startup cannot collide with offline objects.
func (s *Store) UsedIDs(ctx context.Context) ([]int32, error) {
	if err := s.acquire(ctx); err != nil {
		return nil, err
	}
	defer s.release()
	var ids []int32
	for _, table := range []string{"player", "item", "clan", "crest", "couple", "ground_item", "player_shortcut"} {
		var exists bool
		if err := s.conn.QueryRow(ctx, "SELECT to_regclass($1) IS NOT NULL", "world."+table).Scan(&exists); err != nil {
			return nil, err
		}
		if !exists {
			continue
		}
		query := "SELECT id FROM " + table
		if table == "player_shortcut" {
			query = "SELECT target_id FROM player_shortcut WHERE shortcut_type_id=1 AND target_id>0"
		}
		if table == "clan" {
			query += " UNION ALL SELECT crest_id FROM clan WHERE crest_id IS NOT NULL UNION ALL SELECT large_crest_id FROM clan WHERE large_crest_id IS NOT NULL UNION ALL SELECT alliance_crest_id FROM clan WHERE alliance_crest_id IS NOT NULL"
		}
		rows, err := s.conn.Query(ctx, query)
		if err != nil {
			return nil, err
		}
		got, err := pgx.CollectRows(rows, pgx.RowTo[int32])
		if err != nil {
			return nil, err
		}
		ids = append(ids, got...)
	}
	return ids, nil
}

// ReserveIDs burns a bounded range before the world starts. Unused identities
// are never reused after a restart. The world must stop on range exhaustion.
func (s *Store) ReserveIDs(ctx context.Context) (first, last int32, err error) {
	err = s.write(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `UPDATE go_object_id SET high_water=high_water+1048576
   WHERE singleton AND high_water <= 2146435071 RETURNING high_water-1048575,high_water`).Scan(&first, &last)
	})
	return first, last, err
}

// Character resolves both names using PostgreSQL citext in one consistent
// snapshot, returning the database spelling rather than a host-side case fold.
func (s *Store) Character(ctx context.Context, account, name string) (world.Character, error) {
	if err := s.acquire(ctx); err != nil {
		return world.Character{}, err
	}
	defer s.release()
	var found world.Character
	err := pgx.BeginTxFunc(ctx, s.conn, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly}, func(tx pgx.Tx) error {
		var id int32
		if err := tx.QueryRow(ctx, "SELECT id FROM player WHERE account_name=$1 AND name=$2", account, name).Scan(&id); err != nil {
			return err
		}
		chars, err := readCharacters(ctx, tx, account)
		if err != nil {
			return err
		}
		for _, c := range chars {
			if c.ObjectID == id {
				found = c
				return nil
			}
		}
		return pgx.ErrNoRows
	})
	if err != nil {
		return world.Character{}, fmt.Errorf("pgstore: character: %w", err)
	}
	return found, nil
}
