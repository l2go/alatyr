#!/usr/bin/env bash
# Test image/provenance/content gates with controlled CLI peers, without Docker.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"
export CHECK_LOG="$work/calls" CHECK_MANIFEST="$work/manifest" CHECK_WORLD="$work/world"
export CHECK_MODE=good
python3 - "$root" "$work" <<'PY'
import hashlib,json,pathlib,sys
root,work=map(pathlib.Path,sys.argv[1:]);entries=[]
for arch,n in [('amd64',1),('arm64',2)]:entries.append({'digest':'sha256:'+str(n)*64,'platform':{'os':'linux','architecture':arch}})
entries.append({'digest':'sha256:'+'3'*64,'platform':{'os':'unknown','architecture':'unknown'},'annotations':{'vnd.docker.reference.type':'attestation-manifest'}})
(work/'manifest').write_text(json.dumps({'manifests':entries}))
paths=[root/'world/world.properties',root/'world/LICENSE']
for part in ['catalog','data']:paths.extend(p for p in (root/'world'/part).rglob('*') if p.is_file())
(work/'world').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  ./'+str(p.relative_to(root/'world'))+'\n' for p in sorted(paths)))
PY
cat > "$work/bin/docker" <<'STUB'
#!/bin/bash
set -eu
if [ "$1" = buildx ]; then
 python3 - "$CHECK_MANIFEST" "$CHECK_MODE" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]));mode=sys.argv[2]
if mode=='missing-platform':m['manifests']=m['manifests'][:1]
if mode=='foreign-descriptor':m['manifests'][-1].pop('annotations')
print(json.dumps(m))
PY
elif [ "$1" = run ]; then
 cat "$CHECK_WORLD"
else
 echo 'unexpected docker command' >&2;exit 1
fi
STUB
cat > "$work/bin/cosign" <<'STUB'
#!/bin/bash
printf 'cosign %s\n' "$*" >> "$CHECK_LOG"
[ "$CHECK_MODE" != bad-signature ]
STUB
cat > "$work/bin/gh" <<'STUB'
#!/bin/bash
printf 'gh %s\n' "$*" >> "$CHECK_LOG"
[ "$CHECK_MODE" != bad-provenance ]
STUB
chmod +x "$work/bin/"*
export PATH="$work/bin:$PATH" GITHUB_REPOSITORY=l2go/alatyr
image="ghcr.io/l2go/alatyr@sha256:$(printf '%064d' 7)"
cd "$work"
"$root/deploy/verify-published-image.sh" "$image" >/dev/null
[ "$(wc -l < "$CHECK_LOG" | tr -d ' ')" = 6 ]
for mode in missing-platform foreign-descriptor bad-signature bad-provenance; do
 export CHECK_MODE="$mode"
 if "$root/deploy/verify-published-image.sh" "$image" >/dev/null 2>&1; then echo "gate accepted $mode" >&2;exit 1;fi
done
if "$root/deploy/verify-published-image.sh" 'ghcr.io/other/app:edge' >/dev/null 2>&1; then echo 'unpinned image accepted' >&2;exit 1;fi
export CHECK_MODE=good
"$root/deploy/check-image-world.sh" fixture >/dev/null
printf '%064d  ./extra-file\n' 1 >> "$CHECK_WORLD"
if "$root/deploy/check-image-world.sh" fixture >/dev/null 2>&1; then echo 'foreign world bytes accepted' >&2;exit 1;fi
echo 'image checks: all positive and negative cases passed'
