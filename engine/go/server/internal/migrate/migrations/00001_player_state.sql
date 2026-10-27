-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Copyright 2026 l2go
-- +goose Up
-- Empty databases receive only the state used by the authored Go world.
-- Startup refuses foreign schemas before Provider.Up creates its history.
CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;
CREATE TABLE IF NOT EXISTS world.player (
 id integer PRIMARY KEY CHECK (id > 0), account_name public.citext NOT NULL,
 name public.citext NOT NULL UNIQUE, race_id smallint NOT NULL,
 active_class_id smallint NOT NULL, base_class_id smallint NOT NULL,
 is_female boolean NOT NULL DEFAULT false, face smallint NOT NULL DEFAULT 0,
 hair_style smallint NOT NULL DEFAULT 0, hair_color smallint NOT NULL DEFAULT 0,
 level smallint NOT NULL CHECK (level >= 1), exp bigint NOT NULL DEFAULT 0 CHECK (exp >= 0),
 sp integer NOT NULL DEFAULT 0 CHECK (sp >= 0),
 max_hp integer NOT NULL DEFAULT 0 CHECK (max_hp >= 0), current_hp integer NOT NULL DEFAULT 0 CHECK (current_hp >= 0),
 max_cp integer NOT NULL DEFAULT 0 CHECK (max_cp >= 0), current_cp integer NOT NULL DEFAULT 0 CHECK (current_cp >= 0),
 max_mp integer NOT NULL DEFAULT 0 CHECK (max_mp >= 0), current_mp integer NOT NULL DEFAULT 0 CHECK (current_mp >= 0),
 x integer NOT NULL DEFAULT 0, y integer NOT NULL DEFAULT 0, z integer NOT NULL DEFAULT 0, heading integer NOT NULL DEFAULT 0,
 last_access_at timestamptz, vitality_points integer NOT NULL DEFAULT 0 CHECK (vitality_points >= 0),
 death_penalty_level smallint NOT NULL DEFAULT 0 CHECK (death_penalty_level >= 0)
);
CREATE TABLE IF NOT EXISTS world.item (
 id integer PRIMARY KEY CHECK (id > 0), item_template_id integer NOT NULL,
 owner_player_id integer REFERENCES world.player ON DELETE CASCADE,
 owner_clan_id integer, owner_pet_id integer,
 location text NOT NULL CHECK (location IN ('INVENTORY','PAPERDOLL','WAREHOUSE','CLANWH','PET','PET_EQUIP','FREIGHT')),
 location_slot integer NOT NULL DEFAULT 0, count bigint NOT NULL CHECK (count > 0)
);
CREATE INDEX IF NOT EXISTS item_owner_player_id_location_idx ON world.item(owner_player_id,location);
CREATE TABLE IF NOT EXISTS world.player_skill (
 player_id integer NOT NULL REFERENCES world.player ON DELETE CASCADE,
 class_index smallint NOT NULL CHECK (class_index >= 0), skill_id integer NOT NULL, skill_level smallint NOT NULL,
 PRIMARY KEY(player_id,class_index,skill_id)
);
CREATE TABLE IF NOT EXISTS world.player_shortcut (
 player_id integer NOT NULL REFERENCES world.player ON DELETE CASCADE,
 class_index smallint NOT NULL CHECK (class_index >= 0), page smallint NOT NULL CHECK (page >= 0),
 slot smallint NOT NULL CHECK (slot >= 0), shortcut_type_id smallint NOT NULL, target_id integer NOT NULL, level smallint NOT NULL,
 PRIMARY KEY(player_id,class_index,page,slot)
);
CREATE TABLE IF NOT EXISTS world.player_quest_variable (
 player_id integer NOT NULL REFERENCES world.player ON DELETE CASCADE,
 quest_name text NOT NULL, variable_name varchar(20) NOT NULL, value varchar(255) NOT NULL DEFAULT '',
 PRIMARY KEY(player_id,quest_name,variable_name)
);
CREATE TABLE IF NOT EXISTS world.player_name_title_color (
 player_id integer PRIMARY KEY REFERENCES world.player ON DELETE CASCADE,
 name_color varchar(6) NOT NULL CHECK (name_color ~ '^[0-9A-Fa-f]{6}$'),
 title_color varchar(6) NOT NULL CHECK (title_color ~ '^[0-9A-Fa-f]{6}$')
);
CREATE TABLE IF NOT EXISTS world.player_recommendation_status (
 player_id integer PRIMARY KEY REFERENCES world.player ON DELETE CASCADE,
 recommendations_left smallint NOT NULL DEFAULT 3 CHECK (recommendations_left >= 0),
 recommendations_received smallint NOT NULL DEFAULT 0 CHECK (recommendations_received >= 0), updated_at timestamptz NOT NULL
);
CREATE TABLE IF NOT EXISTS world.player_birthday (
 player_id integer PRIMARY KEY REFERENCES world.player ON DELETE CASCADE, created_on date NOT NULL, gift_claimed_year smallint NOT NULL
);
-- Forward-only: rollback restores a database backup, never drops player data.
