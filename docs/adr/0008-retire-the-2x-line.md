# 0008. Retire the 2.x line

- Status: Accepted
- Date: 2026-10-06

## Context

The 2.x line targets Windows 10, MySQL 8.4, and two processes. Version 2.5.0 is its last release. The maintainer does not use it and will not verify it. Windows 10 left regular support on 14 October 2025.

## Decision

`v2.5.0` is the final release of the 2.x line. There will be no further release, patch, or security fix for it, and the repository stops accepting changes to it. Its releases, checksums, and attestations stay published as history. Its documents remain in `docs/` marked as retired, and `docs/index.md` lists them. There is no tool that migrates a 2.x database into 3.0. A 3.0 installation starts clean.

## Alternatives considered

- Keep maintaining 2.x security fixes. It keeps two lines alive with one maintainer.
- Delete the 2.x releases. It removes history that the checksums and attestations still document.

## Consequences

Operators who need Windows 10 and MySQL stay on v2.5.0 at their own risk. The 3.0 line is a Linux image that GitHub Actions builds and starts. The ruleset and CI that served 2.x are replaced by the 3.0 pipeline (see `docs/roadmap.md`).

## Revisit when

Never for the 2.x line. A later decision can supersede how history is presented.
