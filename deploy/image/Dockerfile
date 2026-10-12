# syntax=docker/dockerfile:1

# The server image. linux/amd64 on Arch Linux, pinned by dated tag and digest,
# with a Temurin 25 JRE archive pinned by SHA-256.
#
# The image carries the login module and the world module in one process.
# The platform distribution is unpacked next to this file as
# l2jfree/. It lands read-only in /opt/l2jfree: the defaults of every
# configuration key, the libraries, the datapack data, and the catalog. State
# lives in volumes: the work directory, the operator configuration,
# the log, and the secrets.

FROM archlinux:base-20261004.0.606936@sha256:4e77cf2ea5f410e6f8be5abf93ccf17ce2436e87a138c167208356711a405dbd AS jre

ADD --checksum=sha256:1731a34baadec5479258ea0202e4d5d865d2efeee60cb0c7d7eb056fe96ca219 \
    https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jre_x64_linux_hotspot_25.0.4.1_1.tar.gz \
    /tmp/jre.tar.gz
RUN mkdir -p /opt/jre && tar -xzf /tmp/jre.tar.gz -C /opt/jre --strip-components=1

FROM archlinux:base-20261004.0.606936@sha256:4e77cf2ea5f410e6f8be5abf93ccf17ce2436e87a138c167208356711a405dbd

# captree is a Go program of the Arch base that the server never runs. Its Go standard library carried twenty fixed
# high vulnerabilities at the first scan (SECURITY.md), so it is removed, not excepted.
RUN rm -f /usr/bin/captree \
 && useradd --system --uid 10001 --user-group --home-dir /var/lib/l2jfree --shell /usr/bin/nologin l2jfree \
 && install -d -o 10001 -g 10001 -m 0750 /var/lib/l2jfree /var/lib/l2jfree/log /var/lib/l2jfree/work \
 && install -d -o 10001 -g 10001 -m 0755 /var/lib/l2jfree/config /var/lib/l2jfree/secrets /var/lib/l2jfree/secrets-db /var/lib/l2jfree/aot

COPY --from=jre /opt/jre /opt/jre
COPY l2jfree /opt/l2jfree
COPY --chmod=0755 bin/ /opt/l2jfree/bin/

# The world reads the datapack and the catalog from the read-only tree, so the
# two path keys of the shipped defaults name it. An operator can still override
# them. The build fails when a key is missing from the defaults.
RUN set -eu; \
    cd /opt/l2jfree; \
    if [ ! -f l2jfree-platform.jar ]; then \
        set -- l2jfree-platform-*.jar; \
        [ "$#" -eq 1 ] && [ -f "$1" ] && mv "$1" l2jfree-platform.jar; \
    fi; \
    test -f l2jfree-platform.jar; \
    sed -i -E 's|^DatapackRoot[[:space:]]*=.*|DatapackRoot = /opt/l2jfree|; s|^CatalogDirectory[[:space:]]*=.*|CatalogDirectory = /opt/l2jfree/catalog|' config/server.properties; \
    grep -qx 'DatapackRoot = /opt/l2jfree' config/server.properties; \
    grep -qx 'CatalogDirectory = /opt/l2jfree/catalog' config/server.properties

ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:/usr/local/sbin:/usr/local/bin:/usr/bin

USER 10001:10001
WORKDIR /var/lib/l2jfree/work
EXPOSE 2106 7777
ENTRYPOINT ["/opt/l2jfree/bin/entrypoint"]
