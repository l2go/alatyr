// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// TestImageParticipant runs only against the isolated, already-built image
// stack prepared by deploy/check-server.sh. Ordinary Go checks need no Docker.
func TestImageParticipant(t *testing.T) {
	if os.Getenv("ALATYR_REQUIRE_IMAGE") != "1" {
		t.Skip("image stack not requested")
	}
	endpoint, token, config := os.Getenv("ALATYR_IMAGE_URL"), os.Getenv("ALATYR_IMAGE_TOKEN"), os.Getenv("ALATYR_IMAGE_CONFIG")
	project, composeFile := os.Getenv("ALATYR_COMPOSE_PROJECT"), os.Getenv("ALATYR_COMPOSE_FILE")
	if endpoint == "" || len(token) != 64 || config == "" || !strings.HasPrefix(project, "alatyr-check-") || composeFile == "" {
		t.Fatal("missing isolated image test configuration")
	}
	var cfg Config
	if err := decode(config, &cfg); err != nil {
		t.Fatal(err)
	}
	compose := func(input []byte, args ...string) []byte {
		t.Helper()
		argv := append([]string{"compose", "-p", project, "-f", composeFile}, args...)
		cmd := exec.CommandContext(t.Context(), "docker", argv...)
		cmd.Stdin = bytes.NewReader(input)
		var stderr bytes.Buffer
		cmd.Stderr = &stderr
		out, err := cmd.Output()
		if err != nil {
			t.Fatalf("compose %v: %v\n%s", args, err, stderr.String())
		}
		return out
	}
	codec, err := meaning.NewCodec(limits(len(cfg.Participants)))
	if err != nil {
		t.Fatal(err)
	}
	open := func(token string) (*imageSocket, error) { return openImageSocket(endpoint, token, cfg) }
	if c, err := open(strings.Repeat("b", 64)); err == nil {
		c.Close()
		t.Fatal("unauthorized image participant admitted")
	}
	enter := func(wantX int32) *imageSocket {
		t.Helper()
		c, err := open(token)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { c.Close() })
		sendImage(t, c, codec, wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, meaning.WireVersion), wire.U(2, true, meaning.WireVersion), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}})
		ready, position := false, false
		for !ready || c.control == 0 {
			for _, r := range readImage(t, c, codec) {
				if r.Type == wire.Fact {
					v, ok := wire.Find(r.Fields, 13)
					position = position || ok && v.XYZ == [3]int32{wantX, 257883, -3104}
				}
				if r.Type == wire.Checkpoint && testUint(r, 1) == uint64(wire.CheckpointEnd) && testUint(r, 3) == 1 {
					ready = true
				}
				if r.Type == wire.Control && testUint(r, 1) == uint64(wire.ControlGranted) {
					c.control = testUint(r, 2)
				}
			}
		}
		if !position {
			t.Fatal("image checkpoint lost saved position")
		}
		return c
	}
	stop := func() {
		t.Helper()
		compose(nil, "stop", "-t", "60", "server")
		id := strings.TrimSpace(string(compose(nil, "ps", "--all", "--quiet", "server")))
		cmd := exec.CommandContext(t.Context(), "docker", "inspect", "--format", "{{.State.ExitCode}} {{.HostConfig.ReadonlyRootfs}} {{.Config.User}}", id)
		got, err := cmd.Output()
		if err != nil || strings.TrimSpace(string(got)) != "0 true 10001:10001" {
			t.Fatalf("image shutdown/security: %s/%v", got, err)
		}
		response, err := (&http.Client{Timeout: time.Second}).Get(endpoint + "/readyz")
		if err == nil {
			response.Body.Close()
			if response.StatusCode == 200 {
				t.Fatal("stopped image still ready")
			}
		}
		saved := strings.TrimSpace(string(compose(nil, "exec", "-T", "db", "psql", "-U", "postgres", "-d", "alatyr", "-Atc", `SELECT x||','||sp||','||(SELECT count FROM world.item WHERE id=2)||','||(SELECT value FROM world.player_quest_variable WHERE player_id=1 AND variable_name='progress') FROM world.player WHERE id=1`)))
		if saved != "-71027,20,7,retained" {
			t.Fatalf("live shutdown lost player state: %s", saved)
		}
	}
	c := enter(-71057)
	sendImage(t, c, codec, wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.U(3, true, c.control), wire.At(11, true, -71027, 257883, -3104)}})
	arrived := false
	for !arrived {
		for _, r := range readImage(t, c, codec) {
			if r.Type == wire.Fact {
				v, ok := wire.Find(r.Fields, 13)
				arrived = arrived || ok && v.XYZ == [3]int32{-71027, 257883, -3104}
			}
		}
	}
	stop() // The connection remains alive until Docker sends SIGTERM to PID 1.
	compose(nil, "up", "-d", "--wait", "--wait-timeout", "120", "server")
	enter(-71027)
	stop()
	// Rehearse the runbook backup with the writer stopped, then restore it into
	// an empty world schema. The ordinary startup must accept only its own history.
	dump := compose(nil, "exec", "-T", "db", "pg_dump", "-U", "postgres", "-d", "alatyr", "-n", "world", "-Fc")
	if len(dump) < 5 || string(dump[:5]) != "PGDMP" {
		t.Fatal("invalid world backup")
	}
	compose(nil, "exec", "-T", "db", "psql", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "alatyr", "-c", "DROP SCHEMA world CASCADE")
	compose(dump, "exec", "-T", "db", "pg_restore", "-U", "postgres", "-d", "alatyr", "--exit-on-error")
	compose(nil, "up", "-d", "--wait", "--wait-timeout", "120", "server")
	enter(-71027)
	stop()
}

