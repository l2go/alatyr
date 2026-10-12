# Contributing

## Rules

| Rule | Detail |
|---|---|
| Language | English in code comments, commits, issues, pull requests, and documents |
| Builds | Nothing is built or run on a workstation. GitHub Actions is the build and test environment |
| One change, one pull request | A defect is one root cause. Related documentation edits belong in the same pull request |
| Review | The maintainer merges by hand after the required checks pass and review threads are resolved. `main` takes squash merges only |
| Identity | Every commit must resolve to the `l2go` account and must not carry a `Co-authored-by` trailer. The `commit-identity` check enforces it |

A security report goes to a [private advisory](SECURITY.md).

## Commits

- Subject in the imperative, capitalized, no final period, up to about 70 characters.
- A body that explains why, wrapped near 72 columns. A defect fix starts the body with `Fixes #n`.
- Squash merge adds ` (#N)` to the subject.

## Pull requests

State the issue, what changes for a running server, how it was verified, and what is left out. Link the issue.
