// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"math"
	"reflect"
	"slices"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldlink"
)

// SequenceHost supplies an admitted direct participant and its source audit.
// All callbacks run after the test actors are quiescent, inside synctest.
type SequenceHost struct {
	// Flush releases source publications at deterministic test barriers.
	Flush func() bool
	// World is the running authoritative simulation.
	World *world.World
	// Gateway owns participant sessions and numbering.
	Gateway *gateway.Gateway
	// Config is the host's shared meaning and limit configuration.
	Config meaning.Config
	// Peer is the admitted acting participant.
	Peer *gatewaytest.Peer
	// Owner returns the same incarnation across network reconnects.
	Owner func() world.Ref
	// Saved captures the persisted character after departure.
	Saved func() world.Character
	// Close departs and saves the incarnation.
	Close func() error
	// Stop joins the owning world loop.
	Stop func()
	// Handoffs correlates participant numbers with source-assigned intentions.
	Handoffs func() []worldlink.Handoff
	// Commits returns immutable source commitments, including continuations.
	Commits func() []world.Commit
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
		{2, opHarmTarget, 0, 1},                                                                                   // Two world players observe target clearing and NPC decay.
		{0, opUseItem, 1, 0, opAdvance, 1, 0, opUseItem, 1, 0, opAdvance, 3, 0, opUseItem, 1, 0, opAdvance, 1, 0}, // Item reuse refuses without poisoning trade.
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

type sequencer struct {
	read    map[*gatewaytest.Peer]int
	closed  map[*gatewaytest.Peer]bool
	batch   []wire.Record
	other   *gatewaytest.Peer
	t       *testing.T
	h       SequenceHost
	peers   []*gatewaytest.Peer
	actor   *gatewaytest.Peer
	n       uint64
	last    wire.Record
	stopped bool
}

func (s *sequencer) drain(p *gatewaytest.Peer) {
	p.Drain()
	records := p.Records[s.read[p]:]
	for _, r := range records {
		if r.Type == wire.Bye {
			s.closed[p] = true
		}
	}
	if !s.closed[p] && !s.stopped {
		for _, r := range records {
			if r.Type == wire.Ping {
				p.Send(wire.Record{Type: wire.Pong, Fields: []wire.Field{wire.U(1, true, gatewaytest.U(r, 1))}})
			}
		}
	}
	s.read[p] = len(p.Records)
}

func (s *sequencer) settle() {
	synctest.Wait()
	if s.h.Flush != nil {
		for s.h.Flush() {
			synctest.Wait()
			for _, p := range s.peers {
				s.drain(p)
			}
		}
	}
	for _, p := range s.peers {
		s.drain(p)
	}
	synctest.Wait()
	for _, p := range s.peers {
		s.drain(p)
	}
}
func (s *sequencer) advance(d time.Duration) {
	for d > 0 {
		step := min(d, 250*time.Millisecond)
		time.Sleep(step)
		s.settle()
		d -= step
	}
}

// ExerciseSequence preserves the committed byte-program corpus while testing
// the direct source: permission, exact outcomes, replay, reconnect, lifecycle,
// visibility, independent appearance checks and complete ordered transcripts.
func ExerciseSequence(t *testing.T, data []byte, h SequenceHost) []byte {
	t.Helper()
	s := &sequencer{t: t, h: h, actor: h.Peer, peers: []*gatewaytest.Peer{h.Peer}, n: 1, read: map[*gatewaytest.Peer]int{}, closed: map[*gatewaytest.Peer]bool{}}
	for i := 1; i+2 < len(data) && i < 193; i += 3 {
		s.settle()
		op, a, v := data[i]%opCount, data[i+1], data[i+2]%4
		if s.stopped {
			break
		}
		switch {
		case op <= opUseItem:
			s.send(uint64(op)+1, a, v)
		case op == opAdvance:
			s.advance([]time.Duration{time.Millisecond, 4999 * time.Millisecond, 5 * time.Second, 5001 * time.Millisecond, 30 * time.Second}[int(a)%5])
		case op == opFocusEngage:
			s.batch = []wire.Record{}
			s.send(verbFocus, a, v)
			s.send(verbEngage, a, v)
			if len(s.batch) > 0 {
				s.actor.Send(s.batch...)
			}
			s.batch = nil
		case op == opReplay:
			if s.last.Type != 0 && s.actor.Ready {
				s.actor.Send(s.last)
			}
		case op == opWatch || op == opAct:
			if s.other == nil {
				mode := wire.ModeObserve
				if op == opAct {
					mode = wire.ModeAct
				}
				s.other = gatewaytest.Join(t, h.Gateway, h.Config, "one", mode, false)
				s.peers = append(s.peers, s.other)
			}
		case op == opControl:
			if s.other != nil {
				s.other.Send(wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlTake))}})
				s.settle()
				if s.other.Control != 0 {
					s.actor, s.other = s.other, s.actor
				}
			}

		case op == opResume || op == opExpiredResume || op == opCloseSocket:
			owner, session := h.Owner(), s.actor.Session
			s.actor.Close()
			s.closed[s.actor] = true
			s.settle()
			if op == opExpiredResume {
				s.advance(30 * time.Second)
			}
			p := gatewaytest.Join(t, h.Gateway, h.Config, "one", wire.ModeAct, true)
			s.peers = append(s.peers, p)
			s.actor = p
			if h.Owner() != owner || p.Session != session {
				t.Fatal("reconnect replaced the incarnation or attempt ledger")
			}
		case op == opTeleport:
			scene, err := h.World.Capture(t.Context(), h.Owner())
			if err != nil {
				t.Fatal(err)
			}
			pos := world.Point{X: scene.Self.Char.X + int32(a%3)*30, Y: scene.Self.Char.Y, Z: scene.Self.Char.Z}
			if _, err := h.World.Execute(t.Context(), world.Request{Owner: h.Owner(), Verb: world.VerbTeleport, Position: pos}); err != nil {
				t.Fatal(err)
			}
		case op == opHarmSelf:
			if err := h.World.Harm(t.Context(), world.Ref{}, h.Owner(), 1e6); err != nil {
				t.Fatal(err)
			}
		case op == opHarmTarget || op == opRespawn:
			scene, err := h.World.Capture(t.Context(), h.Owner())
			if err != nil {
				t.Fatal(err)
			}
			for _, o := range scene.Objects {
				if n, ok := o.State.(world.NPCSeen); ok && n.NPC.Template.Role == catalog.Monster {
					if v == 1 && op == opHarmTarget {
						s.observeDecay(n.NPC)
					} else if err := h.World.Harm(t.Context(), h.Owner(), o.Entity, 1e6); err != nil {
						t.Fatal(err)
					}
					break
				}
			}
			if op == opRespawn {
				s.advance(30 * time.Second)
			}
		case op == opSnapshot:
			s.actor.Send(wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlResync)), wire.U(2, true, s.actor.Epoch)}})

		case op == opStopWorld:
			h.Stop()
			s.stopped = true
		case op == opFault || op == opHold || op == opRelease:
			// The old raw receipt fault opcodes remain valid corpus bytes. Source
			// backpressure and loss are exercised by mailbox tests, without a raw port.
			s.advance(time.Duration(a%4) * time.Millisecond)
		}
		s.settle()
		s.check()
	}
	s.advance(time.Millisecond)
	s.check()
	if !s.stopped {
		scene, err := h.World.Capture(t.Context(), h.Owner())
		if err != nil {
			t.Fatal(err)
		}
		for _, p := range s.peers {
			if err := checkPlayerAppearances(p.Records, scene); err != nil {
				t.Fatal(err)
			}
			if err := checkNPCForms(p.Records, scene); err != nil {
				t.Fatal(err)
			}
		}
		if err := h.Close(); err != nil {
			t.Fatal(err)
		}
		if h.Saved != nil {
			want := scene.Self.Char
			want.CurHP = math.Round(want.CurHP)
			want.CurMP = math.Round(want.CurMP)
			want.CurCP = math.Round(want.CurCP)
			if err := checkSaved(want, h.Saved()); err != nil {
				t.Fatal(err)
			}
		}
		s.settle()
		s.check()
	}
	for _, p := range s.peers {
		p.Close()
		s.closed[p] = true
	}
	s.settle()
	codec, err := meaning.NewCodec(h.Config)
	if err != nil {
		t.Fatal(err)
	}
	var out []byte
	for _, p := range s.peers {
		var peer []byte
		for _, r := range p.Records {
			if r.Type == wire.Welcome {
				r.Fields = slices.Clone(r.Fields)
				for i := range r.Fields {
					if r.Fields[i].Number == 4 {
						r.Fields[i] = wire.U(4, true, 1)
					}
				}
			}
			b, err := codec.Encode([]wire.Record{r})
			if err != nil {
				t.Fatal(err)
			}
			peer = binary.LittleEndian.AppendUint32(peer, uint32(len(b)))
			peer = append(peer, b...)
		}
		out = binary.LittleEndian.AppendUint32(out, uint32(len(peer)))
		out = append(out, peer...)
	}
	return out
}

