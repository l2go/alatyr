# Contributing

Report a defect or propose a change with the issue forms. Report a vulnerability through a [private advisory](SECURITY.md).

Only the maintainer opens pull requests. They follow the rules below.

## Rules

- Use English in code comments, commits, issues, pull requests, and documents.
- Address one root cause per pull request, and update the documentation it affects.
- Every commit resolves to the `l2go` account and has no `Co-authored-by` trailer. The `commit-identity` check refuses any other.
- GitHub Actions is the only build of record. The maintainer squash-merges by hand once the required checks pass and every review thread is resolved.
- Check all three Go module roots in workspace and `GOWORK=off` modes with vet, race tests and Staticcheck. Run the boundary checker and actionlint for workflow changes.
- A schema change includes forward goose SQL, updated `docs/database`, and regression tests on disposable PostgreSQL 18. Foreign database contents must remain untouched on refusal.
- Image changes include content, unprivileged runtime, readiness and live shutdown checks in CI. Local image builds are not part of the development workflow.

## Commits

Use an imperative, capitalized subject of about 70 characters with no final period. Explain why in the body, wrapped near 72 columns. Start the body of a defect fix with `Fixes #n`.

## Pull requests

Name the issue, what changes for a running server, how it was verified, and what is left out.
