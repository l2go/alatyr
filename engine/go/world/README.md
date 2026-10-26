# World

An authoritative Go gameplay package, licensed under AGPL-3.0-or-later, with
standard-library dependencies only. It is not connected to a gateway or world
adapter. There is no listener, transport codec or database driver.

`catalog.Load(root)` reads the rendered authored world: templates, spawns,
item functions, active and passive skill XML, direct class skill trees,
teachers, drops, authored HTML, zones and explicit map-region restart points.
It never opens authoring sources. Accessors return detached values.
Missing race, ancestry and start positions remain missing. A skill lookup is
exact: an absent level is not replaced with another level.

Create a host with `world.New(catalog, store, options...)`, run `Run(ctx)`
once, and enter a ready `Character` at its persisted coordinates. Supply all
persisted character and item IDs, including offline ones, with
`WithReservedIDs` before spawning. Allocated IDs are never recycled;
exhaustion does not wrap. No items, skills or quests are granted at entry.

The loop owns characters, NPCs, items on the ground, intentions and timers.
Gameplay includes player and NPC visibility, movement, follow, melee attacks,
PDAM casts, skill reuse, posture, regeneration, death, corpse decay and NPC
respawn. Commands during a swing or cast keep one next intention.
`Action` selects or acts on an object; `AttackRequest` attacks a known
monster. Player-versus-player attacks are not supported. Player defense with
missing fighter/mage metadata remains partial; `Self.PDefKnown` reports it,
and damage to such a player is refused.

`MoveTo` accepts a world position and follows a straight line in 100 ms ticks,
retaining exact horizontal coordinates. There is no client click adjustment.
The default `NoGround` adapter keeps coordinates unchanged; `WithGround`
accepts host-supplied height and walk constraints, without bundled terrain data.
`ValidatePosition` keeps authoritative X/Y; nearby observations can update Z
only where the geometry adapter has no known height.
`Teleport` takes an explicit point and removes visibility;
`Appear` restores it and can complete an explicitly requested revival.
Catalog restart points are readable, but no automatic return policy uses them.

Trainer interaction loads the authored page. `Bypass` accepts only its cached
skill-list choice. `Learn` checks the direct class tree, exact level, character
level and SP, then refreshes passive functions. Learning requires a Store that
also implements `PutSkill(context.Context, int32, Skill) error`. The write runs
outside the loop; a failed write returns an error and leaves the learned state
available for `Leave` to save. `Leave` returns `ErrSaving` while that write
is in flight.

Legacy reward algorithms remain damage-share based, with vitality and overhit,
asset items on the ground and other items auto-collected. They do not establish
hunt-reward parity. XP totals accrue without implicit level thresholds.
`WithExperienceLevels` accepts explicit cumulative thresholds for levels 1..N
and returns an error for invalid input; without it, XP does not change level.
`WithItemLog` installs the item journal. Its synchronous writer must be bounded.
`WithAnnouncements` supplies entry notices; the default list is empty.

`Snapshot` returns detached self state. `Entry.Events` carries semantic state
notifications, not a wire format, command receipt or effect ledger. The host
must drain events; a slow consumer has its channel closed without blocking the
loop. `Entry.Subscription.End` distinguishes overflow, leave, stop and stream
replacement. Channel closure does not automatically remove or save the character.
Each delivered event has source-assigned `Time()` and `Seq()` values. The latter
orders publications across the world independently of commit sequences. Scene
markers retain the last publication in `Seq`. Captured state observations and
caller-constructed events are not publications and have zero event markers.
`DialoguePage` and `LearnList` retain their own detached `Dialogue` and original
affordances; `SkillsShown.Known` retains skill completeness and runtime availability.
Consumers do not pair these queued sources with a later capture.
`Affordance.Parameters` is the complete issued parameter set for choices and
lessons. Choose includes an `action` label; a lesson includes `skill` and `level`.
Consumers read lesson costs and capabilities from the issued affordances rather
than a separate skill-list projection.

`Execute` accepts a `Request` with captured `Ref` values, an exact skill level
and an optional monotonic deadline. It returns the same source `Commit` that
the observer receives. Validation failures have a typed refusal reason; loop,
admission and persistence failures return Go errors. Selection never attacks,
talks or collects an item. Selecting the same target completes with
`StageDone` and no groups. A dialogue choice completes with `StageDone` when
its lesson list is issued, including an empty list. Cast retains the requested
target and level even while queued. Deadlines are checked before execution and deferred effects;
started work is not promoted from `StageUnknown` by later notifications.
Unsupported operations are refused before execution.

