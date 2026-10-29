# Alatyr server bundle

This bundle runs the Go participant server and PostgreSQL 18 with Docker Compose.
Its image is pinned by digest to the build verified for this release. Run commands
from this directory; no source build is required.

## Prepare

```sh
mkdir -p config
cp server.example.json config/server.json
cp character.example.json config/character.json
```

Edit the participant registry, token digests and trusted TLS proxy configuration
in `config/server.json`. Set the actual proxy source IP in `ProxyPeers`; keep
credentials out of URLs and logs. Edit the trusted character snapshot before
import. The example snapshot has explicit values, not automatic starting rules.
See the [deployment guide](https://github.com/l2go/alatyr/blob/main/deploy/README.md)
for proxy setup, permissions, token provisioning, backup and restore.

## Start

```sh
docker compose up -d --wait db
docker compose run --rm --no-deps server -import-character /run/alatyr/config/character.json
docker compose up -d --wait
```

Use a fresh dedicated database. Another implementation's database is refused.
There is no registration service; only the configured participants can enter
existing imported characters. Imports require the server to be stopped.

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Logs | `docker compose logs -f server` |
| Stop, keeping data | `docker compose down` |
| Backup with the server stopped | `docker compose exec -T db pg_dump -U postgres -d alatyr -n world -Fc > alatyr.dump` |

To update, back up first, unpack the new bundle, copy your configuration and
`.env` if present, and run `docker compose up -d --wait`. The `alatyr` Compose
project finds the same persistent volumes. Keep the old image and matching
backup for rollback. Verify release signatures as described in
[the security policy](https://github.com/l2go/alatyr/blob/main/SECURITY.md).

Deployment files are GPL-3.0-or-later; see `LICENSE` in this bundle. The image includes
its component licenses and links to the corresponding public source repository.
