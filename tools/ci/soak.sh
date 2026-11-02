#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright 2026 l2go
# Manual durability soak. Not invoked by push or pull_request workflows.
set -euo pipefail
umask 077

root="$(cd "$(dirname "$0")/../.." && pwd)"
run="${RUNNER_TEMP:?RUNNER_TEMP is required}/alatyr-soak"
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
export SOAK_DURATION="${SOAK_DURATION:-60m}"
# Leave headroom under the 90 minute test timeout and the 100 minute job.
case "$SOAK_DURATION" in
    ''|*[!0-9ms]*) echo "SOAK_DURATION must be a number of seconds or minutes" >&2; exit 2 ;;
esac
number="${SOAK_DURATION%[ms]}"
unit="${SOAK_DURATION#"$number"}"
seconds="$number"
if [[ "$unit" == m ]]; then
    seconds=$((number * 60))
fi
if (( seconds < 1 || seconds > 4800 )); then
    echo "SOAK_DURATION must be from 1s to 80m" >&2
    exit 2
fi
export SOAK_BOTS="${SOAK_BOTS:-8}"
export SOAK_SEED="${SOAK_SEED:-1}"
export SOAK_ARTIFACTS="$run"
cd "$root/engine/go/server"
go test -count=1 -timeout 90m -run '^TestSoak$' ./cmd/alatyr-server
