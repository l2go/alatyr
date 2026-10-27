# Participant server

This application runs the authored Go world with PostgreSQL persistence and the
public participant gateway. Build it from this checkout: its only replacement
binds the parent module to the same source revision. Client libraries retain a
dependency graph without pgx or goose.

From this directory:

```sh
go build -trimpath -o alatyr-server ./cmd/alatyr-server
ALATYR_DATABASE_URL=postgres://user:password@127.0.0.1/database ./alatyr-server -config server.json
```

Use a dedicated database. Stop the Java server and every other writer before
starting Go. The application holds a PostgreSQL session advisory lease for its
entire lifetime; a second Go server fails admission. A lost connection is fatal
to further writes and is never automatically replaced. Java does not participate
in this lease. Back up the database before changing implementations.

The connection role must own the world schema and have permission to create
citext on a new database. Existing databases must have the complete successful
world Flyway history through 1.10. The embedded goose PostgreSQL Provider accepts
that baseline without modifying Flyway history or deleting unsupported tables.
An empty database gets only Go player-state tables, with no world-content seeds.
Migrations are forward-only; restoring a backup is the rollback procedure.

Example configuration (replace the digest with SHA-256 of a random 64-character
hex token; keep that token private):

```json
{
  "HTTPAddress": "127.0.0.1:8080",
  "StreamAddress": "127.0.0.1:8081",
  "WorldPath": "../../../world",
  "WorldName": "authored-world",
  "DatabaseSeconds": 5,
  "ShutdownSeconds": 15,
  "Participants": [{
    "ID": "walker",
    "Account": "example",
    "Character": "Walker",
    "TokenSHA256": "REPLACE_WITH_64_HEX_DIGITS"
  }]
}
```

The registry grants access only to existing characters; no participant-supplied
account or character name is trusted. Import a trusted Character JSON snapshot
with `-import-character character.json` before serving a new database. Provisioning
never chooses starting equipment, position or progression metadata implicitly.
Account and character bindings use PostgreSQL citext equality; the bridge receives
the canonical stored spelling.

HTTP exposes `/participant` (binary WebSocket), `/healthz` (process liveness)
and `/readyz` (world loop and leased database connection). WebSocket requests
need `Authorization: Bearer <token>` and the matching Origin and Host. Public
access requires a same-host TLS proxy. Set `ProxyHosts` and `ProxyOrigins` to
explicit public allowlists. `ProxyPeers` optionally lists numeric trusted source
IPs; by default the source must equal the numeric HTTP listener address, including
IPv6 loopback. Peer checks never trust X-Forwarded-For. The proxy must overwrite
X-Forwarded-Proto and pass
the bearer token. Both application listeners accept only numeric loopback binds.
Do not put tokens in URLs or logs. Browsers need a trusted proxy that supplies
the Authorization header after authenticating the user.

The stream listener is a trusted-host transport, intended for a local client or
an authenticated encrypted tunnel. Before the normal participant framing, send
exactly the 64-byte token followed by LF within five seconds. The server consumes
exactly those 65 bytes; all following bytes use the existing public frame format.
Never expose the plain stream listener over an unencrypted public connection.

Limits are explicit: 64 registered participants, four connections per participant,
8192 bytes per frame, 128 objects/list rows, eight pending attempts, 64 results,
16 intent burst / eight per second, eight service burst / four per second. A
process reserves 1,048,576 object identities durably before spawning; exhaustion
stops the world with an error, and unused identities are burned on restart.
Welcome carries the same limits and millisecond deadlines used by the codec,
projection, attempts and gateway. The sent diagnostic ring and file diagnostics
are disabled (fields 9 and 31-34 are zero); projection and checkpoint budgets
are each 262144 bytes. Wire and dictionary negotiation use their shared constants.

Shutdown closes admission and listeners, joins participant ports, retries failed
saves using the original Ref, and joins the world. Run freezes detached final
checkpoints on every exit, including autonomous exhaustion. After all port I/O
has finished, the host persists any remaining live or pending characters through
the same compatibility store. It reports persistence errors and fails readiness
when the world is stopped. A final save has the configured ShutdownSeconds budget;
an exhausted or unreachable database can still prevent durable shutdown.

Check all three module roots in workspace and GOWORK=off modes with vet,
`go test -race ./...`, Staticcheck and govulncheck. PostgreSQL tests skip without
`ALATYR_TEST_POSTGRES_DSN`; when set it must name a disposable PostgreSQL 18
cluster whose role can create and drop test databases. Tests create isolated
databases and never use a production database.

The storage and migration packages are internal to this application. Storage
holds the lifetime lease; the command invokes `internal/migrate.Apply` before
constructing the world. Migrations use only the goose PostgreSQL Provider and
an explicit pgx/stdlib connector. The boundary policy pins the ten external
build modules and rejects reverse imports and application packages outside its
matrix. Extra checksums retained by tidy for upstream tests are not build inputs.

The `go-postgres` job sets `ALATYR_REQUIRE_POSTGRES=1`, so a missing DSN fails.
It validates the JSON test stream against the closed test list in
`postgres-tests.txt` beside this guide. A missing, skipped or duplicate
test cannot satisfy that gate. Ordinary Go checks have no database and skip
only the PostgreSQL scenarios.
