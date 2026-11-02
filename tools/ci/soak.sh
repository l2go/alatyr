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
# Only a whole number of seconds or a whole number of minutes: 60 and 1m30s are rejected.
if [[ "$SOAK_DURATION" =~ ^([0-9]+)s$ ]]; then
    seconds="${BASH_REMATCH[1]}"
elif [[ "$SOAK_DURATION" =~ ^([0-9]+)m$ ]]; then
    seconds=$((10#${BASH_REMATCH[1]} * 60))
else
    echo "SOAK_DURATION must be <N>s or <N>m" >&2
    exit 2
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
