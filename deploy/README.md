# Deploy

One Linux image and PostgreSQL 18, started with Docker Compose from a checkout of this repository.

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

The first start takes several minutes. It creates the database passwords, migrates the schemas, and loads the game content. Later starts keep all three. `--wait` returns when the world is loaded and ports 2106 (login) and 7777 (world) accept connections.

| Task | Command |
|---|---|
| Log | `docker compose -f deploy/compose.yaml logs -f server` |
| Update | `docker compose -f deploy/compose.yaml pull && docker compose -f deploy/compose.yaml up -d --wait` |
| Stop | `docker compose -f deploy/compose.yaml down` |
| Stop and delete all data | `docker compose -f deploy/compose.yaml down -v` |

## Settings

Set these in the shell or in an `.env` file next to `deploy/compose.yaml`.

| Variable | Default | Meaning |
|---|---|---|
| `L2JFREE_EXTERNAL_HOST` | `127.0.0.1` | Address the server gives clients for the world. The default admits only a client on the same machine |
| `L2JFREE_BIND` | `0.0.0.0` | Host address that publishes the two ports |
| `L2JFREE_IMAGE` | `ghcr.io/l2go/l2jfree:edge` | Image to run |
| `L2JFREE_JAVA_OPTS` | `-Xms1g -Xmx3g -XX:+UseG1GC` | JVM options. Keep the heap below the 4 GB container limit |
| `L2JFREE_NETWORK_TRANSPORT` | `epoll` | `io_uring`, `nio`, or `auto`. A transport that is not available stops the start |
| `L2JFREE_FLIGHT_RECORDER` | `on` | Keeps the last 12 hours, at most 256 MB, in the log volume |

The first login creates the account. On a public server, set `AutoCreateAccounts = False` in `loginserver.properties` as shown below.

## Configuration

The image ships the default of every key. The same files are in `l2jfree-core/config` and `l2jfree-login/config`. To change a key, put a file of the same name with only the changed keys into the `config` volume. The server reads the files at start, and it logs a warning for a key that no default file knows.

With the stack running, to triple the experience rate:

```sh
printf 'RateXp = 3.\n' | docker compose -f deploy/compose.yaml exec -T server tee /var/lib/l2jfree/config/rates.properties >/dev/null
docker compose -f deploy/compose.yaml restart server
```

Leave `DatapackRoot` and `CatalogDirectory` alone. They point to the read-only tree of the image.

## Backup and restore

Back up the `login` and `world` schemas. The server rebuilds `catalog` and `report` at start.

```sh
docker compose -f deploy/compose.yaml exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree.dump
```

`-T` keeps a terminal from altering the dump. To restore, stop the server first. The `catalog` tables have foreign keys into `world`, so the restore drops all four schemas and leaves `catalog` and `report` empty for the server to fill.

```sh
docker compose -f deploy/compose.yaml stop server
docker compose -f deploy/compose.yaml exec -T db psql -U postgres -d l2jfree \
  -c 'DROP SCHEMA IF EXISTS report, catalog, world, login CASCADE' \
  -c 'CREATE SCHEMA catalog AUTHORIZATION l2jfree_world' \
  -c 'CREATE SCHEMA report AUTHORIZATION l2jfree_world'
docker compose -f deploy/compose.yaml exec -T db pg_restore -U postgres -d l2jfree --exit-on-error < l2jfree.dump
docker compose -f deploy/compose.yaml start server
```

## Limits

The image runs as user `10001` with a read-only root, no added capabilities, and a 4 GB memory limit. The datapack and the catalog are read-only, so `//sortmulti`, which writes multisell files into the datapack, cannot save them. Docker's default security profile blocks `io_uring`.
