# L2JFree 3.0 server

This directory starts a complete L2JFree server: the server image (login and
world in one process) and PostgreSQL 18. GitHub Actions builds the image and
starts this stack. Nothing else is installed on the host.

```sh
docker compose up -d --wait
```

When the command returns, the server accepts clients on ports 2106 (login) and
7777 (game). The first start creates random database passwords, the database,
and its schemas, and loads the game content, which takes a few minutes. Later
starts keep them.

## Everyday commands

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Server log | `docker compose logs -f server` |
| Stop | `docker compose down` (the data stays in the volumes) |
| Create an account | accounts are created at the first login (`AutoCreateAccounts` in `loginserver.properties`) |
| Database backup | `docker compose exec db pg_dump -U postgres -Fc l2jfree > l2jfree.dump` |
| Update | unpack the new release here, keep `.env`, then `docker compose up -d --wait` |

## Changing a setting

The image carries the defaults of every setting. A file in the `config` volume
overrides single keys: a `NAME.properties` file with only the changed keys of
the file of the same name in the image.

```sh
printf 'RateXp = 3\n' > rates.properties
docker compose cp rates.properties server:/var/lib/l2jfree/config/
docker compose restart server
```

The file names and the keys are listed in the repository, in `l2jfree-core/config`
and `l2jfree-login/config`.
