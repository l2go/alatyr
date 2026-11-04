// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package goworld

import (
	"context"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

// ParticipantLimit is the supported registry budget, not a measured hardware maximum.
// See limits.md for its source and the scope of the finite crowd check.
const ParticipantLimit = 64

// Limits returns the dictionary configuration for a host admitting sessions
// participants. Its wire-order advertised limits are also the source of the
// gateway's enforced policy.
func Limits(sessions int) meaning.Config {
	const memoryBytes = 1 << 18 // Shared per-participant byte budget; see limits.md.
	c := meaning.Config{Wire: wire.Limits{FrameBytes: 8192, StringBytes: 4096, ListItems: 128, Depth: 4, Records: 64, Fields: 128, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts:   proto.Config{Results: 64, Pending: 8, IntentBytes: 65536, EvidenceMS: 5000, QueueMS: 5000, Classes: []string{"dialog", "take", "trade", "revive", "learn"}},
		ProposalMS: 60000, WindowBytes: 65536, Windows: 8, Objects: 128, HistoryEntries: 16, HistoryBytes: 8192}
	// No sent ring or file diagnostics are retained: fields 9 and 31-34 are zero.
	c.Advertised = [34]uint64{
		uint64(c.Wire.FrameBytes), uint64(c.Wire.StringBytes), uint64(c.Wire.ListItems), uint64(c.Wire.Depth),
		uint64(c.Objects), uint64(c.Attempts.Results), uint64(c.Windows), memoryBytes, 0,
		uint64(sessions), 4, 16, 8, uint64(c.Attempts.Pending), uint64(c.Wire.Records), uint64(c.Wire.Fields),
		uint64(c.Attempts.QueueMS), uint64(c.Attempts.EvidenceMS), 1500, 5000, 1000, 10000, 15000, 30000, 10,
		memoryBytes, uint64(c.WindowBytes), 8, 4, memoryBytes, 0, 0, 0, 0,
	}
	return c
}

// GatewayPolicy derives every advertised gateway bound from the same dictionary
// configuration supplied to the codec and state. Non-wire deployment settings
// (factory, identity grants, origin and shutdown budget) are supplied by the host.
func GatewayPolicy(c meaning.Config) gateway.Config {
	n := func(field int) int { return int(c.Advertised[field-1]) }
	ms := func(field int) time.Duration { return time.Duration(n(field)) * time.Millisecond }
	return gateway.Config{
		Versions: []uint64{meaning.WireVersion}, Meanings: []string{meaning.Meaning}, Limits: c.LimitsFields(), Attempts: c.Attempts,
		FrameBytes: n(1), OutBytes: n(8), Sessions: n(10), Connections: n(11), Teleports: 8,
		IntentBurst: float64(n(12)), IntentRate: float64(n(13)), ServiceBurst: float64(n(28)), ServiceRate: float64(n(29)),
		TargetWait: ms(19), Hello: ms(20), Write: ms(21), Checkpoint: ms(22), Ping: ms(23), Pong: ms(24), Batch: ms(25),
	}
}

// Classify interprets the Go world's source-owned role labels. Only this
// trusted composition reads them; the participant dictionary treats role
// strings as opaque data.
func Classify(o meaning.Observed) meaning.Capabilities {
	switch o.Role {
	case "tutor", "provisioner", "folk":
		return meaning.Capabilities{Talk: true, Cast: true}
	case "creature":
		return meaning.Capabilities{Strike: true, Cast: true}
	case meaning.RoleDrop:
		return meaning.Capabilities{Pickup: true}
	default:
		return meaning.Capabilities{}
	}
}

// BackendConfig supplies a shared clock and target classifier for direct sessions.
func BackendConfig(mc meaning.Config, origin time.Time) worldlink.Config {
	return worldlink.Config{Meaning: mc, Origin: origin, Policy: meaning.Policy{Classify: Classify}}
}

// Open binds a trusted existing character directly to the participant gateway.
// The returned session owns departure and its stable save result.
func Open(ctx context.Context, w *world.World, cat *catalog.Catalog, id worldlink.Identity, load func(context.Context, worldlink.Identity) (world.Character, error), cfg worldlink.Config) (*worldlink.Session, gateway.State, error) {
	cfg.World, cfg.Catalog, cfg.Load = w, cat, load
	return worldlink.Open(ctx, cfg, id)
}
