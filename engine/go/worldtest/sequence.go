// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"math"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/backend"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
)

// SequenceHost supplies an already running composition inside a synctest bubble.
// Callbacks run serially on the test goroutine, after the actors are quiescent.
// Resume must replace the factory's scope only on success. Close joins all ports;
// Stop joins World.Run. The caller also registers cleanup for every resource.
type SequenceHost struct {
	World    *world.World
	Gateway  *gateway.Gateway
	Config   meaning.Config
	Peer     *gatewaytest.Peer
	Owner    func() world.Ref
	Resume   func() error
	Saved    func() world.Character
	Close    func() error
	Stop     func()
	Scopes   func() []*backend.World
	Port     *SequencePort
	Handoffs func() []backend.Handoff
	Commits  func() []world.Commit
}

// SequenceSeeds returns independent byte programs covering each operation and
// successful proposal chains as well as invalid, foreign and retired handles.
func SequenceSeeds() [][]byte {
	seeds := scenarioSeeds()
	for verb := byte(verbWalk); verb <= verbUseItem; verb++ {
		for variant := byte(0); variant < 4; variant++ {
			header := byte(0)
			if verb == verbBuy || verb == verbSell {
				header = 1
			}
			if verb == verbRise {
				header = 3
			}
			if verb == verbTake {
				header = 2
			}
			seed := []byte{header}
			switch verb {
			case verbTake:
				seed = append(seed, opHarmTarget, 0, 0)
			case verbChoose, verbBuy, verbSell, verbLearn:
				seed = append(seed, opTalk, 0, 0)
				if verb == verbBuy || verb == verbLearn {
					seed = append(seed, opChoose, 0, 0)
				}
				if verb == verbSell {
					seed = append(seed, opChoose, 1, 0)
				}
			}
			seed = append(seed, verb-1, 0, variant, opAdvance, 2, 0)
			seeds = append(seeds, seed)
		}
	}
	seeds = append(seeds, []byte{1, opTalk, 0, 0, opChoose, 0, 0, opAdvance, 4, 0, opAdvance, 4, 0, opAdvance, 0, 0, opBuy, 0, 0})
	for mode := byte(faultReceiptsOnly); mode < faultCount; mode++ {
		seeds = append(seeds, []byte{0, opFault, mode, 0, opTalk, 0, 0, opAdvance, 2, 0, opRelease, 0, 0})
	}
	return seeds
}

func scenarioSeeds() [][]byte {
	return [][]byte{
		{0},
		{0, opFocusEngage, 0, 0}, // Focus -> Engage, in one participant frame (A0).
		{0, opTalk, 0, 0, opChoose, 0, 0, opLearn, 0, 0, opAdvance, 3, 0},                // tutor/learning
		{1, opTalk, 0, 0, opChoose, 0, 0, opBuy, 0, 0, opReplay, 0, 0, opAdvance, 3, 0},  // buy/replay
		{1, opTalk, 0, 0, opChoose, 1, 0, opSell, 0, 0, opReplay, 0, 0, opAdvance, 3, 0}, // sell/replay
		{0, opUseItem, 0, 0, opReplay, 0, 0, opUseItem, 1, 0, opUseItem, 1, 0, opAdvance, 3, 0},
		{0, opTalk, 0, 0, opResume, 0, 0, opTalk, 0, 2, opTalk, 0, 0},
		{0, opTalk, 0, 0, opExpiredResume, 0, 0},
		{0, opWatch, 0, 0, opAct, 0, 0, opTalk, 0, 0, opControl, 0, 0, opFocusEngage, 0, 0, opCloseSocket, 0, 0},
		{0, opTalk, 0, 0, opTeleport, 1, 0, opTalk, 0, 2, opHarmSelf, 0, 0, opRise, 0, 0},
		{2, opHarmTarget, 0, 0, opTake, 0, 0, opReplay, 0, 0, opRespawn, 0, 0},
		{0, opTalk, 0, 0, opStopWorld, 0, 0},
		{3, opRise, 0, 0, opAdvance, 3, 0},
		{2, opHarmTarget, 0, 1}, // Two world players observe target clearing and NPC decay.
	}
}

