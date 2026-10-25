# Go components

The Java server remains the authoritative world. Go components provide the path
toward a participant-facing gateway and an adapter to that world, allowing server
features to move to Go incrementally behind a stable participant interface.

| Component | Status and responsibility | License |
|---|---|---|
| [protocol](../../engine/go/protocol/README.md) | Active transport and session core; a client SDK is planned separately | [Apache-2.0](../../engine/go/protocol/LICENSE) |
| [gateway](../../engine/go/gateway/README.md) | Bounded participant runtime, `alatyr/1` dictionary, raw-port adapter, and shared contract driver | [AGPL-3.0-or-later](../../engine/go/gateway/LICENSE) |
| [bridge](../../engine/go/bridge/README.md) | Bounded world client, profile validation, recovery, and raw receipts; joint endpoint scenarios follow separately | [AGPL-3.0-or-later](../../engine/go/bridge/LICENSE) |
| [world](../../engine/go/world/README.md) | Ready-character entry, movement, persistence and authored stat content | [AGPL-3.0-or-later](../../engine/go/world/LICENSE) |
| [worldbridge](../../engine/go/worldbridge/README.md) | Go-world raw port with source-owned facts, receipts, bounded recovery and departure | [AGPL-3.0-or-later](../../engine/go/worldbridge/LICENSE) |

The server packages gateway, bridge, world, and worldbridge share the
[AGPL module](../../engine/go/README.md) `github.com/l2go/alatyr/engine/go`.
Protocol remains a separate Apache-2.0 module. Package import paths are stable;
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
all eight targets must exist and execute successfully. Both module roots
(`engine/go` and `engine/go/protocol`) receive vet and race tests in the workspace
and with `GOWORK=off`, plus Staticcheck and vulnerability checks with tests.
The workspace also contains the independent `tools/world` module.

The next stages add the Java world endpoint, the Go world adapter, and the
participant gateway, followed by an end-to-end check in CI. A standalone wire
specification is a separate documentation step. The core is not a runnable
server, and this integration does not announce a release.

Java builds and server runtime checks run in GitHub Actions.
