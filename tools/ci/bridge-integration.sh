#!/usr/bin/env bash
# Run only in CI: use the packaged Java server, a disposable PostgreSQL database,
# the unchanged NC smoke client, and the Go bridge/participant acceptance tests.
set -euo pipefail
umask 077

root="$(cd "$(dirname "$0")/../.." && pwd)"
run="$root/target/bridge-integration"
mkdir -p "$run/operator" "$run/work"
db=""
server_pid=""
cleanup() {
    result=$?
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        if [[ "$result" != 0 ]]; then
            timeout 10 jcmd "$server_pid" Thread.print > "$run/threads.txt" 2>&1 || true
        fi
        kill -TERM "$server_pid" 2>/dev/null || true
        for ((i=0; i<60; i++)); do
            kill -0 "$server_pid" 2>/dev/null || break
            sleep 1
        done
        if kill -0 "$server_pid" 2>/dev/null; then
            echo "Bridge integration: server exceeded its 60-second shutdown bound" >&2
            kill -KILL "$server_pid" 2>/dev/null || true
            result=1
        fi
        wait "$server_pid" || true
    fi
    if [[ -n "$db" ]]; then
        timeout 10 docker logs "$db" > "$run/postgres.log" 2>&1 || true
        timeout 10 docker rm -f "$db" >/dev/null || true
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mapfile -t archives < <(find "$root/engine/java/platform/target" -maxdepth 1 -name '*-dist.zip')
[[ ${#archives[@]} == 1 ]]
unzip -q "${archives[0]}" -d "$run"
distribution="$run/l2jfree"
password="$(openssl rand -hex 24)"
db="$(timeout 120 docker run --rm -d -p 127.0.0.1::5432 -e POSTGRES_DB=bridge -e POSTGRES_PASSWORD="$password" \
    postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722)"
for ((i=0; i<60; i++)); do
    if timeout 5 docker exec "$db" pg_isready -t 1 -U postgres -d bridge >/dev/null 2>&1; then break; fi
    sleep 1
done
timeout 5 docker exec "$db" pg_isready -t 1 -U postgres -d bridge
port="$(timeout 5 docker port "$db" 5432/tcp)"
port="${port##*:}"
timeout 10 docker exec "$db" psql -X -U postgres -d bridge -v ON_ERROR_STOP=1 -c \
    'CREATE EXTENSION citext; CREATE SCHEMA login; CREATE SCHEMA world; CREATE SCHEMA catalog; CREATE SCHEMA report;'

export L2JFREE_DB_URL="jdbc:postgresql://127.0.0.1:$port/bridge"
export L2JFREE_LOGIN_DB_USER=postgres L2JFREE_WORLD_DB_USER=postgres
export L2JFREE_LOGIN_DB_PASSWORD="$password" L2JFREE_WORLD_DB_PASSWORD="$password"
export L2JFREE_HEALTH_PORT=18080
cat > "$run/operator/server.properties" <<EOF
DatapackRoot = $distribution/world
CatalogDirectory = $distribution/world/catalog
GameserverHostname = 127.0.0.1
EOF
cat > "$run/operator/loginserver.properties" <<EOF
LoginServerHostname = 127.0.0.1
EOF
cat > "$run/operator/other.properties" <<EOF
RespawnRandomInTown=False
TeleportWatchdogTimeout=0
EOF
export JAVA_BRIDGE_SECRET="$(openssl rand -hex 32)"
cat > "$run/operator/bridge.properties" <<EOF
bridge.enabled=true
bridge.port=19016
bridge.secret=$JAVA_BRIDGE_SECRET
bridge.grace=30000
EOF
(
    cd "$run/work"
    exec java -Xms512m -Xmx2g -Dfile.encoding=UTF-8 -Dl2jfree.network.transport=nio \
        "-Dl2jfree.config.defaults=$distribution/config" "-Dl2jfree.config.operator=$run/operator" \
        "-Dlogback.configurationFile=$distribution/config/logback.xml" \
        -cp "$distribution/config:$distribution/libs/*:$distribution/*" com.l2jfree.platform.Platform
) > "$run/server.log" 2>&1 &
server_pid=$!
for ((i=0; i<240; i++)); do
    kill -0 "$server_pid" || { tail -100 "$run/server.log"; exit 1; }
    if curl --connect-timeout 1 --max-time 2 -fsS http://127.0.0.1:18080/health/ready >/dev/null 2>&1; then break; fi
    sleep 1
done
curl --connect-timeout 1 --max-time 2 -fsS http://127.0.0.1:18080/health/ready
timeout 10 docker exec -i "$db" psql -X -U postgres -d bridge -v ON_ERROR_STOP=1 < "$root/tools/ci/bridge-integration.sql"

# The trade test starts this unchanged NC client after its bridge Open has
# completed, so coexistence is established rather than inferred from timing.
mapfile -t smoke < <(find "$root/engine/java/smoke/target" -maxdepth 1 -name 'l2jfree-smoke-*.jar' ! -name '*sources*')
[[ ${#smoke[@]} == 1 ]]
export JAVA_BRIDGE_NC_JAR="${smoke[0]}" JAVA_BRIDGE_NC_PASSWORD="$(openssl rand -hex 16)"
export JAVA_BRIDGE_NC_LOG="$run/nc.log"
export JAVA_BRIDGE_ENDPOINT=127.0.0.1:19016 JAVA_BRIDGE_DB_CONTAINER="$db"
cd "$root/engine/go"
go test -race -count=1 -v -timeout=4m ./gateway/backend -run '^TestJavaBridge' 2>&1 | tee "$run/go.log"
