# Go world translation

This AGPL module separates the Go simulation from the raw bridge/1 profile. It
depends on `world`, `bridge`, `protocol`, and the standard library. The simulation
does not import protocol packages. All translation helpers are unexported.

Scenes supply actual coverage and current state. Events supply facts, never
command effects. Immutable commits supply the owner, cause, outcome, and atomic
effect groups. The bridge's completion rules are narrower than the world's:
unproven completion stays unknown, and failed skill persistence does not undo
the world's in-memory commitment. Numeric stage values are not interchangeable.

Translation rejects incomplete essential data and checks sizes without
truncation. Dialogue text is not executable authority. Only issued affordances
can authorize a dialogue choice or lesson, and their original expiry is retained.
Trade and quest state is known empty only when the world declares those mechanics
absent. Money is classified by the authored catalog's `asset` consumption type,
not a fixed item ID; distinct asset rows cannot be collapsed into one purse.

Run `go test -race ./...` and `go vet ./...` in this module. The workspace and
standalone module checks exercise the same translation tests.

Not yet: Open, a public client, `bridge.Port` sessions, recovery, shutdown,
participant encoding, database stores, new gameplay, and Java-world parity.
