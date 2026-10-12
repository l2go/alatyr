# Architecture decision records

Each record states one decision, why it was made, what it costs, and when to revisit it. A record is immutable once accepted: a later record supersedes it instead of editing the history.

| ID | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-linux-image-delivered-with-compose.md) | Deliver a Linux image with Docker Compose | Accepted |
| [0003](0003-one-process-two-modules.md) | One process with a login module and a world module | Accepted |
| [0004](0004-postgresql-and-the-accepted-data-design.md) | PostgreSQL 18 and the accepted data design | Accepted |
| [0005](0005-netty-network-core.md) | Netty network core adopted without evaluation | Accepted |
| [0006](0006-data-access-layer.md) | Data access: targeted repository seam | Accepted |
| [0007](0007-release-policy.md) | The maintainer names each release | Accepted |
| [0008](0008-retire-the-2x-line.md) | Retire the 2.x line | Accepted |
| [0009](0009-database-roles-schemas-and-migration.md) | Database roles, schemas, and migration at start | Accepted |
| [0010](0010-game-content-as-catalog-data.md) | Game content ships as catalog data | Accepted |
| [0011](0011-configuration-model.md) | Configuration model: defaults in the image, changes in one directory | Accepted |
| [0012](0012-garbage-collector-and-aot-cache.md) | G1 as the garbage collector, no AOT cache in the image | Accepted |
| [0013](0013-what-the-platform-takes-from-postgresql.md) | What the platform takes from PostgreSQL, and what it leaves | Accepted |

## Format

Every record has these sections: Status, Context, Decision, Alternatives considered, Consequences, Revisit when. Statuses are Proposed, Accepted, Superseded by NNNN, and Rejected. A decision made by the maintainer without a comparison says so in its Context.

Number records in order and keep each to about one page. Evidence such as measurements goes into `docs/reports/` and is linked from the record.
