# L2JFree server

This bundle starts the server image, with login and world in one process, and PostgreSQL 18. It needs only Docker.

For players on other machines, first put the address of the host in `.env`:

```sh
echo 'L2JFREE_EXTERNAL_HOST=203.0.113.10' > .env
```

Start:

```sh
docker compose up -d --wait
```

The first start creates the database passwords and schemas and loads the game content. That takes a few minutes. When the command returns, the server listens on 2106 for login and 7777 for the world. The first login creates the account.

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Log | `docker compose logs -f server` |
| Stop | `docker compose down` (the data stays in the volumes) |
| Backup | `docker compose exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree.dump` |
| Update | Unpack the new bundle, copy `.env` into its directory, and run `docker compose up -d --wait` there |

The volumes belong to the project `l2jfree`, so a new bundle directory finds the same data. The restore and the other settings are in the [deploy guide](https://github.com/l2go/l2jfree/blob/main/deploy/README.md).

## Change a setting

A file in the `config` volume overrides single keys of the file of the same name in the image. With the stack running:

```sh
printf 'RateXp = 3.\n' | docker compose exec -T server tee /var/lib/l2jfree/config/rates.properties >/dev/null
docker compose restart server
```

The files and their keys: [world](https://github.com/l2go/l2jfree/tree/main/l2jfree-core/config), [login](https://github.com/l2go/l2jfree/tree/main/l2jfree-login/config). On a public server, stop the automatic creation of accounts with `AutoCreateAccounts = False` in `loginserver.properties`.
