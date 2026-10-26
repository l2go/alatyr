# Go server packages

`github.com/l2go/alatyr/engine/go` is the AGPL-3.0-or-later module for
[gateway](gateway/README.md), [bridge](bridge/README.md),
[world](world/README.md), and [worldbridge](worldbridge/README.md).
Keeping the server packages in one module allows changes across these packages
to be built and reviewed together without intermediate dependency releases.
The module root has no Go package.

[protocol](protocol/README.md) is a separate Apache-2.0 module, pinned to a
published version. It has no server dependencies and remains independently
releasable. Package import paths are unchanged. Consumers of the former server
modules must replace all old server module requirements, including indirect
ones, with `github.com/l2go/alatyr/engine/go` to avoid ambiguous imports.

From this directory, check all server packages with:

```sh
go vet ./...
go test -race -count=1 -timeout=2m ./...
GOWORK=off go vet ./...
GOWORK=off go test -race -count=1 -timeout=2m ./...
```

Check protocol separately from its directory in both modes. The parent `./...`
does not include a nested module. The repository boundary checker enforces
package dependency directions, module ownership, the protocol pin, licenses,
and public content. See [Go components](../../docs/engine-go/README.md).

The [AGPL-3.0-or-later license](LICENSE) covers this module's root files and
all server packages, including worldbridge. The nested protocol module retains
its [Apache-2.0 license](protocol/LICENSE).
