# Player-state database

`alatyr-server` owns a dedicated PostgreSQL 18 database. The application role
owns `world`; content stays in authored image files, described in
[catalog](catalog.md). The schema is managed by embedded forward-only goose
migrations through its PostgreSQL Provider and pgx/stdlib.

First startup accepts an empty database, including a pre-created empty `world`
schema and the citext extension. Later starts accept only the application's own
goose history and objects. Foreign schemas, relations, functions, types or
extensions are refused before migration; no baseline or previous-player-data
import is performed. A failed admission does not rewrite the foreign database.

The canonical definitions are the [embedded SQL migrations](../../engine/go/server/internal/migrate/migrations/).
Update this guide with every schema change.

| Table | State |
|---|---|
| `goose_db_version` | Provider-owned migration history |
| `go_object_id` | Singleton durable high-water mark for object ID reservations |
| `player` | Identity, appearance, class, progression, vitals, position and last access |
| `item` | Persisted item identity, template, ownership, location, order/slot and count |
| `player_skill` | Learned skill ID and level per class index |
| `player_shortcut` | Shortcut page/slot, type, target and level per class index |
| `player_quest_variable` | Trusted quest variables |
| `player_name_title_color` | Six-digit hexadecimal display colors |
| `player_recommendation_status` | Recommendation counters and timestamp |
| `player_birthday` | Creation date and gift marker |

Account and character names use PostgreSQL citext equality. The host resolves
both names inside one repeatable-read snapshot and supplies canonical database
spelling to the world-port identity fence. Operator import does not create an
authentication account; admission remains the static participant registry.

A leased connection serializes all reads and writes. A second application
instance cannot acquire ownership; a lost connection is never replaced behind
the lease. Do not run another writer against this database.

`PutCharacter` writes the detached snapshot and its collections in one
transaction, rejecting a conflicting account or item owner. Inventory ordering
is preserved when an item remains in inventory; equipment transitions use the
new slot. `PutLearning` stores the absolute SP balance and learned skills in one
transaction, so repeating the same snapshot does not spend SP again. The host
preserves unsupported template rows, with a live learned level superseding a
hidden level of the same skill ID.

Each process burns 1,048,576 IDs before constructing the world. Reservations
account for player/item IDs and dangling item-shortcut targets; other shortcut
types do not refer to object identity. Exhaustion stops the world with an error.
Unused reserved IDs are not reused after restart.

World.Run never performs database I/O. Leave saves on its caller; shutdown also
freezes detached final checkpoints. The host joins all admitted operations before
writing these remaining snapshots, including after autonomous world failure.
Persistence errors remain errors at process exit.

Back up `world` while the writer is stopped. Restore a matching backup for a
rollback; no Down migration drops player state. See the
[operator runbook](../../deploy/README.md#backup-and-restore).
