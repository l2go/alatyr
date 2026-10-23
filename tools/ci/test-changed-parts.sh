#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Checks changed-parts.sh on a scratch repository: each part is named for its own change, a moved file counts for the
# part it leaves, and documentation alone names no part.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/changed-parts.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.name test
git config user.email test@example.invalid
mkdir -p engine/java/a engine/go/b world/c docs .github/workflows
for f in engine/java/a/A.java engine/go/b/b.go world/c/c.csv docs/d.md .github/workflows/build.yml .github/workflows/other.yml; do
    echo "$f" > "$f"
done
git add -A
git commit -qm base
expect() {
    local name="$1" want="$2"
    local got
    got="$("$script" HEAD^ HEAD | paste -sd ' ' -)"
    [ "$got" = "$want" ] || { echo "FAIL $name: got '$got', want '$want'"; exit 1; }
    echo "ok   $name: $got"
}
step() { git add -A; git commit -qm "$1"; }

echo more >> docs/d.md; step docs
expect "documentation alone" "java=false go=false world=false"
echo more >> engine/java/a/A.java; step java
expect "java change" "java=true go=false world=false"
echo more >> engine/go/b/b.go; step go
expect "go change" "java=false go=true world=false"
echo more >> world/c/c.csv; step world
expect "world change" "java=false go=false world=true"
echo more >> .github/workflows/other.yml; step workflow
expect "another workflow" "java=false go=true world=false"
echo more >> .github/workflows/build.yml; step pipeline
expect "build workflow" "java=true go=true world=true"
git mv engine/java/a/A.java docs/A.java; step "move java out"
expect "java file moved out" "java=true go=false world=false"
git mv engine/go/b/b.go docs/b.go; step "move go out"
expect "go file moved out" "java=false go=true world=false"
git mv world/c/c.csv docs/c.csv; step "move world out"
expect "world file moved out" "java=false go=false world=true"
