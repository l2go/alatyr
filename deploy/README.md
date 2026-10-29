# Deploy Alatyr

The stack runs `ghcr.io/l2go/alatyr` and PostgreSQL 18 on amd64 or arm64.
Commands below assume a POSIX shell and Docker Compose. The image carries the
Go executable and authored world; configuration and state stay outside it.

## Prepare a deployment

Start with a fresh dedicated database. Databases from another implementation
are refused before migration. Keep the old deployment and its backups separate.
The new Compose project is `alatyr`; it does not reuse earlier project volumes.

From this directory, prepare operator-owned files:

```sh
mkdir -p config
cp server.example.json config/server.json
cp character.example.json config/character.json
```

Edit `config/server.json`:

- Keep the image's `WorldPath` at `/opt/alatyr/world` and HTTP port at `8080`.
- Set `WorldName`, `ProxyHosts` and `ProxyOrigins` to the intended world and exact
  public Host/Origin values, such as `play.example` and `https://play.example`.
- Set `ProxyPeers` to the numeric source IPs the server actually sees from your
  trusted proxy. A host proxy normally reaches the container from its Docker
  network gateway. Discover it after `docker compose create` with
  `docker network inspect alatyr_default --format '{{(index .IPAM.Config 0).Gateway}}'`.
  A container proxy needs its own explicitly assigned source IP. Do not allow
  untrusted clients as proxy peers; X-Forwarded-For never selects the peer.
- Replace the participant example with your registry. Every entry binds an ID,
  account, character name and SHA-256 digest of a random 64-character hex token.
  Generate a token with `openssl rand -hex 32`, retain it securely, and put only
  its digest in `TokenSHA256`. There is no account registration endpoint.

The TLS proxy must authenticate the user, supply `Authorization: Bearer <token>`,
strip incoming forwarding/identity headers and set `X-Forwarded-Proto: https`.
It must forward WebSocket upgrades, preserve the configured Host, and permit
only the configured Origin. Never place a token in a URL or access log.
The default published address is loopback: `127.0.0.1:8080`.

Edit `config/character.json` as a trusted operator snapshot. The example names
an explicit authored position, class, level and balances; these are example
values, not automatic starting rules. Choose unique positive object IDs and
valid authored templates. The account and character names must match the
registry using PostgreSQL citext equality. Keep the JSON readable by UID 10001;
it carries no bearer token or database password.

## Initialize and start

```sh
docker compose up -d --wait db
docker compose run --rm --no-deps server -import-character /run/alatyr/config/character.json
docker compose up -d --wait
```

The first command creates credentials and an isolated application database role.
Only the database mounts the password volume; only the server mounts its DSN
file. The application role owns `world`, is not a superuser, and cannot create
roles or databases. Goose creates the player-state tables. No game-content
schema is loaded into PostgreSQL: the catalog is read from image files.

Import additional characters only while the server is stopped. A running server
holds a database lease and rejects a second writer. Import updates a trusted
snapshot; it does not invent equipment, rewards or spawn positions. Remove
unneeded import JSON from the mounted directory after provisioning.

## Configuration and operations

| Setting | Default | Purpose |
|---|---|---|
| `ALATYR_IMAGE` | `ghcr.io/l2go/alatyr:edge` | Select an image, preferably by verified digest |
| `ALATYR_BIND` | `127.0.0.1` | Published HTTP address on the Docker host |
| `ALATYR_HTTP_PORT` | `8080` | Published HTTP port; the application still listens on 8080 |
| `ALATYR_CONFIG_DIR` | `./config` | Read-only directory containing `server.json` |

The application reads `ALATYR_DATABASE_URL_FILE` from its secret volume.
Standalone execution also accepts `ALATYR_DATABASE_URL`; choosing both is an
error. The container entrypoint uses `exec`, so termination signals reach Go.
Its root is read-only, capabilities are dropped, and the default memory limit
is 256 MiB (`GOMEMLIMIT=192MiB`); these are deployment limits, not measured RSS.
Startup rejects shutdown budgets that exceed the 60-second Compose stop grace.
`ShutdownSeconds` is available separately to gateway close, failed departure
retries and final persistence. Validation reserves the larger of `DatabaseSeconds`
and 10 seconds for joins, 100 milliseconds per participant for port closure, and
two seconds for process teardown. The sum must not exceed 60 seconds. With one
participant and `DatabaseSeconds` at most 10, the maximum `ShutdownSeconds` is 15;
larger registries or database budgets can require a smaller value.

| Task | Command |
|---|---|
| Status and health | `docker compose ps` |
| Logs | `docker compose logs -f server` |
| Check readiness inside the container | `docker compose exec server /opt/alatyr/bin/alatyr-server -healthcheck http://127.0.0.1:8080/readyz` |
| Apply edited configuration | `docker compose restart server` |
| Stop while preserving volumes | `docker compose down` |

`/healthz` reports process liveness. `/readyz` also checks the leased database
connection and the live world loop. Docker health uses readiness. Shutdown stops
admission, joins port operations and saves participants. A persistence failure or
world ID exhaustion exits with an error. Inspect logs before restarting after
such a failure. Do not remove volumes to repair an operational error.

The plain stream transport is optional and remains restricted to loopback;
Compose does not publish it. Public participant connections use the TLS proxy.

## Backup and restore

Back up player state with the writer stopped, then start it again:

```sh
docker compose stop server
docker compose exec -T db pg_dump -U postgres -d alatyr -n world -Fc > alatyr.dump
docker compose start server
```

Retain the operator configuration and token delivery records securely as well.
The image carries content, not player state. Never publish database dumps or
secret volumes. For an existing database, its credential volumes must stay
paired with that database across container updates.

To restore a world dump into a prepared Go stack, first stop the server. The
following commands replace the current `world` schema; keep its backup first:

```sh
docker compose stop server
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d alatyr -c 'DROP SCHEMA world CASCADE'
docker compose exec -T db pg_restore -U postgres -d alatyr --exit-on-error < alatyr.dump
docker compose up -d --wait
```

The restore retains the application's goose history and role ownership. A fresh
stack creates its own credentials; the dump uses the same `alatyr` role name.
Do not restore another server's database into this stack.

## Update and content

Verify the new image's signature and provenance as described in
[SECURITY.md](../SECURITY.md). Stop the writer, take a backup, change `ALATYR_IMAGE`
to the pinned image digest, then run `docker compose up -d --wait`.
Migrations move forward only; rollback uses the previous image and its matching
backup. Release bundles preserve the Compose project name, so they find the same
volumes when unpacked into a new directory. Copy your operator configuration into
the new directory before starting it.

To run another authored world, mount its complete catalog/data/passport directory
read-only and explicitly change `WorldPath` and `WorldName`. Validate it with
`go run ./tools/world check --world <directory>` before deployment. Content does
not enter the database or alter existing player snapshots automatically.
