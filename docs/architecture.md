# Architecture

Five views of Platform 3.0, from the outside in. The decisions behind them are in the [decision records](adr/README.md). Views marked **target** show where the platform is going. Views marked **today** show what runs now.

## 1. Context (target)

Who and what the platform talks to.

```mermaid
flowchart LR
    player(["🎮 Player<br/>Lineage II client, protocol 83"])
    operator(["🧑‍💻 Operator"])
    subgraph platform["L2JFree Platform 3.0"]
        server["🖥️ Server<br/>login and world in one process"]
        db[("🗄️ PostgreSQL 18")]
        server --> db
    end
    player -- "2106 login<br/>7777 world" --> server
    operator -- "docker compose" --> platform
    ci["⚙️ GitHub Actions"] -- "builds, signs, publishes" --> registry["📦 GHCR<br/>edge and sha images"]
    registry -. "pulled by compose" .-> platform

    classDef person fill:#ddf4ff,stroke:#0969da,color:#0a3069
    classDef system fill:#dafbe1,stroke:#1a7f37,color:#0f5323
    classDef external fill:#f6f8fa,stroke:#8c959f,color:#24292f
    class player,operator person
    class server,db system
    class ci,registry external
```

## 2. Containers (target)

What runs in the compose stack and what it exposes. One process holds two modules that talk through the contract module: Java calls, no socket between them ([ADR-0003](adr/0003-one-process-two-modules.md)). The database has two roles, and each role owns its own schemas ([ADR-0009](adr/0009-database-roles-schemas-and-migration.md)).

```mermaid
flowchart TB
    subgraph host["Linux amd64"]
        subgraph stack["compose project l2jfree"]
            subgraph serverc["server · one process · Arch Linux amd64 · Temurin 25"]
                login["login module"]
                world["world module"]
                contract["contract module"]
                login --> contract
                world --> contract
            end
            subgraph pg["db · official PostgreSQL 18 image"]
                loginschema[("schema login<br/>role l2jfree_login")]
                worldschema[("schemas world, catalog, report<br/>role l2jfree_world")]
            end
            vol1[("secrets volume")]
            vol2[("dbdata volume")]
            vol3[("config volume<br/>operator overrides")]
            serverc -- "login role" --> loginschema
            serverc -- "world role" --> worldschema
            pg --- vol2
            serverc -. "reads" .-> vol1
            serverc -. "reads" .-> vol3
        end
    end
    clients(["🎮 Clients"]) -- "2106" --> login
    clients -- "7777" --> world

    classDef mod fill:#dafbe1,stroke:#1a7f37,color:#0f5323
    classDef store fill:#fff8c5,stroke:#bf8700,color:#4d2d00
    class login,world,contract mod
    class loginschema,worldschema,vol1,vol2,vol3 store
```

## 3. Compose stack (today)

The image that the pipeline builds and publishes now, with the services and volumes of the compose file. The server starts the login module, then the world, then opens the login port. The defaults are read-only in the image under `/opt/l2jfree`, and the operator changes only single keys in the `config` volume ([ADR-0011](adr/0011-configuration-model.md)). The pipeline takes a client through a full play session, before and after a restart, with the smoke client of [deploy](../deploy/README.md).

```mermaid
flowchart TB
    subgraph stack["compose project l2jfree"]
        secrets["secrets<br/>one-shot, creates the passwords"]
        db[("db · PostgreSQL 18<br/>roles l2jfree_login and l2jfree_world")]
        server["server · login and world in one process<br/>Arch Linux amd64 · Temurin 25"]
        config[("config volume<br/>operator overrides")]
        secrets --> db
        db --> server
        config -.-> server
    end
    clients(["🎮 Clients"]) -- "2106 login" --> server
    clients -- "7777 world" --> server

    classDef today fill:#ddf4ff,stroke:#0969da,color:#0a3069
    class server,db,secrets,config today
```

## 4. Delivery pipeline (today)

How a change becomes an image.

```mermaid
flowchart LR
    pr["Pull request"] --> build["build<br/>Maven, tests, SBOM, scan"]
    build --> dist[("verified<br/>distributions")]
    dist --> image["image<br/>build, verify, compose up"]
    image --> probe["probe<br/>login packet, world port"]
    probe --> gate{"merge to main?"}
    gate -- "no" --> done["checks green"]
    gate -- "yes" --> publish["publish edge and sha-*<br/>sign · SBOM · provenance"]
    publish --> ghcr[("GHCR, private<br/>until the release")]

    classDef step fill:#ddf4ff,stroke:#0969da,color:#0a3069
    classDef store fill:#fff8c5,stroke:#bf8700,color:#4d2d00
    class pr,build,image,probe,publish,done step
    class dist,ghcr store
```

## 5. Login and admission (target)

Admission is a Java call inside one process. The internal socket between login and world of the 2.x line is removed ([ADR-0003](adr/0003-one-process-two-modules.md)). The login module implements `LoginPort`, the world module implements `WorldPort`, and both ports live in the contract module.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant L as login module
    participant K as contract (LoginPort, WorldPort)
    participant W as world module
    C->>L: connect to 2106
    L-->>C: first packet
    C->>L: credentials
    L->>L: check the account
    C->>L: choose the world
    L->>K: WorldPort.status() and expect(client address)
    K->>W: status, expect
    L-->>C: world list and session key
    C->>W: connect to 7777 with the key
    W->>K: LoginPort.admit(account, session key)
    K->>L: admit
    L-->>W: admitted, or refused when the account is already in the world
    W-->>C: enter the world
    W->>K: LoginPort.leave(account) on logout
```

## Reading the views

| View | Question it answers |
|---|---|
| Context | Who uses the platform and what it depends on |
| Containers | What runs, what it exposes, and where state lives |
| Compose stack | What is really delivered today |
| Delivery pipeline | How a change reaches a published image |
| Login and admission | How a player gets into the world |
