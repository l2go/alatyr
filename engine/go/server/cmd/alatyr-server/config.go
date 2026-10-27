// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net"
	"strings"
	"unicode/utf8"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/proto"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// Participant binds an unguessable bearer-token digest to one existing character.
type Participant struct{ ID, Account, Character, TokenSHA256 string }

// Config is immutable after validation. Both listeners must bind numeric loopback
// addresses. Public HTTP runs behind a TLS proxy on the same host; it must strip
// incoming forwarding headers and set X-Forwarded-Proto itself.
type Config struct {
	HTTPAddress, StreamAddress, WorldPath, WorldName string
	ProxyHosts, ProxyOrigins                         []string
	Participants                                     []Participant
	ShutdownSeconds, DatabaseSeconds                 int
}

type admission struct {
	identity bridge.Identity
	digest   [sha256.Size]byte
}

func (c Config) validate() (map[string]admission, error) {
	for _, address := range []string{c.HTTPAddress, c.StreamAddress} {
		if address == "" {
			continue
		}
		host, _, err := net.SplitHostPort(address)
		if err != nil || net.ParseIP(host) == nil || !net.ParseIP(host).IsLoopback() {
			return nil, errors.New("host: listeners must use numeric loopback addresses")
		}
	}
	if c.HTTPAddress != "" && c.HTTPAddress == c.StreamAddress {
		return nil, errors.New("host: duplicate listener address")
	}
	if c.HTTPAddress == "" && c.StreamAddress == "" || c.WorldName == "" || c.WorldPath == "" || c.ShutdownSeconds < 1 || c.ShutdownSeconds > 60 || c.DatabaseSeconds < 1 || c.DatabaseSeconds > 30 || len(c.Participants) < 1 || len(c.Participants) > 64 {
		return nil, errors.New("host: invalid configuration")
	}
	if len(c.ProxyHosts) > 0 != (len(c.ProxyOrigins) > 0) {
		return nil, errors.New("host: proxy hosts and origins must be paired")
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

func limits() meaning.Config {
	c := meaning.Config{Wire: wire.Limits{FrameBytes: 8192, StringBytes: 4096, ListItems: 128, Depth: 4, Records: 64, Fields: 128, IdentifierBytes: 128, WorldBytes: 128, ClientBytes: 128},
		Attempts:   proto.Config{Results: 64, Pending: 8, IntentBytes: 65536, EvidenceMS: 5000, QueueMS: 5000, Classes: []string{"dialog", "take", "trade", "revive", "learn"}},
		ProposalMS: 60000, WindowBytes: 65536, Windows: 8, Objects: 128, HistoryEntries: 16, HistoryBytes: 8192}
	for i := range c.Advertised {
		c.Advertised[i] = 262144
	}
	c.Advertised[0] = uint64(c.Wire.FrameBytes)
	return c
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
