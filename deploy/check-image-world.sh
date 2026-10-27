#!/usr/bin/env bash
# The files under /opt/alatyr/world in the image must be the authored world:
# the same paths and the same bytes as world.properties, LICENSE, catalog/, and
# data/ in the repository. Sources under world/src and world/i18n are not part
# of that set.
#
#   deploy/check-image-world.sh IMAGE
#
# Writes image-world-manifest.txt in the current directory (sha256sum lines).
# Run it from the repository root, after the image exists.
set -euo pipefail

image="${1:?usage: check-image-world.sh IMAGE}"
root="$(cd "$(dirname "$0")/.." && pwd)"

fail() { echo "world bytes check failed: $*" >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

docker run --rm --entrypoint bash "$image" -c \
	'cd /opt/alatyr/world && find . -type f -print0 | sort -z | xargs -0 -r sha256sum' \
	| sed -E 's|[[:space:]]+\./|  |' \
	| LC_ALL=C sort -k2 > "$tmp/image"

(
	cd "$root/world"
	find world.properties LICENSE catalog data -type f -print0 | sort -z | xargs -0 -r sha256sum
) | LC_ALL=C sort -k2 > "$tmp/repo"

cp "$tmp/image" image-world-manifest.txt

if ! diff -u "$tmp/repo" "$tmp/image"; then
	fail "the image world does not match world/ in the repository"
fi

count="$(wc -l < image-world-manifest.txt | tr -d ' ')"
[ "$count" -gt 0 ] || fail "the image world is empty"
echo "world bytes match: $count files"
