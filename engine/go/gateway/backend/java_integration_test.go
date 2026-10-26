// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package backend_test

import (
	"context"
	"encoding/hex"
	"errors"
	"net"
	"os"
	"os/exec"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// These tests use only the public bridge port. tools/ci/bridge-integration.sh
// starts the packaged Java platform and PostgreSQL and supplies isolated rows.
// Ordinary Go checks still compile them without starting a Java process.
func javaConfig(t *testing.T) bridge.Config {
	t.Helper()
	endpoint := os.Getenv("JAVA_BRIDGE_ENDPOINT")
	if endpoint == "" {
		t.Skip("live Java endpoint is supplied by the integration job")
	}
	secret, err := hex.DecodeString(os.Getenv("JAVA_BRIDGE_SECRET"))
	if err != nil || len(secret) != 32 || os.Getenv("JAVA_BRIDGE_DB_CONTAINER") == "" {
		t.Fatal("live endpoint requires a 32-byte secret and disposable database")
	}
	cfg := bridge.Config{Endpoint: endpoint}
	copy(cfg.Secret[:], secret)
	return cfg
}

type javaDial struct {
	mu   sync.Mutex
	conn net.Conn
}

func (d *javaDial) dial(ctx context.Context, network, address string) (net.Conn, error) {
	c, err := (&net.Dialer{}).DialContext(ctx, network, address)
	if err == nil {
		d.mu.Lock()
		d.conn = c
		d.mu.Unlock()
	}
	return c, err
}

func (d *javaDial) lose(t *testing.T) {
	t.Helper()
	d.mu.Lock()
	c := d.conn
	d.mu.Unlock()
	if c == nil {
		t.Fatal("no live transport")
	}
	if err := c.Close(); err != nil {
		t.Fatal(err)
	}
}

type javaPeer struct {
	t        *testing.T
	c        *bridge.Client
	records  []wire.Record
	receipts []bridge.Receipt
}

func openJava(t *testing.T, ctx context.Context, cfg bridge.Config, account, character string) (*javaPeer, bridge.Snapshot) {
	t.Helper()
	c, snapshot, err := bridge.Open(ctx, cfg, bridge.Identity{Account: account, Character: character})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := c.Close(); err != nil {
			t.Error(err)
		}
	})
	if !snapshot.Ready || len(snapshot.Incomplete) != 0 || snapshot.GatewayEpoch != 1 {
		t.Fatal("opening must return a complete ready checkpoint")
	}
	p := &javaPeer{t: t, c: c, records: snapshot.Facts}
	p.checkFacts(snapshot.Facts)
	return p, snapshot
}

func (p *javaPeer) checkFacts(records []wire.Record) {
	p.t.Helper()
	for _, r := range records {
		if r.Type == wire.Fact {
			if _, ok := wire.Find(r.Fields, 32); ok {
				p.t.Fatal("raw bridge fact has a cause")
			}
		}
	}
}

func (p *javaPeer) next(ctx context.Context) bridge.Update {
	p.t.Helper()
	select {
	case u, ok := <-p.c.Updates():
		if !ok || u.Kind == bridge.Closed {
			p.t.Fatalf("bridge closed before the expected observation: kind=%d failure=%d", u.Kind, u.FailureKind)
		}
		p.checkFacts(u.Batch.Records)
		if len(p.records)+len(u.Batch.Records) > 8192 || len(p.receipts)+len(u.Batch.Receipts) > 64 {
			p.t.Fatal("live test transcript exceeded its bound")
		}
		p.records = append(p.records, u.Batch.Records...)
		p.receipts = append(p.receipts, u.Batch.Receipts...)
		return u
	case <-ctx.Done():
		p.t.Fatal(ctx.Err())
		return bridge.Update{}
	}
}

