# Deploy

One Linux image, started with Docker Compose from a checkout of this repository. GitHub Actions runs the same stack on every pull request.

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

The first start takes several minutes. It creates the database passwords, migrates the schemas, and loads the game content. Later starts keep them. `--wait` returns when the server is healthy: the world is loaded and both ports accept a connection.

| Port | Role |
|---|---|
| 2106 | Login. It sends its first packet to every connection. |
| 7777 | World. It opens before the login port. |

Stop with `docker compose -f deploy/compose.yaml down`. Add `-v` to delete the volumes. That removes the database, the logs, and the configuration overrides.

## Settings

Set these in the shell or in an `.env` file next to `deploy/compose.yaml`.

| Variable | Default | Meaning |
|---|---|---|
| `L2JFREE_IMAGE` | `ghcr.io/l2go/l2jfree:edge` | Image to run |
| `L2JFREE_BIND` | `0.0.0.0` | Host address that publishes the two ports |
| `L2JFREE_EXTERNAL_HOST` | `127.0.0.1` | Address the server announces for the world |
| `L2JFREE_JAVA_OPTS` | `-Xms1g -Xmx3g -XX:+UseG1GC` | JVM options. The container limit is 4 GB, so keep the heap below it |

The image is public. `docker login ghcr.io` is required to push an image, and to pull an image that is private.

The entry point also reads `L2JFREE_DB_URL`, `L2JFREE_LOGIN_DB_USER`, `L2JFREE_LOGIN_DB_PASSWORD_FILE`, `L2JFREE_WORLD_DB_USER`, and `L2JFREE_WORLD_DB_PASSWORD_FILE`. The Compose file sets them. A variable whose name ends in `_FILE` names a file in the `secrets` volume. The entry point stops when a required password file is missing or empty.

`L2JFREE_NETWORK_TRANSPORT` is `epoll` by default. `io_uring` needs a container profile that allows it. `nio` and `auto` are the other values. A transport that is not available stops the start, and the log names the reason. `L2JFREE_FLIGHT_RECORDER` is `on` by default and keeps the last 12 hours in the log volume, at most 256 MB. `L2JFREE_HEALTH_PORT` is off unless the Compose file sets it. The readiness paths are `/health/live` and `/health/ready`.

## Configuration

The image ships the default of every key under `/opt/l2jfree/config`. The same files are in `l2jfree-core/config` and `l2jfree-login/config`. An override is a file of the same name in the `config` volume, mounted at `/var/lib/l2jfree/config`. The file lists only the keys you change. Any other key keeps its default. The server reads the files at start, so a change needs a restart. A key that no default file knows is logged as a warning.

To triple the experience rate, put this in `deploy/config/rates.properties`. In `deploy/compose.yaml`, replace `- config:/var/lib/l2jfree/config` with `- ./config:/var/lib/l2jfree/config`.

```properties
RateXp = 3.
```

```sh
docker compose -f deploy/compose.yaml up -d
docker compose -f deploy/compose.yaml restart server
```

The files must be readable by user `10001`. Without a bind mount, write into the named volume:

```sh
printf 'RateXp = 3.\n' | docker run --rm -i --user 10001:10001 -v l2jfree_config:/config \
  --entrypoint tee "${L2JFREE_IMAGE:-ghcr.io/l2go/l2jfree:edge}" /config/rates.properties
docker compose -f deploy/compose.yaml restart server
```

Leave `DatapackRoot` and `CatalogDirectory` alone. The image sets them to its own read-only tree.

## Backup and restore

The backup is the `login` and `world` schemas. The catalog is loaded from the image at start, and the `report` views are created again at start.

```sh
docker compose -f deploy/compose.yaml exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree.dump
```

Stop the server before a restore. The catalog tables have foreign keys into `world`, and the `report` views read `world`, so the restore drops all four schemas and creates `catalog` and `report` empty. The server fills both when it starts.

```sh
docker compose -f deploy/compose.yaml stop server
docker compose -f deploy/compose.yaml exec -T db psql -U postgres -d l2jfree \
  -c 'DROP SCHEMA IF EXISTS report, catalog, world, login CASCADE' \
  -c 'CREATE SCHEMA catalog AUTHORIZATION l2jfree_world' \
  -c 'CREATE SCHEMA report AUTHORIZATION l2jfree_world'
docker compose -f deploy/compose.yaml exec -T db pg_restore -U postgres -d l2jfree --exit-on-error < l2jfree.dump
docker compose -f deploy/compose.yaml start server
```

`-T` keeps a terminal from altering the dump. On every merge the pipeline dumps a stack, destroys it, restores the dump into a clean stack, and checks that the account and its character are still there.

## Limits

The datapack and the catalog in the image are read-only. A feature that writes into that tree has nowhere to write. Docker's default security profile blocks `io_uring`, so the network core uses epoll unless that profile is replaced.

The image runs as user `10001`, with a read-only root, no added capabilities, and a 4 GB memory limit. The heap defaults to 1 to 3 GB. `deploy/verify-image.sh` checks these properties in CI.
