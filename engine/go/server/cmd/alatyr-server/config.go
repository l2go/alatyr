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
