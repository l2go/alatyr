// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"reflect"
	"slices"
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
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

func TestAdvertisedPolicyMatchesEnforcement(t *testing.T) {
	for _, sessions := range []int{1, 64} {
		c := limits(sessions)
		g := gatewayPolicy(c)
		// Compare independent enforcement surfaces to the numbered wire contract.
		actual := map[int]uint64{
			1: uint64(c.Wire.FrameBytes), 2: uint64(c.Wire.StringBytes), 3: uint64(c.Wire.ListItems), 4: uint64(c.Wire.Depth), 5: uint64(c.Objects), 6: uint64(c.Attempts.Results), 7: uint64(c.Windows),
			8: uint64(g.OutBytes), 10: uint64(g.Sessions), 11: uint64(g.Connections), 12: uint64(g.IntentBurst), 13: uint64(g.IntentRate), 14: uint64(g.Attempts.Pending), 15: uint64(c.Wire.Records), 16: uint64(c.Wire.Fields),
			17: uint64(g.Attempts.QueueMS), 18: uint64(g.Attempts.EvidenceMS), 19: uint64(g.TargetWait / time.Millisecond), 20: uint64(g.Hello / time.Millisecond), 21: uint64(g.Write / time.Millisecond), 22: uint64(g.Checkpoint / time.Millisecond), 23: uint64(g.Ping / time.Millisecond), 24: uint64(g.Pong / time.Millisecond), 25: uint64(g.Batch / time.Millisecond), 27: uint64(c.WindowBytes), 28: uint64(g.ServiceBurst), 29: uint64(g.ServiceRate),
		}
		for field, value := range actual {
			if c.Advertised[field-1] != value {
				t.Errorf("field %d: advertised %d, enforced %d", field, c.Advertised[field-1], value)
			}
		}
		for _, field := range []int{9, 31, 32, 33, 34} {
			if c.Advertised[field-1] != 0 {
				t.Errorf("disabled diagnostics field %d is nonzero", field)
			}
		}
		if g.FrameBytes != c.Wire.FrameBytes || g.Sessions != sessions || !reflect.DeepEqual(g.Limits, c.LimitsFields()) || !reflect.DeepEqual(g.Versions, []uint64{meaning.WireVersion}) || !reflect.DeepEqual(g.Meanings, []string{meaning.Meaning}) {
			t.Fatal("host policy drift")
		}
		// A changed advertised deadline must change the runtime deadline too.
		c.Advertised[19] = 3210
		if gatewayPolicy(c).Hello != 3210*time.Millisecond {
			t.Fatal("duplicate Hello source")
		}
	}
}

func TestHiddenSkillReplacementPersists(t *testing.T) {
	dsn := pgtest.Database(t)
	store, err := pgstore.Open(t.Context(), dsn)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	if err := migrate.Apply(t.Context(), dsn); err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	offers := cat.AvailableSkills(0, 2, func(int32) int32 { return 0 })
	if len(offers) == 0 {
		t.Fatal("missing authored lesson")
	}
	offer := offers[0]
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 257883, Z: -3104, Skills: []world.Skill{{ID: offer.SkillID, Level: 30000}, {ID: 999999, Level: 1}}}
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
	live := chars.load(c)
	if len(live.Skills) != 0 {
		t.Fatal("unknown levels entered live state")
	}
	entry, err := w.Enter(ctx, live)
	if err != nil {
		t.Fatal(err)
	}
	scene, err := w.Capture(ctx, entry.Self.Entity)
	if err != nil {
		t.Fatal(err)
	}
	var trainer int32
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && cat.CanTeach(n.NPC.Template.ID, 0) {
			trainer = o.Entity.ID
		}
	}
	if trainer == 0 {
		t.Fatal("missing teacher")
	}
	for range 2 {
		if err := w.Action(ctx, 1, trainer); err != nil {
			t.Fatal(err)
		}
	}
	if err := w.Bypass(ctx, 1, fmt.Sprintf("npc_%d_SkillList", trainer)); err != nil {
		t.Fatal(err)
	}
	if err := w.Learn(ctx, 1, offer.SkillID, offer.Level); err != nil {
		t.Fatal(err)
	}
	cost, ok := cat.SkillCost(0, offer.SkillID, offer.Level, 2)
	if !ok {
		t.Fatal("missing cost")
	}
	check := func() {
		t.Helper()
		got, err := store.Character(ctx, "EXAMPLE", "walker")
		if err != nil || got.SP != 20-cost || len(got.Skills) != 2 || !slices.Contains(got.Skills, world.Skill{ID: offer.SkillID, Level: offer.Level}) || !slices.Contains(got.Skills, world.Skill{ID: 999999, Level: 1}) {
			t.Fatalf("hidden skill merge: %+v/%v", got, err)
		}
	}
	check()
	if err := w.Leave(ctx, entry.Self.Entity); err != nil {
		t.Fatal(err)
	}
	check()
}

