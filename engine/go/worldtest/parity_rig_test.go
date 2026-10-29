// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bufio"
	"context"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldbridge"
)

type parityItem struct {
	Object   int32 `json:"object"`
	Template int32 `json:"template"`
	Count    int64 `json:"count"`
	Slot     int32 `json:"slot"`
}
type paritySkill struct {
	ID    int32 `json:"id"`
	Level int32 `json:"level"`
}
type parityState struct {
	MP        float64       `json:"mp"`
	HP        float64       `json:"hp"`
	MaxHP     float64       `json:"max_hp"`
	XP        int64         `json:"xp"`
	SP        int32         `json:"sp"`
	Dead      bool          `json:"dead"`
	Point     [3]int32      `json:"point"`
	PAtk      int32         `json:"patk"`
	PDef      int32         `json:"pdef"`
	Inventory []parityItem  `json:"inventory"`
	Skills    []paritySkill `json:"skills"`
}

func (s parityState) count(template int32) int64 {
	var count int64
	for _, it := range s.Inventory {
		if it.Template == template {
			count += it.Count
		}
	}
	return count
}
func (s parityState) item(template int32) parityItem {
	for _, it := range s.Inventory {
		if it.Template == template {
			return it
		}
	}
	return parityItem{}
}
func (s parityState) skill(id int32) int32 {
	for _, sk := range s.Skills {
		if sk.ID == id {
			return sk.Level
		}
	}
	return 0
}

type parityDial struct {
	mu   sync.Mutex
	conn net.Conn
}

func (d *parityDial) dial(ctx context.Context, network, address string) (net.Conn, error) {
	c, err := (&net.Dialer{}).DialContext(ctx, network, address)
	if err == nil {
		d.mu.Lock()
		d.conn = c
		d.mu.Unlock()
	}
	return c, err
}

type parityRig struct {
	*rig
	engine    string
	index     int
	char      world.Character
	owner     world.Ref
	raw       bridge.Port
	dial      *parityDial
	initial   bridge.Snapshot
	detached  atomic.Bool
	historyMu sync.Mutex
	history   []wire.Record
	aux       map[int32]world.Entry
	closed    bool
}

func newParityRig(t *testing.T, engine string, index int, cat *catalog.Catalog) *parityRig {
	t.Helper()
	r := &parityRig{engine: engine, index: index, char: parityCharacter(index, 0), aux: make(map[int32]world.Entry)}
	source := allSources()
	name := parityNames()[index]
	if name == "g10" {
		source.Dialogue = false
	}
	var lost func(bridge.Update) bridge.Update
	switch name {
	case "g02-target":
		lost = lose(nil, true)
	case "g02-receipt":
		lost = lose(map[uint64]bool{24: true}, false)
	case "g08-before", "g08-after":
		lost = lose(map[uint64]bool{11: true}, true)
	}
	filter := func(u bridge.Update) bridge.Update {
		if u.Kind == bridge.Detached {
			r.detached.Store(true)
		}
		r.historyMu.Lock()
		if len(r.history)+len(u.Batch.Records) > 16384 {
			r.historyMu.Unlock()
			return bridge.Update{Kind: bridge.Closed, FailureKind: bridge.Capacity}
		}
		r.history = append(r.history, u.Batch.Records...)
		r.historyMu.Unlock()
		if lost != nil {
			return lost(u)
		}
		return u
	}
	if engine == "go" {
		var reserved []int32
		for a := 1; a <= 3; a++ {
			c := parityCharacter(index, a)
			reserved = append(reserved, c.ObjectID)
			for _, it := range c.Items {
				reserved = append(reserved, it.ObjectID)
			}
		}
		r.rig = newRigWithPolicy(t, cat, r.char, source, filter, parityPolicy(), world.WithReservedIDs(reserved))
		r.raw = r.port.Port
		r.owner = r.raw.(*worldbridge.Client).Owner()
	} else {
		secret, err := hex.DecodeString(os.Getenv("JAVA_BRIDGE_SECRET"))
		if err != nil || len(secret) != 32 || os.Getenv("JAVA_PARITY_ENDPOINT") == "" || os.Getenv("JAVA_PARITY_SECRET") == "" {
			t.Fatal("Java parity requires the isolated full-stack harness")
		}
		r.rig = &rig{t: t, c: gatewaytest.Config()}
		r.dial = &parityDial{}
		cfg := bridge.Config{Endpoint: os.Getenv("JAVA_BRIDGE_ENDPOINT"), DialContext: r.dial.dial}
		copy(cfg.Secret[:], secret)
		origin := time.Now()
		r.raw, r.initial, err = bridge.Open(t.Context(), cfg, bridge.Identity{Account: r.char.Account, Character: r.char.Name})
		if err != nil {
			t.Fatal(err)
		}
		r.owner.ID = r.char.ObjectID
		r.port = wrapPort(r.raw, filter)
		t.Cleanup(func() {
			if err := r.port.Close(); err != nil {
				t.Error(err)
			}
		})
		r.session, r.scope, r.state, err = backend.New(r.port, r.initial, backend.Config{Meaning: r.c, Origin: origin, Source: func(meaning.Event) meaning.Source { return source }, Policy: parityPolicy(), Handoff: func(h backend.Handoff) { r.mu.Lock(); r.handoffs = append(r.handoffs, h); r.mu.Unlock() }})
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			if err := r.session.Close(); err != nil {
				t.Error(err)
			}
		})
		r.g = gatewaytest.New(t, r.c, func(context.Context, string) (gateway.World, gateway.State, error) {
			r.mu.Lock()
			defer r.mu.Unlock()
			return paced{World: r.scope, hold: &r.hold}, r.state, nil
		}, origin)
		r.p = gatewaytest.Join(t, r.g, r.c, "one", wire.ModeAct, true)
	}
	// Auxiliary reward members leave and save before the primary port closes.
	t.Cleanup(func() {
		for id := range r.aux {
			r.closeAux(id)
		}
	})
	return r
}