func (p *javaPeer) fact(ctx context.Context, match func(wire.Record) bool) wire.Record {
	p.t.Helper()
	for {
		for _, r := range p.records {
			if r.Type == wire.Fact && match(r) {
				return r
			}
		}
		p.next(ctx)
	}
}

func (p *javaPeer) write(ctx context.Context, command bridge.Command, stage wire.Stage) bridge.Receipt {
	p.t.Helper()
	start := len(p.receipts)
	n, err := p.c.Write(ctx, command)
	if err != nil {
		p.t.Fatal(err)
	}
	for {
		for _, r := range p.receipts[start:] {
			if r.N == n {
				if r.Stage != stage || r.Key != command.Key {
					p.t.Fatalf("receipt n=%d key=%d stage=%d reason=%d, want key=%d stage=%d", r.N, r.Key, r.Stage, r.Reason, command.Key, stage)
				}
				return r
			}
		}
		p.next(ctx)
	}
}

func javaChoice(t *testing.T, dialogue wire.Record, suffix string) string {
	t.Helper()
	options, ok := wire.Find(dialogue.Fields, 68)
	if !ok {
		t.Fatal("dialogue omitted its captured choices")
	}
	for _, option := range options.Items {
		value, found := wire.Find(option.Fields, 3)
		if found && strings.HasSuffix(string(value.Data), suffix) {
			return string(value.Data)
		}
	}
	t.Fatalf("dialogue did not issue action suffix %q", suffix)
	return ""
}

func javaSQL(t *testing.T, sql string) string {
	t.Helper()
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "docker", "exec", "--env", "PGOPTIONS=-c statement_timeout=3000 -c lock_timeout=2000",
		os.Getenv("JAVA_BRIDGE_DB_CONTAINER"), "psql", "-X", "-U", "postgres", "-d", "bridge", "-v", "ON_ERROR_STOP=1", "-Atc", sql)
	cmd.WaitDelay = time.Second
	b, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("disposable database assertion: %v: %s", err, b)
	}
	return strings.TrimSpace(string(b))
}

func javaNC(t *testing.T, ctx context.Context) {
	t.Helper()
	jar, password, logPath := os.Getenv("JAVA_BRIDGE_NC_JAR"), os.Getenv("JAVA_BRIDGE_NC_PASSWORD"), os.Getenv("JAVA_BRIDGE_NC_LOG")
	if jar == "" || password == "" || logPath == "" {
		t.Fatal("live acceptance requires the packaged ordinary client")
	}
	log, err := os.Create(logPath)
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := log.Close(); err != nil {
			t.Error(err)
		}
	}()
	cmd := exec.CommandContext(ctx, "java", "-jar", jar, "--host", "127.0.0.1", "--timeout-seconds", "30",
		"--account", "bridgelegacy", "--password", password, "--existing-characters", "0")
	cmd.WaitDelay = time.Second
	cmd.Stdout, cmd.Stderr = log, log
	if err := cmd.Run(); err != nil {
		t.Fatalf("ordinary client beside the live bridge failed: %v; see nc.log", err)
	}
}

func javaInventory(t *testing.T, snapshot bridge.Snapshot, wantCoin, wantItem int64) {
	t.Helper()
	var coin, count int64
	carried := false
	for _, r := range snapshot.Facts {
		if profile.U(r, 1) != 14 {
			continue
		}
		carried = true
		rows, ok := wire.Find(r.Fields, 24)
		if !ok {
			t.Fatal("carried checkpoint omitted its inventory")
		}
		for _, row := range rows.Items {
			item := wire.Record{Fields: row.Fields}
			switch profile.S(item, 3) {
			case 57:
				coin += profile.S(item, 4)
			case 1060:
				count += profile.S(item, 4)
			}
		}
	}
	if !carried || coin != wantCoin || count != wantItem {
		t.Fatalf("checkpoint carried=%v coin=%d item=%d, want true/%d/%d", carried, coin, count, wantCoin, wantItem)
	}
}

