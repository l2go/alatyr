#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 l2go
# Shared disposable PostgreSQL 18 for CI scripts. Source this file.
# start_ephemeral_postgres sets db and ALATYR_TEST_POSTGRES_DSN.
# The image's temporary initialization server accepts only Unix sockets. Wait
# for TCP so readiness cannot race its shutdown before the final server starts.
start_ephemeral_postgres() {
    local password port
    password="$(openssl rand -hex 24)"
    echo "::add-mask::$password"
    db="$(timeout 120 docker run --rm -d -p 127.0.0.1::5432 -e POSTGRES_PASSWORD="$password" \
        postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722)"
    for ((i=0; i<60; i++)); do
        if timeout 5 docker exec "$db" pg_isready -h 127.0.0.1 -t 1 -U postgres -d postgres > /dev/null 2>&1; then
            break
        fi
        sleep 1
    done
    timeout 5 docker exec "$db" pg_isready -h 127.0.0.1 -t 1 -U postgres -d postgres
    port="$(timeout 5 docker port "$db" 5432/tcp)"
    port="${port##*:}"
    export ALATYR_TEST_POSTGRES_DSN="postgres://postgres:$password@127.0.0.1:$port/postgres?sslmode=disable"
    export ALATYR_REQUIRE_POSTGRES=1
}