// SequenceCharacter supplies a finite, authored inventory and a deterministic
// starting location. The first program byte chooses tutor, merchant, hunt or death.
func SequenceCharacter(data []byte) world.Character {
	c := world.Character{ObjectID: 1, Account: "example", Name: "Walker", ClassID: 0, Level: 2, Exp: 68, SP: 100,
		CurHP: 100, CurMP: 50, CurCP: 10, X: -71057, Y: 257883, Z: -3104,
		Items: []world.Item{{ObjectID: 10, TemplateID: 57, Count: 1000, Slot: -1}, {ObjectID: 11, TemplateID: 4, Count: 1, Slot: catalog.SlotRHand}, {ObjectID: 12, TemplateID: 1060, Count: 3, Slot: -1}}, Skills: []world.Skill{{ID: 3, Level: 1}}}
	if len(data) > 0 {
		switch data[0] % 4 {
		case 1:
			c.Y = 258243
		case 2:
			c.X, c.Y = -70357, 256863
		case 3:
			c.CurHP = 0
		}
	}
	return c
}

type sequenceAttempt struct {
	record  wire.Record
	epoch   uint64
	at      int64
	replays int
	chosen  meaning.Ref
}

type sequenceScope struct {
	firstHandoff int
	lastHandoff  int
	verified     bool
	monitor      *gatewaytest.Peer
	attempts     map[uint64]*sequenceAttempt
}

type sequencer struct {
	t            *testing.T
	h            SequenceHost
	origin       time.Time
	peers        []*gatewaytest.Peer
	read         map[*gatewaytest.Peer]int
	closed       map[*gatewaytest.Peer]bool
	scope        *sequenceScope
	scopes       []*sequenceScope
	retired      []meaning.Ref
	actor, other *gatewaytest.Peer
	n            uint64
	last         wire.Record
	stopped      bool
	departed     bool
	ended        bool
}

