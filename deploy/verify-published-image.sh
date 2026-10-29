#!/usr/bin/env bash
# Verify the release index and the signature and provenance of each runnable manifest.
set -euo pipefail
image="${1:?usage: verify-published-image.sh IMAGE@DIGEST}"
[[ $image =~ ^ghcr\.io/l2go/alatyr@sha256:[a-f0-9]{64}$ ]] || { echo 'expected ghcr.io/l2go/alatyr@sha256:<digest>' >&2; exit 2; }
repository="${image%@*}"
source_repository="${GITHUB_REPOSITORY:-l2go/alatyr}"
manifest="$(docker buildx imagetools inspect "$image" --raw)"
platforms="$(jq -r '[.manifests[] | select(.platform.os != "unknown") | (.platform.os + "/" + .platform.architecture)] | sort | .[]' <<< "$manifest")"
test "$platforms" = $'linux/amd64\nlinux/arm64'
# The other descriptors must be BuildKit attestations, never extra runtime platforms.
jq -e 'all(.manifests[]; .platform.os == "linux" or (.platform.os == "unknown" and .annotations["vnd.docker.reference.type"] == "attestation-manifest"))' <<< "$manifest" > /dev/null
verify() {
    cosign verify "$1" \
        --certificate-identity "https://github.com/${source_repository}/.github/workflows/build.yml@refs/heads/main" \
        --certificate-oidc-issuer https://token.actions.githubusercontent.com > /dev/null
    gh attestation verify "oci://$1" --repo "$source_repository" > /dev/null
}
verify "$image"
for arch in amd64 arm64; do
    digest="$(jq -er --arg arch "$arch" '.manifests[] | select(.platform.os == "linux" and .platform.architecture == $arch) | .digest' <<< "$manifest")"
    verify "$repository@$digest"
    echo "linux/$arch: $digest; signature and provenance verified"
done
