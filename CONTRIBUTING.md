# Contributing

Report a defect or propose a change with the issue forms. Report a vulnerability through a [private advisory](SECURITY.md).

Only the maintainer opens pull requests. They follow the rules below.

## Rules

- Use English in code comments, commits, issues, pull requests, and documents.
- Address one root cause per pull request, and update the documentation it affects.
- Every commit resolves to the `l2go` account and has no `Co-authored-by` trailer. The `commit-identity` check refuses any other.
- GitHub Actions is the only build of record. The maintainer squash-merges by hand once the required checks pass and every review thread is resolved.
- A schema change includes the regenerated `docs/database`. The failing check writes the new files to `l2jfree-core/target/database-docs`.

## Commits

Use an imperative, capitalized subject of about 70 characters with no final period. Explain why in the body, wrapped near 72 columns. Start the body of a defect fix with `Fixes #n`.

## Pull requests

Name the issue, what changes for a running server, how it was verified, and what is left out.
