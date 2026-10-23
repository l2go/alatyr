#!/usr/bin/env bash
# Verify the release index and the signature and provenance of each runnable manifest.
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
for arch in amd64 arm64; do
    digest="$(jq -er --arg arch "$arch" '.manifests[] | select(.platform.os == "linux" and .platform.architecture == $arch) | .digest' <<< "$manifest")"
    verify "$repository@$digest"
    echo "linux/$arch: $digest; signature and provenance verified"
done
