#!/usr/bin/env bash
# Check the native image and its unprivileged, read-only runtime contract.
set -euo pipefail
image="${1:?usage: verify-image.sh IMAGE [PLATFORM]}"
expected_platform="${2:-linux/amd64}"
fail() { echo "image check failed: $*" >&2; exit 1; }
inside() { docker run --rm --read-only --cap-drop ALL --security-opt no-new-privileges:true --entrypoint bash "$image" -c "$1"; }
platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image")"
[ "$platform" = "$expected_platform" ] || fail "unexpected platform: $platform"
machine="$(inside 'uname -m')"
case "$expected_platform:$machine:$(uname -m)" in
 linux/amd64:x86_64:x86_64|linux/arm64:aarch64:aarch64) ;;
 *) fail "image and runner must be native" ;;
esac
[ "$(docker image inspect --format '{{.Config.User}}' "$image")" = 10001:10001 ] || fail 'unexpected service user'
inside 'test "$(id -u)" = 10001 && test "$(id -g)" = 10001' || fail 'privileged runtime'
inside '. /etc/os-release && test "$ID" = debian && test "$VERSION_ID" = 13' || fail 'unexpected base'
for file in bin/alatyr-server bin/entrypoint bin/init-secrets LICENSE LICENSE.protocol LICENSE.deployment world/world.properties world/LICENSE world/catalog/npc_template.csv; do
 inside "test -s /opt/alatyr/$file" || fail "missing $file"
done
inside '/opt/alatyr/bin/alatyr-server -help 2>&1 | grep -q import-character' || fail 'application binary unavailable'
inside '! command -v java && ! command -v mvn && ! command -v go' || fail 'build tool or removed runtime shipped'
inside 'test -z "$(find /opt/alatyr -type f \( -name "*.jar" -o -name "*.class" -o -name scripts.cfg -o -name "*.py" \) -print)"' || fail 'unexpected executable content'
inside 'test ! -w /opt/alatyr/bin/alatyr-server && test ! -w /opt/alatyr/world' || fail 'writable application tree'
if inside 'touch /opt/alatyr/write-test 2>/dev/null'; then fail 'writable application root'; fi
inside 'find /opt/alatyr -print | sort' > image-file-list.txt
top="$(inside 'find /opt/alatyr/world -mindepth 1 -maxdepth 1 -printf "%f\n" | sort')"
[ "$top" = "$(printf '%s\n' LICENSE catalog data world.properties)" ] || fail 'unexpected world entries'
status=0
output="$(docker run --rm --read-only "$image" 2>&1)" || status=$?
[ "$status" -ne 0 ] || fail 'started without deployment settings'
grep -q 'ALATYR_DATABASE_URL_FILE is required' <<< "$output" || fail 'missing setting not identified'
echo "image check passed: $image ($platform)"