func TestJavaBridgeTradeAndRecovery(t *testing.T) {
	cfg := javaConfig(t)
	ctx, cancel := context.WithTimeout(t.Context(), 45*time.Second)
	defer cancel()
	bad := cfg
	bad.Secret[0] ^= 255
	if c, _, err := bridge.Open(ctx, bad, bridge.Identity{Account: "missing-account", Character: "Missing"}); err == nil {
		if closeErr := c.Close(); closeErr != nil {
			t.Error(closeErr)
		}
		t.Fatal("wrong secret admitted a session")
	} else {
		var failure *bridge.Error
		if !errors.As(err, &failure) || failure.Kind != bridge.Authentication {
			t.Fatalf("wrong secret: %v, want authentication rejection", err)
		}
	}
	dial := &javaDial{}
	cfg.DialContext = dial.dial
	p, snapshot := openJava(t, ctx, cfg, "bridge-trade", "BridgeTrader")
	javaInventory(t, snapshot, 600, 0)
	javaNC(t, ctx)
	merchant := p.fact(ctx, func(r wire.Record) bool { return profile.S(r, 73) == 30002 })
	id := profile.S(merchant, 64)
	p.write(ctx, bridge.Command{Key: 1, Verb: 10, Payload: []wire.Field{wire.S(64, true, id)}}, wire.StageDone)
	p.write(ctx, bridge.Command{Key: 2, Verb: 5, Payload: []wire.Field{wire.S(64, true, id)}}, wire.StageDone)
	dialogue := p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 11 && profile.S(r, 64) == id })
	buyAction := javaChoice(t, dialogue, "_Buy 731")
	p.write(ctx, bridge.Command{Key: 3, Verb: 6, Payload: []wire.Field{wire.S(64, true, id), wire.Data(68, true, buyAction)}}, wire.StageUnknown)
	p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 12 })
	buy := bridge.Command{Key: 4, Verb: 7, Payload: []wire.Field{wire.S(14, true, 731), wire.S(66, true, 1060), wire.S(16, true, 2)}}
	receipt := p.write(ctx, buy, wire.StageDone)
	if !receipt.EffectsPresent || len(receipt.Effects) != 2 {
		t.Fatal("purchase must contain its actual coin and item receipts")
	}
	p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 15 })
	dial.lose(t)
	for p.next(ctx).Kind != bridge.Detached {
	}
	recovered, err := p.c.Resume(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if !recovered.Snapshot.Ready || recovered.Snapshot.GatewayEpoch != 2 {
		t.Fatal("Resume did not restore the same ready session at epoch 2")
	}
	p.checkFacts(recovered.Snapshot.Facts)
	javaInventory(t, recovered.Snapshot, 576, 2)
	found := false
	for _, r := range recovered.Receipts {
		if r.N == receipt.N && r.Key == receipt.Key && r.Stage == wire.StageDone && r.Replay && reflect.DeepEqual(r.Effects, receipt.Effects) {
			found = true
		}
	}
	if !found {
		t.Fatal("Resume lost or changed the retained purchase result")
	}
	// The Client owns keys and does not expose an API for resubmitting an old n.
	// Recovery replays the Java result; repeating this caller key is rejected locally.
	if _, err := p.c.Write(ctx, buy); err == nil {
		t.Fatal("duplicate caller key was accepted")
	} else {
		var failure *bridge.Error
		if !errors.As(err, &failure) || failure.Kind != bridge.StateError {
			t.Fatalf("duplicate caller key: %v, want state error", err)
		}
	}
	sellAction := javaChoice(t, dialogue, "_Sell")
	p.write(ctx, bridge.Command{Key: 5, Verb: 6, Payload: []wire.Field{wire.S(64, true, id), wire.Data(68, true, sellAction)}}, wire.StageUnknown)
	offer := p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 13 })
	rows, ok := wire.Find(offer.Fields, 24)
	if !ok {
		t.Fatal("sale offer omitted its items")
	}
	var itemObject int64
	for _, row := range rows.Items {
		item := wire.Record{Fields: row.Fields}
		if profile.S(item, 3) == 1060 && profile.S(item, 4) == 2 && profile.S(item, 5) == 6 {
			itemObject = profile.S(item, 2)
		}
	}
	if itemObject == 0 {
		t.Fatal("sale offer did not contain the purchased stack and actual sale price")
	}
	sale := p.write(ctx, bridge.Command{Key: 6, Verb: 8, Payload: []wire.Field{wire.S(14, true, 0), wire.S(64, true, itemObject), wire.S(66, true, 1060), wire.S(16, true, 1)}}, wire.StageDone)
	if !sale.EffectsPresent || len(sale.Effects) != 2 {
		t.Fatal("sale did not preserve removal and actual coin receipts")
	}
	if err := p.c.Close(); err != nil {
		t.Fatal(err)
	}
	if got := javaSQL(t, "SELECT item_template_id, count FROM world.item WHERE owner_player_id=1500000001 ORDER BY item_template_id"); got != "57|582\n1060|1" {
		t.Fatalf("saved inventory=%q, want one purchase and one sale", got)
	}
	if got := javaSQL(t, "SELECT is_online FROM world.player WHERE id=1500000001"); got != "f" {
		t.Fatalf("saved online state=%q, want f", got)
	}
}