func parityPolicy() meaning.Policy {
	return meaning.Policy{Classify: func(o meaning.Observed) meaning.Capabilities {
		return meaning.Capabilities{Talk: o.Role == "tutor" || o.Role == "provisioner" || o.Role == "folk", Strike: o.Role == "unknown" || o.Role == "creature", Cast: o.Role == "unknown" || o.Role == "creature", Pickup: o.Role == meaning.RoleDrop}
	}}
}

func (r *parityRig) sync() {
	r.t.Helper()
	if r.engine == "go" {
		synctest.Wait()
	} else {
		ctx, cancel := context.WithTimeout(r.t.Context(), time.Second)
		_, err := r.g.Status(ctx, "one")
		cancel()
		if err != nil && !r.closed && !r.detached.Load() {
			r.t.Fatal(err)
		}
	}
	r.p.Drain()
	if r.engine == "go" {
		synctest.Wait()
		r.p.Drain()
	}
	for _, entry := range r.aux {
		drainParityEvents(entry.Events)
	}
}
func drainParityEvents(events <-chan world.Event) {
	for {
		select {
		case _, ok := <-events:
			if !ok {
				return
			}
		default:
			return
		}
	}
}
func (r *parityRig) advance(d time.Duration) { time.Sleep(d); r.sync() }
func (r *parityRig) wait(f func() bool) {
	r.t.Helper()
	until := time.Now().Add(30 * time.Second)
	for !f() {
		if time.Now().After(until) {
			r.t.Fatal("parity condition exceeded 30 seconds")
		}
		r.advance(20 * time.Millisecond)
	}
}

// result waits for asynchronous runtime work before using the unchanged shared
// driver. A terminal mismatch fails immediately; no evidence window is extended.
func (r *parityRig) result(n uint64, stage wire.Stage) wire.Record {
	r.t.Helper()
	r.wait(func() bool {
		for _, f := range r.p.Records {
			if f.Type == wire.Result && gatewaytest.U(f, 1) == n && gatewaytest.U(f, 2) == uint64(stage) {
				return true
			}
		}
		for _, f := range r.p.Records {
			if f.Type == wire.Result && gatewaytest.U(f, 1) == n && gatewaytest.U(f, 2) != uint64(wire.StageSent) {
				r.t.Fatalf("attempt %d terminal mismatch: got %+v, want stage %v; raw commands=%+v", n, f, stage, r.port.commands())
			}
		}
		return false
	})
	return r.p.Result(n, stage)
}
func (r *parityRig) fact(kind uint64) wire.Record {
	r.t.Helper()
	r.wait(func() bool {
		for _, f := range r.p.Records {
			if f.Type == wire.Fact && gatewaytest.U(f, 1) == kind {
				return true
			}
		}
		return false
	})
	return r.p.Fact(kind)
}

