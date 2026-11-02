#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 l2go
# CI-only PostgreSQL acceptance on an isolated disposable cluster.
set -euo pipefail
umask 077

root="$(cd "$(dirname "$0")/../.." && pwd)"
run="${RUNNER_TEMP:?RUNNER_TEMP is required}/alatyr-go-postgres"
mkdir -p "$run"
db=""
cleanup() {
    result=$?
    if [[ -n "$db" ]]; then
        timeout 10 docker logs "$db" > "$run/postgres.log" 2>&1 || true
        timeout 10 docker rm -f "$db" > /dev/null || result=1
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# shellcheck source=ephemeral-postgres.sh
source "$root/tools/ci/ephemeral-postgres.sh"
start_ephemeral_postgres

(cd "$root/engine/go/server" && go test -race -json -count=1 -timeout=8m ./...) | tee "$run/tests.json"
(cd "$root" && go run ./tools/ci/check-engine-go.go -postgres-results "$run/tests.json")
