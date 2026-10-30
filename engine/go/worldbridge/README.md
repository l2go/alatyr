# Go world port

This AGPL package separates the Go simulation from the raw bridge/1 profile. It
depends on `world`, `bridge`, `protocol`, and the standard library. The simulation
does not import protocol packages. All translation helpers are unexported;
`Client` implements the existing `bridge.Port` without another port interface.

`Open` takes a trusted `Config.World`, its `Catalog`, and a `Load` function for
an existing `bridge.Identity`. The loaded account and character must match that
identity. It returns only after a complete, ready opening snapshot. The host
owns `World.Run`; cancellation of the opening request does not stop an admitted
session. No network listener or character creation is added.
If opening fails after entry and cleanup also fails, `errors.As` exposes an
`OpenError` with the exact `Owner` and both causes. The host retries
`World.Leave(ctx, opening.Owner)`; a failed opening never starts a session owner.

Scenes supply actual coverage and current state. Events supply facts, never
command effects. Immutable commits supply the owner, cause, outcome, and atomic
effect groups. The bridge's completion rules are narrower than the world's:
unproven completion stays unknown, and failed skill persistence does not undo
the world's in-memory commitment. Numeric stage values are not interchangeable.

Translation rejects incomplete essential data and checks sizes without
truncation. Dialogue text is not executable authority. Only issued affordances
can authorize a dialogue choice or lesson, and their original expiry is retained.
Choices bind to an affordance's explicit `action` parameter, not just its target.
Pages, lessons and skills use the event-owned source even if a later scene has
changed. An affordance refresh does not repeat speech or lesson proposals.
Merchant proposals retain their exact world-issued identity, price and expiry.
Buy and sell exchange inventory and currency atomically; their receipts do not
prove participant completion. Quests remain explicitly absent. Money is classified by the authored catalog's `asset` consumption type,
not a fixed item ID; distinct asset rows cannot be collapsed into one purse.

One owner serializes `Write`, source observations and `Resume`. A key and attempt
number are registered before execution. The original caller deadline and queue
budget limit admission, including time waiting for World.Run. They do not revoke
an admitted movement, attack or cast. Explicit world action deadlines remain
independent of this raw queue budget. There is one outstanding Write and
one Resume; concurrent calls of the same method are rejected. The session admits
4096 distinct keys, never reuses them, and retains the latest 64 receipts. The
live backlog is bounded by 256 KiB and 1024 updates, including the unread update.

Overflow loses observation, not the live character. `Resume` restores only the
same incarnation within `ResumeBudget` (30 seconds by default), starts a new
gateway epoch, and returns copied receipts marked `Replay`. It does not Execute
again, renew proposals or change receipt identity. The original recovery deadline
bounds both admission and completion, including internal teleport operations.
Teleport uses the world's
exact fence, an internal Appear, and a complete checkpoint. Revived remains an
original live fact rather than a fact fabricated by the snapshot.

`Close` cancels active operations, waits for the owner, and returns the stable
departure/save result. An unread update can be replaced by the final `Closed`.
`CloseBudget` defaults to 100 milliseconds. Stores must honor their contexts.
There is no automatic persistence retry: after failure the host owns retrying
`World.Leave(ctx, client.Owner())`. Close all ports before stopping `World.Run`;
after a fatal Run exit, join all ports and persist `World.Final` checkpoints
through the same store. The world loop itself performs no persistence I/O.

`TestPortContract` applies the same capability-neutral scenarios to this client
and `bridge.Client` over a synthetic endpoint. It does not assert gameplay parity.
Run `go test -race ./...`, `go vet ./...` and `staticcheck ./...` in this package directory.
Repeat with `GOWORK=off` to check the published protocol pin.

The server application supplies participant encoding, gateway wiring and
durable storage; this package owns the world-port boundary.

This package belongs to the shared [Go server module](../README.md). Protocol
remains a separate module; package dependency directions are enforced by the
repository boundary checker in both workspace and standalone checks.

The authored-world participant scenarios run in [worldtest](../worldtest/README.md)
through the shared gatewaytest driver. Raw receipts are typed evidence delivered
before their associated live facts; participant facts still precede their result.
Proposal birth uses the canonical field 74 and never the training-type field 69.

Go-world NPC roles are source-owned labels: `creature` identifies a monster,
`folk` an ordinary resident, `tutor` a trainer, and `provisioner` a merchant.
The participant host grants Strike/Cast to creatures and Talk/Cast to folk,
tutors and provisioners. The dictionary treats these labels as opaque strings;
a label alone grants no command authority in the world. Contract tests compare
capabilities and command outcomes independently of these source-owned labels.

Item use maps raw verb 14 and positive object field 64 to `world.VerbUseItem`.
The current captured inventory supplies the exact world incarnation. Its receipt
reports Sent only, including immediate equipment changes: a receipt alone never
proves participant completion. Inventory changes carry `UseIntent` from the
originating world intent, including delayed potion launch. The session retains
at most 64 shared intent/key bindings and annotates the matching live event with
raw field 75 (the command Key). Eviction loses proof rather than guessing a key.
This metadata never appears on snapshots or participant facts. The gateway
requires its own matching effect key, live deadline, and audited ItemUse source.
Item change metadata occupies goods field 7; equipment slot occupies field 8.

Choice windows reuse that bounded table and raw field 75 for verb 6. `TradeList`
and `LearnList` carry the original `ChoiceIntent`; attribution also requires the
captured NPC incarnation and command verb. Speech receipts are unchanged, and
windows do not become speech or raw Done receipts. The backend checks the live
packet's matching effect key and original evidence deadline before providing
`Input.Chosen`; meaning then verifies the captured NPC and audited Dialogue
source. Window attribution never appears on checkpoint facts. It does not prove
that a purchase, sale, or skill acquisition occurred.
