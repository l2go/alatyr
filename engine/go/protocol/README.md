# Protocol

This module provides the l2jfree transport and session core.
It depends only on its own packages and the Go standard library.

| Package | Responsibility |
|---|---|
| `github.com/l2go/alatyr/engine/go/protocol/wire` | Bounded binary frames, typed fields, core envelopes, and configurable extension schemas |
| `github.com/l2go/alatyr/engine/go/protocol/proto` | Version negotiation, connection state, control and projection epochs, and attempt outcomes |

Callers supply limits, supported versions, profile identifiers, and extension
schemas. Session operations must be serialized by the caller. These packages do
not start a server, manage sockets, or provide a client SDK.

`Session.Refuse` rejects before contact. After contact, ordinary refusals and
failures remain Unknown and permanently taint the action class. A trusted backend
may instead supply `RefusalEvidence` to `ConfirmRefusal` when it proves that the
entire attempt had no effects. A timely proof permits Sent to advance to Refused
with a dictionary reason and DeliveryNone, releases the class slot, and adds no
taint. It never clears an earlier taint or replaces a terminal outcome. The
live observation must precede the original deadline; processing may reach the
deadline, as with `CommitWitness`, while an expired terminal outcome stays final.
The backend must establish live correspondence for every contacted step; a cached
receipt, timeout, write error or partial delivery supplies no such proof.
`Connection` tracks control and projections, not Result progression. The wire
encoding and the `alatyr/1` name remain unchanged; clients must accept this
additional Sent-to-Refused outcome.

The codec validates a complete frame before returning records. Field presence
is retained separately from zero values. Unknown optional extensions may be
skipped; unsupported essential extensions retain the core disposition rules.
The caller remains responsible for profile admission and applying records.

The immutable historical inputs V01 through V28 are in
[testdata/vectors.json](testdata/vectors.json). V17 and V21 include stream length
prefixes; the other inputs are frames. The `expected` strings are explanatory
annotations. The envelope tests do not implement every profile payload schema.
Other language implementations must read this same file rather than duplicate
the inputs. Additional profile fixtures belong in separate versioned sets.

CI runs vet, unit and race tests, dependency and public-content checks,
vulnerability analysis, fixture integrity checks, and `wire/FuzzDecodeFrame`.
The module has no third-party dependencies. A standalone wire specification
will be published separately; this integration preserves the existing encoding.

See [Go components](../../../docs/engine-go/README.md).

The Go source and test fixtures in this module are licensed under
[Apache-2.0](LICENSE). Copyright 2026 l2go.