// ExerciseSequence executes at most 64 three-byte instructions. It checks every
// quiescent step and the final drain, and returns canonical ordered bytes for
// every peer in registration order. Only opaque Welcome session IDs are normalized.
// The caller runs each input twice in separate bubbles and compares the bytes.
func ExerciseSequence(t *testing.T, data []byte, h SequenceHost) []byte {
	t.Helper()
	s := &sequencer{t: t, h: h, origin: time.Now(), actor: h.Peer, peers: []*gatewaytest.Peer{h.Peer}, n: 1, read: make(map[*gatewaytest.Peer]int), closed: make(map[*gatewaytest.Peer]bool)}
	s.openScope()
	for i := 1; i+2 < len(data) && i < 193; i += 3 {
		op, a, v := data[i]%opCount, data[i+1], data[i+2]%4
		t.Logf("step %d: op=%d arg=%d variant=%d", (i-1)/3, op, a, v)
		s.settle()
		switch {
		case op <= opUseItem:
			s.send(uint64(op)+1, a, v)
		case op == opAdvance:
			delays := []time.Duration{time.Millisecond, 4999 * time.Millisecond, 5 * time.Second, 5001 * time.Millisecond, 30 * time.Second}
			s.advance(delays[int(a)%len(delays)])
		case op == opFocusEngage:
			r1 := s.intent(verbFocus, a, v)
			r2 := s.intent(verbEngage, a, v)
			s.actor.Send(r1, r2)
		case op == opReplay:
			if s.last.Type != 0 {
				s.scope.attempts[gatewaytest.U(s.last, 1)].replays++
				before := len(s.h.Handoffs())
				s.actor.Send(s.last)
				s.settle()
				if len(s.h.Handoffs()) != before {
					t.Fatal("replayed Intent executed again")
				}
			}
		case op == opWatch: // A watcher shares this game, but cannot gain control.
			if s.other == nil {
				s.other = s.join(wire.ModeObserve, false)
				s.probe(s.other, wire.ReasonWatch)
			}
		case op == opAct:
			if s.other == nil {
				s.other = s.join(wire.ModeAct, false)
				s.probe(s.other, wire.ReasonStaleControl)
			}
		case op == opControl:
			if s.other != nil {
				s.other.Send(wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlTake))}})
				s.settle()
				if s.other.Control != 0 {
					s.actor, s.other = s.other, s.actor
				} else {
					s.close(s.other)
					s.other = nil
				}
			}
		case op == opResume || op == opExpiredResume:
			if !s.resume(op == opExpiredResume) {
				s.departed = true
				s.stopped = true
			}
		case op == opCloseSocket:
			// Keep the monitor alive while closing the controlling socket mid-attempt.
			s.send(verbCast, a, 0)
			s.settle()
			s.close(s.actor)
			s.settle()
			if s.other != nil {
				s.close(s.other)
				s.other = nil
				s.settle()
			}
			s.actor = s.join(wire.ModeAct, true)
		case op == opTeleport:
			s.retired = append(s.retired, s.reference(verbTalk, 0, 0))
			points := []world.Point{{X: -71057, Y: 257883, Z: -3104}, {X: -71057, Y: 258243, Z: -3104}, {X: -70357, Y: 256863, Z: -3104}}
			if err := h.World.Teleport(t.Context(), h.Owner().ID, points[int(a)%len(points)], false); err != nil {
				t.Fatal(err)
			}
		case op == opHarmSelf:
			if err := h.World.Harm(t.Context(), world.Ref{}, h.Owner(), 1e6); err != nil {
				t.Fatal(err)
			}
		case op == opHarmTarget:
			scene := s.scene()
			for _, o := range scene.Objects {
				if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.ID == 18003 {
					if v == 1 {
						s.observeDecay(n.NPC)
					} else if err := h.World.Harm(t.Context(), h.Owner(), o.Entity, 1e6); err != nil {
						t.Fatal(err)
					}
					break
				}
			}
		case op == opRespawn:
			s.advance(31 * time.Second)
		case op == opStopWorld:
			s.stopped = true
		case op == opFault:
			s.h.Port.Fault(a % faultCount)
		case op == opSnapshot:
			s.h.Port.Fault(faultSnapshot)
		case op == opHold:
			s.h.Port.Fault(faultHold)
		case op == opRelease:
			s.h.Port.Fault(faultLive)
		}
		s.settle()
		s.check(false)
		if s.stopped {
			break
		}
	}
	// Settle all original queue/evidence deadlines before normal departure.
	h.Port.Fault(faultLive)
	s.settle()
	if !s.stopped {
		s.advance(6 * time.Second)
		s.settle()
		s.check(true)
	}
	var before world.Character
	if !s.departed {
		before = s.scene().Self.Char
	}
	// The world saves rounded vitals and stamps LastAccess on departure.
	before.CurHP = math.Round(before.CurHP)
	before.CurMP = math.Round(before.CurMP)
	before.CurCP = math.Round(before.CurCP)
	if s.stopped && !s.departed {
		h.Stop()
		s.settle()
		final, err := h.World.Final(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		if len(final) != 1 {
			t.Fatalf("final checkpoints: got %d, want 1", len(final))
		}
		if err := checkSaved(before, final[0].Character); err != nil {
			t.Fatal(err)
		}
	} else if !s.departed {
		if err := h.Close(); err != nil && !(s.stopped && errors.Is(err, world.ErrStopped)) {
			t.Fatal(err)
		}
		s.settle()
		if err := checkSaved(before, h.Saved()); err != nil {
			t.Fatal(err)
		}
		if _, err := h.World.Capture(t.Context(), h.Owner()); !errors.Is(err, world.ErrNotPresent) {
			t.Fatalf("departure left a live character: %v", err)
		}
	}
	s.ended = true
	if err := h.Gateway.Close(); err != nil && !(s.stopped && errors.Is(err, world.ErrStopped)) {
		t.Fatal(err)
	}
	s.settle()
	s.check(true)
	if err := h.Close(); err != nil && !(s.stopped && errors.Is(err, world.ErrStopped)) {
		t.Fatal(err)
	}
	for _, p := range s.peers {
		s.close(p)
	}
	h.Stop()
	synctest.Wait()
	if err := checkRetention(h.Gateway, h.Scopes(), h.Config); err != nil {
		t.Fatal(err)
	}
	codec, err := meaning.NewCodec(h.Config)
	if err != nil {
		t.Fatal(err)
	}
	var transcript []byte
	for _, p := range s.peers {
		var records []wire.Record
		for _, r := range p.Records {
			if r.Type == wire.Welcome {
				r.Fields = slices.Clone(r.Fields)
				for i := range r.Fields {
					if r.Fields[i].Number == 4 {
						r.Fields[i] = wire.U(4, true, 1)
					}
				}
			}
			records = append(records, r)
		}
		// Encode separately to preserve every record without frame-size restrictions.
		var peerBytes []byte
		for _, r := range records {
			b, err := codec.Encode([]wire.Record{r})
			if err != nil {
				t.Fatal(err)
			}
			peerBytes = binary.LittleEndian.AppendUint32(peerBytes, uint32(len(b)))
			peerBytes = append(peerBytes, b...)
		}
		transcript = binary.LittleEndian.AppendUint32(transcript, uint32(len(peerBytes)))
		transcript = append(transcript, peerBytes...)
	}
	return transcript
}