func (r *parityRig) actor(template int32) world.Ref {
	r.t.Helper()
	if r.engine == "go" {
		scene, err := r.w.Capture(r.t.Context(), r.owner)
		if err != nil {
			r.t.Fatal(err)
		}
		var best world.Ref
		var distance int64 = 1 << 62
		for _, o := range scene.Objects {
			if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == template && !n.NPC.Dead {
				dx, dy := int64(n.NPC.Pos.X)-int64(r.char.X), int64(n.NPC.Pos.Y)-int64(r.char.Y)
				if d := dx*dx + dy*dy; d < distance {
					distance, best = d, o.Entity
				}
			}
		}
		if best == (world.Ref{}) {
			r.t.Fatalf("visible authored actor %d absent", template)
		}
		return best
	}
	r.historyMu.Lock()
	facts := append(slices.Clone(r.initial.Facts), r.history...)
	r.historyMu.Unlock()
	actors := make(map[int32]wire.Record)
	for _, f := range facts {
		id := int32(profile.S(f, 64))
		switch profile.U(f, 1) {
		case 1:
			if profile.S(f, 73) != 0 {
				actors[id] = f
			}
		case 5, 6:
			delete(actors, id)
		}
	}
	var best world.Ref
	var distance int64 = 1 << 62
	for id, f := range actors {
		if profile.S(f, 73) != int64(template) || profile.U(f, 70) == 0 {
			continue
		}
		at, ok := wire.Find(f.Fields, 13)
		if !ok {
			r.t.Fatal("actor omitted position")
		}
		dx, dy := int64(at.XYZ[0])-int64(r.char.X), int64(at.XYZ[1])-int64(r.char.Y)
		if d := dx*dx + dy*dy; d < distance {
			distance, best = d, world.Ref{ID: id}
		}
	}
	if best.ID == 0 {
		r.t.Fatalf("visible authored actor %d absent", template)
	}
	return best
}

func (r *parityRig) stateOf(id int32) parityState {
	r.t.Helper()
	if r.engine == "java" {
		var out parityState
		if err := json.Unmarshal([]byte(r.fixture("state", strconv.Itoa(int(id)))), &out); err != nil {
			r.t.Fatal(err)
		}
		if out.MaxHP <= 0 || out.HP < 0 || out.HP > out.MaxHP || out.XP < 0 || out.SP < 0 {
			r.t.Fatal("invalid Java fixture state")
		}
		return out
	}
	self, err := r.w.Snapshot(r.t.Context(), id)
	if err != nil {
		r.t.Fatal(err)
	}
	return paritySelf(self)
}
func paritySelf(self world.Self) parityState {
	c := self.Char
	out := parityState{MP: c.CurMP, HP: c.CurHP, MaxHP: c.MaxHP, XP: c.Exp, SP: c.SP, Dead: self.Dead, Point: [3]int32{c.X, c.Y, c.Z}, PAtk: self.PAtk, PDef: self.PDef}
	for _, it := range c.Items {
		out.Inventory = append(out.Inventory, parityItem{it.ObjectID, it.TemplateID, it.Count, it.Slot})
	}
	for _, sk := range c.Skills {
		out.Skills = append(out.Skills, paritySkill{sk.ID, sk.Level})
	}
	return out
}
func (r *parityRig) current() parityState { return r.stateOf(r.char.ObjectID) }

func (r *parityRig) fixture(operation string, args ...string) string {
	r.t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 7*time.Second)
	defer cancel()
	c, err := (&net.Dialer{}).DialContext(ctx, "tcp", os.Getenv("JAVA_PARITY_ENDPOINT"))
	if err != nil {
		r.t.Fatal(err)
	}
	defer c.Close()
	if err := c.SetDeadline(time.Now().Add(6 * time.Second)); err != nil {
		r.t.Fatal(err)
	}
	fields := append([]string{os.Getenv("JAVA_PARITY_SECRET"), strconv.Itoa(int(r.char.ObjectID)), operation}, args...)
	if _, err := fmt.Fprintln(c, strings.Join(fields, "\t")); err != nil {
		r.t.Fatal(err)
	}
	b, err := bufio.NewReader(io.LimitReader(c, 65537)).ReadString('\n')
	if err != nil || len(b) > 65536 || strings.HasPrefix(b, "error:") {
		r.t.Fatalf("Java fixture %s: %s/%v", operation, strings.TrimSpace(b), err)
	}
	return strings.TrimSpace(b)
}

func (r *parityRig) use(template int32) {
	r.t.Helper()
	item := r.current().item(template)
	if item.Object == 0 {
		r.t.Fatalf("item %d absent", template)
	}
	if r.engine == "java" {
		if r.fixture("use", strconv.Itoa(int(item.Object))) != "ok" {
			r.t.Fatal("use rejected")
		}
		return
	}
	scene, err := r.w.Capture(r.t.Context(), r.owner)
	if err != nil {
		r.t.Fatal(err)
	}
	for _, row := range scene.Inventory {
		if row.Item.ObjectID == item.Object {
			c, err := r.w.Execute(r.t.Context(), world.Request{Owner: r.owner, Verb: world.VerbUseItem, Target: row.Entity})
			if err != nil || c.Outcome.Stage == world.StageRefused {
				r.t.Fatalf("use %d: %+v/%v", template, c, err)
			}
			return
		}
	}
	r.t.Fatal("carried item reference absent")
}

