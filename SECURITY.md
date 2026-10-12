# Security policy

No version is supported. A report may get no reply and no fix.

## Report a vulnerability

Open a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new), not a public issue. Say which component is affected, what a running server does wrong, and how to reproduce it. Leave out credentials and session keys; redact account names, addresses, and file paths.

## Verify a release

The image is signed with cosign and carries an SBOM and build provenance:

```sh
cosign verify ghcr.io/l2go/l2jfree:3.1.1 \
  --certificate-identity https://github.com/l2go/l2jfree/.github/workflows/build.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
gh attestation verify oci://ghcr.io/l2go/l2jfree:3.1.1 --repo l2go/l2jfree
```

The compose archive comes with the Sigstore bundle of its provenance:

```sh
gh release download v3.1.1 --repo l2go/l2jfree
gh attestation verify l2jfree-3.1.1-compose.tar.gz --repo l2go/l2jfree \
  --bundle l2jfree-3.1.1-compose.tar.gz.sigstore.json \
  --signer-workflow l2go/l2jfree/.github/workflows/release.yml
```

## Scanning

Trivy scans the image on every merge and every day. A critical or high finding that has a fix fails the daily scan and blocks a release, unless [.trivyignore](.trivyignore) lists it with a date.
