# Changelog

All notable changes are recorded here, in the format of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed

- GitHub Actions are pinned by commit, and Dependabot keeps the pins current.
- The container package is public: one GitHub Container Registry package cannot keep `edge` private and publish a release tag.
- The front page, the deploy guides, the security policy, and the contributing rules are rewritten. The front page has a vector banner instead of a 2 MB picture, and the security policy names the commands that verify an image.
- The deploy guides change a setting with one command, and the pipeline runs that command and checks that the server reads the file.

### Fixed

- The backup command of the release guide ran without `-T` and dumped the whole database. It now dumps the `login` and `world` schemas, as the restore expects.

### Removed

- The design records, plans, reports, and guides that repeated one another or described the 2.x line. The repository keeps the front page, the deploy guides, the security policy, the contributing rules, the changelog, and the generated data dictionary.

## [3.1.0] - 2026-10-12

### Fixed

- A login refusal closes only the client that held the account when the refusal was decided. A password is replaced only while the verified hash is still stored. The published remote-administration object receives the generated password, and a security card is accepted only when it matches the configured id.
- A disconnect save is offered to the packet queue again instead of running beside a packet. A pet is marked saved only after its insert commits.
- Item weight is kept in a long, so a wrapped product cannot pass the load check. An NPC sale that would pass the adena cap is rejected before anything is destroyed. NPC stock is reserved before the buyer's adena is taken, and a failure after that charge returns the adena and the stock. A buy store takes the buyer's adena for a line before the item moves and returns it if the move fails. A buy store sells only the object id the seller named and skips a line the buyer cannot carry.
- A player trade moves both sides or returns what already moved, and does not tell the clients the trade finished. Manufacture crafts only a recipe the shop lists.
- A manor order is stored only when the seed or crop belongs to the castle, the count is within the sale limit, the price is inside the setting band, and a crop reward type is 1 or 2.
- A failed character creation releases the name. A subclass stays in the database and in memory when its delete rolls back. Replacing a subclass deletes the old row and inserts the new one in one transaction. Effect rows stay on the old class until the new effect list is restored.
- A party-room join leaves the waiting list only when the seat is taken, and a broadcast does not walk the live member list. A clan roster is shown only to its alliance or a GM.

## [3.0.0] - 2026-10-12

### Added

