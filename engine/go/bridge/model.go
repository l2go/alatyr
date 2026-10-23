// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package bridge

import (
	"context"
	"net"
	"time"

	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// FailureKind classifies a bridge failure.
type FailureKind uint8

// Failure kinds.
const (
	ConfigError            FailureKind = iota + 1 // invalid Config, Identity or command options
	DialError                                     // the endpoint could not be dialed
	Authentication                                // the endpoint rejected the credentials
	OpenRefused                                   // the endpoint refused to open; Error.Code says why
	StateError                                    // the client state does not admit the operation
	Capacity                                      // a bound on the client or a frame was exceeded
	Protocol                                      // the peer violated the bridge/1 profile
	Timeout                                       // a deadline or the caller's context expired
	Transport                                     // the connection was lost
	RemoteCloseUnconfirmed                        // Close could not confirm the remote closure
)

// Error reports a bridge failure. Code is the endpoint's refusal code for
// OpenRefused. ProtocolKind records the last frame the session failed to read,
// validate or apply: CloseUnsupported for an unsupported frame and
// CloseFrameError for any other failure, including capacity and refusal. It is
// zero if no frame failed and is not reset when the session detaches. Only an
// error that ends a pending Open or Resume through session failure carries it,
// whatever its Kind; a refusal or Bye answered directly does not.
type Error struct {
	Kind         FailureKind
	Code         uint64
	ProtocolKind wire.CloseReason
}

// Error returns a fixed description of the failure kind.
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

// Identity names the account and character to open. Each name is 1 to 128
// bytes of valid UTF-8 without NUL.
type Identity struct{ Account, Character string }

// Config configures a Client. Endpoint must be a 127.0.0.1 host:port and
// defaults to 127.0.0.1:9016. Secret must be nonzero. ResumeBudget defaults to
// 30 seconds and must lie between 1 and 300 seconds. DialContext and Now
// default to a net.Dialer and time.Now.
type Config struct {
	Endpoint     string
	Secret       [32]byte
	ResumeBudget time.Duration
	DialContext  func(context.Context, string, string) (net.Conn, error)
	Now          func() time.Time
}

// Command is one raw command for the endpoint. Key is nonzero and unique for
// the lifetime of the client, including across recovery. Verb selects the
// bridge verb and the payload fields it requires. QueueTTLMS, when set, is at
// most 5000.
type Command struct {
	Key, Verb  uint64
	Payload    []wire.Field
	QueueTTLMS *uint64
}

// Snapshot is the complete checkpoint received while opening or recovering.
// Ready reports that the endpoint declared the checkpoint ready, that no newer
// generation began during it and that no domain is incomplete. Incomplete
// names the domains the checkpoint declared incomplete. FenceSeq is the last
// fact sequence when the checkpoint ended, and EncodedBytes the total encoded
// size of Facts.
type Snapshot struct {
	GatewayEpoch uint64
	Ready        bool
	Incomplete   []string
	Facts        []wire.Record
	FenceSeq     uint64
	EncodedBytes int
}

// Receipt is the endpoint's result for one written command. N is the attempt
// number Write returned and Key the command's key. Requested and Effective are
// verb codes. Namespace and BridgeReason carry a profile-specific reason, Text
// an optional explanation, and Effects the reported effects when
// EffectsPresent is set. Replay marks a receipt delivered again during
// recovery or for a command whose result was already retained. A receipt is
// raw evidence, not a participant outcome.
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

// Batch carries observations and receipts in arrival order. ReceivedAt holds
// the receive time of each record. Snapshot marks records that belong to a
// checkpoint.
type Batch struct {
	Records    []wire.Record
	ReceivedAt []time.Time
	Receipts   []Receipt
	Snapshot   bool
}

// Recovery is the result of Resume: the restored checkpoint and the receipts
// the endpoint delivered during recovery.
type Recovery struct {
	Snapshot Snapshot
	Receipts []Receipt
}

// UpdateKind classifies an Update.
type UpdateKind uint8

// Update kinds.
const (
	LiveBatch UpdateKind = iota + 1 // Batch holds new observations or receipts
	Detached                        // the transport was lost; Resume may reopen it
	Closed                          // the client stopped; the channel closes next
)

// Update is one value from Client.Updates. FailureKind says why the client
// detached or closed.
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
