# Direct gateway compatibility note

The gateway now consumes typed world values through an in-process interface.
The world owns visible state; the gateway retains reference generations and
attempt numbers. There is no intermediate wire protocol or scene replica.

## Participant-visible changes

`alatyr/1` records, fields, verbs and reason codes are unchanged. Existing
participants, the browser client and the site's live world use the same wire.
The initial checkpoint, movement and authored dialogue are checked against
frozen bytes captured from the previous adapter.

| Situation | Previous outcome path | Direct outcome path |
| --- | --- | --- |
| Learning commits successfully | Candidate held for the entire evidence window | Done immediately after the commitment and known-skills fact |
| A prior attempt in the same class was Unknown | Unbound observations could not complete another attempt | Its own exact commitment establishes Done or Refused |
| Talk or pickup requires approaching a target | Attribution depended on live evidence and receipts | A completion commitment keeps the original intent |
| Return completes through relocation | Evidence depended on the relocation checkpoint | Completion keeps the original intent through appearance and revival |
| An admitted operation has no completion by its original deadline | Unknown | Unknown |
| Walk, strike or cast has terminal Sent | Sent | Sent; later commitments cannot rewrite it |
| A trusted pre-execution refusal arrives after contact | Refused only with exclusive evidence | Refused from that exact intent's commitment |

An in-memory Done is not a storage acknowledgement. Persistence errors still
reach host-owned fenced cleanup and retry. This change introduces no database
migration or new protocol stage.

## Backpressure and reconnection

One source mailbox coalesces positions, targets, vitals and posture until an
ordered event creates a barrier. Event overflow requests a fresh authoritative
checkpoint; exact outcomes survive in reserved slots of that same mailbox.
Every connection to a participant receives the same source checkpoint fence.
A blocked transport write still closes only its own connection at its deadline.
A slow source consumer no longer ends the character's world incarnation.

A replacement network connection receives a checkpoint of the same incarnation
and the gateway's retained attempt outcomes. Replaying the identical original
intent returns its retained result and does not execute it again. Reference
lifetimes are retired across an observation gap; an old reference cannot gain
new authority after re-entry.

The source and gateway bounds come from [the shared host profile](../../engine/go/server/goworld/limits.md).
The finite crowd and overflow checks are bounded correctness measurements,
not a claim about maximum hardware capacity. No long load run is part of this
change. A release-hardware capacity sweep remains necessary before changing
visibility or participant defaults.

## Upgrade and rollback

There are no new configuration keys and no database or participant-wire change.
The existing registry, visibility, frame and buffer budgets remain in force.
Deploy the server as one unit; there is no runtime switch back to the removed
bridge. Rolling back the server code restores the previous adapter and its
backpressure behaviour while using the same saved character data.
