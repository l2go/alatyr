#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Names the parts of the repository that changed between two commits: java, go, and world, one line each, as
# key=true|false. A change to the build workflow names every part. Rename detection is off, so a moved file counts
# for its old path as well as its new one.
set -euo pipefail
base="${1:?usage: changed-parts.sh BASE HEAD}"
head="${2:?usage: changed-parts.sh BASE HEAD}"
files="$(git diff --name-only --no-renames "$base" "$head")"
changed() { if grep -Eq "$1" <<< "$files"; then echo true; else echo false; fi; }
java="$(changed '^(engine/java/|pom\.xml$|\.mvn/|mvnw|deploy/release/)')"
go="$(changed '^(engine/go/|tools/ci/|go\.work|\.github/workflows/)')"
world="$(changed '^(world/|tools/world/)')"
if [ "$(changed '^\.github/workflows/build\.yml$')" = true ]; then java=true; go=true; world=true; fi
printf '%s\n' "java=$java" "go=$go" "world=$world"