func TestJavaBridgeLearningPersistsAndAnAlreadyKnownSkillIsNotDone(t *testing.T) {
	cfg := javaConfig(t)
	ctx, cancel := context.WithTimeout(t.Context(), 45*time.Second)
	defer cancel()
	p, _ := openJava(t, ctx, cfg, "bridge-learn", "BridgeLearner")
	trainer := p.fact(ctx, func(r wire.Record) bool { return profile.S(r, 73) == 30001 })
	id := profile.S(trainer, 64)
	p.write(ctx, bridge.Command{Key: 1, Verb: 10, Payload: []wire.Field{wire.S(64, true, id)}}, wire.StageDone)
	p.write(ctx, bridge.Command{Key: 2, Verb: 5, Payload: []wire.Field{wire.S(64, true, id)}}, wire.StageDone)
	dialogue := p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 11 && profile.S(r, 64) == id })
	action := javaChoice(t, dialogue, "_SkillList")
	p.write(ctx, bridge.Command{Key: 3, Verb: 6, Payload: []wire.Field{wire.S(64, true, id), wire.Data(68, true, action)}}, wire.StageUnknown)
	p.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 22 && profile.U(r, 69) == 0 })
	command := bridge.Command{Key: 4, Verb: 13, Payload: []wire.Field{wire.S(14, true, 0), wire.Obj(18, true, []wire.Field{wire.S(1, true, 257), wire.S(2, true, 1)})}}
	learned := p.write(ctx, command, wire.StageDone)
	if !learned.EffectsPresent || len(learned.Effects) != 2 {
		t.Fatal("learning must retain its skill transition and actual SP cost")
	}
	p.fact(ctx, func(r wire.Record) bool {
		if profile.U(r, 1) != 23 {
			return false
		}
		rows, _ := wire.Find(r.Fields, 30)
		for _, row := range rows.Items {
			skill := wire.Record{Fields: row.Fields}
			if profile.S(skill, 1) == 257 && profile.S(skill, 2) == 1 {
				return true
			}
		}
		return false
	})
	command.Key = 5
	if known := p.write(ctx, command, wire.StageUnknown); known.EffectsPresent {
		t.Fatal("already-known skill manufactured a second done result")
	}
	if err := p.c.Close(); err != nil {
		t.Fatal(err)
	}
	if got := javaSQL(t, "SELECT sp FROM world.player WHERE id=1500000004"); got != "88" {
		t.Fatalf("saved SP=%q, want exactly one charge of 12", got)
	}
	if got := javaSQL(t, "SELECT skill_level FROM world.player_skill WHERE player_id=1500000004 AND class_index=0 AND skill_id=257"); got != "1" {
		t.Fatalf("saved skill=%q, want level 1", got)
	}
}

