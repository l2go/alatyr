# Protocol

This module provides the l2jfree transport and session core.
It depends only on its own packages and the Go standard library.

| Package | Responsibility |
|---|---|
| `github.com/l2go/l2jfree/engine/go/protocol/wire` | Bounded binary frames, typed fields, core envelopes, and configurable extension schemas |
| `github.com/l2go/l2jfree/engine/go/protocol/proto` | Version negotiation, connection state, control and projection epochs, and attempt outcomes |

Callers supply limits, supported versions, profile identifiers, and extension
schemas. Session operations must be serialized by the caller. These packages do
not start a server, manage sockets, or provide a client SDK.

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
