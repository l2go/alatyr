# Go components

The Java server remains the authoritative world. Go components will provide a
participant-facing gateway and an adapter to that world, allowing server features
to move to Go incrementally behind a stable participant interface.

This change establishes module boundaries, licenses, and CI checks. All three
modules are documentation-only scaffolds. There is no Go server to start yet.

| Module | Responsibility | License |
|---|---|---|
| [protocol](../../engine/go/protocol/README.md) | Transport and session core, with a client SDK | [Apache-2.0](../../engine/go/protocol/LICENSE) |
| [gateway](../../engine/go/gateway/README.md) | Participant connections, projections, and access to a world adapter | [AGPL-3.0-or-later](../../engine/go/gateway/LICENSE) |
| [bridge](../../engine/go/bridge/README.md) | Go adapter to the Java world endpoint | [AGPL-3.0-or-later](../../engine/go/bridge/LICENSE) |

The protocol core must depend only on its own packages and the Go standard
library. It must not depend on the gateway, the bridge, or Java-specific code.
World-specific behavior belongs outside that core.

The planned order is:

1. Establish the modules, licenses, documentation, and CI checks.
2. Specify the world bridge and review its lifecycle and command outcomes.
3. Integrate the transport and session core with its conformance vectors.
4. Add the Java endpoint, then the Go world adapter.
5. Integrate the participant gateway and run an end-to-end check in CI.
6. Consider a release after the complete path has been verified.

The bridge design and core integration may progress in parallel. No wire format,
world API, or release date is defined by this scaffold.

The workspace lists each module separately. Run Go commands from a module
directory. The `go` CI job vets and checks all three modules, including race
instrumentation and vulnerability analysis. Codec fuzzing becomes mandatory when
the protocol core is integrated; it is explicitly inactive for this scaffold.

Java builds and server runtime checks run in GitHub Actions.
