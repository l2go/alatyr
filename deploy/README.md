# Deploy

The delivery of Platform 3.0: one Linux image started with Docker Compose. The decision and its limits are in [ADR-0002](../docs/adr/0002-linux-image-delivered-with-compose.md). The [architecture views](../docs/architecture.md) show how the parts fit.

> **Status.** The image runs the login module and the world module in one process on PostgreSQL 18. GitHub Actions takes the smoke program through full sessions, before and after a restart, and rehearses the backup, the restore, the update of the image, and the capture of an incident on every merge ([runbook](../docs/runbook.md)).

## Run it

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

The stack starts three services:

- `secrets`: a one-shot service that creates the database passwords once.
- `db`: PostgreSQL 18 with one role per module and the schemas each role owns ([ADR-0009](../docs/adr/0009-database-roles-schemas-and-migration.md)). The role `l2jfree_login` owns `login`; the role `l2jfree_world` owns `world`, `catalog`, and `report`.
- `server`: one process with the login module and the world module ([ADR-0003](../docs/adr/0003-one-process-two-modules.md)). Each module migrates its schema when the process starts, the world loads its data and the catalog, and the login port opens last.

`--wait` returns when the services are healthy. The world needs minutes to load on the first start, so the first `up` can take five minutes or more; the health check of `server` allows up to 300 seconds of start-up and then 30 further tries ten seconds apart. The server is healthy when its readiness endpoint answers 200, which it does once the world is loaded and both ports accept players. The endpoint listens only inside the container (`L2JFREE_HEALTH_PORT`, default off, set to `8080` by the compose file).

| Port | Module | Notes |
|---|---|---|
| 2106 | Login | Sends its first packet to every client that connects |
| 7777 | World | Opens before the login port, so a client that reaches the login finds a world |

The image is public. `docker login ghcr.io` is needed only to push, or to pull a package that was made private. To run a build of your own, set `L2JFREE_IMAGE` to that image.

Variables you set in the shell or in an `.env` file next to `compose.yaml`:

| Variable | Default | Meaning |
|---|---|---|
| `L2JFREE_IMAGE` | `ghcr.io/l2go/l2jfree:edge` | Image to run |
| `L2JFREE_BIND` | `0.0.0.0` | Host address that publishes the two ports. It is a compose setting: the process inside the container always listens on all of its addresses |
| `L2JFREE_EXTERNAL_HOST` | `127.0.0.1` | Address the server announces for the world. The default is `127.0.0.1` |
| `L2JFREE_JAVA_OPTS` | `-Xms1g -Xmx3g -XX:+UseG1GC` | JVM options. The container is limited to 4 GB (`mem_limit`), so keep the heap well below it |

Stop with `docker compose -f deploy/compose.yaml down`. Add `-v` to delete the volumes, which removes the database, the logs, and your configuration overrides.

## Deployment variables of the image

The entry point of the image only validates these variables and starts the process; it does not rewrite any file ([ADR-0011](../docs/adr/0011-configuration-model.md)). The compose file sets all of them. A variable wins over the properties files.

| Variable | Required | Meaning |
|---|---|---|
| `L2JFREE_DB_URL` | yes | JDBC URL of the one database |
| `L2JFREE_LOGIN_DB_USER` | yes | Login role |
| `L2JFREE_LOGIN_DB_PASSWORD_FILE` | yes | File with the password of the login role. The entry point stops when it is missing or empty |
| `L2JFREE_WORLD_DB_USER` | yes | World role |
| `L2JFREE_WORLD_DB_PASSWORD_FILE` | yes | File with the password of the world role. The entry point stops when it is missing or empty |
| `L2JFREE_BIND` | no | Listen address of both ports, default `0.0.0.0`. Compose does not pass it; it uses the variable for the published ports |
| `L2JFREE_EXTERNAL_HOST` | no | Address the world announces when no subnet matches, default the value of `ExternalHostname` in `server.properties` |
| `L2JFREE_JAVA_OPTS` | no | JVM options, default `-Xms1g -Xmx3g -XX:+UseG1GC` |
| `L2JFREE_NETWORK_TRANSPORT` | no | `epoll` (default), `io_uring` (needs a container profile that allows it), `nio`, or `auto` (the first available of the three). A transport that is not available stops the start and the log names the reason |
| `L2JFREE_FLIGHT_RECORDER` | no | `on` (default) keeps a JFR flight recording of the last 12 hours, at most 256 MB, in the `log` volume under `jfr/`; `off` turns it off |
| `L2JFREE_HEALTH_PORT` | no | Port of the readiness endpoint (`/health/live`, `/health/ready`), default off. `L2JFREE_HEALTH_HOST` sets its address, default `127.0.0.1` |