func testAddress(t *testing.T) string {
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

func TestParticipantFatalWorldExitPersists(t *testing.T) {
	dsn := pgtest.Database(t)
	store, err := pgstore.Open(t.Context(), dsn)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	if err := migrate.Apply(t.Context(), dsn); err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", Level: 2, SP: 20, CurHP: 50, CurMP: 20, CurCP: 10, X: -71057, Y: 258243, Z: -3104, Items: []world.Item{{ObjectID: 2, TemplateID: 57, Count: 100, Slot: -1}, {ObjectID: 3, TemplateID: 999999, Count: 7, Slot: -1}}}
	if err := store.PutCharacter(t.Context(), c); err != nil {
		t.Fatal(err)
	}
	cfg := configTest()
	cfg.HTTPAddress = testAddress(t)
	cfg.StreamAddress = testAddress(t)
	cfg.ShutdownSeconds = 3
	spawned := 0
	for _, sp := range cat.Spawns("WORLD") {
		spawned += int(sp.Count)
	}
	captured := make(chan *world.World, 1)
	entered := make(chan world.Ref, 1)
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()
	done := make(chan error, 1)
	joined := false
	defer func() {
		cancel()
		if !joined {
			<-done
		}
	}()
	go func() {
		done <- serve(ctx, cfg, cat, store, world.WithIDRange(1<<28, (1<<28)+int32(spawned)-1), func(w *world.World) { captured <- w }, world.WithCommits(func(c world.Commit) {
			for _, g := range c.Groups {
				for _, e := range g.Effects {
					if e, ok := e.(world.ExistenceChanged); ok && e.After && e.Entity.ID == 1 {
						entered <- e.Entity
					}
				}
			}
		}))
	}()
	w := <-captured
	// A successful Capture proves the loop is running; readiness also probes it.
	if _, err := w.Capture(ctx, world.Ref{}); !errors.Is(err, world.ErrNotPresent) {
		t.Fatal(err)
	}
	client := &http.Client{Timeout: time.Second}
	tick := time.NewTicker(10 * time.Millisecond)
	defer tick.Stop()
	for {
		response, err := client.Get("http://" + cfg.HTTPAddress + "/readyz")
		if err == nil {
			response.Body.Close()
			if response.StatusCode == 200 {
				break
			}
		}
		select {
		case err := <-done:
			joined = true
			t.Fatalf("startup: %v", err)
		case <-ctx.Done():
			t.Fatal(ctx.Err())
		case <-tick.C:
		}
	}
	conn, err := net.DialTimeout("tcp", cfg.StreamAddress, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	if _, err := conn.Write([]byte(strings.Repeat("a", 64) + "\n")); err != nil {
		t.Fatal(err)
	}
	// Complete participant negotiation and its opening checkpoint, then retain
	// the live port throughout the autonomous fatal exit.
	codec, err := meaning.NewCodec(limits(1))
	if err != nil {
		t.Fatal(err)
	}
	stream := &gateway.Stream{Conn: conn, Limit: limits(1).Wire.FrameBytes}
	hello, err := codec.Encode([]wire.Record{{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, meaning.WireVersion), wire.U(2, true, meaning.WireVersion), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}}})
	if err != nil {
		t.Fatal(err)
	}
	if err := stream.Write(hello, time.Now().Add(time.Second)); err != nil {
		t.Fatal(err)
	}
	ready := false
	for !ready {
		data, err := stream.Read(time.Now().Add(5 * time.Second))
		if err != nil {
			t.Fatal(err)
		}
		records, err := codec.Decode(data)
		if err != nil {
			t.Fatal(err)
		}
		for _, r := range records {
			if r.Type == wire.Checkpoint && testUint(r, 1) == uint64(wire.CheckpointEnd) && testUint(r, 3) == 1 {
				ready = true
			}
		}
	}
	var owner world.Ref
	select {
	case owner = <-entered:
	case <-ctx.Done():
		t.Fatal(ctx.Err())
	}
	if err := w.Teleport(ctx, 1, world.Point{X: c.X + 1, Y: c.Y, Z: c.Z}, false); err != nil {
		t.Fatal(err)
	}
	if err := w.Appear(ctx, 1); err != nil {
		t.Fatal(err)
	}
	scene, err := w.Capture(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	var merchant world.Ref
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == 30002 {
			merchant = o.Entity
		}
	}
	if merchant.ID == 0 {
		t.Fatal("missing merchant")
	}
	if _, err := w.Execute(ctx, world.Request{Owner: owner, Verb: world.VerbTalk, Target: merchant}); err != nil {
		t.Fatal(err)
	}
	scene, err = w.Capture(ctx, owner)
	if err != nil || len(scene.Dialogue.Affordances) == 0 {
		t.Fatalf("dialogue: %+v/%v", scene.Dialogue, err)
	}
	if _, err := w.Execute(ctx, world.Request{Owner: owner, Verb: world.VerbChoose, Target: merchant, AffordanceID: scene.Dialogue.Affordances[0].ID}); err != nil {
		t.Fatal(err)
	}
	scene, err = w.Capture(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	var buy world.Request
	for _, a := range scene.Dialogue.Affordances {
		if terms, ok := a.Trade(); ok && terms.Item == 1060 {
			buy = world.Request{Owner: owner, Verb: a.Verb, Target: merchant, AffordanceID: a.ID, Quantity: 1}
		}
	}
	if buy.AffordanceID == 0 {
		t.Fatal("missing buy offer")
	}
	// The would-be item ID is outside the durably reserved range. No debit may
	// commit, and the host must persist the changed position on this exit too.
	if _, err := w.Execute(ctx, buy); !errors.Is(err, world.ErrExhausted) {
		t.Fatalf("range exhaustion: %v", err)
	}
	select {
	case err := <-done:
		joined = true
		if !errors.Is(err, world.ErrExhausted) {
			t.Fatalf("fatal error lost: %v", err)
		}
	case <-ctx.Done():
		t.Fatal("fatal exit did not join")
	}
	probe := &runtime{cfg: cfg, world: w, store: store}
	probe.ready.Store(true)
	response := httptest.NewRecorder()
	probe.handler(cfg.HTTPAddress).ServeHTTP(response, httptest.NewRequest(http.MethodGet, "http://"+cfg.HTTPAddress+"/readyz", nil))
	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("stopped world ready: %d", response.Code)
	}
	got, err := store.Character(ctx, "EXAMPLE", "walker")
	if err != nil || got.X != c.X+1 || len(got.Items) != 2 || !slices.ContainsFunc(got.Items, func(it world.Item) bool { return it.TemplateID == 57 && it.Count == 100 }) || !slices.ContainsFunc(got.Items, func(it world.Item) bool { return it.TemplateID == 999999 && it.Count == 7 }) {
		t.Fatalf("fatal departure lost state: %+v/%v", got, err)
	}
}
