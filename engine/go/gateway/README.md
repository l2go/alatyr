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

The package is a library, not a running server. The root runtime imports only the
protocol module and the standard library. [meaning](meaning/README.md) supplies
the fixed `alatyr/1` dictionary and bounded State. [backend](backend/README.md)
adapts an opened `bridge.Port` and its ready snapshot to that runtime;
[fixture](fixture) supplies a scripted raw port, and
[gatewaytest](gatewaytest/README.md) drives the actual participant boundary.
Gateway and bridge share the [server module](../README.md), which pins only
protocol without replacements. Package dependency rules are enforced by the
boundary checker. The [server application](../server/README.md) supplies the running host.

The adapter binds participant attempt, step index, helper/effect/system role,
command key, and raw attempt before accepting a receipt. Raw Done is not a
participant outcome. Facts retain receive order and precede their results;
asynchronous refusal also works without a Fact. The original contact, deadline,
and shrinking queue budget are not renewed by delayed processing or recovery.
After contact, Refused requires a trusted live proof that the entire attempt
had no effects. `Observation.Refusals` carries that proof separately from ordinary
failures. A valid proof releases the class slot with DeliveryNone and the world's
reason; it never clears a class tainted by a genuine Unknown. A helper already
written, partial delivery, an error or missing evidence retains uncertainty.
Relocation fences invalidate old references. A live revival is held until raw
readiness and participant checkpoint drain, then released once; a timeout cannot
reopen a terminal attempt.

Detach ends a participant scope without closing its retained raw session. Its
owner must explicitly Resume within the original retention budget or Close it.
Recovery creates a fresh participant scope while retaining raw keys and reference
counters. Cached receipts never create a new outcome or replay a command.

From this directory, run standalone package checks with:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
```

CI also checks formatting, vulnerabilities, dependency boundaries, and bounded
native fuzzing of raw event translation, the dictionary, and transport framing. Core conformance vectors remain
owned by the protocol module; the gateway does not implement another record codec.

See [Go components](../../../docs/engine-go/README.md).

License: [AGPL-3.0-or-later](LICENSE).
