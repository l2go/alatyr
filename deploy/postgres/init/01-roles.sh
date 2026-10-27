#!/bin/bash
# The official PostgreSQL entrypoint runs this once on a fresh cluster.
set -euo pipefail
password="$(cat /run/alatyr-dbsecrets/world_password)"
psql -v ON_ERROR_STOP=1 -v world_password="$password" \
 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE EXTENSION citext SCHEMA public;
CREATE ROLE alatyr LOGIN PASSWORD :'world_password';
CREATE SCHEMA world AUTHORIZATION alatyr;
REVOKE ALL ON DATABASE alatyr FROM PUBLIC;
GRANT CONNECT ON DATABASE alatyr TO alatyr;
ALTER ROLE alatyr SET search_path = world, public;
SQL
