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
stop_server() {
    local failed=0
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        kill -TERM "$server_pid" 2>/dev/null || true
        for ((i=0; i<60; i++)); do
            kill -0 "$server_pid" 2>/dev/null || break
            sleep 1
        done
        if kill -0 "$server_pid" 2>/dev/null; then
            echo "Bridge integration: server exceeded its 60-second shutdown bound" >&2
            timeout 10 jcmd "$server_pid" Thread.print > "$run/threads.txt" 2>&1 || true
            kill -KILL "$server_pid" 2>/dev/null || true
            failed=1
        fi
        wait "$server_pid" || true
    fi
    server_pid=""
    return "$failed"
}
cleanup() {
    result=$?
    if [[ "$result" != 0 && -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        timeout 10 jcmd "$server_pid" Thread.print > "$run/threads.txt" 2>&1 || true
    fi
    stop_server || result=1
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
JAVA_PARITY_WORLD="$distribution/world" JAVA_PARITY_SQL="$run/parity.sql" \
    go -C "$root/engine/go" test -count=1 ./worldtest -run '^TestPrepareJavaParityFixtures$'
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
MaximumSlotsForNoDwarf=128
EOF
cat > "$run/operator/vitality.properties" <<EOF
EnableVitality=False
EOF
cat > "$run/operator/geodata.properties" <<EOF
GeoData=0
EOF
export JAVA_BRIDGE_SECRET="$(openssl rand -hex 32)"
export JAVA_PARITY_SECRET="$(openssl rand -hex 32)"
export JAVA_PARITY_ENDPOINT=127.0.0.1:19017
# Compile only the disposable fixture control. No production class is changed,
# and this jar is neither copied into the distribution nor published as an image.
mkdir -p "$run/fixture-classes"
javac --release 25 -cp "$distribution/libs/*:$distribution/*" -d "$run/fixture-classes" \
    "$root/tools/ci/java-fixture/WorldFixture.java"
printf 'Premain-Class: org.alatyr.testing.WorldFixture\n' > "$run/fixture-manifest"
jar --create --file "$run/fixture.jar" --manifest "$run/fixture-manifest" -C "$run/fixture-classes" .
runtime_classpath="$distribution/config"
while IFS= read -r -d '' runtime_jar; do
    runtime_classpath="$runtime_classpath:$runtime_jar"
done < <(find "$distribution" -name '*.jar' -print0)
JAVA_PARITY_VECTORS="$root/engine/go/world/hunt/testdata/parity.tsv" \
    jshell --class-path "$runtime_classpath" "$root/tools/ci/java-fixture/hunt.jsh" | tee "$run/hunt-model.log"
grep -qx 'HUNT_PARITY_OK=14' "$run/hunt-model.log"
cat > "$run/operator/bridge.properties" <<EOF
bridge.enabled=true
bridge.port=19016
bridge.secret=$JAVA_BRIDGE_SECRET
bridge.grace=30000
EOF
start_server() {
    (
        cd "$run/work"
        exec java -Xms512m -Xmx2g -Dfile.encoding=UTF-8 -Dl2jfree.network.transport=nio \
            "-javaagent:$run/fixture.jar=19017:$JAVA_PARITY_SECRET" \
            "-Dl2jfree.config.defaults=$distribution/config" "-Dl2jfree.config.operator=$run/operator" \
            "-Dlogback.configurationFile=$distribution/config/logback.xml" \
            -cp "$distribution/config:$distribution/libs/*:$distribution/*" com.l2jfree.platform.Platform
    ) >> "$run/server.log" 2>&1 &
    server_pid=$!
    for ((i=0; i<240; i++)); do
        kill -0 "$server_pid" || { tail -100 "$run/server.log"; return 1; }
        if curl --connect-timeout 1 --max-time 2 -fsS http://127.0.0.1:18080/health/ready >/dev/null 2>&1; then break; fi
        sleep 1
    done
    curl --connect-timeout 1 --max-time 2 -fsS http://127.0.0.1:18080/health/ready
}

# Migrate with the packaged server, then load fixtures while no process owns
# cached character names or object IDs. The acceptance process must see the
# fixture in its startup snapshot just as it sees an operator's existing data.
echo "Bridge integration: migrate the disposable database" >> "$run/server.log"
start_server
stop_server
timeout 10 docker exec -i "$db" psql -X -U postgres -d bridge -v ON_ERROR_STOP=1 --single-transaction < "$root/tools/ci/bridge-integration.sql"
timeout 10 docker exec -i "$db" psql -X -U postgres -d bridge -v ON_ERROR_STOP=1 --single-transaction < "$run/parity.sql"
echo "Bridge integration: start with persisted acceptance characters" >> "$run/server.log"
start_server

# The trade test starts this unchanged NC client after its bridge Open has
# completed, so coexistence is established rather than inferred from timing.
mapfile -t smoke < <(find "$root/engine/java/smoke/target" -maxdepth 1 -name 'l2jfree-smoke-*.jar' ! -name '*sources*')
[[ ${#smoke[@]} == 1 ]]
export JAVA_BRIDGE_NC_JAR="${smoke[0]}" JAVA_BRIDGE_NC_PASSWORD="$(openssl rand -hex 8)"
export JAVA_BRIDGE_NC_LOG="$run/nc.log"
export JAVA_BRIDGE_ENDPOINT=127.0.0.1:19016 JAVA_BRIDGE_DB_CONTAINER="$db"
cd "$root/engine/go"
go test -race -p=1 -count=1 -v -timeout=8m ./gateway/backend ./worldtest -run '^(TestJavaBridge|TestOkolitsaParity)' 2>&1 | tee "$run/go.log"