func (s *sequencer) settle() {
	s.h.Port.Flush()
	synctest.Wait()
	for _, p := range s.peers {
		p.Drain()
	}
	synctest.Wait()
	for _, p := range s.peers {
		p.Drain()
	}
	for _, p := range s.peers {
		rs := p.Records[s.read[p]:]
		for _, r := range rs {
			if r.Type == wire.Bye {
				s.closed[p] = true
			}
		}
		if !s.closed[p] {
			for _, r := range rs {
				if r.Type == wire.Ping {
					p.Send(wire.Record{Type: wire.Pong, Fields: []wire.Field{wire.U(1, true, gatewaytest.U(r, 1))}})
				}
			}
		}
		s.read[p] = len(p.Records)
	}
	synctest.Wait()
}
func (s *sequencer) advance(d time.Duration) {
	for d > 0 {
		step := min(d, time.Second)
		time.Sleep(step)
		s.settle()
		d -= step
	}
}
func (s *sequencer) close(p *gatewaytest.Peer) { s.closed[p] = true; p.Close() }
func (s *sequencer) join(mode wire.Mode, control bool) *gatewaytest.Peer {
	p := gatewaytest.Join(s.t, s.h.Gateway, s.h.Config, "one", mode, control)
	s.peers = append(s.peers, p)
	s.settle()
	return p
}
func (s *sequencer) openScope() {
	monitor := s.join(wire.ModeObserve, false)
	s.scope = &sequenceScope{monitor: monitor, attempts: make(map[uint64]*sequenceAttempt), firstHandoff: len(s.h.Handoffs()), lastHandoff: -1}
	s.scopes = append(s.scopes, s.scope)
}
func (s *sequencer) scene() world.Scene {
	scene, err := s.h.World.Capture(s.t.Context(), s.h.Owner())
	if err != nil {
		s.t.Fatal(err)
	}
	return scene
}
func (s *sequencer) resume(expired bool) bool {
	s.retired = append(s.retired, s.reference(verbTalk, 0, 0))
	var savedWant world.Character
	if expired {
		// A dead character has no regeneration or moving intention. This freezes
		// its durable state through retention so expiry can check the exact save.
		if err := s.h.World.Harm(s.t.Context(), world.Ref{}, s.h.Owner(), 1e6); err != nil {
			s.t.Fatal(err)
		}
		s.settle()
		savedWant = s.scene().Self.Char
		savedWant.CurHP = math.Round(savedWant.CurHP)
		savedWant.CurMP = math.Round(savedWant.CurMP)
		savedWant.CurCP = math.Round(savedWant.CurCP)
	}
	// Subscribe replaces the real observation stream and makes the adapter detach.
	if _, _, err := s.h.World.Subscribe(s.t.Context(), s.h.Owner()); err != nil {
		s.t.Fatal(err)
	}
	s.settle()
	s.check(true)
	for _, p := range s.peers {
		s.close(p)
	}
	if expired {
		time.Sleep(30 * time.Second)
		s.settle()
	}
	err := s.h.Resume()
	if expired {
		if err == nil {
			s.t.Fatal("expired Resume succeeded")
		}
		if err := checkSaved(savedWant, s.h.Saved()); err != nil {
			s.t.Fatal(err)
		}
		return false
	}
	if err != nil {
		s.t.Fatal(err)
	}
	s.replaceScope()
	return true
}
func (s *sequencer) replaceScope() {
	s.scope.lastHandoff = len(s.h.Handoffs())
	for _, p := range s.peers {
		s.close(p)
	}
	s.actor = s.join(wire.ModeAct, true)
	s.other = nil
	s.n = 1
	s.last = wire.Record{}
	s.openScope()
}
func (s *sequencer) checkScope(scope *sequenceScope, final bool) {
	if scope.verified {
		return
	}
	handoffs := s.h.Handoffs()
	end := scope.lastHandoff
	if end < 0 {
		end = len(handoffs)
	}
	trace, writes := s.h.Port.audit()
	if err := checkRawEvidence(scope.monitor.Records, scope.attempts, handoffs[scope.firstHandoff:end], trace, writes, s.h.Config.Attempts.EvidenceMS); err != nil {
		s.t.Fatal(err)
	}

	if err := checkTranscript(scope.monitor.Records, scope.attempts, final, s.h.Config.Attempts.EvidenceMS); err != nil {
		s.t.Fatal(err)
	}
	if scope != s.scope && scope.lastHandoff >= 0 {
		scope.verified = true
	}
}
func (s *sequencer) probe(p *gatewaytest.Peer, reason wire.Reason) {
	before := s.scene()
	_, writes := s.h.Port.audit()
	p.Send(wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, s.n), wire.U(2, true, verbUseItem), wire.U(3, true, p.Control), wire.S(21, true, 12)}})
	s.settle()
	result := p.Result(s.n, wire.StageRefused)
	if gatewaytest.U(result, 3) != uint64(reason) {
		s.t.Fatalf("permission refusal: got %d, want %d", gatewaytest.U(result, 3), reason)
	}
	_, after := s.h.Port.audit()
	if len(writes) != len(after) || !reflect.DeepEqual(before, s.scene()) {
		s.t.Fatal("unauthorized request mutated the world")
	}
}

