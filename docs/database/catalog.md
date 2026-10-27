# Authored catalog

The Go server reads authored content from `WorldPath` with `catalog.Load`.
There is no PostgreSQL catalog schema. Player state belongs to
[world](world.md); image content is immutable and is not part of a player dump.

The repository's [world directory](../../world/) contains CSV catalog tables,
XML stat/zone definitions, dialogue pages, a passport and the content license.
[tools/world](../../tools/world/) renders sources and checks that the public
output matches them. Image verification compares every shipped content byte
with `world.properties`, `LICENSE`, `catalog/` and `data/` in the checkout.

| Source | Responsibility |
|---|---|
| `world.properties` | Authored-world identity and metadata |
| `catalog/` | Templates, spawn records, skill-learning tables and merchant offers |
| `data/stats/` | Item and skill stat definitions |
| `data/zone/` and other authored data | Geometry-independent world regions and zones |
| `data/html/` | Authored dialogue presentation and issued choices |

The Go [catalog package](../../engine/go/world/catalog/) validates the supported
content contract. Runtime mechanics interpret the loaded values; the database
does not seed or reconstruct content. Unsupported templates in stored character
snapshots remain durable but do not gain live references or affect stats.
