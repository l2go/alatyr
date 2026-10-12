<div align="center">

<img src="assets/l2jfree-hero.svg" width="100%" alt="L2JFree: Lineage II server for Gracia Final, protocol 83">

<br>

[![Build](https://img.shields.io/github/actions/workflow/status/l2go/l2jfree/build.yml?branch=main&style=for-the-badge&label=build&logo=githubactions&logoColor=white&labelColor=0b1030)](https://github.com/l2go/l2jfree/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/l2go/l2jfree?sort=semver&display_name=tag&style=for-the-badge&logo=github&logoColor=white&labelColor=0b1030&color=e879f9)](https://github.com/l2go/l2jfree/releases)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-a78bfa?style=for-the-badge&labelColor=0b1030)](LICENSE)
<br>
![Java 25](https://img.shields.io/badge/Java-25-22d3ee?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=0b1030)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-22d3ee?style=for-the-badge&logo=postgresql&logoColor=white&labelColor=0b1030)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-22d3ee?style=for-the-badge&logo=docker&logoColor=white&labelColor=0b1030)
![linux/amd64](https://img.shields.io/badge/linux-amd64-22d3ee?style=for-the-badge&logo=linux&logoColor=white&labelColor=0b1030)
![Signed](https://img.shields.io/badge/cosign-signed-34d399?style=for-the-badge&logo=sigstore&logoColor=white&labelColor=0b1030)

### One process · Two ports · One command

[Launch](#launch) · [Architecture](#architecture) · [What ships](#what-ships) · [Releases](#releases) · [Deploy guide](deploy/README.md)

</div>

## Launch

From a checkout, with Docker:

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

When the command returns, login answers on **2106** and the world on **7777**. The first login creates the account.

> [!TIP]
> Players on other machines need `L2JFREE_EXTERNAL_HOST` set to the address of the host. Settings, backup, and restore are in the [deploy guide](deploy/README.md). A release bundle has its own [guide](deploy/release/README.md).

## Architecture

```mermaid
flowchart LR
    client(["Lineage II client<br/>protocol 83"])

    subgraph proc["Server image: one JVM process on Java 25"]
        login["Login module<br/>:2106"]
        world["World module<br/>:7777"]
        login <-->|"contract<br/>LoginPort · WorldPort"| world
    end

    subgraph pg["PostgreSQL 18"]
        sl[("login")]
        sw[("world")]
        sc[("catalog")]
        sr[("report")]
    end

    client --> login
    client --> world
    login -->|"role l2jfree_login"| sl
    world -->|"role l2jfree_world"| sw
    world --> sc
    world --> sr

    classDef srv fill:#0b1030,stroke:#22d3ee,stroke-width:2px,color:#e0f2fe
    classDef db fill:#1a0b33,stroke:#e879f9,stroke-width:2px,color:#fae8ff
    classDef ext fill:#020617,stroke:#a78bfa,stroke-width:2px,color:#ede9fe
    class login,world srv
    class sl,sw,sc,sr db
    class client ext
```

The two modules meet only through the contract, and the build fails if either reaches into the other. Each module connects with its own database role and reaches only the schemas it owns. At start, the world brings `catalog` up to the game content of the image.

## What ships

<table>
<tr>
<td width="33%" valign="top">

**⚡ One process**

Login and world in one JVM, joined by a typed contract instead of a socket protocol.

</td>
<td width="33%" valign="top">

**🐘 PostgreSQL 18**

Migrations run at start. CI prepares every SQL statement of the server against the real schema.

</td>
<td width="33%" valign="top">

**🔏 Signed image**

A cosign signature, an SBOM, and build provenance on every published image.

</td>
</tr>
<tr>
<td width="33%" valign="top">

**🧪 Proved on every merge**

The image starts, 24 clients play whole sessions, the server restarts, and a backup is restored into a clean stack.

</td>
<td width="33%" valign="top">

**🛰️ Native network core**

Netty on epoll by default, with io_uring on request.

</td>
<td width="33%" valign="top">

**🛡️ Locked down**

User `10001`, a read-only root, no added capabilities, and a 4 GB memory limit.

</td>
</tr>
</table>

## Releases

| Channel | Image | Published |
|---|---|---|
| Release | `ghcr.io/l2go/l2jfree:<version>` | With a compose bundle on the [releases page](https://github.com/l2go/l2jfree/releases) |
| Edge | `ghcr.io/l2go/l2jfree:edge` | On every merge to `main` |

> [!IMPORTANT]
> No version is supported. The 2.x line ended with `v2.5.0`, and a 2.x database cannot be migrated. Report a vulnerability through the [security policy](SECURITY.md).

## Origin and license

Forked from [l2jfree/l2jfree-ct2.3](https://github.com/l2jfree/l2jfree-ct2.3), which descends from [savormix/l2jfree-genesis](https://github.com/savormix/l2jfree-genesis). The [original README](UPSTREAM_README.md) is kept unchanged. This project provides the server source only, with no client and no hosted server. The inherited datapack text was not audited. Lineage II is a trademark of its owner. The code is under [GPLv3](LICENSE).

<div align="center">
<sub>Built, started, and checked by GitHub Actions on every merge.</sub>
</div>