Trainer pages and lesson lists issue `Affordance` values with stable identities,
captured targets, exact costs and a one-minute lifetime in world time. `Choose`
and `Learn` requests accept only an issued choice; rendering text grants no
authority. A successful learn replaces the lesson list as part of that command.

`Capture` reads self, known objects, all inventory rows, effective skills with
reuse state, and current dialogue in one loop turn. `Scene.End` is its final
marker. Each domain has explicit `Coverage`; a missing source prevents ready.
Trade and quests are declared absent mechanics, not inferred from empty data.
Capture does not refresh visibility, renew choices or replay transitions.
`Subscribe` replaces the stream and captures the same live incarnation atomically.
It can recover observation after overflow, but not after departure or Run-stop.

Every teleport increments a relocation `Fence`, without changing `Ref.Gen`.
Its scene stays not ready until `Execute(VerbAppear)` supplies that exact fence.
Legacy `Appear` remains a trusted-host compatibility call without a fence check.

`WithCommits` installs a bounded synchronous observer of source-authored
transitions. A `Commit` fixes an `Outcome` with ordered atomic `EffectGroup`
values. Each group names its cause: an intent or a world rule. References use
`Ref{ID, Gen}` for incarnations, and timestamps use monotonic world `Time`.
The observer receives detached values, must not call back into the world, and
must filter knowledge before external delivery. There is no retained log.
An absent `Outcome.Intent` means no intent outcome, not a successful command.
Legacy command handoff remains unknown unless its promised transition is proven.

Learning fixes one group containing the SP transfer and skill capability.
`WithDurability` reports the subsequent external skill write separately; an
error does not rewrite the in-memory outcome or claim rollback. `Fact` values
are closed values with typed accessors, not arbitrary payloads.
`WithRandomSource` supplies an exclusively owned random source before spawn;
the default remains crypto-seeded ChaCha8, without a bit-exact replay promise.

`Leave` takes the captured `Ref`, closes events and saves a detached snapshot
outside the loop. A failed save can be retried only by the same incarnation;
re-entry is blocked until it succeeds. A different live or pending incarnation
returns `RefusalError` with `ReasonStale`, inspectable with `errors.As`.
`Logout` takes the same `Ref` and also checks combat stance atomically with
departure. Both operations require a captured incarnation; there is no ID-only
departure API.
An accepted command completes even if its caller cancels in flight.
Leave all characters before cancelling Run; shutdown does not implicitly save.

Verify this package with `go vet ./...` and
`go test -race -count=1 -timeout=60s ./...`.
Tests use virtual time and synthetic fixtures; authored catalog tests read the
repository's CC-BY-SA-4.0 world directly, without copying its data.

not yet: world-to-port adapter and shared port contract; session deduplication and replay; shops; healing items and HEAL_STATIC; parity with the Java hunt-reward rules introduced in #263; automatic return without restart0; character creation and authored start points; pgstore.

This package belongs to the shared [Go server module](../README.md). Protocol
remains a separate module; package dependency directions are enforced by the
repository boundary checker in both workspace and standalone checks.

## Authored merchants and restart

Merchant pages issue only catalog-backed buy choices and the authored sell
choice. Each trade offer binds a live merchant, item, quantity bound, unit price,
and a one-minute lifetime. A committed trade consumes its offers. The owning
world loop checks both balances and builds the complete next inventory before
publishing one atomic resource group. Departure saves that inventory normally.

Buys use merchant_buylist prices without residence tax. Sales require an
unequipped, sellable authored template and return its reference price divided
by two. Currency comes from the unique authored asset template. Quantity is
bounded at 1,000,000; trade lists and resulting inventory allocations at 128
rows. Inventory rows are counted after removing depleted goods and currency;
purchases check the final load after payment. Finite stock and restocking rows
are rejected rather than interpreted as unlimited stock; the current authored
catalog has unlimited stock.

A dead character can restart only through its explicitly authored normal map
region. No destination is invented when a region has none. The raw port completes
the existing fenced Appear transition; a sent return alone cannot prove revival.

Full actor refreshes are followed by fresh movement when the source still moves.
A repeated appearance replaces retained spatial state, including old motion.
See the [participant integration tests](../worldtest/README.md).
