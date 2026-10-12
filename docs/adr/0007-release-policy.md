# 0007. The maintainer names each release

- Status: Accepted
- Date: 2026-10-06

## Context

The 1.x and 2.x lines produced many tags: release candidates, a stable release, and a post-release version within days. Each tag costs review, verification, and explanation. Platform 3.0 is large and its design is already decided, so fine slicing would cost more than it gives.

## Decision

1. `v3.0.0` is the first release of the 3.0 line. `v3.1.0`, cut on 2026-10-12, contains the correctness fixes found in it.
2. Every merge to `main` builds the image and publishes it to the GitHub Container Registry as `edge` and `sha-<commit>`, without a GitHub Release. Acceptance runs on those images.
3. The images stay private until the release. `sha-*` tags are kept for 30 days.
4. The maintainer names the next version, when it is cut, and what it contains. A documentation change does not by itself require a version. The maintainer approves the tag in the `stable-release` environment. Tags `v*` are protected from deletion and update.

## Alternatives considered

- A release candidate before the stable tag. It checks the release process, but images already do that, and it adds a tag.
- One release per milestone. It gives visible progress, but it is the fine slicing this record avoids.

## Consequences

Progress between tags is visible through decision records, milestone reports, and the project board. A release tag records the version the maintainer named and the changes the maintainer put in it.

## Revisit when

The release process itself changes in a way that only a tagged pre-release can verify.
