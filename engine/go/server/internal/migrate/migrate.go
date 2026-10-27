// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package migrate

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"log/slog"

	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
)

//go:embed migrations/*.sql
var migrations embed.FS

// Apply accepts an empty database or this application's own schema and runs
// forward PostgreSQL migrations. The caller must hold the database lease.
func Apply(ctx context.Context, dsn string) (result error) {
	providerDriver, ok := stdlib.GetDefaultDriver().(driver.DriverContext)
	if !ok {
		return errors.New("migrate: PostgreSQL connector unavailable")
	}
	connector, err := providerDriver.OpenConnector(dsn)
	if err != nil {
		return errors.New("migrate: invalid database configuration")
	}
	db := sql.OpenDB(connector)
	db.SetMaxOpenConns(1)
	defer func() { result = errors.Join(result, db.Close()) }()
	if err := db.PingContext(ctx); err != nil {
		return errors.New("migrate: connect failed")
	}

	var own, schema bool
	if err := db.QueryRowContext(ctx, "SELECT to_regclass('world.goose_db_version') IS NOT NULL,to_regnamespace('world') IS NOT NULL").Scan(&own, &schema); err != nil {
		return err
	}
	if err := validateSchema(ctx, db, own); err != nil {
		return err
	}
	if own {
		var unsupported bool
		if err := db.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM world.goose_db_version WHERE version_id NOT IN (0,1,2) OR NOT is_applied)").Scan(&unsupported); err != nil {
			return err
		}
		if unsupported {
			return errors.New("migrate: unsupported goose history")
		}
	}
	if !schema {
		if _, err := db.ExecContext(ctx, "CREATE SCHEMA world"); err != nil {
			return err
		}
	}
	files, err := fs.Sub(migrations, "migrations")
	if err != nil {
		return err
	}
	provider, err := goose.NewProvider(goose.DialectPostgres, db, files, goose.WithTableName("world.goose_db_version"), goose.WithDisableGlobalRegistry(true), goose.WithSlog(slog.Default()))
	if err != nil {
		return err
	}
	if _, err := provider.Up(ctx); err != nil {
		return fmt.Errorf("migrate: goose up: %w", err)
	}
	// Include dangling item shortcuts as well as actual objects on every start.
	_, err = db.ExecContext(ctx, `UPDATE world.go_object_id SET high_water=greatest(high_water,
  coalesce((SELECT max(id) FROM world.player),0),coalesce((SELECT max(id) FROM world.item),0),
  coalesce((SELECT max(target_id) FROM world.player_shortcut WHERE shortcut_type_id=1 AND target_id>0),0))`)
	return err
}

// validateSchema refuses foreign objects before Provider can create its history.
// Extension-owned citext objects are allowed; application tables, their indexes
// and owned sequences are allowed only with an existing application history.
func validateSchema(ctx context.Context, db *sql.DB, own bool) error {
	var foreign bool
	err := db.QueryRowContext(ctx, `WITH own_tables AS (
 SELECT c.oid FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE $1 AND n.nspname='world' AND c.relkind='r' AND c.relname=ANY($2)
 ) SELECT
 EXISTS(SELECT 1 FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname NOT IN ('information_schema','public','world'))
 OR EXISTS(SELECT 1 FROM pg_extension WHERE extname NOT IN ('plpgsql','citext'))
 OR EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname !~ '^pg_' AND n.nspname<>'information_schema'
  AND NOT (c.oid IN(SELECT oid FROM own_tables)
   OR c.relkind='i' AND EXISTS(SELECT 1 FROM pg_index i WHERE i.indexrelid=c.oid AND i.indrelid IN(SELECT oid FROM own_tables))
   OR c.relkind='S' AND EXISTS(SELECT 1 FROM pg_depend d WHERE d.objid=c.oid AND d.classid='pg_class'::regclass AND d.refclassid='pg_class'::regclass AND d.deptype IN ('a','i') AND d.refobjid IN(SELECT oid FROM own_tables))))
 OR EXISTS(SELECT 1 FROM pg_largeobject_metadata)
 OR EXISTS(SELECT 1 FROM pg_depend d JOIN pg_namespace n ON n.oid=d.refobjid
  WHERE d.refclassid='pg_namespace'::regclass AND n.nspname IN ('world','public')
  AND d.classid NOT IN ('pg_class'::regclass,'pg_proc'::regclass,'pg_type'::regclass,'pg_extension'::regclass)
  AND NOT EXISTS(SELECT 1 FROM pg_depend e WHERE e.classid=d.classid AND e.objid=d.objid AND e.deptype='e'))
 OR EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname !~ '^pg_' AND n.nspname<>'information_schema'
  AND NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid='pg_proc'::regclass AND d.objid=p.oid AND d.deptype='e'))
 OR EXISTS(SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
  WHERE n.nspname !~ '^pg_' AND n.nspname<>'information_schema' AND t.typrelid=0 AND t.typelem=0
  AND NOT EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid='pg_type'::regclass AND d.objid=t.oid AND d.deptype='e'))`,
		own, []string{"goose_db_version", "go_object_id", "player", "item", "player_skill", "player_shortcut", "player_quest_variable", "player_name_title_color", "player_recommendation_status", "player_birthday"}).Scan(&foreign)
	if err != nil {
		return err
	}
	if foreign {
		return errors.New("migrate: foreign database objects; use a fresh dedicated database")
	}
	return nil
}
