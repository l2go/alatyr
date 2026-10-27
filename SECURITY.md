# Security policy

No version is supported. A report may get no reply and no fix.

## Report a vulnerability

Open a [private security advisory](https://github.com/l2go/alatyr/security/advisories/new), not a public issue. Say which component is affected, what a running server does wrong, and how to reproduce it. Leave out credentials and session keys; redact account names, addresses, and file paths.

## Verify a release

Set `VERSION` to the release number without the leading `v`, chosen for the Go server, from the [releases page](https://github.com/l2go/alatyr/releases). The image is signed with cosign and carries build provenance:

```sh
VERSION=YOUR_GO_RELEASE_VERSION
cosign verify ghcr.io/l2go/alatyr:$VERSION \
  --certificate-identity https://github.com/l2go/alatyr/.github/workflows/build.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
gh attestation verify oci://ghcr.io/l2go/alatyr:$VERSION --repo l2go/alatyr
```

The compose archive comes with the Sigstore bundle of its provenance:

```sh
gh release download v$VERSION --repo l2go/alatyr
gh attestation verify alatyr-$VERSION-compose.tar.gz --repo l2go/alatyr \
  --bundle alatyr-$VERSION-compose.tar.gz.sigstore.json \
  --signer-workflow l2go/alatyr/.github/workflows/release.yml
```

Earlier server images and release pages remain historical artifacts; they are not the Go server image named above.

## Scanning

Trivy scans the published image every day and both platforms of a release before it is published. A critical or high finding that has a fix fails the daily scan and blocks a release, unless [.trivyignore](.trivyignore) lists it with a date.
