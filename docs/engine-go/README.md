# Go components

The Java server remains the authoritative world. Go components provide the path
toward a participant-facing gateway and an adapter to that world, allowing server
features to move to Go incrementally behind a stable participant interface.

| Module | Status and responsibility | License |
|---|---|---|
| [protocol](../../engine/go/protocol/README.md) | Active transport and session core; a client SDK is planned separately | [Apache-2.0](../../engine/go/protocol/LICENSE) |
| [gateway](../../engine/go/gateway/README.md) | Documentation-only scaffold for participant connections, projections, and world access | [AGPL-3.0-or-later](../../engine/go/gateway/LICENSE) |
| [bridge](../../engine/go/bridge/README.md) | Bounded world client, profile validation, recovery, and raw receipts; joint endpoint scenarios follow separately | [AGPL-3.0-or-later](../../engine/go/bridge/LICENSE) |

The protocol module contains the `wire` and `proto` packages and the shared
[historical conformance inputs](../../engine/go/protocol/testdata/vectors.json).
Its implementation and tests use only the module's own packages and the Go
standard library. World-specific behavior belongs outside this core.

The `go` CI job checks module boundaries, licenses, public content, formatting,
dependencies, unit and race tests, and vulnerabilities. Codec fuzzing is active:
the configured target must exist and execute successfully. Each module is also
checked with the workspace disabled.

The next stages add the Java world endpoint, the Go world adapter, and the
participant gateway, followed by an end-to-end check in CI. A standalone wire
specification is a separate documentation step. The core is not a runnable
server, and this integration does not announce a release.

Java builds and server runtime checks run in GitHub Actions.
