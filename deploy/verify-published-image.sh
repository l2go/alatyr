#!/usr/bin/env bash
# Verify the release index, each runnable manifest, and each platform's SBOM.
set -euo pipefail
image="${1:?usage: verify-published-image.sh IMAGE@DIGEST}"
repository="${image%@*}"
manifest="$(docker buildx imagetools inspect "$image" --raw)"
platforms="$(jq -r '[.manifests[] | select(.platform.os != "unknown") | (.platform.os + "/" + .platform.architecture)] | sort | .[]' <<< "$manifest")"
test "$platforms" = $'linux/amd64\nlinux/arm64'
# The other descriptors must be BuildKit attestations, never extra runtime platforms.
jq -e 'all(.manifests[]; .platform.os == "linux" or (.platform.os == "unknown" and .annotations["vnd.docker.reference.type"] == "attestation-manifest"))' <<< "$manifest" > /dev/null
verify() {
    cosign verify "$1" \
        --certificate-identity "https://github.com/${GITHUB_REPOSITORY}/.github/workflows/build.yml@refs/heads/main" \
        --certificate-oidc-issuer https://token.actions.githubusercontent.com > /dev/null
    gh attestation verify "oci://$1" --repo "$GITHUB_REPOSITORY" > /dev/null
}
verify "$image"
sbom="$(docker buildx imagetools inspect "$image" --format '{{ json .SBOM.SPDX }}')"
for arch in amd64 arm64; do
    digest="$(jq -er --arg arch "$arch" '.manifests[] | select(.platform.os == "linux" and .platform.architecture == $arch) | .digest' <<< "$manifest")"
    verify "$repository@$digest"
    packages="$(jq -er --arg platform "linux/$arch" '.[$platform].packages | length | select(. > 0)' <<< "$sbom")"
    echo "linux/$arch: $digest; signature, provenance, SBOM ($packages packages) verified"
done
