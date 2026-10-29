#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Each active part and moved-out file must select its checks; docs select none.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/changed-parts.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.name test
git config user.email test@example.invalid
mkdir -p engine/go world docs deploy/release .github/workflows
for file in engine/go/code.go world/content.csv docs/guide.md .github/workflows/build.yml .github/workflows/other.yml; do
 echo base > "$file"
done
git add -A; git commit -qm base
step() { git add -A; git commit -qm "$1"; }
expect() {
 local got
 got="$("$script" HEAD^ HEAD | paste -sd ' ' -)"
 [ "$got" = "$2" ] || { echo "FAIL $1: got '$got', want '$2'" >&2; exit 1; }
 echo "ok: $1"
}
echo more >> docs/guide.md; step docs
expect docs 'go=false world=false'
echo more >> engine/go/code.go; step go
expect go 'go=true world=false'
echo more >> world/content.csv; step world
expect world 'go=true world=true'
echo more >> .github/workflows/other.yml; step workflow
expect workflow 'go=true world=false'
echo more >> .github/workflows/build.yml; step pipeline
expect pipeline 'go=true world=true'
git mv engine/go/code.go docs/code.go; step moved-go
expect moved-go 'go=true world=false'
git mv world/content.csv docs/content.csv; step moved-world
expect moved-world 'go=true world=true'
echo test > deploy/release/test.sh; step deployment
expect deployment 'go=true world=false'
echo context > .dockerignore; step image-context
expect image-context 'go=true world=false'
