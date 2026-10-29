# World bridge client

This package defines the raw-port contract and a bounded client for compatible endpoints using the
`bridge/1` profile and the shared protocol codec. It depends on the protocol
module and the Go standard library.

The raw world port exposes opening, command handoff, ordered observations,
receipts, recovery, and closure. Open and Opened negotiate the connection;
participant Hello, Welcome, and Control records are not accepted by this profile.

One writer owns the connection. Recovery restores a complete checkpoint before
new commands are admitted. Cached receipts are never replayed as mutations and
commands are not re-executed after ambiguous transport failures.

The caller owns participant admission, observation references, proposal lifetime,
and outcome policy. An endpoint receipt alone does not prove a participant
outcome. Relocation completion remains inside the endpoint; the client
does not acknowledge it with another command.

Core and endpoint conformance inputs are retained in the protocol
module's testdata directory. CI checks bounds, dependencies, race safety,
profile fuzzing, and port-level contract traces with a scripted endpoint.
The production Go server uses worldbridge for its world port. Controlled-peer
tests retain the client contract and shared gateway traces.

See [Go components](../../../docs/engine-go/README.md).

License: [AGPL-3.0-or-later](LICENSE). The shared protocol fixtures remain under
the protocol module's Apache-2.0 license.

The protocol dependency is pinned to one published commit. Tests locate the shared
fixtures through the protocol module directory; they do not download a separate copy.

The client returns raw facts and receipts. Participant outcome policy and gateway
traces remain separate integration work. All returned views transfer read-only
ownership to the caller. Do not modify them. Command keys must remain unique for
the lifetime of a client, including across recovery.

This package belongs to the shared [Go server module](../README.md). Protocol
remains a separate module; package dependency directions are enforced by the
repository boundary checker in both workspace and standalone checks.