func (s *sequencer) send(verb uint64, a, v byte) {
	if !s.actor.Ready || s.actor.Control == 0 {
		return
	}
	r := wire.Record{Type: wire.Intent, Fields: []wire.Field{wire.U(1, true, s.n), wire.U(2, true, verb), wire.U(3, true, s.actor.Control)}}
	var target meaning.Ref
	var window meaning.DecodedEvent
	for i := len(s.actor.Records) - 1; i >= 0; i-- {
		rec := s.actor.Records[i]
		if rec.Type != wire.Fact {
			continue
		}
		e, err := meaning.EventValue(rec)
		if err != nil {
			s.t.Fatal(err)
		}
		if target.Gen == 0 && e.Event.Kind == meaning.Appeared && !e.Event.Mine && (verb != verbTalk && verb != verbEngage || e.Event.Role == "tutor" || e.Event.Role == "provisioner") {
			target = e.Context.Ref
		}
		if verb == verbTake && e.Event.Kind == meaning.Dropped {
			target = e.Context.Ref
		}
		if window.Event.Kind == "" && ((verb == verbChoose && e.Event.Kind == meaning.Spoke) || (verb == verbBuy && e.Event.Kind == meaning.Shop) || (verb == verbSell && e.Event.Kind == meaning.Offer) || (verb == verbLearn && e.Event.Kind == meaning.Practice)) {
			window = e
		}
	}
	if target.Gen == 0 {
		target = meaning.Ref{ID: 123, Gen: 1}
	}
	if v == 1 {
		target.Gen++
	}
	if v == 2 {
		target.ID = 123
	}
	ref := func(n uint32) wire.Field {
		return wire.Obj(n, true, []wire.Field{wire.S(1, true, int64(target.ID)), wire.U(2, true, target.Gen)})
	}
	switch verb {
	case verbWalk:
		r.Fields = append(r.Fields, wire.At(11, true, -71057+int32(a%4)*10, 257883, -3104))
	case verbAim, verbStrike, verbTalk, verbFocus, verbEngage:
		r.Fields = append(r.Fields, ref(10))
	case verbTake:
		r.Fields = append(r.Fields, ref(20))
	case verbCast:
		r.Fields = append(r.Fields, ref(10), wire.Obj(18, true, []wire.Field{wire.S(1, true, 3), wire.S(2, true, 1)}))
	case verbChoose:
		option := uint64(1)
		if len(window.Context.Options) > 0 {
			option = window.Context.Options[int(a)%len(window.Context.Options)].ID
		}
		handle := window.Context.Dialog
		if handle == 0 {
			handle = 1
		}
		if v == 1 {
			handle++
		}
		r.Fields = append(r.Fields, wire.U(12, true, handle), wire.U(13, true, option))
	case verbBuy, verbSell, verbLearn:
		handle := uint64(1)
		price := int64(0)
		if len(window.Context.Offers) > 0 {
			index := int(a) % len(window.Context.Offers)
			handle = window.Context.Offers[index]
			price = window.Event.Goods[index].Price
		}
		if v == 1 {
			handle++
		}
		r.Fields = append(r.Fields, wire.S(14, true, int64(window.Event.List)), wire.U(15, true, handle))
		if verb != verbLearn {
			r.Fields = append(r.Fields, wire.S(16, true, 1), wire.S(17, true, price))
		}
	case verbUseItem:
		r.Fields = append(r.Fields, wire.S(21, true, 11+int64(a%2)))
	}
	s.n++
	s.last = r
	if s.batch != nil {
		s.batch = append(s.batch, r)
	} else {
		s.actor.Send(r)
	}
}

