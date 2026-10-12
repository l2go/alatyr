# 0002. Deliver a Linux image with Docker Compose

- Status: Accepted
- Date: 2026-10-06

## Context

Platform 3.0 replaces the Windows 10 host with a Linux image. The image publishes two ports, 2106 for login and 7777 for the world. The maintainer prefers Arch Linux as the image base where possible. This decision was made by the maintainer without a comparison of other bases. GitHub Actions builds the image and starts it.

Facts checked on 2026-10-06:

- The official `archlinux` image on Docker Hub is `linux/amd64` only. It has dated tags such as `base-20261004.0.606936`.
- Arch's `jdk-openjdk` package is JDK 27, so installing the default package would break the JDK 25 decision. Arch also carries `jre25-openjdk-headless`.
- Eclipse Temurin 25.0.4.1 JRE archives with SHA-256 checksums exist for x64 and aarch64.
- Docker's default seccomp profile is an allowlist and does not include `io_uring`.

## Decision

1. A release bundle is started with `docker compose up -d --wait`. The bundle starts the server image and the official `postgres` image of the 18 line, pinned by digest. There is no systemd unit, no Kubernetes manifest, and no install script on the host. GitHub Actions is where that start is run.
2. The server image is `linux/amd64` on an Arch Linux base pinned by dated tag and digest. Packages added with `pacman` come from an Arch Linux Archive snapshot of the same date.
3. The runtime is a Temurin 25 JRE archive pinned by SHA-256. It is not the distribution's JDK package.
4. The server publishes exactly two ports, 2106 and 7777. PostgreSQL is not published. State lives in named volumes. The process runs as a non-root user with a read-only root file system and no added capabilities.
5. The default network transport is epoll. `io_uring` is an opt-in that needs a custom seccomp profile.

## Alternatives considered

- The official Temurin image as the base. Standard and multi-arch, but not the maintainer's preferred base.
- Debian or Ubuntu slim. Stable, with native arm64, but it also departs from Arch.
- A native Windows installer, a systemd unit, or Kubernetes. Each adds a host-specific path that Compose already replaces.
- A native arm64 variant on another base. It doubles the verification matrix.

## Consequences

- A rolling base needs pinning and a refresh routine. Digest and snapshot pins make builds repeatable.
- The `io_uring` benefit named in the vision is not available by default.

## Revisit when

An official Arch image for arm64 appears, Docker changes its default seccomp profile, or JDK 29 (the next LTS, expected September 2027) becomes the target.
