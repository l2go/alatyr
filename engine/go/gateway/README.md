# Gateway

This package provides a bounded participant-session runtime. A session loop
serializes projection policy, attempt admission, control, cached outcomes,
checkpoint drain, and world writes. The caller supplies the protocol codec,
world factory, dictionary policy, authenticated identities, and explicit limits.

The runtime supports length-prefixed streams, in-memory pipes, and binary
WebSockets. The HTTP handler checks host and origin before admission and accepts
forwarded identity only from numeric source IPs in `HTTPConfig.ProxyPeers`.
An empty allowlist preserves the historical 127.0.0.1 default. The check uses
the socket peer, never X-Forwarded-For. The host owns proxy authentication and
forwarding-header sanitation.

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

The root runtime imports the protocol module and standard library. It never
imports the world. [meaning](meaning/README.md) supplies the fixed `alatyr/1`
dictionary, protocol reference generations and saved proposal authority.
The [direct adapter](../worldlink/README.md) is the sole production composition
that knows both the world and dictionary. [fixture](fixture) supplies typed test
inputs; [gatewaytest](gatewaytest/README.md) drives participant transports.

`Receiver` lets an in-process world expose its existing bounded mailbox without
another channel queue or pump. Its state observations are typed values.
`Observation.Resolutions` carries exact source-authored commitments separately
from optional inferred witnesses and failures. A terminal result is immutable.
After contact, a precise refusal certifies absence of effects. A missing or
inconclusive asynchronous result retains uncertainty.

A source overflow requests resynchronization rather than scope closure. A fresh
world checkpoint fences the queue and replaces every attached connection's
projection. The gateway continues to own attempt numbers and retained outcomes.
Disconnecting a participant does not depart its character; reconnecting receives
that same incarnation and the retained attempt ledger. Transport write deadlines
bound participants that are not reading.

From this directory, run standalone package checks with:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
```

CI also checks formatting, vulnerabilities, dependency boundaries, and bounded
native fuzzing of authored text translation, the dictionary, and transport framing. Core conformance vectors remain
owned by the protocol module; the gateway does not implement another record codec.

See [Go components](../../../docs/engine-go/README.md).

License: [AGPL-3.0-or-later](LICENSE).

## Player appearance

See [the meaning dictionary](meaning/README.md#player-appearance) for player
appearance fields, presence rules and live/snapshot behavior.

## NPC form

See [the meaning dictionary](meaning/README.md#npc-form) for the NPC form
label, presence rules and live/snapshot behavior.
