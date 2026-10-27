# Full-stack world parity fixtures

`bridge-integration.sh` runs the same `worldtest/TestOkolitsaParity` cases through
`gatewaytest` on the Go world and on the packaged Java bridge. Initial characters
and SQL are derived from one fixture. Three additional monsters use fixed reward
tables so that party and neutral shares have exact comparable balances. The
ordinary authored hunt still uses the world's real drop table.

The temporary `WorldFixture` agent exposes a bounded, authenticated loopback
control socket only in this disposable CI process. It calls existing item and
party mechanics and applies explicit synthetic harm through normal HP handling.
It does not transform production classes, forge bridge evidence, or enter the
published distribution. Client operations run on the admitted player's FIFO.
A timed-out operation fails its scenario and is never retried. Neutral harm uses
an actual non-player source (the probe itself), because the reference HP handler
rejects a null attacker before accounting. This changes no Java production class.
The process owns the socket; the integration shell owns and terminates the
process and database.

Comparison uses outcomes, causal publication order, conserved balances, and
visibility. It does not compare packet bytes, text, role labels, or timer values.
Ordinary drop counts are checked against the authored range and conserved through
pickup and Close. Exact allocations use fixed tables and the 14 explicit reward
vectors consumed by both `hunt.jsh` and `world/hunt` tests. Facts are not sorted.

The Java neutral-finisher path omits the highest-attacker drop penalty. The Go
world retains that penalty. Authored levels 1–5 do not reach this case; it is a
known boundary of the live comparison, covered separately by Go reward tests.

Run the integration shell only in CI after packaging the Java platform. Local Go
checks run `go test ./worldtest ./world/... ./worldbridge`; the Java half requires
the isolated integration environment and is otherwise omitted.

The two Go test packages run sequentially against the single Java process. The
parity cases close and verify their remote save before participant cleanup.
Participant observation waits allow asynchronous delivery within the original
runtime evidence windows; a mismatched terminal result fails immediately. The
shared driver and frozen contract oracles are unchanged.
