# Participant contract driver

`New` constructs the real gateway from a caller-owned factory and dictionary
policy. `Join` drives Hello, Welcome, control, and checkpoint drain on an owned
participant transport. `Peer` retains every decoded record in arrival order;
`Send`, `Raw`, `Read`, `Result`, `Fact`, and `Drain` never erase earlier records.
`Config` returns explicit synthetic limits, not deployment defaults. See
`ExampleConfig`; hosts keep their own configuration and producer audit.

The contract suite uses one set of scenarios and one adapter on a scripted raw
fixture and the real `bridge.Client` over a controlled peer below its transport.
Ordered canonical participant bytes and numeric handoffs are compared exactly.
Each peer's ordered transcript is retained, including the old scope after
Resume; peers are compared in registration order, without inventing a total
receive order across independent sockets. Only opaque Welcome session IDs are
normalized. Static SHA-256 oracles in
`testdata/contracts.json` also catch common-mode changes; nothing is sorted or
filtered. `GATEWAY_PRINT_ORACLE=1` prints candidate oracles for inspection only;
it never writes or updates fixtures.

| Contract | Test | Pinned port contract IDs |
|---|---|---|
| G01 | `TestG01Dialogue` | target-receipt, talk-requires-separate-witness, choose-requires-separate-witness |
| G02 | `TestG02HelperFailures`, `TestG02ControlTransferBeforeEffect` | helper-refusal-no-automatic-action, quiet-unknown, backend-failure-partial, interrupted-partial |
| G03 | `TestG03SinglePickupAndDuplicate` | handoff-sent; pickup adds its own receipt/live-fact checks |
| G04 | `TestG04TradeRemainsUnknown` | buy-receipt-is-not-a-fact, sell-receipt-is-not-a-fact |
| G05 | `TestG05LearningFullWindow` | learning-requires-separate-witness |
| G06 | `TestG06ReturnAndDrain` | rise-sent-is-not-revival |
| G07 | `TestG07LateRefusal`, `TestG07TerminalSentIsImmutable` | proven zero-effect refusal; terminal Sent is immutable |
| G08 | `TestG08WorldCloseByContact` | transport-lost-before-run |
| G09 | `TestG09DetachAndExplicitResume` | handoff-sent; explicit recovery checks are additional |
| G10 | `TestG10RejectFalseWitnesses` | separate-witness IDs; source, epoch, generation, and deadline checks are additional |
| G11 | `TestG11ProvenRefusalPreservesClass` | a live effect refusal releases its class; a fresh action can complete |
| G12 | `TestG12PlayerAppearance` | explicit player appearance survives live delivery and an observer checkpoint |

G11 uses explicit outcome and cause assertions and the same exact fixture/Client
comparison. Its new semantics do not rewrite the historical frozen inputs.
Only the G07 gateway oracle for the retired post-contact refusal rule changes;
the raw bridge refusal contract remains unchanged.

`TestPinnedBridgeContracts` resolves the bridge package in the same server module and reads its unchanged
15-scenario fixture, checks its hash, and fails on a missing fixture or replacement.
Those port scenarios do not substitute for gateway acceptance. Supplementary
tests cover malformed-frame atomicity, duplicate mismatch, watch permissions,
slow-reader isolation, metadata, receipt timing, and bounded retention.

From the gateway package directory:

```sh
GOWORK=off go test -race -count=1 -timeout=2m ./...
GOWORK=off go test -run '^TestG' ./gatewaytest
```

These are controlled-peer checks. They do not certify a live external endpoint or
its producer exclusivity; the same participant driver is available for that
later integration.

License: [AGPL-3.0-or-later](../LICENSE).

G12 uses semantic assertions and exact fixture/Client parity. Its optional
appearance data does not change any frozen `contracts.json` entry; historical
inputs without an appearance report retain their original bytes.
