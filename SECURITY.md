# Security policy

No version is supported. A report is not a promise of a reply or a fix.

## Report a vulnerability

Open a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new), not a public issue. Name the affected component, the impact on a running server, and the steps to reproduce. Leave out credentials and session keys, and redact account names, addresses, and file paths.

## Verify a release

The image is signed with cosign and carries an SBOM and build provenance:

```sh
cosign verify ghcr.io/l2go/l2jfree:3.1.1 \
  --certificate-identity https://github.com/l2go/l2jfree/.github/workflows/build.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
gh attestation verify oci://ghcr.io/l2go/l2jfree:3.1.1 --repo l2go/l2jfree
```

The compose archive of a release comes with the Sigstore bundle of its provenance:

```sh
gh release download v3.1.1 --repo l2go/l2jfree
gh attestation verify l2jfree-3.1.1-compose.tar.gz --repo l2go/l2jfree \
  --bundle l2jfree-3.1.1-compose.tar.gz.sigstore.json \
  --signer-workflow l2go/l2jfree/.github/workflows/release.yml
```

Trivy scans the image on every merge and every day. A release and the daily scan fail on a critical or high finding that has a fixed version. An exception is a dated entry in [.trivyignore](.trivyignore).
