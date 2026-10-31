# Participant gateway on the Go world

This test composition runs G01–G12 through the existing `gatewaytest` participant
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

G01 also checks Say opening authored shop, sale, and learning windows: each live
window precedes Done and carries the choice cause. G04/G05 distinguish that
completed choice from the subsequent trade or full-window learning attempt.
The scripted `choose-requires-separate-witness` oracle remains unchanged: a raw
receipt alone is still insufficient. Negative Go scenarios cover foreign NPCs,
wrong keys, snapshots, late windows, and receipt-only delivery.

## Command sequence reliability

`FuzzGatewaySequence` executes a bounded byte program through this composition,
not a model of the gateway. `TestGatewayReliabilitySeeds` and
`TestGeneratedGatewaySequences` replay directed and PCG-generated programs twice.
`TestHostGatewayReliability` in the server module runs the same directed alphabet
through `runtime.openPort`, `hostBackendConfig`, the production gateway limits,
`characterStore` and `hostMemoryStore`. Only its raw observation delivery is
scheduled by the test; the actual producer audit and classification are unchanged.
The driver answers Ping with Pong and advances long intervals in virtual steps
of at most one second, preserving the host's actual heartbeat policy.
Independent input pairs run in parallel, with two test slots in CI. Each pair
still executes both replays serially in separate synctest bubbles, with fresh
worlds, ports, stores and random sources. The immutable authored catalog is
loaded once per test or fuzz owner and shared read-only.
All resources, including readers, raw ports, pumps and World.Run, are joined
inside `synctest`. There is no process-wide recovery handler.

A program has one setup byte, then up to 64 three-byte instructions
`(opcode, argument, variant)`. Incomplete trailing instructions are ignored.
Setup modulo 4 selects the tutor, merchant, hunt, or a dead character. The world
always uses PCG(1, 2). No wall clock, network endpoint, environment setting or
uncontrolled random source selects an operation. Byte programs retain their
meaning when selecting an individual generated subtest.

| Opcode modulo 32 | Operation |
| --- | --- |
| 0–13 | The 14 `alatyr/1` verbs, in registry order |
| 14 | Advance virtual time by 1, 4999, 5000, 5001 or 30000 ms |
| 15 | Focus and Engage in one participant frame |
| 16 | Replay the exact previous Intent, without another world write |
| 17–18 | Add an observing or acting peer; probe its missing command authority |
| 19 | Transfer control to the second acting peer; an observer's invalid request closes its socket |
| 20 | Replace the world subscription, Detach, then explicitly Resume |
| 21 | Detach and reject Resume at the original 30 s expiry; death freezes durable state for exact save comparison |
| 22 | Close the controlling socket after an admitted Cast, retaining an observer; reconnect |
| 23 | Relocate between authored tutor, merchant and hunting positions |
| 24–25 | Environmental self or target death; target variant 1 adds two world observers through decay |
| 26 | Advance 31 s through decay and respawn |
| 27 | Stop World.Run with the scope still open |
| 28–31 | Lose witnesses or receipts, mark observations as snapshots, hold delivery, or release held delivery |

Targets come from current public observations; the argument selects friendly or
hostile objects and rows. Variants also supply foreign IDs, retired generations,
boundary coordinates, unknown skills, invalid choices, foreign lists, extreme
quantities and mismatched prices. Saved dialogue, shop, sale and learning handles
are read from participant facts. Directed programs cover successful chains before
trying malformed arguments, so a stale proposal cannot mask every boundary check.
An empty or unavailable target remains an explicit invalid reference.

Target-death variant 1 enters two controlled characters beside the NPC. The
lower ID selects it; the higher ID must receive TargetCleared before ObjectGone
after its death and decay. Both streams are drained every virtual second and
both characters leave before final shutdown. This source assertion is independent
of participant observation faults. The directed seed runs in both compositions.

`SequencePort` is a deterministic delivery schedule around the real port. The
test releases one available raw update at a time and lets the actors become
quiescent. It preserves every delivered record's order and original receive time.
Observation-loss modes cannot create a witness. Held delivery is bounded at 1024
updates and is discarded at a detached scope fence. Transcripts contain every
received participant record, framed separately and grouped by peer admission
order; only opaque Welcome session IDs are normalized. No sorting of facts,
sequence-number rewriting, timestamp rewriting or filtering of the returned
transcript is used. The harness searches this controlled schedule; it does not
claim exhaustive exploration of all possible concurrent schedules.