func (s *sequencer) check() {
	handoffs := []worldlink.Handoff{}
	if s.h.Handoffs != nil {
		handoffs = s.h.Handoffs()
	}
	commits := []world.Commit{}
	if s.h.Commits != nil {
		commits = s.h.Commits()
	}
	for _, p := range s.peers {
		terminal := map[uint64]wire.Record{}
		for _, r := range p.Records {
			if r.Type != wire.Result {
				continue
			}
			n, stage := gatewaytest.U(r, 1), wire.Stage(gatewaytest.U(r, 2))
			if stage == wire.StageSent {
				verb, _ := meaning.VerbByCode(gatewaytest.U(r, 6))
				if verb.Waiting {
					continue
				}
			}
			if old, ok := terminal[n]; ok && !reflect.DeepEqual(old, r) {
				s.t.Fatalf("attempt %d changed terminal outcome", n)
			}
			terminal[n] = r
			if stage != wire.StageDone || gatewaytest.U(r, 7) != uint64(wire.DeliveryBackend) {
				continue
			}
			if err := checkCommittedResult(r, handoffs, commits); err != nil {
				s.t.Fatal(err)
			}
		}
	}
}

// checkCommittedResult is independent of the adapter translator and planner.
// It accepts only a matching source-owned Done, including a continuation.
func checkCommittedResult(r wire.Record, handoffs []worldlink.Handoff, commits []world.Commit) error {
	n := gatewaytest.U(r, 1)
	for _, h := range handoffs {
		if h.N != n || h.Intent.N == 0 {
			continue
		}
		for _, c := range commits {
			if c.Outcome.Intent == h.Intent && c.Outcome.Stage == world.StageDone {
				return nil
			}
		}
	}
	return fmt.Errorf("attempt %d has no matching source commitment", n)
}
func checkSaved(want, got world.Character) error {
	want.LastAccess = got.LastAccess
	if got.LastAccess == 0 || !reflect.DeepEqual(want, got) {
		return fmt.Errorf("saved character differs from final world state")
	}
	return nil
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
				t.Fatalf("nondeterministic transcript: peer %d record %d: first %s (%v), replay %s (%v)", peer, index, recordSummary(da), ea, recordSummary(db), eb)
			}
		}
		if len(pa) != len(pb) {
			t.Fatalf("nondeterministic transcript: peer %d has different record counts", peer)
		}
	}
	t.Fatal("nondeterministic transcript: different peer counts")
}

func recordSummary(records []wire.Record) string {
	if len(records) == 0 {
		return "empty"
	}
	r := records[0]
	return fmt.Sprintf("type=%d field1=%d field2=%d field3=%d", r.Type, gatewaytest.U(r, 1), gatewaytest.U(r, 2), gatewaytest.U(r, 3))
}
