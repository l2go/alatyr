# 0005. Netty network core adopted without evaluation

- Status: Accepted
- Date: 2026-10-06

## Context

The 2.x line uses its own NIO transport. The earlier vision proposed Netty 4.2 only after a packet-replay comparison of encryption, ordering, disconnect behavior, p99 latency, and allocations. The maintainer decided to adopt Netty without that evaluation. This record states the decision as made by authority, not as the result of a comparison.

## Decision

The network core is built on Netty 4.2. The protocol codec stays the project's own: frame decoder, both ciphers, and the existing packet classes. The transport order is `io_uring`, then epoll, then NIO, and the default container profile uses epoll because the default container security profile blocks `io_uring` (see decision 0002). The replay comparison is not a gate.

## Alternatives considered

- Keep the own NIO core. It carries no migration risk but keeps the code that Netty replaces.
- Evaluate by packet replay first. It was the earlier plan and was declined.

## Consequences and accepted risks

- No latency or allocation numbers exist for the old core, so no regression claim can be made against it.
- The risks that matter are cipher ordering, back-pressure, and disconnect behavior. They are covered by two measures instead of a comparison: conformance tests of frames and ciphers on fixed vectors built from the existing packet classes, and an end-to-end smoke test in CI that logs in, lists the world, enters the world, and leaves.

## Revisit when

A load run shows latency or allocation that is unacceptable, the smoke test finds an ordering or disconnect defect, or the project needs a transport that Netty does not offer.
