# Milestone report: M2. Platform 3.0

Prepared on 2026-10-07. Planned outcome: the image runs login and world in one process on PostgreSQL 18, the network core runs on Netty, the JDK collections replace Javolution and Trove, the known concurrency defects are tested and fixed, and a pipeline takes a session through login, the world, and logout.

## Result against the exit criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Account and character save and restart pass on PostgreSQL 18 in CI | Met | The smoke client creates an account and a character, the server restarts, and the same account finds its character again (`--existing-characters 1`). Repository tests for player, items, and clans run on a real PostgreSQL 18 (112 integration tests) |
| 2 | Login and world run in one process, and the architecture rules report no forbidden dependencies | Met | Pull request #133; `ArchitectureTest` fails the build when a module depends on the other, on the platform, or on the network core |
| 3 | The Netty core passes the frame and cipher conformance tests, and the smoke test is green | Met | Pull requests #139, #141, #142: both ciphers against independent implementations, the frame decoder and encoder, and a full session (login, world selection, character creation, enter world, logout, second login) before and after a restart |
| 4 | The suspected concurrency defects are confirmed or dismissed with tests, and the confirmed ones are fixed | Met, with one residue | Pull request #143. A test from eight threads lost failed logins (15,998 of 16,000 counted) and now passes; the announced-connection counter and the skills of a creature have tests; the clan, boss, tomb, and event collections are concurrent; the flood manager and the accounts in the world were already under a lock. The maps of monster spawns of the grand bosses are read by timers and written at load, and stay under watch ([R-9](../risks.md)). Reading the code after the report found more of the same kind, mostly loops that remove from the list they walk; they are fixed, a test bans the pattern, and the rest stays open in R-9 |
| 5 | Start-up time is measured in CI and the collector and AOT-cache decision is recorded with numbers | Met | Pull request #144, [ADR-0012](../adr/0012-garbage-collector-and-aot-cache.md), [the measurement](startup-measurement.md): G1 starts in 16.9 s with 1.2 GiB, ZGC in 19.6 s with 2.4 GiB, and the cache saves at most 5 % |

## Delivery

| Measure | Value |
|---|---|
| Pull requests merged | 8 (#132, #133, #139, #140, #141, #142, #143, #144) |
| Time from opening to merge | 7 h 25 min, 43 min, 18 min, 10 min, 29 min, 1 h 23 min, 19 min, and 8 h 46 min (the first waited on conflicts, the last on the long measurement runs) |
| Tests in the build | 230 unit tests and 112 integration tests, all green |
| SQL statements prepared against the real schema in CI | 688 |

Pull request #133 is large (802 files) because it carries the generated catalog data, the schema files, and the retired MySQL scripts; the code is a small part of it. The numbers come from the GitHub API and the CI logs. One person writes and merges, so they describe flow, not team throughput.

## Deviations and lessons

- **The mass conversion of collections needed three CI runs, two of them with compile errors.** The code cannot be built on the maintainer's machine by rule, so the compiler is CI. The runs found the forms the converter did not handle: constructors with an argument, `clear()`, classes that extended a Javolution type, `unmodifiable()`. One check, `instanceof` on a replaced class, compiled and was wrong: it made every skill addition to a non-player character fail. The lesson is to search for the forms of a replaced API, not only for its imports.
- **The first three start-up measurements measured themselves.** The script read the whole container log at every poll, the log grew with every start, and restarts looked 8 s slower than new containers. A comparison of three kinds of start after the same stop found it; reading the end of the log removed the difference. [ADR-0012](../adr/0012-garbage-collector-and-aot-cache.md) records it.
- **The container log was empty.** The server replaces `System.out`, and the console appender of Logback follows it, so `docker logs` showed only section headers. A measurement needed the ready line, which exposed the defect. The appender now keeps the standard output of the start, and the log of Docker is rotated.
- **The vision planned ZGC and the AOT cache.** The measurement did not support either, and the vision was changed to match.
- **The test of the game cipher had a mistake of its own** (the counter of the reference was advanced at the wrong key offset), found by the first CI run that reached it. The server was right.

## Carried to M3

- The pause time of G1 under player load, in the load test of the release.
- The visibility of the container package ([R-13](../risks.md)): the policy says private, the package is public.
- The exemption of dependency bots from the `commit-identity` check, which is a decision of the maintainer.
- Actions pinned by commit SHA, classifiers for `aarch64` natives, and an admission test across the two modules.
- The runbook, the restore rehearsal, and the threat model of M3.