func (s *sequencer) send(verb uint64, a, v byte) { s.actor.Send(s.intent(verb, a, v)) }
func (s *sequencer) intent(verb uint64, a, v byte) wire.Record {
	n := s.n
	s.n++
	fields := []wire.Field{wire.U(1, true, n), wire.U(2, true, verb), wire.U(3, true, s.actor.Control)}
	ref := s.reference(verb, a, v)
	switch verb {
	case verbWalk:
		x, y, z := int32(-71047+int32(a)), int32(257883), int32(-3104)
		if v == 3 {
			x, y, z = math.MaxInt32, math.MinInt32, math.MaxInt32
		}
		fields = append(fields, wire.At(11, true, x, y, z))
	case verbAim, verbStrike, verbTalk, verbFocus, verbEngage, verbCast:
		fields = append(fields, wire.Obj(10, true, []wire.Field{wire.S(1, true, int64(ref.ID)), wire.U(2, true, ref.Gen)}))
		if verb == verbCast {
			id, level := int64(3), int64(1)
			if v != 0 {
				id = int64(math.MaxInt32)
				level = -1
			}
			fields = append(fields, wire.Obj(18, true, []wire.Field{wire.S(1, true, id), wire.S(2, true, level)}))
		}
	case verbTake:
		fields = append(fields, wire.Obj(20, true, []wire.Field{wire.S(1, true, int64(ref.ID)), wire.U(2, true, ref.Gen)}))
	case verbChoose:
		d := s.latest(kindSpoke)
		dialog, option := d.Context.Dialog, uint64(1)
		if len(d.Context.Options) > 0 {
			option = d.Context.Options[int(a)%len(d.Context.Options)].ID
		}
		if v != 0 {
			dialog = math.MaxUint64
			option = math.MaxUint64
		}
		fields = append(fields, wire.U(12, true, dialog), wire.U(13, true, option))
	case verbBuy, verbSell, verbLearn:
		kind := uint64(kindShop)
		if verb == verbSell {
			kind = kindOffer
		}
		if verb == verbLearn {
			kind = kindPractice
		}
		d := s.latest(kind)
		offer := uint64(1)
		price := int64(1)
		if len(d.Event.Goods) > 0 {
			i := int(a) % len(d.Event.Goods)
			if verb == verbLearn && a == 0 {
				for j, g := range d.Event.Goods {
					if g.ID == 257 {
						i = j
						break
					}
				}
			}
			offer = d.Context.Offers[i]
			price = d.Event.Goods[i].Price
		}
		list := d.Event.List
		if v == 1 {
			list = math.MaxInt32
			offer = math.MaxUint64
		}
		fields = append(fields, wire.S(14, true, int64(list)), wire.U(15, true, offer))
		if verb != verbLearn {
			counts := []int64{1, 0, -1, math.MaxInt64}
			count := counts[int(a/4)%len(counts)]
			if v == 1 {
				count = -1
			}
			if v == 2 {
				count = math.MaxInt64
			}
			if v == 3 {
				price = math.MaxInt64
			}
			fields = append(fields, wire.S(16, true, count), wire.S(17, true, price))
		}
	case verbUseItem:
		id := int64(11)
		if a%2 == 1 {
			id = 12
		}
		if v != 0 {
			id = math.MaxInt32
		}
		fields = append(fields, wire.S(21, true, id))
	}
	r := wire.Record{Type: wire.Intent, Fields: fields}
	attempt := &sequenceAttempt{record: r, epoch: s.actor.Epoch, at: time.Since(s.origin).Milliseconds()}
	if verb == verbChoose {
		attempt.chosen = s.latest(kindSpoke).Context.Ref
	}
	s.scope.attempts[n] = attempt
	s.last = r
	return r
}
func (s *sequencer) reference(verb uint64, a, v byte) meaning.Ref {
	live := make(map[int32]meaning.DecodedEvent)
	for _, r := range s.actor.Records {
		if r.Type != wire.Fact || gatewaytest.U(r, 4) != s.actor.Epoch {
			continue
		}
		d, err := meaning.EventValue(r)
		if err != nil {
			s.t.Fatal(err)
		}
		switch gatewaytest.U(r, 1) {
		case kindAppeared, kindDropped:
			if !d.Event.Mine {
				live[d.Event.ID] = d
			}
		case kindGone:
			delete(live, d.Event.ID)
		}
	}
	var candidates []meaning.DecodedEvent
	for _, d := range live {
		preferred := verb == verbTake && d.Event.Role == meaning.RoleDrop || verb != verbTake && a%2 == 0 && (d.Event.Role == "tutor" || d.Event.Role == "provisioner") || verb != verbTake && a%2 == 1 && d.Event.Role == "creature"
		if preferred {
			candidates = append(candidates, d)
		}
	}
	self := s.scene().Self.Char
	distance := func(d meaning.DecodedEvent) float64 {
		return math.Hypot(float64(d.Event.X)-float64(self.X), float64(d.Event.Y)-float64(self.Y))
	}
	slices.SortFunc(candidates, func(a, b meaning.DecodedEvent) int {
		da, db := distance(a), distance(b)
		if da < db {
			return -1
		}
		if da > db {
			return 1
		}
		if a.Event.ID < b.Event.ID {
			return -1
		}
		if a.Event.ID > b.Event.ID {
			return 1
		}
		return 0
	})
	ref := meaning.Ref{ID: math.MaxInt32, Gen: 1}
	if len(candidates) > 0 {
		ref = candidates[int(a/2)%len(candidates)].Context.Ref
	}
	if v == 1 {
		ref.ID = math.MaxInt32
	}
	if v == 2 {
		if len(s.retired) > 0 {
			ref = s.retired[int(a)%len(s.retired)]
		} else {
			ref.Gen = math.MaxUint64
		}
	}
	if v == 3 {
		ref.ID = math.MinInt32
	}
	return ref
}
func (s *sequencer) latest(kind uint64) meaning.DecodedEvent {
	for i := len(s.actor.Records) - 1; i >= 0; i-- {
		r := s.actor.Records[i]
		if r.Type == wire.Fact && gatewaytest.U(r, 1) == kind {
			d, err := meaning.EventValue(r)
			if err != nil {
				s.t.Fatal(err)
			}
			return d
		}
	}
	return meaning.DecodedEvent{}
}
func (s *sequencer) check(final bool) {
	if err := checkEconomy(s.h.Commits()); err != nil {
		s.t.Fatal(err)
	}
	for _, scope := range s.scopes {
		s.checkScope(scope, final || scope != s.scope)
	}
	if !s.stopped && !s.ended {
		scene := s.scene()
		if err := checkCharacter(scene.Self.Char); err != nil {
			s.t.Fatal(err)
		}
		if final {
			return
		}
		status, err := s.h.Gateway.Status(s.t.Context(), "one")
		if err != nil {
			trace, _ := s.h.Port.audit()
			if len(trace) == 0 || trace[len(trace)-1].Kind != bridge.Detached || trace[len(trace)-1].FailureKind != bridge.Capacity {
				s.t.Fatal("unexpected raw scope failure")
			}
			// A bounded observation overflow ends the old scope. Recovery is
			// explicit and checked just like the generated detach operation.
			s.checkScope(s.scope, true)
			if err := s.h.Resume(); err != nil {
				s.t.Fatalf("scope ended without recovery: %v", err)
			}
			s.replaceScope()
			return
		}
		if status.Pending > s.h.Config.Attempts.Pending || status.Results > s.h.Config.Attempts.Results || status.Usage.Objects > s.h.Config.Objects || status.Usage.Windows > s.h.Config.Windows || status.Usage.Rows > 128 {
			s.t.Fatalf("retention budget exceeded: %+v", status)
		}
	}
}

