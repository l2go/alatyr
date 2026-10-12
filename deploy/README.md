# Deploy

The server runs in one container beside PostgreSQL 18 and needs Docker Compose. Run every command below from the `deploy` directory of a checkout.

```sh
docker compose up -d --wait
```

The first start creates the database passwords, migrates the schemas, and loads the game content. It takes a few minutes. Passwords and game data stay in Docker volumes, so later starts are quick. The command returns when login answers on port 2106 and the world on 7777.

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Log | `docker compose logs -f server` |
| Update | `docker compose pull && docker compose up -d --wait` |
| Stop | `docker compose down` |
| Stop and delete all data | `docker compose down -v` |

## Settings

Put these in an `.env` file in the `deploy` directory, or set them in the shell.

| Variable | Default | Meaning |
|---|---|---|
| `L2JFREE_EXTERNAL_HOST` | `127.0.0.1` | Address the server gives clients for the world. Set the address of the host when players connect from other machines |
| `L2JFREE_BIND` | `0.0.0.0` | Host address the two ports listen on |
| `L2JFREE_IMAGE` | `ghcr.io/l2go/l2jfree:edge` | Image to run. Set `ghcr.io/l2go/l2jfree:<version>` to stay on a release |
| `L2JFREE_JAVA_OPTS` | `-Xms1g -Xmx3g -XX:+UseG1GC` | JVM options. Keep the heap under the 4 GB container limit |
| `L2JFREE_NETWORK_TRANSPORT` | `epoll` | Or `io_uring`, `nio`, `auto`. If the transport is not available, the server does not start. Docker's default security profile blocks `io_uring` |
| `L2JFREE_FLIGHT_RECORDER` | `on` | Java Flight Recorder keeps the last 12 hours, at most 256 MB, in the log volume. `off` turns it off |

## Server options

The defaults are in [`l2jfree-core/config`](../l2jfree-core/config) (world) and [`l2jfree-login/config`](../l2jfree-login/config) (login). A file in the `config` volume overrides the keys it names in the default file of the same name; put only the changed keys in it. The server reads the files at start and warns about a key that no default file has, usually a typo.

To triple the experience rate:

```sh
printf 'RateXp = 3.\n' | docker compose exec -T server tee /var/lib/l2jfree/config/rates.properties >/dev/null
docker compose restart server
```

`tee` replaces the file, so write all changed keys of one file at once.

The first login creates the account. On a public server, turn this off the same way: `AutoCreateAccounts = False` in `loginserver.properties`.

Leave `DatapackRoot` and `CatalogDirectory` alone: they point into the read-only image.

## Backup and restore

Back up the `login` and `world` schemas. The server rebuilds `catalog` and `report` at start.

```sh
docker compose exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree.dump
```

Keep `-T`: a terminal would alter the binary dump.

To restore, stop the server, recreate the schemas, load the dump, and start the server again. `catalog` has foreign keys into `world`, so all four schemas are dropped, and the server refills `catalog` and `report`:

```sh
docker compose stop server
docker compose exec -T db psql -U postgres -d l2jfree \
  -c 'DROP SCHEMA IF EXISTS report, catalog, world, login CASCADE' \
  -c 'CREATE SCHEMA catalog AUTHORIZATION l2jfree_world' \
  -c 'CREATE SCHEMA report AUTHORIZATION l2jfree_world'
docker compose exec -T db pg_restore -U postgres -d l2jfree --exit-on-error < l2jfree.dump
docker compose start server
```

## Limits

The container runs as user `10001` with a read-only root, no added capabilities, and a 4 GB memory limit. The datapack is read-only, so the GM command `//sortmulti` cannot save multisell files.
