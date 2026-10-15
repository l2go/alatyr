# Security policy

No version is supported. A report may get no reply and no fix.

## Report a vulnerability

Open a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new), not a public issue. Say which component is affected, what a running server does wrong, and how to reproduce it. Leave out credentials and session keys; redact account names, addresses, and file paths.

## Verify a release

Set `VERSION` to the release number without the leading `v`, for example `3.2.0`, from the [releases page](https://github.com/l2go/l2jfree/releases). The image is signed with cosign and carries an SBOM and build provenance:

```sh
VERSION=3.2.0
cosign verify ghcr.io/l2go/l2jfree:$VERSION \
  --certificate-identity https://github.com/l2go/l2jfree/.github/workflows/build.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
gh attestation verify oci://ghcr.io/l2go/l2jfree:$VERSION --repo l2go/l2jfree
```

The compose archive comes with the Sigstore bundle of its provenance:

```sh
gh release download v$VERSION --repo l2go/l2jfree
gh attestation verify l2jfree-$VERSION-compose.tar.gz --repo l2go/l2jfree \
  --bundle l2jfree-$VERSION-compose.tar.gz.sigstore.json \
  --signer-workflow l2go/l2jfree/.github/workflows/release.yml
```

Releases before 3.2.0 are no longer published; their pages remain as history.

## Scanning

Trivy scans the image on every merge and every day. A critical or high finding that has a fix fails the daily scan and blocks a release, unless [.trivyignore](.trivyignore) lists it with a date.
