# Security policy

No version of this project is supported. A report is not a promise of a reply or a fix. `v2.5.0` is the final release of the 2.x line and receives no fixes.

## Report a vulnerability

Do not open a public issue. Use a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new). Include the affected component, the impact on a running server, and the steps to reproduce.

Credentials and session keys must never appear in a report. Redact account names, addresses, and file paths.

## Supply chain

The image is signed with cosign and carries an SBOM and build provenance. The pipeline checks the signature, the provenance, and the SBOM after each publication.

## Vulnerability scan

Trivy scans the image and its dependencies on every merge and every day. A finding counts when a fixed version exists. The scan on a merge reports and does not fail the build. The daily scan and the release job fail on a critical or high finding in a package the image runs when a fixed version exists. An exception is a dated entry in [.trivyignore](.trivyignore) with the identifier, the reason, and the date it is reviewed again. The scan is a check in the pipeline, not a promise to publish a fix.
