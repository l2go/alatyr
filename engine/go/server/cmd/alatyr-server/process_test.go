// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/server/internal/migrate"
	"github.com/l2go/alatyr/engine/go/server/internal/pgstore"
	"github.com/l2go/alatyr/engine/go/server/internal/pgtest"
	"github.com/l2go/alatyr/engine/go/world"
)

func TestServerProcessHelper(t *testing.T) {
	if os.Getenv("ALATYR_PROCESS_HELPER") != "1" {
		return
	}
	flag.CommandLine = flag.NewFlagSet("alatyr", flag.ContinueOnError)
	os.Args = []string{"alatyr", "-config", os.Getenv("ALATYR_PROCESS_CONFIG")}
	if err := run(); err != nil {
		t.Fatal(err)
	}
}

func TestParticipantProcessRestart(t *testing.T) {
	dsn := pgtest.Database(t)
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()
	s, err := pgstore.Open(ctx, dsn)
	if err == nil {
		err = migrate.Apply(ctx, dsn)
	}
	if err != nil {
		t.Fatal(err)
	}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, BaseClass: 0, Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104,
		Items: []world.Item{{ObjectID: 2, TemplateID: 999999, Count: 7, Slot: -1}}, QuestVars: []world.QuestVar{{Quest: "authored", Name: "progress", Value: "retained"}}, NameColor: 0xffffff, TitleColor: 0xffff77}
	if err := s.PutCharacter(ctx, c); err != nil {
		t.Fatal(err)
	}
	if err := s.Close(); err != nil {
		t.Fatal(err)
	}
	address := func() string {
		t.Helper()
		l, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		a := l.Addr().String()
		if err := l.Close(); err != nil {
			t.Fatal(err)
		}
		return a
	}
	token := strings.Repeat("a", 64)
	digest := sha256.Sum256([]byte(token))
	root, err := filepath.Abs("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cfg := Config{HTTPAddress: address(), StreamAddress: address(), WorldPath: root, WorldName: "authored", ShutdownSeconds: 5, DatabaseSeconds: 3,
		Participants: []Participant{{ID: "one", Account: strings.ToUpper(c.Account), Character: strings.ToLower(c.Name), TokenSHA256: hex.EncodeToString(digest[:])}}}
	data, err := json.Marshal(cfg)
	if err != nil {
		t.Fatal(err)
	}
	config := filepath.Join(t.TempDir(), "server.json")
	if err := os.WriteFile(config, data, 0600); err != nil {
		t.Fatal(err)
	}
	start := func() func() {
		t.Helper()
		cmd := exec.Command(os.Args[0], "-test.run=^TestServerProcessHelper$", "-test.timeout=30s")
		cmd.Env = append(os.Environ(), "ALATYR_PROCESS_HELPER=1", "ALATYR_PROCESS_CONFIG="+config, "ALATYR_DATABASE_URL="+dsn)
		var output bytes.Buffer
		cmd.Stdout = &output
		cmd.Stderr = &output
		if err := cmd.Start(); err != nil {
			t.Fatal(err)
		}
		done := make(chan error, 1)
		go func() { done <- cmd.Wait() }()
		stopped := false
		stop := func() {
			t.Helper()
			if stopped {
				return
			}
			stopped = true
			if err := cmd.Process.Signal(os.Interrupt); err != nil {
				t.Error(err)
			}
			select {
			case err := <-done:
				if err != nil {
					t.Errorf("server exit: %v\n%s", err, output.String())
				}
			case <-time.After(10 * time.Second):
				cmd.Process.Kill()
				<-done
				t.Error("server shutdown exceeded bound")
			}
		}
		t.Cleanup(stop)
		client := &http.Client{Timeout: 200 * time.Millisecond}
		deadline := time.NewTimer(10 * time.Second)
		defer deadline.Stop()
		ticker := time.NewTicker(10 * time.Millisecond)
		defer ticker.Stop()
		for {
			response, err := client.Get("http://" + cfg.HTTPAddress + "/readyz")
			if err == nil {
				response.Body.Close()
				if response.StatusCode == 200 {
					return stop
				}
			}
			select {
			case err := <-done:
				stopped = true
				t.Fatalf("server startup: %v\n%s", err, output.String())
			case <-deadline.C:
				t.Fatal("server readiness timeout")
			case <-ticker.C:
			}
		}
	}
	stop := start()
	conn, err := net.DialTimeout("tcp", cfg.StreamAddress, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	if _, err := conn.Write([]byte(token + "\n")); err != nil {
		t.Fatal(err)
	}
	stream := &gateway.Stream{Conn: conn, Limit: 8192}
	codec, err := meaning.NewCodec(limits(1))
	if err != nil {
		t.Fatal(err)
	}
	send := func(records ...wire.Record) {
		t.Helper()
		b, err := codec.Encode(records)
		if err != nil {
			t.Fatal(err)
		}
		if err := stream.Write(b, time.Now().Add(time.Second)); err != nil {
			t.Fatal(err)
		}
	}
	send(wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}})
	var control uint64
	welcomed := false
	ready := false
	read := func() []wire.Record {
		t.Helper()
		b, err := stream.Read(time.Now().Add(5 * time.Second))
		if err != nil {
			t.Fatal(err)
		}
		rs, err := codec.Decode(b)
		if err != nil {
			t.Fatal(err)
		}

		return rs
	}
	for !ready || control == 0 || !welcomed {
		for _, r := range read() {
			if r.Type == wire.Welcome {
				welcomed = true
				advertised, ok := wire.Find(r.Fields, 7)
				meaningID, _ := wire.Find(r.Fields, 2)
				worldName, _ := wire.Find(r.Fields, 3)
				if !ok || !reflect.DeepEqual(advertised.Fields, limits(len(cfg.Participants)).LimitsFields()) || testUint(r, 1) != meaning.WireVersion || string(meaningID.Data) != meaning.Meaning || string(worldName.Data) != cfg.WorldName {
					t.Fatalf("Welcome policy mismatch: %+v", r)
				}
			}
			if r.Type == wire.Checkpoint && testUint(r, 1) == uint64(wire.CheckpointEnd) && testUint(r, 3) == 1 {
				ready = true
			}
			if r.Type == wire.Control && testUint(r, 1) == uint64(wire.ControlGranted) {
				control = testUint(r, 2)
			}
		}
	}
	send(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.U(3, true, control), wire.At(11, true, -71027, 257883, -3104)}})
	arrived := false
	for !arrived {
		for _, r := range read() {
			if r.Type != wire.Fact {
				continue
			}
			v, ok := wire.Find(r.Fields, 13)
			if ok && v.XYZ == [3]int32{-71027, 257883, -3104} {
				arrived = true
			}
		}
	}
	stop() // A live participant is saved before the process's world stops.
	verify, err := pgstore.Open(ctx, dsn)
	if err != nil {
		t.Fatal(err)
	}
	chars, err := verify.Characters(ctx, c.Account)
	if err != nil || len(chars) != 1 {
		t.Fatalf("saved characters: %v/%v", chars, err)
	}
	if chars[0].X != -71027 || chars[0].Items[0].TemplateID != 999999 || chars[0].QuestVars[0].Value != "retained" {
		t.Fatalf("saved state: %+v", chars[0])
	}
	if err := verify.Close(); err != nil {
		t.Fatal(err)
	}
	stop = start()
	restoredConn, err := net.DialTimeout("tcp", cfg.StreamAddress, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer restoredConn.Close()
	if _, err := restoredConn.Write([]byte(token + "\n")); err != nil {
		t.Fatal(err)
	}
	stream = &gateway.Stream{Conn: restoredConn, Limit: 8192}
	send(wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}})
	ready = false
	restored := false
	for !ready {
		for _, record := range read() {
			if record.Type == wire.Checkpoint && testUint(record, 1) == uint64(wire.CheckpointEnd) && testUint(record, 3) == 1 {
				ready = true
			}
			if record.Type == wire.Fact {
				position, ok := wire.Find(record.Fields, 13)
				if ok && position.XYZ == [3]int32{-71027, 257883, -3104} {
					restored = true
				}
			}
		}
	}
	if !restored {
		t.Fatal("restarted participant checkpoint lost saved position")
	}
	stop()
}

func testUint(r wire.Record, n uint32) uint64 { v, _ := wire.Find(r.Fields, n); return v.U }