func (r *parityRig) harm(source, target world.Ref, damage float64) {
	r.t.Helper()
	if r.engine == "java" {
		if r.fixture("damage", strconv.Itoa(int(target.ID)), strconv.FormatFloat(damage, 'g', -1, 64), strconv.Itoa(int(source.ID))) != "ok" {
			r.t.Fatal("harm rejected")
		}
	} else if err := r.w.Harm(r.t.Context(), source, target, damage); err != nil {
		r.t.Fatal(err)
	}
	r.sync()
}

func (r *parityRig) openAux(actor int) int32 {
	r.t.Helper()
	c := parityCharacter(r.index, actor)
	if r.engine == "java" {
		if r.fixture("openaux", strconv.Itoa(int(c.ObjectID))) != "ok" {
			r.t.Fatal("auxiliary open")
		}
		r.aux[c.ObjectID] = world.Entry{}
	} else {
		if err := r.store.PutCharacter(r.t.Context(), c); err != nil {
			r.t.Fatal(err)
		}
		e, err := r.w.Enter(r.t.Context(), c)
		if err != nil {
			r.t.Fatal(err)
		}
		r.aux[c.ObjectID] = e
	}
	return c.ObjectID
}
func (r *parityRig) closeAux(id int32) {
	r.t.Helper()
	if _, ok := r.aux[id]; !ok {
		return
	}
	if r.engine == "java" {
		if r.fixture("closeaux", strconv.Itoa(int(id))) != "ok" {
			r.t.Error("auxiliary close")
		}
	} else {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := r.w.Leave(ctx, r.aux[id].Self.Entity); err != nil {
			r.t.Error(err)
		}
	}
	delete(r.aux, id)
}
func (r *parityRig) party(ids []int32) {
	r.t.Helper()
	if r.engine == "java" {
		args := []string{"3"}
		for _, id := range ids {
			args = append(args, strconv.Itoa(int(id)))
		}
		if r.fixture("party", args...) != "ok" {
			r.t.Fatal("party rejected")
		}
		return
	}
	var refs []world.Ref
	for _, id := range ids {
		refs = append(refs, r.aux[id].Self.Entity)
	}
	if _, err := r.w.SetParty(r.t.Context(), 0, refs, world.LootRoundRobin); err != nil {
		r.t.Fatal(err)
	}
}
func (r *parityRig) actorRef(id int32) world.Ref {
	if id == r.char.ObjectID {
		return r.owner
	}
	if r.engine == "go" {
		return r.aux[id].Self.Entity
	}
	return world.Ref{ID: id}
}

func parityDB(t *testing.T, sql string) string {
	t.Helper()
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "docker", "exec", "--env", "PGOPTIONS=-c statement_timeout=3000 -c lock_timeout=2000", os.Getenv("JAVA_BRIDGE_DB_CONTAINER"), "psql", "-X", "-U", "postgres", "-d", "bridge", "-v", "ON_ERROR_STOP=1", "-Atc", sql)
	cmd.WaitDelay = time.Second
	b, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("parity database assertion: %v: %s", err, b)
	}
	return strings.TrimSpace(string(b))
}

func (r *parityRig) save() parityState {
	r.t.Helper()
	for id := range r.aux {
		r.closeAux(id)
	}
	if !r.closed {
		if err := r.session.Close(); err != nil {
			r.t.Fatal(err)
		}
		r.closed = true
	}
	if r.engine == "go" {
		chars, err := r.store.Characters(r.t.Context(), r.char.Account)
		if err != nil || len(chars) != 1 {
			r.t.Fatalf("saved character: %v", err)
		}
		return paritySelf(world.Self{Char: chars[0], Dead: chars[0].CurHP == 0})
	}
	return r.savedJava(r.char.ObjectID)
}

func (r *parityRig) savedJava(id int32) parityState {
	r.t.Helper()
	sql := fmt.Sprintf(`SELECT json_build_object('hp',current_hp,'max_hp',max_hp,'xp',exp,'sp',sp,'dead',current_hp=0,'point',json_build_array(x,y,z),'inventory',(SELECT coalesce(json_agg(json_build_object('object',id,'template',item_template_id,'count',count,'slot',CASE WHEN location='PAPERDOLL' THEN location_slot ELSE -1 END)),'[]') FROM world.item WHERE owner_player_id=%d),'skills',(SELECT coalesce(json_agg(json_build_object('id',skill_id,'level',skill_level)),'[]') FROM world.player_skill WHERE player_id=%d AND class_index=0)) FROM world.player WHERE id=%d AND NOT is_online`, id, id, id)
	var saved parityState
	if err := json.Unmarshal([]byte(parityDB(r.t, sql)), &saved); err != nil {
		r.t.Fatal(err)
	}
	return saved
}