A secret is never an environment value: a variable that ends in `_FILE` names a file inside the container, and the `secrets` volume holds the files.

## Configuration

The image ships the defaults of every key in `/opt/l2jfree/config`, a read-only tree. The same files are in the repository: `l2jfree-core/config` for the world, `l2jfree-login/config` for the login. They are the reference of every key.

Your changes live in one directory, the volume `config`, which is mounted at `/var/lib/l2jfree/config`. A file there with the name of a default file overrides only the keys it contains; every other key keeps its default. Put the few keys you changed in it, not a copy of the default. An image update replaces the defaults and keeps your files. The server logs a warning at start for a key in your files that no default file knows, which is usually a typo.

The path keys of the world are an exception that the image sets for you. The shipped default `server.properties` in the image names the read-only tree: `DatapackRoot = /opt/l2jfree` and `CatalogDirectory = /opt/l2jfree/catalog`. The image build rewrites these two lines of the defaults and fails when a line is missing. The loader has only two layers, defaults and operator ([ADR-0011](../docs/adr/0011-configuration-model.md)), so a third file for the image would need a loader change. You should not need to override these two keys.

### Example: change the experience rate

The default `rates.properties` has `RateXp = 1.`. To triple it, create a file named `rates.properties` in the operator directory that holds only that key:

```properties
RateXp = 3.
```

The simplest way to edit the directory is a bind mount. In `compose.yaml`, replace the line `- config:/var/lib/l2jfree/config` with `- ./config:/var/lib/l2jfree/config`, create `deploy/config/rates.properties` with the line above, and restart:

```sh
docker compose -f deploy/compose.yaml up -d
docker compose -f deploy/compose.yaml restart server
```

The files must be readable by user `10001`. Without a bind mount, write the file into the named volume with a throwaway container:

```sh
printf 'RateXp = 3.\n' | docker run --rm -i --user 10001:10001 -v l2jfree_config:/config \
  --entrypoint tee "${L2JFREE_IMAGE:-ghcr.io/l2go/l2jfree:edge}" /config/rates.properties
docker compose -f deploy/compose.yaml restart server
```

The server reads the files only at start, so a change needs a restart.

## Logs

The console output of the process is in the container log:

```sh
docker compose -f deploy/compose.yaml logs -f server
```

`Platform ready` marks the moment the login port opens. The log files of the logging configuration (`java`, `error`, `login`, `chat`, `item`, `audit`) are in the volume `logs`, mounted at `/var/lib/l2jfree/log`. The work directory `/var/lib/l2jfree/work` (the `work` volume) holds the caches, such as the HTML cache, and a `log` link to that volume. To copy the files out:

```sh
docker compose -f deploy/compose.yaml cp server:/var/lib/l2jfree/log ./log
```

The OpenTelemetry agent is in the image but is not attached, so telemetry stays off.

The database counts every statement (`pg_stat_statements`) and logs the plan of a statement that takes longer than 500 ms (`auto_explain`), so a slow start or a slow save can be found afterwards:

```sh
docker compose -f deploy/compose.yaml exec db psql -U postgres -d l2jfree \
  -c 'SELECT calls, round(total_exec_time) AS ms, left(query, 80) FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10'
```

## Capture an incident

One command collects the state of the containers, the logs of the server and the database, the ten statements that took the most time, what the database sessions are doing, and a thread dump of the server into one directory. The server keeps running: the thread dump is a SIGQUIT, which makes the JVM print its threads to the container log.

