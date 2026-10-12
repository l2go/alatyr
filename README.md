<div align="center">

<img src="assets/l2jfree-hero.png" alt="A dawn-lit mountain citadel traced with subtle connections between server nodes" width="100%">

# L2JFree

**Gracia Final, protocol 83**

[![Build](https://github.com/l2go/l2jfree/actions/workflows/build.yml/badge.svg)](https://github.com/l2go/l2jfree/actions/workflows/build.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-2563eb)](LICENSE)
[![Release](https://img.shields.io/github/v/release/l2go/l2jfree?display_name=tag&sort=semver)](https://github.com/l2go/l2jfree/releases)

</div>

An unofficial Lineage II server. The published form is one Linux image for `linux/amd64`. GitHub Actions builds the image and starts it. No version is supported.

The image runs the login and the world in one process, on Java 25 and PostgreSQL 18. It listens on **2106** and **7777**.

```sh
docker compose -f deploy/compose.yaml up -d --wait
```

Further commands for a checkout are in [deploy/README.md](deploy/README.md). A release bundle has its own [guide](deploy/release/README.md).

`v3.1.0` is the current release. `v3.0.0` was the first release of this line. `v2.5.0` is the final release of the 2.x line and receives no fixes. There is no migration from a 2.x database. Every merge to `main` publishes an `edge` image.

This fork descends from [savormix/l2jfree-genesis](https://github.com/savormix/l2jfree-genesis) through [l2jfree/l2jfree-ct2.3](https://github.com/l2jfree/l2jfree-ct2.3). The [original README](UPSTREAM_README.md) is preserved unchanged. The project supplies server source. It does not supply a client or a hosted server. Inherited datapack text and HTML were not audited here. "Lineage 2" is a trademark of its owner. The code is distributed under [GPLv3](LICENSE).
