// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Participant binds an unguessable bearer-token digest to one existing character.
type Participant struct{ ID, Account, Character, TokenSHA256 string }

// Config is immutable after validation. Stream stays on numeric loopback. HTTP
// may bind a container interface only with explicit proxy peers, hosts and origins.
// The trusted TLS proxy strips incoming forwarding headers and sets the scheme.
type Config struct {
	HTTPAddress, StreamAddress, WorldPath, WorldName string
	ProxyHosts, ProxyOrigins, ProxyPeers             []string
	Participants                                     []Participant
	ShutdownSeconds, DatabaseSeconds                 int
}

type admission struct {
	identity bridge.Identity
	digest   [sha256.Size]byte
}

func (c Config) validate() (map[string]admission, error) {
	for _, listener := range []struct {
		address string
		http    bool
	}{{c.HTTPAddress, true}, {c.StreamAddress, false}} {
		if listener.address == "" {
			continue
		}
		host, _, err := net.SplitHostPort(listener.address)
		ip := net.ParseIP(host)
		if err != nil || ip == nil {
			return nil, errors.New("host: listeners require numeric IP addresses")
		}
		if !ip.IsLoopback() && (!listener.http || len(c.ProxyPeers) == 0 || len(c.ProxyHosts) == 0 || len(c.ProxyOrigins) == 0) {
			return nil, errors.New("host: non-loopback HTTP requires explicit trusted proxy configuration")
		}
	}
	if c.HTTPAddress != "" && c.HTTPAddress == c.StreamAddress {
		return nil, errors.New("host: duplicate listener address")
	}
	if c.HTTPAddress == "" && c.StreamAddress == "" || c.WorldName == "" || c.WorldPath == "" || c.ShutdownSeconds < 1 || c.ShutdownSeconds > 60 || c.DatabaseSeconds < 1 || c.DatabaseSeconds > 30 || len(c.Participants) < 1 || len(c.Participants) > 64 {
		return nil, errors.New("host: invalid configuration")
	}
	// ShutdownSeconds is spent independently by Gateway.Close, failed Leave
	// retries and final persistence. Reserve the longest opening/operation join,
	// every port's 100 ms close budget, and two seconds for process teardown.
	// This must fit the deployment's 60-second stop grace before accepting work.
	join := max(time.Duration(c.DatabaseSeconds)*time.Second, 10*time.Second)
	shutdown := 3 * time.Duration(c.ShutdownSeconds) * time.Second
	if shutdown+join+time.Duration(len(c.Participants))*100*time.Millisecond+2*time.Second > 60*time.Second {
		return nil, errors.New("host: shutdown phases exceed the 60-second stop grace")
	}

	if len(c.ProxyHosts) > 0 != (len(c.ProxyOrigins) > 0) {
		return nil, errors.New("host: proxy hosts and origins must be paired")
	}
	for _, peer := range c.ProxyPeers {
		if net.ParseIP(peer) == nil || len(c.ProxyHosts) == 0 {
			return nil, errors.New("host: proxy peers require numeric IPs and proxy allowlists")
		}
	}
	out := make(map[string]admission)
	chars := make(map[string]bool)
	tokens := make(map[[32]byte]bool)
	for _, p := range c.Participants {
		for _, s := range []string{p.ID, p.Account, p.Character} {
			if len(s) < 1 || len(s) > 128 || !utf8.ValidString(s) || strings.ContainsRune(s, 0) {
				return nil, errors.New("host: invalid participant binding")
			}
		}
		raw, err := hex.DecodeString(p.TokenSHA256)
		if err != nil || len(raw) != 32 {
			return nil, errors.New("host: invalid token digest")
		}
		a := admission{identity: bridge.Identity{Account: p.Account, Character: p.Character}, digest: [32]byte(raw)}
		key := strings.ToLower(p.Character)
		if _, ok := out[p.ID]; ok || chars[key] || tokens[a.digest] {
			return nil, errors.New("host: duplicate participant binding")
		}
		out[p.ID] = a
		chars[key] = true
		tokens[a.digest] = true
	}
	return out, nil
}

func limits(sessions int) meaning.Config {
	c := meaning.Config{Wire: wire.Limits{FrameBytes: 8192, StringBytes: 4096, ListItems: 128, Depth: 4, Records: 64, Fields: 128, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts:   proto.Config{Results: 64, Pending: 8, IntentBytes: 65536, EvidenceMS: 5000, QueueMS: 5000, Classes: []string{"dialog", "take", "trade", "revive", "learn"}},
		ProposalMS: 60000, WindowBytes: 65536, Windows: 8, Objects: 128, HistoryEntries: 16, HistoryBytes: 8192}
	// Wire-order limits are also the source for the gateway's enforced policy.
	// No sent ring or file diagnostics are retained: fields 9 and 31-34 are zero.
	c.Advertised = [34]uint64{
		uint64(c.Wire.FrameBytes), uint64(c.Wire.StringBytes), uint64(c.Wire.ListItems), uint64(c.Wire.Depth),
		uint64(c.Objects), uint64(c.Attempts.Results), uint64(c.Windows), 262144, 0,
		uint64(sessions), 4, 16, 8, uint64(c.Attempts.Pending), uint64(c.Wire.Records), uint64(c.Wire.Fields),
		uint64(c.Attempts.QueueMS), uint64(c.Attempts.EvidenceMS), 1500, 5000, 1000, 10000, 15000, 30000, 10,
		262144, uint64(c.WindowBytes), 8, 4, 262144, 0, 0, 0, 0,
	}

	return c
}

// gatewayPolicy derives every advertised gateway bound from the same dictionary
// configuration supplied to the codec and state. Non-wire deployment settings
// (factory, identity grants, origin and shutdown budget) are supplied by the host.
func gatewayPolicy(c meaning.Config) gateway.Config {
	n := func(field int) int { return int(c.Advertised[field-1]) }
	ms := func(field int) time.Duration { return time.Duration(n(field)) * time.Millisecond }
	return gateway.Config{
		Versions: []uint64{meaning.WireVersion}, Meanings: []string{meaning.Meaning}, Limits: c.LimitsFields(), Attempts: c.Attempts,
		FrameBytes: n(1), OutBytes: n(8), Sessions: n(10), Connections: n(11), Teleports: 8,
		IntentBurst: float64(n(12)), IntentRate: float64(n(13)), ServiceBurst: float64(n(28)), ServiceRate: float64(n(29)),
		TargetWait: ms(19), Hello: ms(20), Write: ms(21), Checkpoint: ms(22), Ping: ms(23), Pong: ms(24), Batch: ms(25),
	}
}

// Only this trusted Go-world composition interprets its source-owned labels.
// The participant dictionary treats role strings as opaque data.
func classify(o meaning.Observed) meaning.Capabilities {
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
