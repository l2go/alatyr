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
Trade and quest state is known empty only when the world declares those mechanics
absent. Money is classified by the authored catalog's `asset` consumption type,
not a fixed item ID; distinct asset rows cannot be collapsed into one purse.

One owner serializes `Write`, source observations and `Resume`. A key and attempt
number are registered before execution. The original caller deadline and queue
budget can only shorten the world deadline. There is one outstanding Write and
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
world shutdown does not implicitly save characters.

`TestPortContract` applies the same capability-neutral scenarios to this client
and `bridge.Client` over a synthetic endpoint. It does not assert gameplay parity.
Run `go test -race ./...`, `go vet ./...` and `staticcheck ./...` in this package directory.
Repeat with `GOWORK=off` to check the published protocol pin.

Not yet: participant encoding, gateway wiring, database stores, new gameplay,
and Java-world parity.

This package belongs to the shared [Go server module](../README.md). Protocol
remains a separate module; package dependency directions are enforced by the
repository boundary checker in both workspace and standalone checks.