type imageSocket struct {
	net.Conn
	reader  *bufio.Reader
	control uint64
}

func openImageSocket(endpoint, token string, cfg Config) (*imageSocket, error) {
	u, err := url.Parse(endpoint)
	if err != nil {
		return nil, err
	}
	conn, err := net.DialTimeout("tcp", u.Host, time.Second)
	if err != nil {
		return nil, err
	}
	fail := func(err error) (*imageSocket, error) { conn.Close(); return nil, err }
	if err := conn.SetDeadline(time.Now().Add(5 * time.Second)); err != nil {
		return fail(err)
	}
	var key [16]byte
	rand.Read(key[:])
	request, err := http.NewRequest(http.MethodGet, endpoint+"/participant", nil)
	if err != nil {
		return fail(err)
	}
	request.Host = cfg.ProxyHosts[0]
	request.Header.Set("Origin", cfg.ProxyOrigins[0])
	request.Header.Set("X-Forwarded-Proto", "http")
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("Connection", "Upgrade")
	request.Header.Set("Upgrade", "websocket")
	request.Header.Set("Sec-WebSocket-Version", "13")
	request.Header.Set("Sec-WebSocket-Key", base64.StdEncoding.EncodeToString(key[:]))
	if err := request.Write(conn); err != nil {
		return fail(err)
	}
	reader := bufio.NewReader(conn)
	response, err := http.ReadResponse(reader, request)
	if err != nil {
		return fail(err)
	}
	if response.StatusCode != http.StatusSwitchingProtocols {
		response.Body.Close()
		return fail(errors.New("image participant refused"))
	}
	return &imageSocket{Conn: conn, reader: reader}, nil
}
func sendImage(t *testing.T, c *imageSocket, codec *meaning.Codec, records ...wire.Record) {
	t.Helper()
	data, err := codec.Encode(records)
	if err != nil {
		t.Fatal(err)
	}
	var mask [4]byte
	rand.Read(mask[:])
	header := []byte{0x82, 0x80 | byte(len(data))}
	if len(data) >= 126 {
		header = []byte{0x82, 0xfe, byte(len(data) >> 8), byte(len(data))}
	}
	frame := append(header, mask[:]...)
	for i, b := range data {
		frame = append(frame, b^mask[i%4])
	}
	if err := c.SetWriteDeadline(time.Now().Add(time.Second)); err != nil {
		t.Fatal(err)
	}
	for len(frame) > 0 {
		n, err := c.Write(frame)
		if err != nil {
			t.Fatal(err)
		}
		if n == 0 {
			t.Fatal(io.ErrShortWrite)
		}
		frame = frame[n:]
	}
}
func readImage(t *testing.T, c *imageSocket, codec *meaning.Codec) []wire.Record {
	t.Helper()
	if err := c.SetReadDeadline(time.Now().Add(5 * time.Second)); err != nil {
		t.Fatal(err)
	}
	var head [2]byte
	if _, err := io.ReadFull(c.reader, head[:]); err != nil {
		t.Fatal(err)
	}
	if head[0] != 0x82 || head[1]&0x80 != 0 {
		t.Fatal("unexpected image WebSocket frame")
	}
	size := int(head[1])
	if size == 126 {
		var extended [2]byte
		if _, err := io.ReadFull(c.reader, extended[:]); err != nil {
			t.Fatal(err)
		}
		size = int(binary.BigEndian.Uint16(extended[:]))
	}
	if size < 1 || size > 8192 {
		t.Fatal("image frame exceeds bound")
	}
	data := make([]byte, size)
	if _, err := io.ReadFull(c.reader, data); err != nil {
		t.Fatal(err)
	}
	records, err := codec.Decode(data)
	if err != nil {
		t.Fatal(fmt.Errorf("image records: %w", err))
	}
	return records
}

