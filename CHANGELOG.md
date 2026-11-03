# Changelog

Notable changes, in [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

### Added

- The project site is built from this repository: pages and notes under `site/`, the `alatyr/1` reference from the participant dictionary, and releases from this changelog. Its home page runs the server's own world and gateway as WebAssembly in a browser worker, with a visitor and an agent playing side by side.
- `server/live` hosts the world and its gateway in one process and joins participants over in-memory streams; `live.Dial` connects the same minimal participant to a server's stream endpoint, and `live.RunAgent` plays by itself.
- `catalog.LoadFS` loads a rendered world from any file system, such as an archive.

### Changed

- The Go world's composition with the gateway, its limits, role classification, evidence authority and port opening, moved from the server's main package to `server/goworld`, shared by every host of the world.

## [5.0.0] - 2026-11-03

Alatyr 5.0.0 is the first release of the Go server: the authored world on the island of Buyan, including the village of Okolitsa, served through the participant protocol. The Java 3.x line is finished and is not supported.

**Upgrade**

- Create a new database. Earlier databases are not migrated; the server refuses them before migration.
- The image name is `ghcr.io/l2go/alatyr`.
- The participant protocol is `alatyr/1`. Module `engine/go/protocol` is v0.1.1, Apache-2.0.
- This release includes no public game client.

**Known limitations**

- A server admits at most 64 registered participants and shows each participant at most 127 objects besides itself. Both bounds are initial values, not yet measured.
- When the server's CPU is overloaded, a participant's world session can fall behind and the participant is disconnected with `WorldClosed`. A reconnect saves the character and enters it again.

### Added

- Alatyr ships the authored Go world and participant gateway as `ghcr.io/l2go/alatyr`, for linux/amd64 and linux/arm64. Compose supplies PostgreSQL 18, file-based credentials, a static participant registry and operator character import. Native image checks cover readiness, authenticated entry, live shutdown persistence, restart and backup restore. Startup rejects configurations whose shutdown phases exceed the 60-second deployment stop grace.
- The Go world settles per-life hunting contributions and party rewards, supports equipment and healing items, and keeps experience at x1 regardless of stored vitality.
- The world chooses what each participant sees: at most the objects the gateway advertises, first what the participant acts on or what acts on it (its target, current or queued action, dialogue partner, party, anyone targeting, attacking or hating it), then the nearest, with a hysteresis step. One visibility pass replaces at most eight objects with nearer unpinned ones; replacements by pinned objects are not limited. An event about objects reaches a participant only when it sees the event's subject and every object the event acts on.
- Participants can toggle authored equipment and consume healing items through `use-item`. Live intent-attributed inventory changes prove completion; equipment slots and physical attack/defense are visible in participant facts.
- The host grants audited exclusive evidence authority for dialogue, pickup, trade, learning and return, so confirmed participant actions finish as Done, including a talk or pickup completed after a walk to the target. Cast finishes at Sent.
- A dialogue choice can complete when its selected NPC opens an intent-attributed shop, sale, or learning window. Delivery receipts and unrelated windows cannot prove completion.
- `alatyr/1` player facts and snapshots report people, sex and the known fighter/mystic visual branch. Missing class-kind metadata remains explicitly unknown; numeric class IDs and NPC player appearance are not exposed.
- `alatyr/1` NPC appearance facts may carry the template's visual form, an opaque world label. An unset form is absent. Players, drops and empty or illegal tokens do not receive one.
- PostgreSQL persistence saves learned skills and the SP balance in one transaction. A canceled or timed-out request keeps the server's single storage connection. Detached final checkpoints preserve participants when the world stops itself.
- A deterministic participant-command reliability harness checks the real Go world and production host composition, with replayed transcripts, lifecycle and economy invariants, mutation checks, and native sequence fuzzing.
- A manual `soak` workflow runs the production host against PostgreSQL with participant bots for up to 330 minutes and reports heap, RSS, goroutines, sessions, scheduled world timers and timer-drain time.

### Changed

- Deployments begin with a fresh dedicated database managed by goose. Foreign database objects are refused before migration; previous player databases are not imported.
- Release promotion, signatures, provenance, vulnerability scans and image retention use the new image name. Releases promote the tested digest without rebuilding.
- Gateway, bridge, world and worldbridge share one AGPL Go module; the nested server application alone requires database modules. The Apache-2.0 protocol module and its pin remain independent. Go-world contracts exercise the participant gateway with the shared deterministic driver.
- Engage and cast reach the Go world as one command that selects its target. They no longer wait for a separate selection, so they no longer report `TargetUnconfirmed`; the reason remains in the protocol.
- `alatyr/1` clients can receive Refused after Sent when live, exclusive world evidence proves that the entire attempt had no effects. The action class remains usable. A genuine Unknown still taints it for unbound evidence, while evidence keyed to a later attempt completes that attempt.

### Removed

- The previous server implementation, build system, native-client transport, database import path and runtime-specific integration jobs. The shared Go raw-port contract and its controlled-peer tests remain.

## [3.2.1] - 2026-10-23

### Changed

- The repository moved from `l2go/l2jfree` to `l2go/alatyr`, and the project site moved to <https://l2go.github.io/alatyr/>. GitHub redirects the old repository address; the old site address does not. The image name `ghcr.io/l2go/l2jfree` is unchanged. Go module paths moved to `github.com/l2go/alatyr/...`; consumers must update their imports and dependency pins (#303).

- Vitality is off by default, so a new character earns exactly the experience and SP the world records; set `EnableVitality = True` in your `vitality.properties` to bring it back.

- The image is published for linux/arm64 as well as linux/amd64; its base is Debian 13 slim instead of Arch Linux. The distribution adds the native network transports for linux/arm64. This change leaves the world and the server code unchanged.

## [3.2.0] - 2026-10-15

The image runs a new, original world. The world inherited from the 2015 fork is gone from the repository, the image, and the release archive.

### Added

- Okolitsa, an original world on the island of Buyan, under CC BY-SA 4.0: a village with a trainer and a merchant, and a meadow with beasts for the first levels. Its catalog has 13 tables and 114 rows (#247).
- `tools/world`, a Go tool that builds the world from source files that refer to text entries by keys in a single string table. It checks that the built world matches its sources and that every text is printable ASCII (#245).
- The server starts on an empty or partial world and logs one line for each missing component. A missing world or catalog directory stops startup, and the log identifies the configuration key and path (#239).
- CI starts the image on an empty world and on the authored world. On the authored world, 24 clients create a character, fight, pick up coins, buy and drink a potion, learn a skill, and log out and back in (#247, #257).
- CodeQL analyzes the Go code (#264).
- The image check fails when a script fails to load (#265).

### Changed

- The image and the release archive carry the authored world in `/opt/l2jfree/world`. `DatapackRoot` defaults to that directory; `CatalogDirectory` defaults to `/opt/l2jfree/world/catalog`. To run another world, set `DatapackRoot` to its root directory and `CatalogDirectory` to its `catalog/` subdirectory (#255).
- An ordinary monster's rewards reflect the contribution of each solo player or party. Experience and SP are proportional to actual HP damage. Coins and items use contribution weighted by level, with a participation threshold of 10% of the total weight; a solo player or party with at least 60% of the total weight receives the items. Players far above the monster's level have less loot weight. Within a party, experience, SP, and coins are shared equally; items follow the party's loot rules. Only players alive and within reward range when the monster dies receive rewards (#263, #257).
- When a character's inventory loads, an item whose template the world lacks is skipped, with one warning, and its database row is kept (#239).
- Flyway sets the session notice level in a callback instead of `initSql` (#239).
- The Java engine moved to `engine/java`; module artifacts keep their names (#267).
- The security policy names the command that verifies a release archive; the front page gained a one-sentence description, the engineering cards, the trademark statement, and links to the project site.

### Fixed

- Killing a monster gave no experience or SP (#263).
- A party share of experience was applied twice, and a party lost the coins left over from an equal split (#263).
- A merchant failed in a world without castles (#242). Entering the world failed without a dimensional rift (#251) and logged errors for lairs without a zone (#253) and for castles without a zone (#259). The hourly door task logged an error in a world without that door (#257).
- A script rejected before loading, such as a Java script without bytecode, was missing from the loading summary (#249).
- A broken extensions file was reported as missing (#243).
- Releases are titled by their tag, for example `v3.1.1`.

### Removed

- The world inherited from the 2015 fork: 17,129 files of data, scripts, and pages (#255). The Python syntax check went with its scripts.
- The images and archives of releases before 3.2.0.
- `UPSTREAM_README.md`; it remains in the history (#267).

## [3.1.1] - 2026-10-12

Gameplay is unchanged from 3.1.0. This release updates documentation and delivery checks.

### Changed

- Pinned GitHub Actions by commit; Dependabot maintains the pins.
- Made the container package public, including `edge` and release images.
- Shortened the README, deploy guides, security policy, and contributing rules.
- Added CI checks for the configuration commands in the deploy guides.
- Added `l2jfree-<version>-compose.tar.gz.sigstore.json` beside the release archive and checksum.
- Limited image-retention write permissions to the cleanup job.
- Removed `maven-wrapper.jar`; the wrapper downloads Maven directly.

### Fixed

- The backup command of the release guide uses `-T` and dumps only `login` and `world`, as the restore expects.

### Removed

- Obsolete 2.x guides and duplicated plans, design records, and reports.

## [3.1.0] - 2026-10-12

### Fixed

- Login refusals no longer close a newer connection. Password changes check the verified hash; remote administration receives its generated password; security cards require the configured id.
- Disconnect saves rejoin the packet queue. Pets are marked saved only after a successful insert.
- Fixed item-weight overflow and NPC sales above the adena limit.
- Fixed stock reservation and refunds for failed NPC purchases. Buy stores validate the selected item and the buyer's capacity, and refund failed transfers.
- Failed trades roll back transferred items and no longer report success. Manufacture accepts only recipes listed by the shop.
- Manor orders validate the castle, sale limits, price range, and reward type.
- Failed character creation releases the name. Subclass replacement is transactional; failed deletion preserves the subclass; effects remain on the old class until restoration.
- Party-room joins update the waiting list only after taking a seat; broadcasts use a stable member list. Clan rosters are visible only to allies and GMs.

## [3.0.0] - 2026-10-12

A 2.x database cannot be migrated; start with a fresh database.

### Added

- A `linux/amd64` Docker image on pinned Arch Linux and Temurin 25, delivered with Docker Compose. Login and world run in one JVM on ports 2106 and 7777.
- PostgreSQL 18, separate database roles, startup migrations, and atomic saves. Game content is loaded into `catalog`; players, items, and clans are stored in `world`.
- A typed login/world contract, architecture checks, and a startup lock against a second server using the database.
- Integration tests, SQL preparation and JDBC type checks, and a generated data dictionary checked in CI.
- Signed `edge` and `sha-*` images with SBOMs and provenance; retention of `sha-*` images for 30 days.
- Release verification, image scanning, a compose bundle pinned by digest, and publication after maintainer approval.
- CI checks for concurrent clients, role isolation, datapack script loading, restart persistence, configuration overrides, backup/restore, image updates, and graceful shutdown.
- Configuration overrides, connection limits, a 60-second container stop timeout, and a 30-second client shutdown grace period.
- Query statistics and slow-query plans, incident capture, startup measurements, and a rolling 12-hour flight recording capped at 256 MB.
- Security and contribution policies, issue forms, code owners, scanning, Dependabot updates, and documentation link checks.
- Design records, architecture views, plans, and reports, later removed in 3.1.1.
- Secure random session keys and generated administration passwords. Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations; legacy account hashes are upgraded at login.

### Changed

- Replaced MySQL with PostgreSQL and separate module roles; database passwords are generated on first start.
- Combined login and world into one service. Deployment uses Docker Desktop, Colima, or Docker Engine.
- Enabled epoll by default with an executable directory for native libraries; unavailable transports stop startup. G1 without an AOT cache is the default.
- An online world reports its age limit in the server list; before, it reported none.

### Fixed

- Restored datapack script initialization with Jython 2.7.4 and checked script loading in CI.
- Fixed shutdown loops and concurrent collection changes affecting raids, Olympiad, parties, petitions, sieges, items, warehouses, scripts, and quest timers.
- Fixed lost connection-count updates, empty NPC skill handling, and SQL parameter/type mismatches.
- Restored server logs to container output. Packet queues no longer leave packets unread; periodic tasks continue after a logged error.
- Fixed Tower of Naia door lists, Benom spawns and siege timers, quest timer collisions, blocking quest handlers, and stale quest spawns.
- Fixed GM checks and connection leaks in telnet administration, and custom drops missing after an NPC reload.
- Fixed pet creation, rolled-back saves, stale character names, duplicate Olympiad registration, and repeated player deletion. Shutdown flushes the remaining SQL queue.
- Login updates preserve concurrent bans and passwords; stale disconnects no longer remove newer sessions. Failed character creation replies and releases its id.
- Subclass saves no longer change the live class index. Failed item saves preserve rows and save flags for retry.
- Password comparisons check the whole value in constant time; an empty stored hash no longer accepts every password.

### Removed

- MySQL, its installers, SQL files, update scripts, backups, table optimizer, and Liquibase compatibility test.
- Separate module distributions, launchers, the login/world socket protocol, server registration, hex ids, and login telnet status.
- Windows packaging, the 2.x release job, the dead HTTP dependency repository, and optional 2.x content mods.
- Factions, community-board item auctions, deleted-mail archives, the Rebuild id factory, the Compaction option, and the Olympiad start-values file.

## [2.5.0] - 2026-10-05

Final 2.x release: Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback configuration,
the bundled datapack, CI unit tests, and dependency upgrades. See the
[release](https://github.com/l2go/alatyr/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/alatyr/compare/v5.0.0...HEAD
[5.0.0]: https://github.com/l2go/alatyr/compare/v3.2.1...v5.0.0
[3.2.1]: https://github.com/l2go/alatyr/compare/v3.2.0...v3.2.1
[3.2.0]: https://github.com/l2go/alatyr/compare/v3.1.1...v3.2.0
[3.1.1]: https://github.com/l2go/alatyr/compare/v3.1.0...v3.1.1
[3.1.0]: https://github.com/l2go/alatyr/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/l2go/alatyr/compare/v2.5.0...v3.0.0
[2.5.0]: https://github.com/l2go/alatyr/releases/tag/v2.5.0