func terminal(r wire.Record) bool {
	if gatewaytest.U(r, 2) != uint64(wire.StageSent) {
		return true
	}
	v, ok := meaning.VerbByCode(gatewaytest.U(r, 6))
	return ok && !v.Waiting
}

func sameOutcome(a, b wire.Record) bool {
	for _, n := range []uint32{1, 2, 3, 5, 6, 7} {
		if gatewaytest.U(a, n) != gatewaytest.U(b, n) {
			return false
		}
	}
	return true
}

func checkTranscript(records []wire.Record, attempts map[uint64]*sequenceAttempt, final bool, evidenceMS int64) error {
	stages := make(map[uint64]wire.Record)
	repeats := make(map[uint64]int)
	causes := make(map[uint64]wire.Record)
	epoch := uint64(1)
	snapshot := false
	for _, r := range records {
		if r.Type == wire.Checkpoint {
			epoch = gatewaytest.U(r, 2)
			snapshot = gatewaytest.U(r, 1) == uint64(wire.CheckpointBegin)
		}
		if r.Type == wire.Fact && gatewaytest.U(r, 32) != 0 {
			n := gatewaytest.U(r, 32)
			a := attempts[n]
			f, _ := wire.Find(r.Fields, 3)
			if a == nil || snapshot || gatewaytest.U(r, 4) != epoch || f.S < a.at || f.S > a.at+2*evidenceMS {
				return fmt.Errorf("invalid causal fact for attempt %d", n)
			}
			if _, ok := causes[n]; ok {
				return fmt.Errorf("duplicate causal fact for attempt %d", n)
			}
			causes[n] = r
		}
		if r.Type != wire.Result {
			continue
		}
		n := gatewaytest.U(r, 1)
		a := attempts[n]
		if a == nil {
			return fmt.Errorf("result for unsubmitted attempt %d", n)
		}
		stage, reason := wire.Stage(gatewaytest.U(r, 2)), wire.Reason(gatewaytest.U(r, 3))
		if stage < wire.StageSent || stage > wire.StageUnrecoverable || reason > wire.ReasonBackendError || (stage == wire.StageRefused && reason == wire.ReasonNone) {
			return fmt.Errorf("invalid outcome vocabulary for attempt %d", n)
		}
		if old, ok := stages[n]; ok {
			if terminal(old) || gatewaytest.U(old, 2) == uint64(stage) {
				if !sameOutcome(old, r) || !snapshot && repeats[n] >= a.replays {
					return fmt.Errorf("reopened or repeated result for attempt %d", n)
				}
				if !snapshot {
					repeats[n]++
				}
				continue
			}
			if gatewaytest.U(old, 2) != uint64(wire.StageSent) || stage == wire.StageSent {
				return fmt.Errorf("backward result for attempt %d", n)
			}
		}
		if stage == wire.StageDone {
			fact, ok := causes[n]
			if !ok {
				return fmt.Errorf("done without preceding live cause for attempt %d", n)
			}
			effective := gatewaytest.U(r, 6)
			kind := gatewaytest.U(fact, 1)
			valid := validWitnessKind(effective, kind)
			if !valid {
				return fmt.Errorf("wrong witness kind %d for verb %d", kind, effective)
			}
		}
		if stage == wire.StageRefused {
			if _, ok := causes[n]; ok {
				return fmt.Errorf("refused attempt %d has a causal effect", n)
			}
		}
		stages[n] = r
	}
	if final {
		for n := range attempts {
			r, ok := stages[n]
			if !ok || !terminal(r) {
				return fmt.Errorf("attempt %d has no terminal outcome", n)
			}
		}
	}
	return nil
}

