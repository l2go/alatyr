# World integration contracts

This package exercises the production participant gateway, direct world adapter,
authored catalog and authoritative world in one process. It does not implement
another world, another record codec or a raw transport between server packages.

The G-series scenarios cover dialogue, pickup, trading, learning, return,
terminal Sent commands, world closure, reconnects, precise refusals, player
appearance and NPC form. Mechanical tests independently check inventory,
equipment, healing, skill levels, progression, death and save state.

`ExerciseSequence` replays the committed byte-program corpus. The source receiver
releases real publications at deterministic test barriers without a producer
goroutine. The oracle matches participant Done against the exact source intent
and its commitment or continuation. It also checks immutable results, source
appearance, economy, incarnation retention, replay and persisted state.
Complete ordered participant transcripts are compared byte for byte on replay.

The historical raw-receipt fault opcodes retain their corpus byte values; they
no longer introduce a removed protocol. Mailbox-specific tests exercise actual
coalescing, ordered-event overflow, retained outcomes and resynchronization.