```sh
deploy/probe/capture.sh [directory]
```

The pipeline runs the script against the running stack on every merge.

## Backup and restore

The backup is the `login` and `world` schemas. The catalog comes from the image and is loaded again at start, and the `report` views are created again at start, so neither is backed up.

```sh
docker compose -f deploy/compose.yaml exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree-$(date +%F).dump
```

To restore, stop the server first. The tables of `catalog` have foreign keys into the reference tables of `world`, and the views of `report` read `world`, so the restore drops all four schemas and creates `catalog` and `report` again empty. The server builds both when it starts: the catalog is loaded from the image, and the views are created again. The dump brings back `login` and `world` with their owners and their rights:

```sh
docker compose -f deploy/compose.yaml stop server
docker compose -f deploy/compose.yaml exec -T db psql -U postgres -d l2jfree \
  -c 'DROP SCHEMA IF EXISTS report, catalog, world, login CASCADE' \
  -c 'CREATE SCHEMA catalog AUTHORIZATION l2jfree_world' \
  -c 'CREATE SCHEMA report AUTHORIZATION l2jfree_world'
docker compose -f deploy/compose.yaml exec -T db pg_restore -U postgres -d l2jfree --exit-on-error < l2jfree-2026-10-07.dump
docker compose -f deploy/compose.yaml start server
```

The `-T` keeps a terminal from altering the dump. The pipeline runs these commands on every merge: it dumps a stack that holds an account with a character, destroys the stack with its volumes, starts a clean one, restores the dump, and the same account finds its character again.

## What the image contains

| Part | Choice |
|---|---|
| Base | Arch Linux, `linux/amd64`, pinned by dated tag and digest |
| Java | Eclipse Temurin 25 JRE archive, pinned by SHA-256 |
| User | `10001`, not root |
| File system | Read-only root and a read-only application tree under `/opt/l2jfree`; state lives in named volumes (`work`, `config`, `logs`, `secrets`, `dbdata`) |
| Capabilities | All dropped, `no-new-privileges` set |
| Memory | `mem_limit: 4g` on the server; the JVM heap defaults to 1 to 3 GB |
| Process | One Java process: `com.l2jfree.platform.Platform` starts login, then world, then opens the login port |

`deploy/verify-image.sh` checks these properties in CI, including that the application tree is not writable and that the entry point refuses to start without its settings. `deploy/probe/login_init.py` checks that the login port sends its first packet, and with `--connect-only` that the world port accepts a connection.

## Pipeline

Every pull request builds the image from the verified platform distribution (`l2jfree-platform-<version>-dist.zip`, unpacked as `l2jfree/` next to the Dockerfile), runs the content check, starts the stack with `docker compose up -d --wait --wait-timeout 600`, probes both ports, restarts the `server` service, and probes again. When a step fails, the job prints the last 200 lines of the server log. A merge to `main` also publishes `edge` and `sha-<commit>` images with a cosign signature, an SBOM, and build provenance. Versions tagged only `sha-*` are deleted after 30 days.

## Known limits

- The datapack and the catalog are read-only in the image. A feature that writes into the datapack tree, such as a saved leaderboard file, cannot write there.
- `io_uring` is blocked by Docker's default security profile, so the network core uses epoll by default.

## The smoke client

`l2jfree-smoke` is a JDK program. The pipeline runs it against the started stack, before and after a restart of the server: it logs in, takes the one world of the server list, creates a character, enters the world, logs out, and logs in again with the same account. It exits with 1 and names the step when one fails.

```sh
java -jar l2jfree-smoke.jar --host 127.0.0.1
```

The options are `--login-port`, `--world-port`, `--protocol-revision`, `--timeout-seconds`, `--account` with `--password` for an existing account, and `--existing-characters` for the number of characters that account must have on entry. The pipeline uses the last one to check that an account and its character survive a restart. The jar is part of the `l2jfree-dist` artifact of the pipeline.
