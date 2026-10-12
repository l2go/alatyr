# Security policy

No version of this project is supported. Sending a report is not a commitment that anyone will reply or publish a fix. `v2.5.0` is the final release of the 2.x line and receives no fixes ([decision](docs/adr/0008-retire-the-2x-line.md)).

## Report a vulnerability

Do not open a public issue. Use a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new). Include the affected component, the impact on a running server, and the steps to reproduce.

Credentials and session keys must never appear in a report. Redact account names, addresses, and file paths.

## Supply chain

The Platform 3.0 image is signed with cosign, carries an SBOM and build provenance, and is verified by the pipeline right after it is published. The [image verification guide](docs/image-verification.md) shows the commands. The 2.x releases are verified as the [retired guide](docs/RELEASE-VERIFICATION.md) describes.

## Vulnerability scan

The image and its dependencies are scanned with Trivy on every merge and every day. A finding counts when a fixed version exists. The scan reports on merges and does not fail them. The daily scan and the release job fail on a critical or high finding in a package the image runs when a fixed version exists. An exception is a dated entry in [.trivyignore](.trivyignore) with the identifier, the reason, and the date it is reviewed again. The scan is a check in the pipeline, not a promise to publish a fix.