// Exercise the same masked WebSocket smoke client locally against the actual
// gateway and Go world, without constructing or starting an image.
func TestImageSocketUsesParticipantProtocol(t *testing.T) {
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	store := &hostMemoryStore{}
	var c world.Character
	if err := decode("../../../../../deploy/character.example.json", &c); err != nil {
		t.Fatal(err)
	}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	chars := &characterStore{store: store, catalog: cat, hidden: make(map[int32]hidden)}
	w := world.New(cat, chars, world.WithReservedIDs([]int32{1}))
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	go func() { done <- w.Run(ctx) }()
	defer func() {
		cancel()
		if err := <-done; err != nil {
			t.Error(err)
		}
	}()
	server := httptest.NewUnstartedServer(nil)
	address := server.Listener.Addr().String()
	cfg := configTest()
	cfg.ProxyHosts = []string{address}
	cfg.ProxyOrigins = []string{"http://" + address}
	cfg.ProxyPeers = []string{"127.0.0.1", "::1"}
	admissions, err := cfg.validate()
	if err != nil {
		t.Fatal(err)
	}
	mc := limits(1)
	origin := time.Now()
	r := &runtime{cfg: cfg, store: store, characters: chars, world: w, cat: cat, admissions: admissions, ports: make(map[string]opened), failed: make(map[world.Ref]bool), openingCancel: make(map[string]context.CancelFunc), meaningConfig: backend.Config{Meaning: mc, Origin: origin}}
	policy := gatewayPolicy(mc)
	policy.Origin = origin
	policy.Factory = r.open
	policy.World = cfg.WorldName
	policy.Identities = map[string]gateway.Identity{"one": {Act: true}}
	policy.Codec, err = meaning.NewCodec(mc)
	if err != nil {
		t.Fatal(err)
	}
	r.gateway, err = gateway.New(policy)
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := r.gateway.Close(); err != nil {
			t.Error(err)
		}
	}()
	server.Config.Handler = r.handler(address)
	server.Start()
	defer server.Close()
	token := strings.Repeat("a", 64)
	socket, err := openImageSocket(server.URL, token, cfg)
	if err != nil {
		t.Fatal(err)
	}
	defer socket.Close()
	codec, err := meaning.NewCodec(mc)
	if err != nil {
		t.Fatal(err)
	}
	sendImage(t, socket, codec, wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, meaning.WireVersion), wire.U(2, true, meaning.WireVersion), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}})
	for {
		for _, r := range readImage(t, socket, codec) {
			if r.Type == wire.Checkpoint && testUint(r, 1) == uint64(wire.CheckpointEnd) && testUint(r, 3) == 1 {
				return
			}
		}
	}
}
