#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 l2go
# Exercise initialization readiness and cleanup without Docker or a database.
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd)/go-postgres.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin"
cat > "$fixture/bin/stub" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
case "${0##*/}" in
    timeout) shift; exec "$@" ;;
    openssl) echo fixture-password ;;
    sleep) ;;
    go)
        test "$ALATYR_REQUIRE_POSTGRES" = 1
        test "$ALATYR_TEST_POSTGRES_DSN" = 'postgres://postgres:fixture-password@127.0.0.1:15432/postgres?sslmode=disable'
        printf '%s\n' "$*" >> "$CASE_DIR/go"
        ;;
    docker)
        case "$1" in
            run) echo fixture-db ;;
            exec)
                count=0
                if [[ -f "$CASE_DIR/probes" ]]; then
                    read -r count < "$CASE_DIR/probes"
                fi
                count=$((count+1))
                echo "$count" > "$CASE_DIR/probes"
                if [[ "$CASE_MODE" = unavailable ]]; then
                    exit 1
                fi
                # A socket probe sees the temporary server once, then its
                # shutdown. TCP becomes ready only after initialization ends.
                if [[ " $* " != *' -h 127.0.0.1 '* ]]; then
                    test "$count" = 1
                else
                    test "$count" -ge 2
                fi
                ;;
            port) echo 127.0.0.1:15432 ;;
            logs) echo fixture-log ;;
            rm) touch "$CASE_DIR/removed" ;;
            *) exit 2 ;;
        esac
        ;;
    *) exit 2 ;;
esac
STUB
chmod +x "$fixture/bin/stub"
for command in timeout openssl sleep go docker; do
    ln -s stub "$fixture/bin/$command"
done
for mode in initializing unavailable; do
    case_dir="$fixture/$mode"
    mkdir -p "$case_dir"
    result=0
    CASE_DIR="$case_dir" CASE_MODE="$mode" RUNNER_TEMP="$case_dir" PATH="$fixture/bin:$PATH" \
        bash "$script" > "$case_dir/output" 2>&1 || result=$?
    test -f "$case_dir/removed"
    test -s "$case_dir/alatyr-go-postgres/postgres.log"
    if [[ "$mode" = initializing ]]; then
        test "$result" = 0
        test "$(cat "$case_dir/probes")" = 3
        test "$(wc -l < "$case_dir/go" | tr -d ' ')" = 2
    else
        test "$result" != 0
        test "$(cat "$case_dir/probes")" = 61
        test ! -e "$case_dir/go"
    fi
    echo "ok: PostgreSQL $mode"
done
