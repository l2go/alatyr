#!/bin/bash
# Creates the roles and schemas of Platform 3.0 on the first start of an empty
# database. The official image runs this script once, as the database superuser.
# Each module owns one schema and connects with its own role, so a module cannot
# read the data of another.
set -euo pipefail

secrets=/run/l2jfree-secrets
login_password="$(< "$secrets/login_password")"
world_password="$(< "$secrets/world_password")"

psql -v ON_ERROR_STOP=1 -v login_password="$login_password" -v world_password="$world_password" \
	--username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
-- Case-insensitive text for login names. The migrations rely on it and the
-- module roles do not need the right to create extensions.
CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;
-- Counts every statement of the database; an operator reads it with SELECT ... FROM pg_stat_statements.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements SCHEMA public;

-- The login module: accounts and session data. Owns the schema "login".
CREATE ROLE l2jfree_login LOGIN PASSWORD :'login_password';
CREATE SCHEMA login AUTHORIZATION l2jfree_login;
ALTER ROLE l2jfree_login SET search_path = login, public;

-- The world module: game state, game content, and the views for people. The role
-- owns three schemas: "world" (state, the backup), "catalog" (content rebuilt from
-- the image, UNLOGGED tables) and "report" (read-only views). Owning the schemas is
-- enough to create, alter, and drop tables and views in them, and advisory locks
-- need no right. The server migrates "world" and reloads "catalog" at start, so
-- it needs no right to create a schema or an extension.
CREATE ROLE l2jfree_world LOGIN PASSWORD :'world_password';
CREATE SCHEMA world AUTHORIZATION l2jfree_world;
CREATE SCHEMA catalog AUTHORIZATION l2jfree_world;
CREATE SCHEMA report AUTHORIZATION l2jfree_world;
ALTER ROLE l2jfree_world SET search_path = world, catalog, public;

-- What the roles must not have, and why nothing is granted here: a module
-- reaches only the schemas it owns. The world role has no right on
-- "login", and the login role has none on "world", "catalog", or "report". Roles
-- that do not own a schema get no right on it by default, so there is no REVOKE.
-- Neither role is a superuser and neither may create schemas or extensions.
-- The read-only role "l2_readonly" for people is created by the operator; the
-- server grants it the views when it exists.
SQL
