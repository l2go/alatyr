# Changelog

Notable changes, in [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

### Changed

- Added the command that verifies a release archive against its Sigstore bundle to the security policy.
- Added a one-sentence description to the front page; removed its support notice, which the security policy already states.
- Replaced the front page cards with six engineering decisions and how each is enforced, placed before the architecture; removed the stack line from the banner.

### Fixed

- Releases are titled by their tag, for example `v3.1.1`, instead of `Platform 3.1.1`.

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
[release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v3.1.1...HEAD
[3.1.1]: https://github.com/l2go/l2jfree/compare/v3.1.0...v3.1.1
[3.1.0]: https://github.com/l2go/l2jfree/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/l2go/l2jfree/compare/v2.5.0...v3.0.0
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