- Decision records for Platform 3.0: delivery, process model, data, network core, data access, release policy, and retirement of 2.x.
- Roadmap, risk register, and documentation index.
- Platform 3.0 project board and issue forms for work packages, decisions, and features.
- Security policy, contributing guide, code of conduct, support guide, and code owners.
- Code scanning, dependency review, OpenSSF Scorecard, and a documentation link check.
- A CI check of the datapack Python scripts with Jython.
- The Platform 3.0 walking skeleton: a `linux/amd64` image on a pinned Arch Linux base with a pinned Temurin 25 JRE, a Docker Compose stack that publishes ports 2106 and 7777, a content check, and a first-packet probe of the login port.
- Publication of `edge` and `sha-*` images with a cosign signature, an SBOM, and build provenance, and a daily job that deletes `sha-*` versions older than 30 days.
- Architecture views as diagrams in `docs/architecture.md`.
- PostgreSQL 18 for the login module: the `login` schema and its Flyway migration, a shared migration runner, a pool that sends text untyped, and integration tests against a real database.
- A decision record for database roles, schemas, and migration at start, and the report of milestone M1.
- The world data on PostgreSQL 18: the `world` schema with ten migrations, the `catalog` schema loaded from 57 CSV files with a SHA-256 revision, the report views, and a lock that keeps a second server off the database.
- A transaction scope: everything that runs on a thread inside `WorldTransaction.run` joins one database transaction, so saving a player is atomic.
- Repositories for the player, the items, and the clans, each with integration tests on a real PostgreSQL 18.
- A check that prepares every SQL statement of the compiled server (688 today) against the real schema, a schema test for the database conventions, and a data dictionary with entity diagrams generated from the migrated database and compared in CI.
- Decision record ADR-0010 for game content as catalog data, the database conventions, and a list of the behavior changes since the 2.x line.
- Monthly Dependabot updates for the image base and the compose images.
- One process for login and world: a `contract` module with `LoginPort` and `WorldPort`, admission and status as Java calls, and a `platform` module that starts both and assembles the one distribution. Architecture rules fail the build when the login module and the world module depend on each other.
- A check that compares every JDBC setter and getter of the game server with the PostgreSQL type of its parameter or column, a test that keeps the number of classes with direct database access from growing, source and bytecode rules that refuse the removed libraries and the update of a map by remove and put, and tests for code that came over without them.
- The limits on the connections from one address are keys of the configuration (`AcceptWarn`, `AcceptReject`, `AcceptSeconds`, and the long-period keys), with the old values as defaults.
- Checks on the running image: many clients at once, what each database role may reach, the scripts that loaded, the errors in the log, and how the server stops. A coverage report on request, and a record of what the platform takes from PostgreSQL.
- A check in the pipeline that no datapack script fails to load and tests of the script that creates the passwords.
- The database counts and explains its statements (`pg_stat_statements`, `auto_explain`), and the deployment guide names the backup and the restore.
- A check in the pipeline that an account and its character survive a restart of the server, and the report of milestone M2.
- A repeatable start-up measurement under G1 and ZGC, with and without the AOT cache, a record of the decision for G1 without a cache, and a ready line that reports the start by step.
- A configuration model with the defaults in the image and the operator's changes in one directory, and `L2JFREE_*` environment variables for the database, the bind address, and the announced address.
- The backup and restore of the deployment guide run in the pipeline: a dump of a stack with an account and a character, a stack destroyed with its volumes, the restore, and the same account finds its character. A script collects the logs, the slowest statements, the database sessions, and a thread dump of the running stack, and the pipeline runs it.
- A vulnerability policy with severity classes, times to fix, and what blocks the `v3.0.0` tag; a Trivy scan of the image on every merge and a daily scan of the published image that fails on a fixed critical or high finding; and a check, right after each publication, that the signature, the provenance, and the SBOM of the image verify with the commands of the new image verification guide.
- The first scan of the published image found twenty fixed high vulnerabilities, all in `captree`, a Go program of the Arch base that the server never runs. The image no longer carries it, and the check of the image content fails if it comes back. The Java libraries had none.
- The image ran on the NIO transport all along: `/tmp` is a tmpfs without the right to execute, so Netty could not load its native libraries and fell back silently. Netty now unpacks them into a directory of its own that may execute code, the transport is `epoll` unless `L2JFREE_NETWORK_TRANSPORT` says otherwise, a transport that is not available stops the start, and the pipeline fails if the ports do not run on epoll.
- The Tower of Naia built three door lists from the pre-open door ids (#135), the telnet GM check accepted any character name and went on after an error (#136), the offline give-item path of the telnet interface never returned its connection to the pool (#137), and the reload of the drops of an NPC after an admin edit left out its custom drops (#138). A test fails the build if an array is grown from another array.
- A runbook for the first start, the daily checks, a backup and a restore, an update of the image and the way back, the capture of an incident, and the stop; the pipeline rehearses the update of the image from the published one to the one of the commit. The compose file gives the server 60 seconds to stop instead of Docker's 10, since the save goes through the players one after the other.
- A threat model for the six data flows of the platform, with the control that shows each defence and the residual risk with a decision.
- A flight recording of the last 12 hours (at most 256 MB) runs all the time into the log volume, `L2JFREE_FLIGHT_RECORDER=off` turns it off, the capture script copies it, and the start-up measurement puts its cost at about half a second per start and 46 MiB of memory.
- A release workflow: it checks that the commit is on `main` and passed the pipeline, verifies the signature, the provenance, and the SBOM of its image, scans it under the policy, takes the notes from the changelog, builds the compose bundle by digest, and stops there unless it is not a dry run; then, after the approval in the `stable-release` environment, it names the image with the version, attests the bundle, and creates the tag and the release.
- The shutdown stops accepting connections first, the packets of one login client run in order, and the disconnect of a world client runs behind the packets it sent, with a grace period of 30 seconds.
- The play keys that the login hands to the world, and the passwords that the optional telnet and remote administration generate, were made with the fast game generator, whose 48 bits of state can be recovered from a few outputs. They come from `SecureRandom` now, and a test fails the build if a session key or a generated password uses the game generator again.
- Passwords are stored as salted PBKDF2-HMAC-SHA256 (600,000 iterations); an account of the 2.x line, which holds an unsalted SHA-1 digest, gets the new form at its next login. The old comparison stopped at the length of the stored value, so an empty stored hash accepted every password; it compares the whole value in constant time.

### Changed

- The vision describes a Linux image delivered with Docker Compose on Docker Desktop, Colima, or Docker Engine, with ports 2106 and 7777.
- The documentation archive carries the whole `docs/` tree.
- The Docker Compose stack runs PostgreSQL 18 instead of MySQL, with passwords generated on the first start.
- The image carries the login and the world in one service. The world connects with its own database role and the login with its own.
- The age limit of the world is applied to a world that is online. The old code returned no limit for an online world.

### Fixed

- A Python class that a datapack script stored in a global ran without its constructor, so no script prepared its state and some AI scripts failed to load. Jython is the final 2.7.4, and the pipeline fails when a script does not load.
- The save of the raid bosses on shutdown never ended, and the heroes and the Olympiad threw an exception while a map was walked and changed; the maps are updated with `put` alone.
- The collections that are walked and changed at once (the decay and attack-stance tasks, the chat definitions, the status filters, the geo editor list, the items on the ground, the warehouse and script caches) are safe to do both.
- The failed logins of one address and the announced connections of the world were counted with lost updates; a non-player character without skills failed when it got its first skill, and one with skills got an error.
- The fort siege clans were deleted with two bound values for one parameter, the dates of the changelog and of the online record were read as text, and the Hellbound variables, which are text, as numbers.
- The log of the server reached the container output only for its section headers.
- Loops that removed from the list or map they walked threw an exception that Javolution's collections had tolerated: leaving a quest, cancelling and unloading quest timers, dissolving a clan or deleting a pet (the items), removing castle upgrades or releasing a clan hall, and casting a skill with more than eight targets. They walk a copy, a test fails the build on a new case, and the timers of one quest no longer disappear when another quest is unloaded.
- A packet that arrived while the per-client queue finished was left unread until the next packet, and an `Error` in one periodic task stopped it for the rest of the process. The queue looks again under its lock, and the wrapper logs the error and keeps the schedule.
- The death of a player aborted when a quest left the death-notification list, the Olympiad cleaned up while matches ran, and the party matching, petition, and fort siege lists were changed by several threads at once.
- Benom never appeared on a new database and the siege event passed no monster to its timers. Quest timers with the same name can no longer replace each other, a one-shot timer no longer interrupts its own thread, and four quests that called `time.sleep` in a handler use a timer or no delay. A repeating Saga timer is cancelled when its monster despawns, and quest spawns leave the spawn table when they decay.
- A new pet failed on its foreign key while its control item waited in the write queue, a save that was rolled back on disconnect was not repeated, and a deleted character kept its name and its place on the account until the next start.
- The Olympiad registers and the game map were changed by the packet threads and the manager thread at once, and two clicks could register a noble twice. A player's `deleteMe` could run twice when the disconnect, a relogin, and the shutdown met, and the shutdown now writes the SQL queue once more after the pools stop.
- A login wrote the whole account row it had read back, so a ban set in between was undone; it writes the time and the address only, as does the last-server update. Two clients that created the same new account at once could replace each other's password, a disconnecting login connection removed the session entry of a newer connection of the same account, and the state of a login connection is visible to all its threads.
- A character whose row could not be written got no answer and kept its id, and the save of a player with a subclass changed the class index of the live player for the time of the save, so a concurrent save of skills or shortcuts could land in the wrong class.
- A save writes the player, the pet, the effects, and the items in one transaction. A save that rolls back leaves the item rows and the flags that remember them as they were, and the next save writes the items (#161).

### Removed

- The faction mod and the file of start values of the Olympiad.
- The dead plain-HTTP distribution repository in the root build file.
- The Windows packaging job and the 2.x release job of the pipeline.
- MySQL from the login module, its SQL files and installer scripts, and the Liquibase compatibility test.
- MySQL from the game server, the build, and the repository: the database installer, the 214 SQL files of the datapack, the update scripts, the dump-based backup, and the table optimizer.
- The optional content mods of the 2.x line.
- The internal login-to-world socket and its protocol, the registration of a game server, the `game_server` table, `servername.xml`, the hex id file, and the `register_gameserver` tool.
- The telnet status server of the login module and its four commands; only the world has a telnet status.
- The separate distributions of the login, the world, and the datapack, and their launcher scripts.
- Features whose data has no home in the schema: the factions, the community board item auction, and the archive of deleted mail.
- The experimental Rebuild id factory and the Compaction option.

## [2.5.0] - 2026-10-05

Final release of the 2.x line. Java 25 bytecode, HikariCP, Jython 2.7.5b1, Logback loaded from `config/logback.xml`, the datapack inside the GameServer archive, unit tests in CI, and the dependency upgrades named in the v2.0.0 vulnerability report. See the [release](https://github.com/l2go/l2jfree/releases/tag/v2.5.0).

[Unreleased]: https://github.com/l2go/l2jfree/compare/v3.1.0...HEAD
[3.1.0]: https://github.com/l2go/l2jfree/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/l2go/l2jfree/compare/v2.5.0...v3.0.0
[2.5.0]: https://github.com/l2go/l2jfree/releases/tag/v2.5.0
