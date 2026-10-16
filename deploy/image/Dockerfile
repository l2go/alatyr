# syntax=docker/dockerfile:1

# The server image. linux/amd64 and linux/arm64 on Debian 13 slim, pinned by dated tag and index digest,
# with a Temurin 25 JRE archive pinned by SHA-256.
#
# The image carries the login module and the world module in one process.
# The platform distribution is unpacked next to this file as
# l2jfree/. It lands read-only in /opt/l2jfree: the defaults of every
# configuration key, the libraries, and the world. State
# lives in volumes: the work directory, the operator configuration,
# the log, and the secrets.

FROM debian:trixie-20261005-slim@sha256:a29215f6a35e51e22adffa17f89e9d2ef06214e64a2bad10d765c46aea49f11f AS jre

ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*
RUN set -eu; \
    case "$TARGETARCH" in \
      amd64) arch=x64; checksum=1731a34baadec5479258ea0202e4d5d865d2efeee60cb0c7d7eb056fe96ca219 ;; \
      arm64) arch=aarch64; checksum=34828cbb93ed31c281c84ecb31ddab655d11a802f263c1fc019d42e9e0230fed ;; \
      *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_${arch}_linux_hotspot_25.0.4.1_1.tar.gz" -o /tmp/jre.tar.gz; \
    echo "$checksum  /tmp/jre.tar.gz" | sha256sum -c -; \
    mkdir -p /opt/jre; tar -xzf /tmp/jre.tar.gz -C /opt/jre --strip-components=1

FROM debian:trixie-20261005-slim@sha256:a29215f6a35e51e22adffa17f89e9d2ef06214e64a2bad10d765c46aea49f11f

# JRE native dependencies and the scripts' shell; utility commands come from coreutils.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates libstdc++6 zlib1g \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 l2jfree \
 && useradd --system --uid 10001 --gid 10001 --home-dir /var/lib/l2jfree --shell /usr/sbin/nologin l2jfree \
 && install -d -o 10001 -g 10001 -m 0750 /var/lib/l2jfree /var/lib/l2jfree/log /var/lib/l2jfree/work \
 && install -d -o 10001 -g 10001 -m 0755 /var/lib/l2jfree/config /var/lib/l2jfree/secrets /var/lib/l2jfree/secrets-db /var/lib/l2jfree/aot

COPY --from=jre /opt/jre /opt/jre
COPY l2jfree /opt/l2jfree
COPY --chmod=0755 bin/ /opt/l2jfree/bin/

# The world reads its files from the read-only tree, so the
# two path keys of the shipped defaults name it. An operator can still override
# them. The build fails when a key is missing from the defaults.
RUN set -eu; \
    cd /opt/l2jfree; \
    if [ ! -f l2jfree-platform.jar ]; then \
        set -- l2jfree-platform-*.jar; \
        [ "$#" -eq 1 ] && [ -f "$1" ] && mv "$1" l2jfree-platform.jar; \
    fi; \
    test -f l2jfree-platform.jar; \
    sed -i -E 's|^DatapackRoot[[:space:]]*=.*|DatapackRoot = /opt/l2jfree/world|; s|^CatalogDirectory[[:space:]]*=.*|CatalogDirectory = /opt/l2jfree/world/catalog|' config/server.properties; \
    grep -qx 'DatapackRoot = /opt/l2jfree/world' config/server.properties; \
    grep -qx 'CatalogDirectory = /opt/l2jfree/world/catalog' config/server.properties

ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:/usr/local/sbin:/usr/local/bin:/usr/bin

USER 10001:10001
WORKDIR /var/lib/l2jfree/work
EXPOSE 2106 7777
ENTRYPOINT ["/opt/l2jfree/bin/entrypoint"]
