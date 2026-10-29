# Participant meaning

The package implements the fixed `alatyr/1` dictionary: 24 fact kinds, 14 verbs,
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
uses that receipt or a conservative original `ProposalBorn`, so delayed
processing and recovery snapshots cannot renew a handle. `Incomplete` revokes
domain completeness rather than turning missing lists into known-empty lists.
Refs and proposal handles are never reused within their owner's lifetime.
Unknown lists remain nil; known-empty lists stay empty; a nil stat remains
distinct from a known zero. Snapshots and proofs own their returned records.

`Source` is an audited producer assertion, not participant configuration. Its
zero value grants no causal authority. Positive dialogue, pickup, learning, and
return evidence also requires an attributable handoff supplied by the trusted
adapter. Dialogue, pickup, and learning require matching effect completion;
return accepts `Returned` only for the handed-off rise attempt because raw rise
does not report Done. Return proof retains an actual live revival, not a
fabricated snapshot outcome. Raw receipt matching and readiness fences belong
to [backend](../backend/README.md); the runtime orders participant records.

All 11 gateway State methods remain. Start, Confirmed, Witness, and CandidateValid
return errors for foreign, nil, or wrong-owner action values; Witness also rejects
foreign update values. The runtime closes that participant with `CloseInternal`
and never logs the value or error text. Apply and Proof already return errors;
Validate returns an explicit refusal. A State is discarded after Apply errors,
including invalid shapes or exhausted bounds, rather than reused partially.

From the gateway package directory:

```sh
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
GOWORK=off go test -run='^$' -fuzz='^FuzzDecodeMeaning$' -fuzztime=10s -parallel=1 ./meaning
```

Conformance reads the unchanged V01-V28 fixture from the exact published protocol
snapshot. Missing or changed fixtures fail the check; there is no fallback copy.
The package imports no bridge or running backend.

License: [AGPL-3.0-or-later](../LICENSE).

`use-item` is verb 14. Intent field 21 is the positive signed 32-bit object ID
of a currently carried inventory row, not a template ID or ground reference.
The world chooses the authored slot and toggles equipment, or launches its
consumable effect. No participant slot, quantity or arbitrary skill is accepted.
It shares the `trade` attempt class with buy/sell to serialize inventory actions.
Goods field 7 is change metadata; field 8, when present, is the essential
equipment slot (-1 means unequipped). Stats fields 8 and 9 are physical attack and known defense.

Done requires `Source.ItemUse`, a matching `Input.Used` attempt attribution, and
a live inventory fact with the same object/template and a decreased quantity or
changed known slot. Delivery receipts, snapshots, unrelated changes, and a
missing attribution grant no success. Healing at full health can still consume
the item; Done proves consumption, not an HP increase. A refused world request
is reported with the existing post-handoff Unknown/Interrupted result and an
explicit world-rejection explanation; local stale/dead requests are Refused.
The pinned protocol's post-contact uncertainty rules remain unchanged.
