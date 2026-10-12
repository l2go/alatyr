# Documentation index

`v3.1.0` is the release of Platform 3.0. v2.5.0 is the final release of the 2.x line, which is retired ([ADR-0008](adr/0008-retire-the-2x-line.md)).

## Current

| Document | Purpose |
|---|---|
| [Roadmap](roadmap.md) | Milestones, exit criteria, and work packages for Platform 3.0 |
| [Architecture](architecture.md) | Context, containers, walking skeleton, pipeline, and login admission as diagrams |
| [Deploy](../deploy/README.md) | The Linux image and the Docker Compose stack |
| [Runbook](runbook.md) | First start, daily checks, backup, restore, update, rollback, incident capture, and stop |
| [Platform module](../l2jfree-platform/README.md) | The launcher, the layout of the distribution, and how the two ports are wired |
| [Risk register](risks.md) | Open risks and their mitigations |
| [Threat model](threat-model.md) | What can go wrong at each trust boundary, what stops it, and what remains |
| [Image verification](image-verification.md) | Signature, provenance, and SBOM of the Platform 3.0 image |
| [Milestone reports](reports/m1-foundation.md) | Plan, result, deviations, and delivery numbers of each closed milestone |
| [Milestone report M2](reports/m2-platform.md) | Result of the Platform 3.0 platform work against its exit criteria, and the deviations |
| [Start-up measurement](reports/startup-measurement.md) | The start of the server under G1 and ZGC, with and without the AOT cache |
| [Architecture decision records](adr/README.md) | One record per decision, with rationale and consequences |
| [Platform 3.0 vision](PLATFORM-3.0-VISION.md) | Target platform and design, revised by the decision records |
| [Database conventions](DATABASE-CONVENTIONS.md) | The rules of the PostgreSQL schemas; the schema test checks them |
| [Data dictionary](database/world.md) | Tables, columns, keys, and an entity diagram per schema, generated from the migrated database |
| [Behavior changes](BEHAVIOR-CHANGES.md) | What differs from the 2.x line for players, operators, and script authors |
| [Correctness audit](2026-Q4-CORRECTNESS-AUDIT.md) | Policy for filing and fixing defects; the testing sections are historical |

## 2.x line (retired)

These describe Windows 10, MySQL 8.4, and two processes. They stay as history.

| Document | Purpose |
|---|---|
| [Operations runbook](OPERATIONS-RUNBOOK.md) | Release acceptance and routine operation of v2.5.0 |
| [Deployment qualification](2026-Q4-UPGRADE.md) | Windows 10 qualification notes |
| [Release verification](RELEASE-VERIFICATION.md) | Checksums and attestations of the retired 2.x releases |
| [Dependency inventory](DEPENDENCY-INVENTORY.md) | Direct dependencies of v2.5.0 |
| [Infrastructure stack](INFRASTRUCTURE-STACK.md) | Stack map of the 2.0 line |
| [Infrastructure vision 2.0](INFRASTRUCTURE-MODERNIZATION-VISION-2.0.md) | Target and execution status of the 2.0 line |
| [Database migration strategy](DATABASE-MIGRATION-STRATEGY.md) | Forward-only SQL changes for MySQL |
| [Logging migration plan](LOGGING-MIGRATION-PLAN.md) | Logging backend and named channels of the 2.0 line |

## Historical

| Document | Purpose |
|---|---|
| [Issue 52 implementation plan](ISSUE-52-JDBC-IMPLEMENTATION-PLAN.md) | Login persistence work completed in v1.5.1 |
| [Infrastructure modernization program](2026-Q4-INFRASTRUCTURE-MODERNIZATION.md) | Summary of the first modernization stages |
| [Original readme](../UPSTREAM_README.md) | Upstream text, kept unchanged |
