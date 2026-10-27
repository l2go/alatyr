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

password="$(openssl rand -hex 24)"
echo "::add-mask::$password"
db="$(timeout 120 docker run --rm -d -p 127.0.0.1::5432 -e POSTGRES_PASSWORD="$password" \
    postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722)"
for ((i=0; i<60; i++)); do
    if timeout 5 docker exec "$db" pg_isready -t 1 -U postgres -d postgres > /dev/null 2>&1; then
        break
    fi
    sleep 1
done
timeout 5 docker exec "$db" pg_isready -t 1 -U postgres -d postgres
port="$(timeout 5 docker port "$db" 5432/tcp)"
port="${port##*:}"
export ALATYR_TEST_POSTGRES_DSN="postgres://postgres:$password@127.0.0.1:$port/postgres?sslmode=disable"
export ALATYR_REQUIRE_POSTGRES=1

(cd "$root/engine/go/server" && go test -race -json -count=1 -timeout=5m ./...) | tee "$run/tests.json"
(cd "$root" && go run ./tools/ci/check-engine-go.go -postgres-results "$run/tests.json")
