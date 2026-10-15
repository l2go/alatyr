<div align="center">

<a href="https://l2go.github.io/l2jfree/" title="Open the L2JFree project site"><img src="assets/l2jfree-hero.svg" width="100%" alt="L2JFree: Lineage II server for Gracia Final, protocol 83. Opens the project site."></a>

<br>

[![Project site](https://img.shields.io/badge/project%20site-l2go.github.io%2Fl2jfree-d9a441?style=for-the-badge&logo=githubpages&logoColor=white&labelColor=1a1610)](https://l2go.github.io/l2jfree/)
[![Build](https://img.shields.io/github/actions/workflow/status/l2go/l2jfree/build.yml?branch=main&style=for-the-badge&label=build&logo=githubactions&logoColor=white&labelColor=1a1610)](https://github.com/l2go/l2jfree/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/l2go/l2jfree?sort=semver&display_name=tag&style=for-the-badge&logo=github&logoColor=white&labelColor=1a1610&color=d9a441)](https://github.com/l2go/l2jfree/releases)
[![OpenSSF Scorecard](https://img.shields.io/ossf-scorecard/github.com/l2go/l2jfree?label=openssf%20scorecard&style=for-the-badge&labelColor=1a1610)](https://scorecard.dev/viewer/?uri=github.com/l2go/l2jfree)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-c9a86a?style=for-the-badge&labelColor=1a1610)](LICENSE)

### One process · Two ports · One command

**[Project site](https://l2go.github.io/l2jfree/)** · [Start](#start) · [Engineering](#engineering) · [Architecture](#architecture) · [Releases](#releases) · [Deploy guide](deploy/README.md) · [Security](SECURITY.md)

</div>

L2JFree is a Lineage II server for Gracia Final, protocol 83. Login and world run in one Java 25 process on PostgreSQL 18, deployed with Docker Compose.

## Start

From a checkout:

```sh
cd deploy
docker compose up -d --wait
```

The first start runs the `edge` image and takes a few minutes. When the command returns, login answers on port **2106** and the world on **7777**. The first login creates the account.

## Engineering

<table>
<tr>
<td width="33%" valign="top">

**🧱 One process, hard boundaries**

Login and world share a JVM, not code. They meet only through a typed contract, and architecture rules fail the build on any other dependency. Each module has its own database role, and the pipeline checks what each role can reach.

</td>
<td width="33%" valign="top">

**🗄️ SQL checked against the schema**

Every SQL statement of the server is prepared against the migrated PostgreSQL schema, and every JDBC type is compared with its column. A ratchet keeps direct database access from spreading.

</td>
<td width="33%" valign="top">

**🧪 Every merge is a release rehearsal**

The image starts, and 24 clients log in, create a character, enter the world, and log out. The server restarts, a backup is restored into a clean stack, and the published image is upgraded in place. The characters survive each step.

</td>
</tr>
<tr>
<td width="33%" valign="top">

**🚀 Releases promote, never rebuild**

A release retags the image digest that `main` built and tested, after the maintainer approves. The image carries a cosign signature, an SBOM, and build provenance; the compose archive carries its own signature.

</td>
<td width="33%" valign="top">

**🛡️ Hardened supply chain and runtime**

Actions are pinned by commit, and Trivy blocks a release on a critical or high finding that has a fix. The container runs as user `10001` with a read-only root, no capabilities, and a 4 GB memory limit.

</td>
<td width="33%" valign="top">

**🔭 Diagnosable in production**

Java Flight Recorder keeps the last 12 hours. PostgreSQL counts every statement and logs the plan of any that runs over 500 ms. A health endpoint gates readiness, and a stop refuses new connections first.

</td>
</tr>
</table>

The decisions in code: the [architecture rules](engine/java/platform/src/test/java/com/l2jfree/platform/ArchitectureTest.java) between the modules, a player save in [one transaction](https://github.com/l2go/l2jfree/pull/162), and a weight-check bypass closed by [a wider type](https://github.com/l2go/l2jfree/pull/193). The [engineering notes](https://l2go.github.io/l2jfree/#notes) on the project site take one decision at a time.

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

    classDef srv fill:#1b1712,stroke:#d9a441,stroke-width:2px,color:#f6ead0
    classDef db fill:#141a26,stroke:#8fa3c7,stroke-width:2px,color:#e6ecf7
    classDef ext fill:#0f0d0a,stroke:#f2c46b,stroke-width:2px,color:#fff6e0
    class login,world srv
    class sl,sw,sc,sr db
    class client ext
```

Login owns the `login` schema; world owns `world`, `catalog`, and `report`. At start, the world updates `catalog` from the game content of the image.

## Releases

| Channel | Image | Published |
|---|---|---|
| Release | `ghcr.io/l2go/l2jfree:<version>` | With a compose bundle on the [releases page](https://github.com/l2go/l2jfree/releases) |
| Edge | `ghcr.io/l2go/l2jfree:edge` | On every merge to `main` |

## Origin, trademarks, and license

L2JFree is an open-source Lineage II server from the L2J community; its history in this repository starts in 2006. This repository revives [l2jfree/l2jfree-ct2.3](https://github.com/l2jfree/l2jfree-ct2.3), archived since 2016, a descendant of [savormix/l2jfree-genesis](https://github.com/savormix/l2jfree-genesis); the upstream [README](https://github.com/l2go/l2jfree/blob/6ead790c93a1bae8150ef23090098b4669e342b0/UPSTREAM_README.md) is kept unchanged. The work here is modernization: the code moved to Java 25 and PostgreSQL 18, login and world joined in one process, a player save became one transaction, and economy exploits and concurrency races were closed, with regression checks in CI. It adds no game content, and it does not modify, patch, or distribute the game client.

Lineage II and NCSOFT are trademarks or registered trademarks of NCSOFT Corporation. L2JFree is an independent, non-commercial project, not affiliated with, endorsed, or sponsored by NCSOFT. The names say only which game client the server works with. The repository holds no game client, no client files, and no NCSOFT software, and no server is hosted. The datapack came with the fork and was not reviewed.

The code is under [GPLv3](LICENSE).
