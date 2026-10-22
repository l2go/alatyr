# World

An authoritative Go world base, licensed under AGPL-3.0-or-later, with one
module and standard-library dependencies only. It is not connected to a
gateway or world adapter.

`catalog.Load(root)` reads a rendered authored world: `catalog/*.csv` and
`data/stats/**`, never the authoring sources. This base loads player templates,
level gains, items, item XML functions and passive skill XML functions.
Missing class race, ancestry and start positions remain missing.
Defense requiring fighter/mage metadata is partial (`stat.Player.PDefKnown`).

Create a host with `world.New(catalog, store)`, run `Run(ctx)` once, and enter
a ready `Character` at its persisted coordinates. `MoveTo` accepts a world
position and follows a straight line in 100 ms game ticks, retaining exact
horizontal coordinates between ticks. There is no client click adjustment
or terrain provider. `Snapshot` returns a detached value; `Entry.Events`
carries semantic movement and departure events, not protocol messages.

`Leave` closes the event channel and saves a detached snapshot outside the
loop. A failed save can be retried with `Leave`; re-entry is blocked until
it succeeds. Leave all characters before cancelling Run; shutdown does not
implicitly save them. A slow event consumer has its channel closed, without
blocking the loop; the host must still leave that character.
Concurrent saves of the same character return `ErrSaving`; an accepted command
completes before returning even if its context is cancelled in flight.

`memstore` is a concurrent in-memory store. `stat` preserves ordered stat
calculation; CSV template numbers do not generate additional XML functions.
`itemlog` provides the item-change journal, for later gameplay consumers.

not yet: NPCs, spawns, drops, trainers, active skills, zones and map regions; shops; healing items and HEAL_STATIC; hunt rewards (#263); return without restart0; character creation and authored start points; pgstore.