// The tap observes rather than fabricates raw receipts. Its forwarding worker
// has one owner, finite buffers, a stop path and a joined Close.
type javaTap struct {
	bridge.Port
	updates chan bridge.Update
	stop    chan struct{}
	done    chan struct{}
	once    sync.Once
	mu      sync.Mutex
	results []bridge.Receipt
	over    bool
	err     error
}

func tapJava(port bridge.Port) *javaTap {
	p := &javaTap{Port: port, updates: make(chan bridge.Update, 8), stop: make(chan struct{}), done: make(chan struct{})}
	go func() {
		defer close(p.done)
		defer close(p.updates)
		for {
			select {
			case <-p.stop:
				return
			case u, ok := <-port.Updates():
				if !ok {
					return
				}
				p.mu.Lock()
				if len(p.results)+len(u.Batch.Receipts) > 64 {
					p.over = true
				} else {
					p.results = append(p.results, u.Batch.Receipts...)
				}
				p.mu.Unlock()
				select {
				case p.updates <- u:
				case <-p.stop:
					return
				}
			}
		}
	}()
	return p
}

func (p *javaTap) Updates() <-chan bridge.Update { return p.updates }
func (p *javaTap) Close() error {
	p.once.Do(func() {
		close(p.stop)
		p.err = p.Port.Close()
		<-p.done
	})
	return p.err
}

func TestJavaBridgeReturnCompletesInTheParticipantAndWorld(t *testing.T) {
	cfg := javaConfig(t)
	ctx, cancel := context.WithTimeout(t.Context(), 45*time.Second)
	defer cancel()
	witness, _ := openJava(t, ctx, cfg, "bridge-witness", "BridgeWitness")
	origin := time.Now()
	raw, snapshot := openJava(t, ctx, cfg, "bridge-rise", "BridgeRiser")
	self := int64(1500000002)
	dead := raw.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 1 && profile.S(r, 64) == self })
	if profile.U(dead, 70) != 0 {
		t.Fatal("return fixture must start dead")
	}
	witness.fact(ctx, func(r wire.Record) bool {
		return profile.U(r, 1) == 1 && profile.S(r, 64) == self
	})
	tap := tapJava(raw.c)
	limits := gatewaytest.Config()
	session, world, state, err := backend.New(tap, snapshot, backend.Config{Meaning: limits, Origin: origin,
		// Only this isolated character's return command can revive it. No other
		// caller, combatant, script, GM or delayed revive is admitted in this fixture.
		Source: func(e meaning.Event) meaning.Source { return meaning.Source{Return: e.ID == int32(self)} }})
	if err != nil {
		if closeErr := tap.Close(); closeErr != nil {
			t.Error(closeErr)
		}
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := session.Close(); err != nil {
			t.Error(err)
		}
	})
	g := gatewaytest.New(t, limits, func(context.Context, string) (gateway.World, gateway.State, error) { return world, state, nil }, origin)
	peer := gatewaytest.Join(t, g, limits, "one", wire.ModeAct, true)
	peer.Send(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 9), wire.U(3, true, peer.Control)}})
	peer.Result(1, wire.StageSent)
	done := peer.Result(1, wire.StageDone)
	if gatewaytest.U(done, 3) != uint64(wire.ReasonNone) || peer.Epoch != 2 || !peer.Ready {
		t.Fatal("participant return completed without the new ready relocation epoch")
	}
	causes := 0
	for _, r := range peer.Records {
		if r.Type == wire.Fact && gatewaytest.U(r, 1) == 7 && gatewaytest.U(r, 32) == 1 {
			causes++
		}
	}
	if causes != 1 {
		t.Fatalf("participant live revival causes=%d, want 1", causes)
	}
	witness.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 7 && profile.S(r, 64) == self })
	witness.fact(ctx, func(r wire.Record) bool {
		position, ok := wire.Find(r.Fields, 13)
		return profile.U(r, 1) == 1 && profile.S(r, 64) == self && ok && position.XYZ == [3]int32{-72117, 258063, -3099}
	})
	tap.mu.Lock()
	results := append([]bridge.Receipt(nil), tap.results...)
	over := tap.over
	tap.mu.Unlock()
	if over || len(results) != 1 || results[0].Requested != 9 || results[0].Stage != wire.StageSent {
		t.Fatal("raw return must remain sent; only the participant dictionary can prove done")
	}
	if err := session.Close(); err != nil {
		t.Fatal(err)
	}
	if got := javaSQL(t, "SELECT current_hp > 0, is_online, x, y, z FROM world.player WHERE id=1500000002"); got != "t|f|-72117|258063|-3099" {
		t.Fatalf("saved revival=%q, want alive/offline at the town restart point", got)
	}
}