func validWitnessKind(verb, kind uint64) bool {
	switch verb {
	case verbTake:
		return kind == kindTook
	case verbTalk:
		return kind == kindSpoke
	case verbChoose:
		return kind == kindSpoke || kind == kindShop || kind == kindOffer || kind == kindPractice
	case verbRise:
		return kind == kindRevived
	case verbLearn:
		return kind == kindKnown
	case verbUseItem:
		return kind == kindCarried || kind == kindInventory
	default:
		return false
	}
}

func checkCharacter(c world.Character) error {
	for _, v := range [][2]float64{{c.CurHP, c.MaxHP}, {c.CurMP, c.MaxMP}} {
		if math.IsNaN(v[0]) || math.IsInf(v[0], 0) || math.IsNaN(v[1]) || math.IsInf(v[1], 0) || v[1] < 0 || v[0] < 0 || v[0] > v[1] {
			return fmt.Errorf("vital outside [0,max]: %v", v)
		}
	}
	seen := make(map[int32]bool)
	for _, it := range c.Items {
		if it.Count < 0 || seen[it.ObjectID] {
			return fmt.Errorf("invalid inventory row: %+v", it)
		}
		seen[it.ObjectID] = true
	}
	if c.SP < 0 || c.Exp < 0 {
		return fmt.Errorf("negative progression balance")
	}
	return nil
}
func checkSaved(want, got world.Character) error {
	want.LastAccess = got.LastAccess
	if got.LastAccess == 0 || !reflect.DeepEqual(want, got) {
		return fmt.Errorf("saved character differs from final world state: got %+v, want %+v", got, want)
	}
	return nil
}