The checks follow existing contracts:

| Invariant | Contract and oracle |
| --- | --- |
| Process and owned loops survive | `gateway/README.md`, world and backend lifecycle ownership; any panic fails the test process, any leaked blocked goroutine fails the synctest bubble |
| One immutable terminal outcome per admitted attempt | G02/G07, protocol Sent/terminal rules and gateway cached outcomes; `checkTranscript` distinguishes nonwaiting Sent, progression, exact Intent replay and checkpoint replay |
| Done requires a live timely witness | G01/G03/G05/G06/G10 and `meaning/README.md`; ordered causal fact before Done, same projection epoch and target generation, then independent raw kind, subject, effect key, receipt and original first-contact deadline checks in `checkRawEvidence` |
| Honest refusal | `wire` reason vocabulary; no causal fact, and either no handoff or one live, exclusive no-effect receipt matching the raw command, scope epoch and original window. The actual world command and later commitments contain no effect groups for its intent; observer and stale-control probes also compare the complete world capture |
| Bounded drain | Gateway Status budgets after each step; after joined closure, gateway admission/connection/action maps and backend binding maps remain within their existing budgets and every backend update channel is closed |
| Consistent world and saved state | World resource/character/departure contracts; HP/MP ranges, finite vitals, unique inventory rows, nonnegative balances, exact integer debit/credit arithmetic, one item consumption per world intent, and complete saved-character equality with rounded vitals and departure timestamp |

Full-window learning retains its earlier live candidate and receipt; its current
Known proof is emitted at the original window end. The proof timestamp does not
replace the candidate's original receive time or extend its deadline.

Outcome, raw-evidence, drain and world-state oracle tests include positive
controls and deliberately broken observations, outcomes, accounting, transfers
and saves. Panic and goroutine-leak sensitivity can also be checked by temporary
source overlays; a blanket recover would defeat the first invariant.
G11 checks refusal followed by successful reuse of the same action class, including
the real world's healing cooldown. A purchase rejected after handoff also leaves
trade usable. Missing evidence still causes permanent taint after Unknown.
The raw bridge oracle is unchanged. Only the G07 gateway oracle pinning the old
post-contact refusal rule is revised; all other historical entries remain intact.
The named opcode, verb and fact constants are checked against the meaning
dictionary by `TestSequenceDictionaryCodes`. Drain limits use the configured
participant pending budget and the backend contract's eight raw bindings; the
mutation tests accept each boundary and reject the first value above it.

Native corpus regressions retain the minimal visibility, multi-peer revival and
last-lesson Resume programs; named regressions also exercise each defect directly.

From `engine/go`:

```sh
go test -race -p=1 -parallel=2 ./worldtest
GOWORK=off go test -race -p=1 -parallel=2 ./worldtest
GOWORK=off go test -run='^$' -fuzz='^FuzzGatewaySequence$' -fuzztime=20s -parallel=1 -timeout=2m ./worldtest
```

From `engine/go/server`, run `go test -race -p=1 -run '^TestHostGatewayReliability$'
./cmd/alatyr-server`. Ordinary PR tests run all 15 fixed scenarios, 76 directed seeds, committed
corpus entries and 32 generated programs. In each workspace mode CI invokes
fixed scenarios and other package tests, generated programs, and the native
corpus as three separate test batches, each retaining `-race` and double replay.
Each package invocation must finish within half its timeout. The
existing main-only fuzz job verifies target readiness before searching for new
sequences. Reproduce a retained input with `go test -run
'^FuzzGatewaySequence/<corpus-name>$' ./worldtest`; minimize new failures with Go's
native fuzzing and keep an explicit regression alongside the corpus entry.

`TestG12WorldAppearanceMatrix` exercises all five peoples, both sexes and the
fighter/mystic/unknown branches through a catalog and the real Go-world pipeline.
The shipped catalog has one fighter; test-only catalog copies add explicit mage
and missing-kind templates without changing authored world data. Visibility,
full self refresh and Resume are covered by `TestWorldAppearanceVisibilityAndResume`.
`checkPlayerAppearances` independently compares public appearance fields with
actual captured world views on existing sequence inputs; its mutation tests
reject missing, guessed, mismatched and extra metadata. No new opcode or seed
format is needed, and all previous reliability inputs remain in place.