// A checkpoint is a fresh description. Its Appeared must be followed by current
// Moving, including when the observer first enters visibility and later resumes.
func TestJavaBridgeMovingActorSurvivesFreshDescriptions(t *testing.T) {
	cfg := javaConfig(t)
	ctx, cancel := context.WithTimeout(t.Context(), 45*time.Second)
	defer cancel()
	mover, initial := openJava(t, ctx, cfg, "bridge-move", "BridgeMover")
	const actor = int64(1500000005)
	var origin [3]int32
	found := false
	for _, r := range initial.Facts {
		if profile.U(r, 1) == 1 && profile.S(r, 64) == actor {
			at, ok := wire.Find(r.Fields, 13)
			if ok {
				origin, found = at.XYZ, true
			}
		}
	}
	if !found {
		t.Fatal("mover checkpoint omitted its own position")
	}
	destination := origin
	destination[0] += 8000 // Below the source handler's 9900 limit; longer than this test's deadline.
	mover.write(ctx, bridge.Command{Key: 1, Verb: 1, Payload: []wire.Field{wire.At(11, true, destination[0], destination[1], destination[2])}}, wire.StageSent)
	mover.fact(ctx, func(r wire.Record) bool { return profile.U(r, 1) == 2 && profile.S(r, 64) == actor })
	dial := &javaDial{}
	cfg.DialContext = dial.dial
	observer, snapshot := openJava(t, ctx, cfg, "bridge-watch", "BridgeWatcher")
	check := func(snapshot bridge.Snapshot) {
		t.Helper()
		appeared, moving := -1, -1
		for i, r := range snapshot.Facts {
			if profile.S(r, 64) != actor {
				continue
			}
			switch profile.U(r, 1) {
			case 1:
				appeared = i
			case 2:
				at, ok := wire.Find(r.Fields, 14)
				if !ok || at.XYZ != destination {
					t.Fatalf("moving destination=%v, want %v", at.XYZ, destination)
				}
				moving = i
			}
		}
		if appeared < 0 || moving <= appeared {
			t.Fatalf("fresh actor description lost movement: appeared=%d moving=%d epoch=%d", appeared, moving, snapshot.GatewayEpoch)
		}
	}
	check(snapshot)
	for epoch := uint64(2); epoch <= 3; epoch++ {
		dial.lose(t)
		for observer.next(ctx).Kind != bridge.Detached {
		}
		recovered, err := observer.c.Resume(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if !recovered.Snapshot.Ready || recovered.Snapshot.GatewayEpoch != epoch {
			t.Fatalf("resume checkpoint ready=%v epoch=%d, want true/%d", recovered.Snapshot.Ready, recovered.Snapshot.GatewayEpoch, epoch)
		}
		check(recovered.Snapshot)
	}
}