type sequenceRetention struct{ games, opening, connections, actions, bindings int }

// backendBindingLimit is the bound enforced by backend.World.Write, including
// helpers and system writes, independently of the participant pending budget.
// See gateway/backend/README.md, retained raw bindings.
const backendBindingLimit = 8

type retentionLimits struct{ games, actions, bindings int }

func checkRetained(r sequenceRetention, limits retentionLimits) error {
	if r.games > limits.games || r.opening != 0 || r.connections != 0 || r.actions > limits.actions || r.bindings > limits.bindings {
		return fmt.Errorf("retention budget exceeded after drain: %+v", r)
	}
	return nil
}
func checkRetention(g *gateway.Gateway, scopes []*backend.World, config meaning.Config) error {
	// Every actor and pump is joined. Reflection keeps test-only map accounting
	// out of the runtime API; no unsafe pointers or unexported values are mutated.
	gate := reflect.ValueOf(g).Elem()
	games := gate.FieldByName("games")
	r := sequenceRetention{games: games.Len(), opening: gate.FieldByName("opening").Len()}
	for _, key := range games.MapKeys() {
		game := games.MapIndex(key).Elem()
		r.connections += game.FieldByName("conns").Len()
		r.actions = max(r.actions, game.FieldByName("actions").Len())
	}
	for _, w := range scopes {
		r.bindings = max(r.bindings, reflect.ValueOf(w).Elem().FieldByName("pending").Len())
		for {
			select {
			case _, ok := <-w.Updates():
				if !ok {
					goto joined
				}
			default:
				return fmt.Errorf("backend pump did not close its port")
			}
		}
	joined:
	}
	return checkRetained(r, retentionLimits{games: int(gate.FieldByName("cfg").FieldByName("Sessions").Int()), actions: config.Attempts.Pending, bindings: backendBindingLimit})
}

// CompareSequence rejects any change in ordered participant bytes on replay.
func CompareSequence(t *testing.T, a, b []byte) {
	t.Helper()
	if bytes.Equal(a, b) {
		return
	}
	codec, err := meaning.NewCodec(gatewaytest.Config())
	if err != nil {
		t.Fatal(err)
	}
	for peer := 0; len(a) >= 4 && len(b) >= 4; peer++ {
		na, nb := int(binary.LittleEndian.Uint32(a)), int(binary.LittleEndian.Uint32(b))
		a, b = a[4:], b[4:]
		pa, pb := a[:na], b[:nb]
		a, b = a[na:], b[nb:]
		for index := 0; len(pa) >= 4 && len(pb) >= 4; index++ {
			ra, rb := int(binary.LittleEndian.Uint32(pa)), int(binary.LittleEndian.Uint32(pb))
			pa, pb = pa[4:], pb[4:]
			xa, xb := pa[:ra], pb[:rb]
			pa, pb = pa[ra:], pb[rb:]
			if !bytes.Equal(xa, xb) {
				da, ea := codec.Decode(xa)
				db, eb := codec.Decode(xb)
				t.Fatalf("nondeterministic transcript: peer %d record %d: first %+v (%v), replay %+v (%v)", peer, index, da, ea, db, eb)
			}
		}
		if len(pa) != len(pb) {
			t.Fatalf("nondeterministic transcript: peer %d has different record counts", peer)
		}
	}
	t.Fatal("nondeterministic transcript: different peer counts")
}
