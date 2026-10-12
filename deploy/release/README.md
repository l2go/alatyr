# L2JFree server

This bundle runs the L2JFree server and PostgreSQL 18. It needs Docker Compose. Run every command in this directory.

If players connect from other machines, first write the address of this host to `.env`:

```sh
echo 'L2JFREE_EXTERNAL_HOST=203.0.113.10' > .env
```

Start:

```sh
docker compose up -d --wait
```

The first start creates the database passwords and schemas and loads the game content. It takes a few minutes. When the command returns, login answers on port 2106 and the world on 7777. The first login creates the account.

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Log | `docker compose logs -f server` |
| Stop | `docker compose down` (the data stays in the volumes) |
| Backup | `docker compose exec -T db pg_dump -U postgres -d l2jfree -n login -n world -Fc > l2jfree.dump` |

To update, unpack the new bundle, copy `.env` into its directory if you have one, and run `docker compose up -d --wait` there. The volumes belong to the project `l2jfree`, so the new directory finds the same data.

## Change an option

A file in the `config` volume overrides the keys it names in the file of the same name in the image. With the stack running, to triple the experience rate:

```sh
printf 'RateXp = 3.\n' | docker compose exec -T server tee /var/lib/l2jfree/config/rates.properties >/dev/null
docker compose restart server
```

`tee` replaces the file, so write all changed keys of one file at once. The defaults: [world](https://github.com/l2go/l2jfree/tree/main/l2jfree-core/config), [login](https://github.com/l2go/l2jfree/tree/main/l2jfree-login/config). On a public server, turn off the automatic creation of accounts with `AutoCreateAccounts = False` in `loginserver.properties`.

The restore and every setting are in the [deploy guide](https://github.com/l2go/l2jfree/blob/main/deploy/README.md).

## Trademarks

Lineage II and NCSOFT are trademarks or registered trademarks of NCSOFT Corporation. L2JFree is an independent, non-commercial open-source project, not affiliated with, endorsed, or sponsored by NCSOFT. This bundle contains no game client.
