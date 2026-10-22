// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"context"
	"github.com/l2go/l2jfree/engine/go/protocol/wire"
	"net"
	"time"
)

type FailureKind uint8

const (
	ConfigError FailureKind = iota + 1
	DialError
	Authentication
	OpenRefused
	StateError
	Capacity
	Protocol
	Timeout
	Transport
	RemoteCloseUnconfirmed
)

type Error struct {
	Kind         FailureKind
	Code         uint64
	ProtocolKind wire.CloseReason
}

func (e *Error) Error() string {
	switch e.Kind {
	case ConfigError:
		return "invalid bridge configuration"
	case DialError:
		return "bridge dial failed"
	case Authentication:
		return "bridge authentication failed"
	case OpenRefused:
		return "bridge opening refused"
	case StateError:
		return "bridge state does not admit operation"
	case Capacity:
		return "bridge capacity exceeded"
	case Protocol:
		return "bridge protocol failure"
	case Timeout:
		return "bridge deadline expired"
	case Transport:
		return "bridge transport lost"
	default:
		return "remote closure unconfirmed"
	}
}
func failure(k FailureKind) *Error { return &Error{Kind: k} }

type Identity struct{ Account, Character string }
type Config struct {
	Endpoint     string
	Secret       [32]byte
	ResumeBudget time.Duration
	DialContext  func(context.Context, string, string) (net.Conn, error)
	Now          func() time.Time
}
type Command struct {
	Key, Verb  uint64
	Payload    []wire.Field
	QueueTTLMS *uint64
}
type Snapshot struct {
	GatewayEpoch uint64
	Ready        bool
	Incomplete   []string
	Facts        []wire.Record
	FenceSeq     uint64
	EncodedBytes int
}
type Receipt struct {
	N, Key, GatewayEpoch   uint64
	Stage                  wire.Stage
	Reason                 wire.Reason
	Delivery               wire.Delivery
	Requested, Effective   uint64
	Namespace              string
	BridgeReason           *uint64
	Text                   *wire.Value
	Effects                []wire.Value
	EffectsPresent, Replay bool
	ObservedAt             time.Time
}
type Batch struct {
	Records    []wire.Record
	ReceivedAt []time.Time
	Receipts   []Receipt
	Snapshot   bool
}
type Recovery struct {
	Snapshot Snapshot
	Receipts []Receipt
}
type UpdateKind uint8

const (
	LiveBatch UpdateKind = iota + 1
	Detached
	Closed
)

type Update struct {
	Kind         UpdateKind
	Batch        Batch
	FailureKind  FailureKind
	GatewayEpoch uint64
}

// ProposalExpiry is a conservative monotonic bound rooted at the original Open.
// It must not be recomputed with a new origin on recovery.
func ProposalExpiry(origin, received time.Time, proposalMS int64) (time.Time, bool) {
	const maxMS = int64((1<<63-1)/int64(time.Millisecond)) - 60000
	if proposalMS < 0 || proposalMS > maxMS || origin.IsZero() || received.IsZero() {
		return time.Time{}, false
	}
	a := origin.Add(time.Duration(proposalMS+60000) * time.Millisecond)
	b := received.Add(time.Minute)
	if b.Before(a) {
		a = b
	}
	return a, true
}
