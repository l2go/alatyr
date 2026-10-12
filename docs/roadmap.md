# Roadmap

Platform 3.0 is built in three milestones. `v3.0.0` and `v3.1.0` are published. The maintainer names any later version and what it contains ([ADR-0007](adr/0007-release-policy.md)). A milestone is a checkable outcome, not a version. Only one milestone is open at a time. The [Platform 3.0 project board](https://github.com/users/l2go/projects/2) shows the live state of the work. The board of the 2.x program is a closed record and receives no 3.0 cards.

## Milestones

| Milestone | Outcome | Status |
|---|---|---|
| M1. Foundation | Decisions recorded, vision revised, 2.x retired, docs structured, and a pipeline that builds a Linux image and starts it with Docker Compose in CI | Done ([report](reports/m1-foundation.md)) |
| M2. Platform 3.0 | The image runs on PostgreSQL 18 in one process with Netty. An end-to-end smoke test (log in, list the world, enter it, leave) is green in CI. A real client has reached the world | Done except the run with a real client ([report](reports/m2-platform.md)) |
| M3. Release | Runbook, restore rehearsal, threat model, vulnerability policy, signature and SBOM verified, and the tag `v3.0.0` | Everything the pipeline can do is done; the maintainer's runs and the approval are open |

## Exit criteria

M1 is done when all of these hold:

1. The decision records in [docs/adr](adr/README.md) cover platform and delivery, process model, data, network, data access, release policy, and retirement of 2.x.
2. `docs/index.md` lists every document with its status, and the vision matches the records.
3. A pipeline builds the Linux image and runs `docker compose up -d --wait` in CI. The login port answers with its first packet, and the world port is published and gets its listener in M2. Pushes to `main` publish the `edge` and `sha-<commit>` images.
4. The required checks in the `main` ruleset match the new pipeline.
5. The risk register is reviewed.

M2 is done when:

1. Account and character save and restart pass on PostgreSQL 18 in CI. *Met: the image job restarts the server and finds the character.*
2. Login and world run in one process, and the architecture rules report no forbidden dependencies between modules. *Met: the architecture rules and the check of the database roles.*
3. The Netty core passes the frame and cipher conformance tests, and the end-to-end smoke test is green. *Met, also with 24 clients at once.*
4. The suspected concurrency defects listed in the risk register are confirmed or dismissed with tests, and the confirmed ones are fixed. *Met; later reading of the code found more of the same kind, which are fixed and guarded by source tests. The rest is in [R-9](risks.md).*
5. Start-up time is measured in CI and the collector and AOT-cache decision is recorded with numbers. *Met: [ADR-0012](adr/0012-garbage-collector-and-aot-cache.md), [measurement](reports/startup-measurement.md).*
6. The maintainer has run a real client against the `edge` image. *Open.*

M3 is done when:

1. The runbook covers first start, backup, restore, upgrade of the image, and incident capture, and the restore has been rehearsed on a clean host. *The [runbook](runbook.md) is written; the pipeline rehearses the backup, the restore, the update, and the capture on every merge. The rehearsal on a clean host by the maintainer is open.*
2. The threat model is written and checked against the delivered image. *Met: [threat model](threat-model.md), each defence names its test or check.*
3. The vulnerability policy is in force and the scan passes under it. *Met: [policy](../SECURITY.md#vulnerability-policy), a scan on every merge and a daily scan.*
4. The cosign signature and the SBOM verify with the commands in the documentation. *Met: the pipeline runs the commands of the [image verification guide](image-verification.md) after every publication.*
5. The maintainer has run a real client again and approved the release in the `stable-release` environment. *Open. The release workflow (`release.yml`) is ready and passes as a dry run.*

## Work packages

Work is cut into a few large packages along module boundaries, not into small slices.

| Package | Milestone | Content | State |
|---|---|---|---|
| WP1. Decisions and direction | M1 | Decision records, vision revision, roadmap, risk register, docs index, retirement of 2.x | Done |
| WP2. Pipeline and image | M1 | Image on a pinned Arch Linux base, compose stack, start-to-ready check, first-packet probe, publication, ruleset update. Delivered first as a walking skeleton that ran the LoginServer; since WP4 the image runs login and world in one process ([deploy](../deploy/README.md)) | Done |
| WP3. Data | M2 | Schemas, migrations, catalog loader, repository seam for inventory, player, and clan | Done |
| WP4. Modules and one process | M2 | Contract module, admission in process, dependency rules, configuration model, one image process with a role per module | Done |
| WP5. Network on Netty | M2 | Netty pipeline, conformance tests, end-to-end smoke test | Done |
| WP6. Runtime | M2 | JDK collections, thread model, known concurrency defects | Done |
| WP7. Measurements | M2 | Repeatable start-up measurement and the collector decision | Done |
| WP8. Operations | M3 | Runbook, flight recording, restore rehearsal, capture of an incident, threat model | Done; the restore on a clean host is the maintainer's |
| WP9. Release | M3 | Vulnerability policy and scans, verification of signature and SBOM, the release workflow and the tag | Ready; the tag waits for the maintainer |

## Reports

Each milestone ends with a report in `docs/reports/` that states the plan, the result, the deviations, and the delivery numbers from the repository history. The reports are written when a milestone closes.
