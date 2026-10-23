# Participant meaning

The package implements the fixed `alatyr/1` dictionary: 24 fact kinds, 13 verbs,
neutral event and command values, bounded observation lifetimes, saved proposals,
and an actor-owned `State`. `Codec` supplies the dictionary registry to the pinned
protocol codec; it does not duplicate record framing or primitive decoding.

Construct State with explicit `Config` bounds and trusted `Policy`. Role strings
are opaque labels. A nil classifier grants no target capability, even for a
collectible label. The default strategy performs no backend write for local aim
and focus, confirms any target helper, and supplies one effect write. Backend
strategies may supply their own captured steps through `Policy.Steps`; they must
check concrete step values and return errors, not panic.

`Input` comes only from a trusted backend adapter. `Event.MS` keeps the original
fact time; `ReceivedAt` is gateway-relative backend receipt time. Proposal expiry
uses that receipt, so delayed processing and snapshots cannot renew a handle.
Refs and proposal handles are never reused within their owner's lifetime.
Unknown lists remain nil; known-empty lists stay empty; a nil stat remains
distinct from a known zero. Snapshots and proofs own their returned records.

`Source` is an audited producer assertion, not participant configuration. Its
zero value grants no causal authority. Positive dialogue, pickup, learning, and
return evidence also requires a matching effect completion supplied by the
trusted adapter. Return proof retains an actual live revival, not a fabricated
snapshot outcome. Raw receipt matching, readiness fences, and participant result
ordering belong to the later world-port integration, not to this library change.

All 11 gateway State methods remain. Start, Confirmed, Witness, and CandidateValid
return errors for foreign, nil, or wrong-owner action values; Witness also rejects
foreign update values. The runtime closes that participant with `CloseInternal`
and never logs the value or error text. Apply and Proof already return errors;
Validate returns an explicit refusal. A State is discarded after Apply errors,
including invalid shapes or exhausted bounds, rather than reused partially.

From the gateway module directory:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
GOWORK=off go test -run='^$' -fuzz='^FuzzDecodeMeaning$' -fuzztime=10s -parallel=1 ./meaning
```

Conformance reads the unchanged V01-V28 fixture from the exact published protocol
snapshot. Missing or changed fixtures fail the check; there is no fallback copy.
The package adds no module dependency or running backend.

License: [AGPL-3.0-or-later](../LICENSE).
