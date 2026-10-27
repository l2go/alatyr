#!/usr/bin/env bash
# Exercise persistence, file permissions, separation, and redacted failure.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/secrets" "$work/dbsecrets"
export ALATYR_SECRETS_DIR="$work/secrets" ALATYR_DB_SECRETS_DIR="$work/dbsecrets"
"$here/bin/init-secrets" > "$work/log"
python3 - "$work" <<'PY'
import pathlib,stat,sys,re
root=pathlib.Path(sys.argv[1]);db=root/'dbsecrets';app=root/'secrets'
assert sorted(p.name for p in app.iterdir())==['database_url']
assert sorted(p.name for p in db.iterdir())==['postgres_password','world_password']
values=[p.read_text() for p in db.iterdir()]
assert all(re.fullmatch('[0-9a-f]{64}',v) for v in values) and len(set(values))==2
assert all(stat.S_IMODE(p.stat().st_mode)==0o644 for p in db.iterdir())
assert stat.S_IMODE((app/'database_url').stat().st_mode)==0o600
assert (app/'database_url').read_text()=='postgres://alatyr:'+ (db/'world_password').read_text()+'@db:5432/alatyr?sslmode=disable\n'
assert all(v not in (root/'log').read_text() for v in values)
PY
cp "$work/secrets/database_url" "$work/before"
"$here/bin/init-secrets" >> "$work/log"
cmp "$work/before" "$work/secrets/database_url"
printf bad > "$work/dbsecrets/world_password"
if "$here/bin/init-secrets" > "$work/error" 2>&1; then echo 'invalid password accepted' >&2; exit 1; fi
[ "$(cat "$work/error")" = 'init-secrets: invalid existing password file' ]
cmp "$work/before" "$work/secrets/database_url"
echo 'init-secrets: all checks passed'
