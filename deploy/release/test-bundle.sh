#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
digest="$(printf '%064d' 7)"
image="ghcr.io/l2go/alatyr:0.0.0@sha256:$digest"
"$here/bundle.sh" 0.0.0 "$image" "$work/one" >/dev/null
"$here/bundle.sh" 0.0.0 "$image" "$work/two" >/dev/null
cmp "$work/one/alatyr-0.0.0-compose.tar.gz" "$work/two/alatyr-0.0.0-compose.tar.gz"
python3 - "$work/one" "$image" <<'PY'
import hashlib,pathlib,sys,tarfile
root=pathlib.Path(sys.argv[1]);archive=root/'alatyr-0.0.0-compose.tar.gz'
assert (root/(archive.name+'.sha256')).read_text().split()[0]==hashlib.sha256(archive.read_bytes()).hexdigest()
with tarfile.open(archive) as tar:
 names=[m.name.removeprefix('alatyr-0.0.0/') for m in tar.getmembers()]
 assert sorted(names)==['LICENSE','README.md','character.example.json','compose.yaml','postgres/init/01-roles.sh','server.example.json']
 compose=tar.extractfile('alatyr-0.0.0/compose.yaml').read().decode()
 assert '  image: ${ALATYR_IMAGE:-'+sys.argv[2]+'}' in compose and 'build:' not in compose
 assert tar.getmember('alatyr-0.0.0/postgres/init/01-roles.sh').mode==0o755
PY
for bad in '1.2' '../bad' '1.2.3/../../bad'; do
 if "$here/bundle.sh" "$bad" "$image" "$work/bad" >/dev/null 2>&1; then echo 'invalid version accepted' >&2; exit 1; fi
done
for bad in 'ghcr.io/l2go/alatyr:edge' "ghcr.io/other/app@sha256:$digest"; do
 if "$here/bundle.sh" 0.0.0 "$bad" "$work/bad" >/dev/null 2>&1; then echo 'invalid image accepted' >&2; exit 1; fi
done
echo 'bundle: all checks passed'
