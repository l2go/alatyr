# Go components

The participant gateway can run over the Java bridge or the authored Go world.
The Go participant application adds durable PostgreSQL storage behind the same
participant interface; the Java runtime remains a separate deployment.

| Component | Status and responsibility | License |
|---|---|---|
| [server](../../engine/go/server/README.md) | Checkout-built participant application, pgx storage, goose migrations and bounded shutdown | [AGPL-3.0-or-later](../../engine/go/server/LICENSE) |
| [protocol](../../engine/go/protocol/README.md) | Active transport and session core; a client SDK is planned separately | [Apache-2.0](../../engine/go/protocol/LICENSE) |
| [gateway](../../engine/go/gateway/README.md) | Bounded participant runtime, `alatyr/1` dictionary, raw-port adapter, and shared contract driver | [AGPL-3.0-or-later](../../engine/go/gateway/LICENSE) |
| [bridge](../../engine/go/bridge/README.md) | Bounded world client, profile validation, recovery, and raw receipts; [Java endpoint contract and live integration](bridge.md) | [AGPL-3.0-or-later](../../engine/go/bridge/LICENSE) |
| [world](../../engine/go/world/README.md) | Ready-character entry, movement, persistence and authored stat content | [AGPL-3.0-or-later](../../engine/go/world/LICENSE) |
| [worldbridge](../../engine/go/worldbridge/README.md) | Go-world raw port with source-owned facts, receipts, bounded recovery and departure | [AGPL-3.0-or-later](../../engine/go/worldbridge/LICENSE) |

The server packages gateway, bridge, world, and worldbridge share the
[AGPL module](../../engine/go/README.md) `github.com/l2go/alatyr/engine/go`.
Protocol remains a separate Apache-2.0 module. The nested `server` application
module imports the parent from the same checkout with its sole `replace` on `..`.
Only the application requires pgx and goose; the reusable parent keeps protocol
as its only dependency. Package import paths are stable;
server consumers migrate their module requirements to the parent and remove
all former server module requirements, including indirect ones.

The dependency checker denies package imports unless the matrix allows them:

| Source | Allowed component imports (plus the standard library) |
|---|---|
| `protocol/**` | protocol |
| `world/**` | world |
| `bridge/**` | bridge, protocol |
| `gateway` | gateway root, protocol |
| `gateway/meaning` | gateway root, meaning, protocol |
| `gateway/backend` | gateway root, meaning, backend, bridge, protocol; tests also fixture and gatewaytest |
| `gateway/fixture` | fixture, bridge, protocol |
| `gateway/gatewaytest` | gateway packages, bridge, protocol |
| `worldbridge/**` | worldbridge, world, bridge, protocol |
| `server/internal/pgstore` | world, pgstore; pinned pgx, pgxpool and pgconn |
| `server/internal/migrate` | migrate; goose root PostgreSQL Provider API and pgx/stdlib |
| `server/cmd/alatyr-server` | pgstore, migrate, world, worldbridge, bridge, gateway, meaning, backend, protocol |
| application tests | the same matrix plus the internal PostgreSQL test fixture |
| `server/internal/pgtest` | test-only database fixture using migrate and pinned pgx |

Component names include their subpackages except the exact gateway directories.
Unknown package directories, including new gateway descendants, require an
explicit policy entry. The guard inspects every Go file, including inactive
build tags and tests, then validates the resolved graphs in both modes. It
also verifies which module supplies each package; sharing a module does not
grant permission to import another component.

The protocol module contains the `wire` and `proto` packages and the shared
[historical conformance inputs](../../engine/go/protocol/testdata/vectors.json).
Its implementation and tests use only the module's own packages and the Go
standard library. World-specific behavior belongs outside this core.

The `go` CI job checks module boundaries, licenses, public content, formatting,
dependencies, unit and race tests, and vulnerabilities. Codec fuzzing is active:
all eight targets must exist and execute successfully. All three module roots
(`engine/go`, `engine/go/protocol` and `engine/go/server`) receive vet and race tests in the workspace
and with `GOWORK=off`, plus Staticcheck and vulnerability checks with tests.
The workspace also contains the independent `tools/world` module. A separate
`go-postgres` job provisions PostgreSQL 18 and proves rollback, atomic learning,
Flyway baseline compatibility, and participant state after a process restart.
Database tests skip in ordinary Go checks when no test DSN is configured.

The Java endpoint, Go world adapter and participant gateway have separate
implementation and evidence boundaries. The integration job checks the Java
endpoint through the Go client and the participant gateway; deterministic
contract suites cover the shared raw-port adapters. A standalone wire
specification is a separate documentation step. This integration does not
announce a release.

Java builds and server runtime checks run in GitHub Actions.

[Authored-world gateway contracts](../../engine/go/worldtest/README.md) run
G01–G10 on the real Go world through worldbridge and the shared participant
driver in the existing `go` job. The test-only composition package cannot be
imported by the runtime components.
