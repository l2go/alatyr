# Contributing

Issues are welcome. Use the issue forms, and a [private advisory](SECURITY.md) for a vulnerability. Pull requests come from the maintainer only: every commit must resolve to the `l2go` account and carry no `Co-authored-by` trailer. The `commit-identity` check enforces it.

## Rules

- English in code comments, commits, issues, pull requests, and documents.
- One root cause per pull request. The documentation it changes comes with it.
- GitHub Actions is the only build of record. The maintainer squash-merges by hand after the required checks pass and the review threads are resolved.
- A migration that changes a table also commits the regenerated `docs/database`. The failing check writes the new files to `l2jfree-core/target/database-docs`.

## Commits

The subject is imperative and capitalized, has no final period, and is about 70 characters. The body says why and is wrapped near 72 columns. A defect fix starts the body with `Fixes #n`.

## Pull requests

State the issue, what changes for a running server, how it was verified, and what is left out.
