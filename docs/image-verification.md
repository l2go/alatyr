# Image verification

Every image that the pipeline publishes from `main` is signed with cosign, carries build provenance and an SBOM, and is verified by the pipeline right after the push. The commands below are the ones the pipeline runs, with the digest of the image you pull.

Take the digest from the registry, not from a tag, because a tag can move:

```sh
docker buildx imagetools inspect ghcr.io/l2go/l2jfree:edge | grep Digest
image=ghcr.io/l2go/l2jfree@sha256:<digest>
```

## Signature

The signature is keyless. It is made by the workflow `build.yml` on `main` and recorded in the public transparency log.

```sh
cosign verify "$image" \
  --certificate-identity https://github.com/l2go/l2jfree/.github/workflows/build.yml@refs/heads/main \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

## Provenance

The attestation says which commit and which workflow built the image.

```sh
gh attestation verify "oci://$image" --repo l2go/l2jfree
```

## SBOM

The SBOM is stored with the image. This prints the number of packages it lists:

```sh
docker buildx imagetools inspect "$image" --format '{{ json .SBOM.SPDX }}' | jq '.packages | length'
```

## Known vulnerabilities

The [vulnerability scan](../SECURITY.md#vulnerability-scan) is a check in the pipeline. To scan an image yourself:

```sh
trivy image --severity CRITICAL,HIGH --ignore-unfixed "$image"
```
