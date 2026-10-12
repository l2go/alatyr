# Security policy

## Supported versions

| Version | Supported |
|---|---|
| `v3.1.0` | Yes |
| `v3.0.0` | No. Superseded by `v3.1.0` |
| 2.x line | No. `v2.5.0` is the final release and receives no fixes ([decision](docs/adr/0008-retire-the-2x-line.md)) |

## Report a vulnerability

Do not open a public issue. Use a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new). Include the affected component, the impact on a running server, and the steps to reproduce.

Credentials and session keys must never appear in a report. Redact account names, addresses, and file paths.

## What to expect

- Acknowledgement of the report.
- An assessment of the impact and a fix or a stated decision.
- Credit in the release notes if you want it.

This is a noncommercial archive project with one maintainer, so there is no guaranteed response time.

## Supply chain

The Platform 3.0 image is signed with cosign, carries an SBOM and build provenance, and is verified by the pipeline right after it is published. The [image verification guide](docs/image-verification.md) shows the commands. The 2.x releases are verified as the [retired guide](docs/RELEASE-VERIFICATION.md) describes.

## Vulnerability policy

The image and its dependencies are scanned with Trivy on every merge and every day. A finding counts when a fixed version exists.

| Severity | Fix within | Blocks a release tag |
|---|---|---|
| Critical | 7 days | Yes |
| High | 30 days | Yes |
| Medium | the next release | No |
| Low | when convenient | No |

A critical or high finding that is in a package the image runs and has a fixed version blocks the tag until the fix is in, or until an exception is recorded. An exception is a dated entry in [.trivyignore](.trivyignore) with the identifier, the reason, and the date it is reviewed again. The scan reports on merges and does not fail them; the daily scan and the release job fail on a finding that blocks.
