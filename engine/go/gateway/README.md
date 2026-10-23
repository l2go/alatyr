# Gateway

This module provides a bounded participant-session runtime. A session loop
serializes projection policy, attempt admission, control, cached outcomes,
checkpoint drain, and world writes. The caller supplies the protocol codec,
world factory, dictionary policy, authenticated identities, and explicit limits.

The runtime supports length-prefixed streams, in-memory pipes, and binary
WebSockets. The HTTP handler checks host and origin before admission and accepts
forwarded identity only from a configured IPv4 loopback proxy. It is not a login
service; a forwarded header from a remote peer is never an identity.

Opening reserves an identity and capacity before calling the world factory,
without holding the shared admission lock across I/O. The admission context
bounds opening but does not own an accepted session. Close cancels sessions,
drains bounded output, joins owned loops, and reports close errors or expiry of
the configured shutdown budget.

State keeps dictionary and evidence policy outside the transport runtime.
Opaque step and update values are local adapter boundaries, not serialized data.
Adapters must validate concrete types and return errors instead of panicking.
Start, Confirmed, Witness, and CandidateValid report invalid adapter values as
errors. The runtime closes the participant with an internal reason and does not
log the value or error text.
Numeric status and journal APIs cannot retain identity strings or frame content.

The module is a library, not a running server. It imports only the pinned
protocol module and the standard library. The [meaning](meaning/README.md)
subpackage supplies the fixed `alatyr/1` dictionary and bounded State. A raw world-port
adapter, backend contract harness, executable, and end-to-end Java verification
are separate changes. No backend is opened until the caller binds a participant.

From this directory, run standalone checks with:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
```

CI also checks formatting, vulnerabilities, dependency boundaries, and bounded
native fuzzing of the dictionary and stream and WebSocket framing. Core conformance vectors remain
owned by the protocol module; the gateway does not implement another record codec.

See [Go components](../../../docs/engine-go/README.md).

License: [AGPL-3.0-or-later](LICENSE).
