# Threat model

What can go wrong with Platform 3.0, for each place where data crosses a trust boundary, what stops it today, and what remains. The method is STRIDE (spoofing, tampering, repudiation, information disclosure, denial of service, elevation of privilege). Every control names the test, the check, or the setting that shows it; a control that has none is not listed as one.

The model describes the image as the pipeline builds it. It does not cover the host that runs Docker, or the players' machines.

## Assets

| Asset | Why it matters |
|---|---|
| Accounts and their password hashes | A takeover gives the attacker every character of the account |
| Characters, items, and clans | The progress of players; the reason they play |
| The play key | The one secret that lets a client enter the world as an account |
| The database passwords | Reach to every table of a module |
| Availability | A world that is down is the damage players notice |
| The image | What runs on the operator's host, with the operator's data |

## Trust boundaries and data flows

```text
players ──2106──▶ login ┐
players ──7777──▶ world ┴─ one process ──▶ PostgreSQL (login role, world role)
operator ──▶ Docker host ──▶ image, volumes, secrets, configuration
GitHub Actions ──▶ registry ──▶ operator
```

The login and the world share a process (ADR-0003) and meet only through the contract module. The two database roles each reach their own schema.

## 1. Player to login (port 2106)

| | Threat | Control | Residual |
|---|---|---|---|
| S | Guessing a password | An address that fails `LoginTryBeforeBan` (10) times is blocked for `LoginBlockAfterBan` seconds (600). The count is tested from eight threads (`FailedLoginCountingTest`) and resets on a good login (`LoginFailureResetTest`) | A guesser behind many addresses is not stopped; no second factor exists in the protocol |
| S | Taking an account by creating it first | `AutoCreateAccounts` is a setting; the creation of one name by two connections at once is serialized | The default is `True`, as in every L2J server: anyone who connects creates an account. **A public server sets it to `False`** |
| T | Changing a packet on the way | The protocol has no integrity check | Protocol-fixed. Accepted |
| I | Reading the password on the way | The password travels in an RSA block with a 1024-bit key and no padding (R-15) | Protocol-fixed. Accepted: the login port should be offered only to players, with a firewall filter in front of it if the host allows |
| I | A stolen database exposes passwords | The hash is stored, never the password: salted PBKDF2-HMAC-SHA256 with 600,000 iterations for every new password (`PasswordHasherTest`). An account of the 2.x line, which holds an unsalted SHA-1, gets the new form at its next login | An account that never logs in again keeps its SHA-1 hash |
| R | Denying a login | The `login` and `login.try` log channels name the account and the address | The address behind the Docker gateway is one address (R-4) |
| D | Flooding the port | Per-address limits on connections (`AcceptWarn`, `AcceptReject`, and the long-period keys), per-connection limits on packets and errors, a frame size of at most 64 KiB; the accept limits are tested (`NetworkServerTest`) and the 24-client load of the pipeline passes with raised limits | Many clients behind one address share its limit; an attacker with many addresses is a job for the host firewall |
| E | Entering as another account | The play key comes from `SecureRandom` since this model was written; a test fails the build if a session key uses the game generator again (`SourcePatternsTest`) | None known |

## 2. Player to world (port 7777)

| | Threat | Control | Residual |
|---|---|---|---|
| S | Entering without the login | The world admits only an account with a play key that matches the one the login issued, under one lock, and refuses an account that is already in the world (`PlaySessionAdmissionTest`, `AdmissionRoundTripTest`) | The key is 124 bits of `SecureRandom`; it travels in the clear in the protocol |
| T | Sending packets that no client sends (duplication, impossible values) | The packet handlers check state and ownership of what a packet names; the review of the persistence and the runtime paths found no injection | The checks are the old ones, 125,000 lines with 1.9 % test coverage: expect more defects of the kind the review found. The pipeline guards patterns, not behavior |
| I | Reading other players' data | The world sends what the protocol sends | Protocol-fixed |
| D | A malformed frame, a flood of packets, a client that never reads | Frame limits, per-connection packet and error limits, a write buffer limit that drops a client that does not read (`FrameDecoderTest`, `NetworkServerTest`) | A valid but expensive packet is a game-design matter |
| E | A player runs GM commands | Commands check the access level of the account; the level lives in `login.account` and is set by the operator (`LoginManagerAccessLevelPostgresTest`) | Whoever writes to the login database is a GM. The roles keep the world from doing it |

