#!/usr/bin/env bash
# CI-only rehearsal of an image on an isolated Compose project. Never builds it.
set -euo pipefail
image="${1:?usage: check-server.sh IMAGE}"
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
chmod 0755 "$work"
export ALATYR_IMAGE="$image" ALATYR_CONFIG_DIR="$work"
export ALATYR_COMPOSE_PROJECT="alatyr-check-${GITHUB_RUN_ID:-0}-${RANDOM}"
export ALATYR_COMPOSE_FILE="$root/deploy/compose.yaml"
compose=(docker compose -p "$ALATYR_COMPOSE_PROJECT" -f "$ALATYR_COMPOSE_FILE")
cleanup() {
 result=$?
 "${compose[@]}" logs --no-color server > "$root/image-server.log" 2>&1 || true
 "${compose[@]}" logs --no-color db > "$root/image-postgres.log" 2>&1 || true
 "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || result=1
 rm -rf "$work"
 exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Creating, without starting, establishes the network whose gateway is the
# actual source address of this runner's reverse-proxy/participant requests.
"${compose[@]}" create secrets db server
gateway="$(docker network inspect "${ALATYR_COMPOSE_PROJECT}_default" --format '{{(index .IPAM.Config 0).Gateway}}')"
export ALATYR_IMAGE_TOKEN="$(openssl rand -hex 32)"
if [ "${GITHUB_ACTIONS:-}" = true ]; then echo "::add-mask::$ALATYR_IMAGE_TOKEN"; fi
python3 - "$root" "$work" "$gateway" <<'PY'
import hashlib,json,os,pathlib,sys
root,work=map(pathlib.Path,sys.argv[1:3]);peer=sys.argv[3]
cfg=json.loads((root/'deploy/server.example.json').read_text())
cfg['ProxyHosts']=['127.0.0.1:8080'];cfg['ProxyOrigins']=['http://127.0.0.1:8080'];cfg['ProxyPeers']=[peer]
cfg['Participants'][0]['TokenSHA256']=hashlib.sha256(os.environ['ALATYR_IMAGE_TOKEN'].encode()).hexdigest()
(work/'server.json').write_text(json.dumps(cfg))
c=json.loads((root/'deploy/character.example.json').read_text())
c['Items']=[{'ObjectID':2,'TemplateID':999999,'Count':7,'Slot':-1}]
c['QuestVars']=[{'Quest':'authored','Name':'progress','Value':'retained'}]
(work/'character.json').write_text(json.dumps(c))
PY
chmod 0644 "$work/server.json" "$work/character.json"
"${compose[@]}" up -d --wait --wait-timeout 120 db
"${compose[@]}" run --rm --no-deps server -import-character /run/alatyr/config/character.json
"${compose[@]}" up -d --wait --wait-timeout 120 server
export ALATYR_REQUIRE_IMAGE=1 ALATYR_IMAGE_URL=http://127.0.0.1:8080
export ALATYR_IMAGE_CONFIG="$work/server.json"
cd "$root/engine/go/server"
go test -list '^TestImageParticipant$' ./cmd/alatyr-server | grep -Fx TestImageParticipant
GOWORK=off go test -json -count=1 -timeout=3m -run '^TestImageParticipant$' ./cmd/alatyr-server | tee "$root/image-participant-tests.jsonl"
# The named test must really have run and passed, never silently skipped.
python3 - "$root/image-participant-tests.jsonl" <<'PY'
import json,sys
rows=[json.loads(line) for line in open(sys.argv[1])]
actions=[r['Action'] for r in rows if r.get('Test')=='TestImageParticipant' and r['Action']!='output']
assert actions==['run','pass'],actions
PY
