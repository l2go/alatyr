# Direct world adapter

`worldlink` is the production translation boundary between the world and the
participant dictionary. Hosts compose it with their lifecycle and storage policy. It implements `gateway.World` in the same process.
The world owns visibility and current state. The adapter retains observation
reference generations, saved proposal authority and participant attempt bindings.
It does not maintain a scene, encode an intermediate protocol, or start a pump.

The gateway consumes a notification and one typed publication from the world's
mailbox. State updates coalesce by object and property within runs of state;
ordered events and commitments are barriers. The event budget is eight times
the advertised object budget, covering a forget/reveal burst during relocation.
Event overflow requests a fresh checkpoint. Exact outcomes remain in the same
queue and cannot be evicted by state or event traffic. Outcome subscriptions are
bounded by the advertised pending-attempt count.

A checkpoint captures the world and retires queued state at the same source
fence. Every attached connection receives that checkpoint. A transport that
cannot keep up gets another checkpoint; a blocked transport write still has its
own bounded deadline. Backpressure does not end the character's world scope.

Participant results come from `Commit.Outcome`, including separate completion
commitments for delayed talk, pickup, item use and return. A completion retains
the original intent as its cause. Terminal Sent commands remain terminal Sent.
Unknown remains available for an asynchronous operation that has not completed
within its original window, or whose effect could not be established.

Network reconnection stays in the same gateway session and world incarnation.
Its checkpoint includes the gateway's retained attempt outcomes. It does not
repeat commands or negotiate a raw-port recovery protocol. The host owns final
session closure and fenced save retries.

The participant wire format remains `alatyr/1`. `TestGatewayBaselineTranscript`
compares the initial checkpoint, movement and authored dialogue with immutable
bytes captured from the previous adapter. The gateway and world contract suites
also cover exact outcomes, reference safety, economy, relocation and reconnect.

License: [AGPL-3.0-or-later](LICENSE).
