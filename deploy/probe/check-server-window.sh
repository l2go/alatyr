#!/usr/bin/env bash
# Checks one window of the server log of the running stack.
#
#   deploy/probe/check-server-window.sh STAGE [--since TIMESTAMP] [--expect-unknown-items]
#
# The window is the server log since TIMESTAMP, or the whole log when --since is
# omitted. It must contain one passport line "World id=buyan-okolitsa version=3.2.0",
# one "World has no scripts", and no ERROR line or "Exception in thread". WARN lines
# are printed as a group named by STAGE. --expect-unknown-items requires a warning
# that items have no template. That warning is not an error, and a routine update
# from the published image does not pass the flag: both sides share one catalog.
# The window is written to image-server-logs/STAGE.log.
set -euo pipefail

stage="${1:?usage: check-server-window.sh STAGE [--since TIMESTAMP] [--expect-unknown-items]}"
shift

if [[ ! "$stage" =~ ^[a-z0-9-]+$ ]]; then
	echo "stage must be a short name, found: $stage" >&2
	exit 2
fi

since=""
expect_unknown=0
while [[ $# -gt 0 ]]; do
	case "$1" in
		--since)
			since="${2:?--since needs a timestamp}"
			shift
			;;
		--expect-unknown-items) expect_unknown=1 ;;
		*)
			echo "unknown argument: $1" >&2
			exit 2
			;;
	esac
	shift
done

deploy="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$deploy/.." && pwd)"
mkdir -p "$root/image-server-logs"
log="$root/image-server-logs/${stage}.log"

if [[ -n "$since" ]]; then
	docker compose -f "$deploy/compose.yaml" logs --no-color --since "$since" server > "$log"
else
	docker compose -f "$deploy/compose.yaml" logs --no-color server > "$log"
fi

passport='World id=buyan-okolitsa version=3.2.0 '
passport_count="$(grep -c "$passport" "$log" || true)"
if [[ "$passport_count" != 1 ]]; then
	echo "::error::the passport line of ${stage} is not unique (found ${passport_count})"
	exit 1
fi
grep "$passport" "$log"

scripts_count="$(grep -c 'World has no scripts' "$log" || true)"
if [[ "$scripts_count" != 1 ]]; then
	echo "::error::the no-scripts line of ${stage} is not unique (found ${scripts_count})"
	exit 1
fi
grep 'World has no scripts' "$log"

echo "::group::WARN lines of ${stage}"
grep -E '(^|\| )WARN ' "$log" || true
echo "::endgroup::"

if grep -E '(^|\| )ERROR |Exception in thread' "$log"; then
	echo "::error::the server log of ${stage} has an ERROR line or an uncaught exception"
	exit 1
fi

if [[ "$expect_unknown" -eq 1 ]]; then
	if ! grep -q 'items have no template in this world' "$log"; then
		echo "::error::the ${stage} window has no warning about items without a template"
		exit 1
	fi
	echo "the ${stage} window has the warning about items without a template"
fi
