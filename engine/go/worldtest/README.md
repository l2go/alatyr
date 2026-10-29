# Participant gateway on the Go world

This test composition runs G01–G10 through the existing `gatewaytest` participant
transport, the production gateway/backend, and a real `worldbridge.Client` over
`world.World`. It loads the repository's authored world and uses `memstore`.
The deterministic suite needs no image, database, alternative driver or generated oracle.

The `go` job's workspace and standalone `./...` runs include this package.
Run it directly from `engine/go` with `go test -race ./worldtest`, then repeat
with `GOWORK=off`. The original scripted and bridge-client contract suites keep
their frozen oracles. Go-world assertions are separate and explicit: authored
NPCs, offers and prices differ from the scripted fixture.

The raw-port wrapper records writes and can remove evidence to exercise helper
confirmation, missing source authority, and closure around contact. It forwards
real world observations and never creates gameplay results. World.Run, ports,
backend scopes and participant readers each have an owner and a joined cleanup.

Merchant tests cover atomic balances, live-list reuse by distinct commands, expiry, wrong
incarnations, quantity and carrying bounds, integer overflow, nonstackable rows,
sale eligibility, and saving on departure. Motion tests verify that refreshed
descriptions retire earlier motion and moving producers republish current motion.

`TestOkolitsaParity` retains the accepted participant scenarios on the Go world:
semantic outcomes, causal order, balances, visibility and saved state. Fixed
reward probes supplement the authored catalog without rewriting its rows.

The Go host can set reward-party membership with `World.SetParty`, and apply
trusted environmental harm with `World.Harm`. Neither grants wire authority.
Equipment and healing use exact carried references through `World.Execute`.
Class kind is authored as `world.class.<id>.kind=fighter|mage` in the passport;
missing metadata remains unknown rather than inferred from numeric class IDs.