## 3. Login to world (in the process)

| | Threat | Control | Residual |
|---|---|---|---|
| S, E | The world reads or writes accounts, or the login reads characters | The contract has no dependency; architecture rules forbid the modules from knowing each other; the database roles are checked on the image: each role is refused the other's schema (`check-roles.sh`) | A fault or a compromise in one module reaches the memory of the other (R-16). The process exits non-zero when the start fails and the compose policy restarts it |
| D | A deadlock in the world stops the login | A thread dump can be taken without stopping the server (`capture.sh`); a flight recording of the last 12 hours is kept in the log volume and read after the incident; the readiness endpoint reports both ports | Accepted by ADR-0003. A second process is the answer if it ever matters |

## 4. Server to PostgreSQL

| | Threat | Control | Residual |
|---|---|---|---|
| S | Using the password of another role | One password per role, generated at the first start into a volume, read from a file, never an environment value (`test-init-secrets.sh`) | Whoever can read the `secrets` volume has both |
| T, E | SQL injection | The statements are fixed text with bound values; the review found no SQL built from player input in the server or the login; 688 statements are prepared against the real schema, and every setter and getter is compared with the column type | New code can break this; the checks run on every merge |
| I | Reaching PostgreSQL from outside | The database port is not published (`compose.yaml`) | The host's own users reach the Docker network |
| D | A slow statement holds the pool | Pool wait ceiling of 30 seconds; the database logs plans of statements over 500 ms (`auto_explain`) and counts all statements (`pg_stat_statements`) | The pool has no metrics endpoint yet |
| T | Losing data | One transaction per player save, a lock that keeps a second server off the database, a restore that is rehearsed on every merge | Items are written by a queue outside the player transaction: a crash can split a row from its items (B-03 of the review) |

## 5. Operator to the image and the host

| | Threat | Control | Residual |
|---|---|---|---|
| E | A break-out from the container | The process is not root (10001), the root file system is read-only, all capabilities are dropped, no new privileges (`compose.yaml`, `verify-image.sh`) | Docker's own isolation |
| I | Secrets in a log or in the image | Secrets are files in a volume; the image holds none; the entry point stops when a file is missing | A password in the operator's own configuration file |
| T | An operator's file changes behavior | The operator directory holds properties only; the datapack and the catalog are in the read-only tree | `DatapackRoot` is a key an operator can point to a directory with scripts, and scripts run with all the rights of the process. The operator is trusted |
| E | The optional telnet and remote administration | Both are off by default, their ports are not published, and their generated passwords come from `SecureRandom` | If an operator turns one on, its password travels in the clear and is written to the log |
| I | The readiness endpoint | It listens on `127.0.0.1` inside the container | None |

## 6. Supply chain

| | Threat | Control | Residual |
|---|---|---|---|
| T | A changed base, JRE, or dependency | The base image and the PostgreSQL image are pinned by digest, the JRE by SHA-256; Dependabot updates them; the dependency review runs on every pull request; code scanning runs (`build.yml`, `dependency-review.yml`, `codeql.yml`) | GitHub Actions are pinned by tag, not by commit (open; maintainer) |
| T | A swapped image | Every image from `main` is signed (cosign, keyless), has provenance and an SBOM, and the pipeline verifies all three right after the push ([commands](image-verification.md)) | An operator who does not verify gets no benefit |
| I | A known vulnerability | A scan on every merge and a daily scan of the published image. The daily scan and the release job fail on a fixed critical or high finding in a package the image runs ([scan](../SECURITY.md#vulnerability-scan)). The first scan found twenty findings in a program of the base that the server never runs; it was removed | A vulnerability that is not yet known |
| S | A fake release | Tags `v*` are immutable (a repository ruleset), and the release job runs in the `stable-release` environment | Who may approve there is the maintainer's setting |

## What this model changed

Writing it found that the play keys and the passwords for the optional services were made with the fast game generator, whose state can be recovered from a few outputs. They use `SecureRandom` now, and a test keeps them there.

## Open decisions

| Decision | Owner | Note |
|---|---|---|
| `AutoCreateAccounts` default | Maintainer | Keep `True` for a private server; the runbook tells a public server to turn it off |
| Pin GitHub Actions by commit | Maintainer | A one-time change, then Dependabot keeps them current |
| Package visibility until the release (R-13) | Maintainer | The package is public today |
