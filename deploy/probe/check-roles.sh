#!/usr/bin/env bash
# Checks the boundary between the two modules at the database: each module role reaches the schemas it owns
# and nothing else, neither role is a superuser, and neither may create a schema or an extension. It runs in the
# database container of a started stack, after the server has migrated both schemas.
#
#   deploy/probe/check-roles.sh [compose file]
set -euo pipefail

compose_file="${1:-$(cd "$(dirname "$0")/.." && pwd)/compose.yaml}"
failures=0

# as ROLE SQL: runs the statement as the role l2jfree_<ROLE>, with the password the init script generated, and prints
# what psql prints, errors included.
as() {
	docker compose -f "$compose_file" exec -T db bash -c \
		'PGPASSWORD=$(cat "/run/l2jfree-secrets/$1_password") psql -h 127.0.0.1 -U "l2jfree_$1" -d l2jfree -At -c "$2" 2>&1 || true' \
		_ "$1" "$2"
}

allowed() {
	local role=$1 sql=$2 out
	out=$(as "$role" "$sql")
	if [[ $out == *"ERROR"* ]]; then
		echo "FAIL $role must be allowed: $sql"
		echo "     $out"
		failures=$((failures + 1))
	else
		echo "ok   $role may: $sql"
	fi
}

refused() {
	local role=$1 sql=$2 out
	out=$(as "$role" "$sql")
	if [[ $out == *"permission denied"* ]]; then
		echo "ok   $role may not: $sql"
	else
		echo "FAIL $role must be refused: $sql"
		echo "     $out"
		failures=$((failures + 1))
	fi
}

allowed login "SELECT count(*) FROM login.account"
refused login "SELECT 1 FROM world.player"
refused login "SELECT 1 FROM catalog.npc_template"
refused login "CREATE SCHEMA login_probe"
refused login "CREATE EXTENSION hstore"

allowed world "SELECT count(*) FROM world.player"
allowed world "SELECT count(*) FROM catalog.npc_template"
refused world "SELECT 1 FROM login.account"
refused world "CREATE SCHEMA world_probe"
refused world "CREATE EXTENSION hstore"

for role in login world; do
	superuser=$(as "$role" "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")
	if [[ $superuser == "f" ]]; then
		echo "ok   $role is not a superuser"
	else
		echo "FAIL $role must not be a superuser: $superuser"
		failures=$((failures + 1))
	fi
done

if ((failures)); then
	echo "$failures check(s) failed" >&2
	exit 1
fi
echo "roles: all checks passed"
