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
	"slices"

	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
)

//go:embed migrations/*.sql
var migrations embed.FS

// Apply validates an empty or complete Flyway baseline and runs only forward
// PostgreSQL migrations. The caller must hold the Go-server database lease.
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

	var own, flyway, player bool
	if err := db.QueryRowContext(ctx, `SELECT to_regclass('world.goose_db_version') IS NOT NULL,
  to_regclass('world.flyway_schema_history') IS NOT NULL,to_regclass('world.player') IS NOT NULL`).Scan(&own, &flyway, &player); err != nil {
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
	if !own {
		if player || flyway {
			if !player || !flyway {
				return errors.New("migrate: incomplete Flyway baseline")
			}
			rows, err := db.QueryContext(ctx, `SELECT version FROM world.flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank`)
			if err != nil {
				return err
			}
			var versions []string
			for rows.Next() {
				var version string
				if err := rows.Scan(&version); err != nil {
					return errors.Join(err, rows.Close())
				}
				versions = append(versions, version)
			}
			if err := errors.Join(rows.Err(), rows.Close()); err != nil {
				return err
			}
			want := []string{"1.01", "1.02", "1.03", "1.04", "1.05", "1.06", "1.07", "1.08", "1.09", "1.10"}
			if !slices.Equal(versions, want) {
				return errors.New("migrate: unsupported Flyway baseline")
			}
			var failed bool
			if err := db.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM world.flyway_schema_history WHERE NOT success)").Scan(&failed); err != nil {
				return err
			}
			if failed {
				return errors.New("migrate: failed Flyway migration")
			}
			for _, query := range []string{
				"SELECT id,account_name,name,active_class_id,base_class_id,race_id,is_female,hair_style,hair_color,face,level,exp,sp,max_hp,max_cp,max_mp,current_hp,current_cp,current_mp,x,y,z,heading,vitality_points,last_access_at,death_penalty_level FROM world.player LIMIT 0",
				"SELECT owner_player_id,id,item_template_id,count,location,location_slot,owner_clan_id,owner_pet_id FROM world.item LIMIT 0",
				"SELECT player_id,class_index,skill_id,skill_level FROM world.player_skill LIMIT 0",
				"SELECT player_id,class_index,page,slot,shortcut_type_id,target_id,level FROM world.player_shortcut LIMIT 0",
				"SELECT player_id,quest_name,variable_name,value FROM world.player_quest_variable LIMIT 0",
				"SELECT player_id,name_color,title_color FROM world.player_name_title_color LIMIT 0",
				"SELECT player_id,recommendations_left,updated_at FROM world.player_recommendation_status LIMIT 0",
				"SELECT player_id,created_on,gift_claimed_year FROM world.player_birthday LIMIT 0",
			} {
				rows, err := db.QueryContext(ctx, query)
				if err != nil {
					return fmt.Errorf("migrate: incomplete Flyway schema: %w", err)
				}
				if err := rows.Close(); err != nil {
					return err
				}
				if rows.Err() != nil {
					return rows.Err()
				}
			}
		} else {
			var occupied bool
			if err := db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='world')`).Scan(&occupied); err != nil {
				return err
			}
			if occupied {
				return errors.New("migrate: unrecognized world schema")
			}
		}
	}
	if _, err := db.ExecContext(ctx, "CREATE SCHEMA IF NOT EXISTS world"); err != nil {
		return err
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
	// Account for identities in unsupported tables before reserving new ranges.
	for _, table := range []string{"player", "item", "clan", "crest", "couple", "ground_item"} {
		var exists bool
		if err := db.QueryRowContext(ctx, "SELECT to_regclass($1) IS NOT NULL", "world."+table).Scan(&exists); err != nil {
			return err
		}
		if !exists {
			continue
		}
		maximum := "coalesce((SELECT max(id) FROM world." + table + "),0)"
		if table == "clan" {
			maximum += ",coalesce((SELECT max(crest_id) FROM world.clan),0),coalesce((SELECT max(large_crest_id) FROM world.clan),0),coalesce((SELECT max(alliance_crest_id) FROM world.clan),0)"
		}
		if _, err := db.ExecContext(ctx, "UPDATE world.go_object_id SET high_water=greatest(high_water,"+maximum+")"); err != nil {
			return err
		}
	}
	return nil
}
