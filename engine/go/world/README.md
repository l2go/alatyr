# World

An authoritative Go gameplay module, licensed under AGPL-3.0-or-later, with
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

`Snapshot` returns a detached value. `Entry.Events` carries semantic state
notifications, not a wire format, command receipt or effect ledger. The host
must drain events; a slow consumer has its channel closed without blocking the
loop. Channel closure does not automatically remove or save the character.

`Leave` closes events and saves a detached snapshot outside the loop.
A failed save can be retried; re-entry is blocked until it succeeds.
`Logout` also checks combat stance atomically with departure.
An accepted command completes even if its caller cancels in flight.
Leave all characters before cancelling Run; shutdown does not implicitly save.

Verify this module with `go vet ./...` and
`go test -race -count=1 -timeout=60s ./...`.
Tests use virtual time and synthetic fixtures; authored catalog tests read the
repository's CC-BY-SA-4.0 world directly, without copying its data.

not yet: shops; healing items and HEAL_STATIC; hunt-reward parity (#263); automatic return without restart0; character creation and authored start points; pgstore.
