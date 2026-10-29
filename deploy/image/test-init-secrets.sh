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

# Inspect the initializer's real argv/environment with a controlled psql peer.
# Read the password from the command environment, not argv or unexpanded SQL.
python3 - "$here/../postgres/init/01-roles.sh" "$work" <<'PYTEST'
import os,pathlib,subprocess,sys
script=pathlib.Path(sys.argv[1]);work=pathlib.Path(sys.argv[2]);bindir=work/'bin';bindir.mkdir()
secret="role password with ' and spaces"
(bindir/'cat').write_text("#!/usr/bin/env python3\nimport os,sys\nassert sys.argv[1:]==['/run/alatyr-dbsecrets/world_password']\nprint(os.environ['TEST_ROLE_SECRET'],end='')\n")
(bindir/'psql').write_text("""#!/usr/bin/env python3
import os,sys
secret=os.environ['TEST_ROLE_SECRET']
assert sys.argv[1:]==['-v','ON_ERROR_STOP=1','--username','postgres','--dbname','alatyr']
assert os.environ['ALATYR_WORLD_PASSWORD']==secret
sql=sys.stdin.read()
assert secret not in sql
assert "\\getenv world_password ALATYR_WORLD_PASSWORD" in sql
assert "PASSWORD :'world_password'" in sql
""")
for p in bindir.iterdir():p.chmod(0o700)
env=dict(os.environ,PATH=str(bindir)+os.pathsep+os.environ['PATH'],TEST_ROLE_SECRET=secret,POSTGRES_USER='postgres',POSTGRES_DB='alatyr')
result=subprocess.run(['bash',str(script)],env=env,capture_output=True,text=True,check=True)
assert secret not in result.stdout+result.stderr
print('init-roles: password excluded from argv and SQL input')
PYTEST
