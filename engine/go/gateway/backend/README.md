# Raw-port adapter

`New(port, snapshot, Config)` takes ownership only on success. It returns a
`Session`, one gateway `World`, and its actor-owned `State`. The caller supplies
explicit meaning limits, a clock origin shared with `gateway.Config.Origin`,
and audited producer policy. A nil `Source` grants no causal authority. The
default single-effect plan is required; `Policy.Steps` is not accepted here.
See `ExampleNew` for a synthetic complete snapshot.

Only the gateway actor calls State or Write. The bounded pump transfers raw
records and original receive times in order; it never performs business writes.
Receipt keys bind to captured attempt/index/role before `Port.Write` returns.
Target helpers require both their own Done and current target confirmation.
Effect receipts cannot authorize helpers or system steps, and raw Done cannot
authorize participant Done without live, exclusive, timely dictionary evidence.
Optional `Handoff` reports numeric correlation only, never payloads or errors.
`World.Write` retains at most eight raw bindings, including helper and system
writes; expired bindings are removed before another write. This adapter bound
is independent of the configured participant pending-attempt limit.

A post-contact Refused needs a live receipt for the only write of that attempt:
the same command Key, raw N, requested/effective verb and GatewayEpoch, not Replay
or a snapshot, observed at or after original contact and strictly before its
original deadline. The receipt must be StageRefused/DeliveryNone, carry World, Invalid or
QueueExpired (the producer's zero-effect reasons), and contain no effects. For the Go world this is authored by
`worldbridge/receipt.go` from an owned refusal or expired admission with no effect
groups, or from a pre-execution `p.refusal`. No write may have returned an error.
The actor removes the remaining action before it can perform another step.
Scope closure and backend errors retain their existing failure disposition.

| Step role | Refusal authority |
| --- | --- |
| Effect | A live no-effect refusal can certify the attempt only when this was its sole write. |
| Helper | Selection can change the target and intention. Once written, it prevents certifying the whole attempt, even if a later effect is refused. A helper skipped because the target is already confirmed was never written and does not prevent proof. |
| System | N is zero; guarded stop and relocation service are independent operations and never certify a participant outcome. |

The trusted proof reaches `proto.Session.ConfirmRefusal` through
`Observation.Refusals`. Ordinary `Observation.Failures`, missing or late evidence,
write errors and partial delivery keep the existing Unknown and class-taint
rules. A proven refusal releases its slot without adding or clearing taint.
Receipts from snapshots and recovery are diagnostic and cannot create outcomes.

Ready snapshots require explicit complete inventory, skill, and quest lists.
Unknown does not become known-empty. The audited bridge buy/sell packets carry
one complete goods list even when the optional completeness flag is absent.
An explicitly incomplete trade list remains non-actionable, and this rule does
not apply to inventory or other list producers. Recovery preserves original proposal
birth, reference counters, and raw command keys. `Session.Resume(ctx)` is explicit
and bounded by the original detach expiry (at most 30 seconds); failed recovery
closes the port. It does not replay commands or treat recovery receipts as live
evidence. The host must wait for the old scope to end before Resume, bound its
identity registry to at most 64 retained sessions, and close that registry on
shutdown. `Session.Close` joins the scope pump and returns raw closure errors.

The same adapter accepts the scripted fixture and `bridge.Client`. Contract
tests with a controlled raw peer verify participant semantics, not real-world
producer exclusivity. Live endpoint and host integration are separate work.

License: [AGPL-3.0-or-later](../LICENSE).
