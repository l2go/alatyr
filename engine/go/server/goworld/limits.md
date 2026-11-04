# Direct gateway limit sources

`Limits(sessions)` in `goworld.go` is the host profile's single source. The
codec, dictionary, gateway policy and `worldlink.Config.Meaning` receive that
same value. World visibility is `Objects - 1`; the participant itself occupies
the remaining reference slot. `ParticipantLimit` bounds the durable registry.
These are supported policy budgets, not measured maximum hardware capacity.

The profile preserves the values introduced with the original host. Changing
those values requires a capacity measurement on release hardware. The direct
adapter removes intermediate queues without enlarging the supported registry
or visibility budget. Explicit values are preferable to inventing a maximum
from a short correctness run.

## Wire profile

The first column names the Welcome limit field. Related values are derived
from the same configuration member; equal values for different resources do
not imply that those resources share storage.

| Field | Value | Source and purpose |
| --- | --- | --- |
| 1 | 8192 bytes | `Wire.FrameBytes`; allocation cap before decoding participant frames |
| 2 | 4096 bytes | `Wire.StringBytes`; authored text and untrusted string cap |
| 3 | 128 elements | `Wire.ListItems`; bounded inventory, offers and nested lists |
| 4 | 4 levels | `Wire.Depth`; bounds nested decoding work |
| 5 | 128 objects | `Objects`; visibility is 127 other objects, selected by the world |
| 6 | 64 results | `Attempts.Results`; bounded attempt replay history |
| 7 | 8 windows | `Windows`; bounded proposal authority and cursor metadata |
| 8 | 262144 bytes | Shared `memoryBytes` policy; per-connection output backlog |
| 9 | 0 | Sent diagnostic retention disabled |
| 10 | registered sessions | Supplied by the host; registry bounded by `ParticipantLimit = 64` |
| 11 | 4 connections | Per-session transport budget; permits control and observers |
| 12–13 | 16 / 8 per second | Intent token bucket burst / refill; bounds participant command work |
| 14 | 8 pending | `Attempts.Pending`; bounds unfinished attempts and source outcome reservations |
| 15–16 | 64 records / 128 fields | `Wire.Records` / `Wire.Fields`; bounds frame decoding work |
| 17–18 | 5000 ms each | `Attempts.QueueMS` / `EvidenceMS`; queued admission and genuine asynchronous uncertainty |
| 19 | 1500 ms | Compatibility value for the generic awaited-helper policy; the direct world supplies no awaited helper |
| 20 | 5000 ms | Hello deadline; bounds an unauthenticated protocol handshake |
| 21 | 1000 ms | Transport write deadline; releases a connection that does not read |
| 22 | 10000 ms | Checkpoint deadline; separate from source mailbox resynchronization |
| 23–24 | 15000 / 30000 ms | Ping interval / pong timeout; bounds dead transports |
| 25 | 10 ms | Gateway scheduling batch; input and source delivery can also wake it |
| 26 | 262144 bytes | Shared `memoryBytes` policy; dictionary projection budget for typed fixtures |
| 27 | 65536 bytes | `WindowBytes`; proposal content and saved action-text cap |
| 28–29 | 8 / 4 per second | Service token bucket burst / refill; bounds control and ping work |
| 30 | 262144 bytes | Shared `memoryBytes` policy; encoded checkpoint budget |
| 31–34 | 0 | File diagnostics and their retention disabled |

Additional explicit bounds: `Attempts.IntentBytes = 65536`, proposal lifetime
60000 ms from original issue, fixture history 16 entries / 8192 bytes, identifier,
world and client names 128 bytes each. Those are retained safety policy values,
not a benchmark result. `GatewayPolicy` derives enforced transport limits from
the advertised array. The direct adapter's close attempt is at most 100 ms;
the host owns any persistence retry within the existing deployment stop budget.

## Source queue

The source event budget is `Objects * publicationsPerObject`, with
`publicationsPerObject = 8` defined once in `worldlink`. This allows a complete
visibility departure/reappearance burst plus movement, target and resource
refreshes. Its adequacy is a latency policy: correctness does not depend on
fitting a burst, because overflow captures current source state. Exact outcome
reservations use `Attempts.Pending`; a queued terminal outcome keeps its slot
until consumed. At this profile the single queue has at most 1024 observations
and 8 outcomes. Its notification channel has one slot and stores no data.
`Receiver.Capacity` supplies the finite initial-burst drain budget to the gateway.

## Bounded measurements

`TestLimitsBoundDirectCrowd` opens 64 direct participants in the authored village,
captures every checkpoint, encodes every fact with the production codec, and
closes every source. The recorded maximum was 119 facts, 13893 fact bytes,
136 bytes for the largest fact and 1032 source slots. Framing, checkpoint markers
and retained outcomes consume additional bytes under the same enforced budget.
This test exercises one finite crowded capture, not tick latency, network bots,
PostgreSQL throughput, sustained memory or maximum server population.

`BenchmarkCursorCheckpoint128`, on Apple M4 / darwin-arm64, Go 1.27.3,
`GOMAXPROCS=2`, 100 ms sample: approximately 117 microseconds, 892572 transient
bytes and 1153 allocations per 128-object reference checkpoint. This isolates
reference translation; it excludes world capture, encoding and transport.
It makes no before/after performance claim. Mailbox overflow, barriers, reserved
outcomes and superseded-observer cleanup have separate deterministic tests.

Reproduce without starting a server or running a load test:

```sh
go test -v ./engine/go/server/goworld -run '^TestLimitsBoundDirectCrowd$'
go test ./engine/go/gateway/meaning -run '^$' -bench '^BenchmarkCursorCheckpoint128$' -benchtime=100ms
```

Before raising the registry or choosing a new visibility default, measure a
release-hardware crowd with admission, timer latency, sustained memory, traffic,
shutdown and restart. A finite check cannot establish those operating limits.
