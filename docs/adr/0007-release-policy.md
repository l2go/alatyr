# 0007. One release and continuous images

- Status: Accepted
- Date: 2026-10-06

## Context

The 1.x and 2.x lines produced many tags: release candidates, a stable release, and a post-release version within days. Each tag costs review, verification, and explanation. Platform 3.0 is large and its design is already decided, so fine slicing would cost more than it gives.

## Decision

1. `v3.0.0` is the first release of the 3.0 line. There are no release candidates and no intermediate tags before a release.
2. Every merge to `main` builds the image and publishes it to the GitHub Container Registry as `edge` and `sha-<commit>`, without a GitHub Release. Acceptance runs on those images.
3. The images stay private until the release. `sha-*` tags are kept for 30 days.
4. The tag is created once, after the exit criteria of the last milestone pass and the maintainer approves it in the `stable-release` environment. Tags `v*` are protected from deletion and update.
5. After a release, another version is cut only for a defect that configuration cannot work around. Improvements wait for the next release that is worth a tag.
6. `v3.1.0`, cut on 2026-10-12, collects the correctness defects of `v3.0.0` that configuration cannot work around. The maintainer set that version at 3.1.0.

## Alternatives considered

- A release candidate before the stable tag. It checks the release process, but images already do that, and it adds a tag.
- One release per milestone. It gives visible progress, but it is the fine slicing this record avoids.

## Consequences

Progress is visible through decision records, milestone reports, and the project board, not through tags. A defect found at acceptance is fixed before the tag, so the first public version carries no known blockers.

## Revisit when

The release process itself changes in a way that only a tagged pre-release can verify.
