#!/bin/bash
# The official PostgreSQL entrypoint runs this once on a fresh cluster.
set -euo pipefail
ALATYR_WORLD_PASSWORD="$(cat /run/alatyr-dbsecrets/world_password)" \
psql -v ON_ERROR_STOP=1 \
 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv world_password ALATYR_WORLD_PASSWORD
CREATE EXTENSION citext SCHEMA public;
CREATE ROLE alatyr LOGIN PASSWORD :'world_password';
CREATE SCHEMA world AUTHORIZATION alatyr;
REVOKE ALL ON DATABASE alatyr FROM PUBLIC;
GRANT CONNECT ON DATABASE alatyr TO alatyr;
ALTER ROLE alatyr SET search_path = world, public;
SQL
