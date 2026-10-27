# syntax=docker/dockerfile:1
# Build the application with its published protocol pin and local parent module.
FROM --platform=$BUILDPLATFORM golang:1.27.3-trixie@sha256:c124b9ad3452f46061b5054607d26ea51730dae5372c5e469920063ba2bc6dcc AS build
ARG TARGETOS
ARG TARGETARCH
WORKDIR /src
COPY engine/go/ engine/go/
WORKDIR /src/engine/go/server
RUN GOWORK=off GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -buildvcs=false -ldflags='-s -w' -o /out/alatyr-server ./cmd/alatyr-server

FROM debian:trixie-20261005-slim@sha256:a29215f6a35e51e22adffa17f89e9d2ef06214e64a2bad10d765c46aea49f11f
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 alatyr \
 && useradd --system --uid 10001 --gid 10001 --home-dir /var/lib/alatyr --shell /usr/sbin/nologin alatyr \
 && install -d -o 10001 -g 10001 -m 0755 /var/lib/alatyr/secrets /var/lib/alatyr/secrets-db
COPY --from=build /out/alatyr-server /opt/alatyr/bin/alatyr-server
COPY --chmod=0755 deploy/image/bin/ /opt/alatyr/bin/
COPY LICENSE /opt/alatyr/LICENSE.deployment
COPY engine/go/LICENSE /opt/alatyr/LICENSE
COPY engine/go/protocol/LICENSE /opt/alatyr/LICENSE.protocol
COPY world/LICENSE world/world.properties /opt/alatyr/world/
COPY world/catalog/ /opt/alatyr/world/catalog/
COPY world/data/ /opt/alatyr/world/data/
LABEL org.opencontainers.image.source="https://github.com/l2go/alatyr" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later AND Apache-2.0 AND GPL-3.0-or-later AND CC-BY-SA-4.0"
USER 10001:10001
WORKDIR /opt/alatyr
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
 CMD ["/opt/alatyr/bin/alatyr-server", "-healthcheck", "http://127.0.0.1:8080/readyz"]
ENTRYPOINT ["/opt/alatyr/bin/entrypoint"]
