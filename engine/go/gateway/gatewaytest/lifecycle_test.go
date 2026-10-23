// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gatewaytest_test

import (
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/gateway/gatewaytest"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

func (r *rig) closeWorld(detach bool) {
	r.t.Helper()
	if !r.client {
		kind := bridge.Closed
		if detach {
			kind = bridge.Detached
		}
		if err := r.f.Emit(r.t.Context(), bridge.Update{Kind: kind, GatewayEpoch: r.epoch}); err != nil {
			r.t.Fatal(err)
		}
	} else {
		r.mu.Lock()
		conn := r.conn
		r.mu.Unlock()
		if detach {
			if err := conn.Close(); err != nil {
				r.t.Fatal(err)
			}
		} else {
			if err := r.rawWrite(conn, []wire.Record{{Type: wire.Bye, Fields: []wire.Field{wire.U(1, true, 0)}}}); err != nil {
				r.t.Fatal(err)
			}
		}
	}
	r.sync()
}

func TestG08WorldCloseByContact(t *testing.T) {
	for _, before := range []bool{true, false} {
		t.Run(map[bool]string{true: "before", false: "after"}[before], func(t *testing.T) {
			both(t, func(r *rig) {
				r.hold = before
				r.p.Send(r.intent(1, 5, r.ref(2)))
				r.sync()
				if !before {
					r.p.Result(1, wire.StageSent)
				}
				r.closeWorld(false)
				stage, delivery := wire.StageUnknown, wire.DeliveryBackend
				if before {
					stage, delivery = wire.StageRefused, wire.DeliveryNone
				}
				r.expect(1, 5, 5, stage, wire.ReasonWorldClosed, delivery, 0)
				log := r.log()
				want := 1
				if before {
					want = 0
				}
				if len(log) != want {
					r.t.Fatal(log)
				}
				byes := 0
				for _, rec := range r.p.Records {
					if rec.Type == wire.Bye {
						byes++
						if gatewaytest.U(rec, 1) != uint64(wire.CloseWorldClosed) {
							r.t.Fatal(rec)
						}
					}
				}
				if byes != 1 {
					r.t.Fatal("missing unique Bye")
				}
			})
		})
	}
}

func TestG09DetachAndExplicitResume(t *testing.T) {
	both(t, func(r *rig) {
		r.offer(meaning.Shop, 2)
		oldRef := r.ref(2)
		oldSession := r.p.Session
		r.p.Send(r.intent(1, 5, oldRef))
		r.p.Result(1, wire.StageSent)
		log := r.log()
		if len(log) != 1 {
			r.t.Fatal(log)
		}
		r.closeWorld(true)
		r.expect(1, 5, 5, wire.StageUnknown, wire.ReasonWorldClosed, wire.DeliveryBackend, 0)
		r.epoch = 2
		snapshot := r.snapshot()
		cached := r.receipt(log[0], wire.StageDone, wire.ReasonNone)
		cached.Replay = true
		r.cached = []bridge.Receipt{cached}
		if !r.client {
			r.f.Recover(bridge.Recovery{Snapshot: snapshot, Receipts: r.cached})
		}
		w, st, err := r.session.Resume(r.t.Context())
		if err != nil {
			r.t.Fatal(err)
		}
		r.world, r.state = w, st
		r.p = gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeAct, true)
		r.sync()
		if r.p.Epoch != 1 || r.p.Session == oldSession {
			r.t.Fatal("resume reused participant scope")
		}
		if len(r.log()) != 1 {
			r.t.Fatal("resume reexecuted")
		}
		for _, rec := range r.p.Records {
			if rec.Type == wire.Result || gatewaytest.U(rec, 32) != 0 {
				r.t.Fatal("recovery created old cause/outcome")
			}
		}
		newRef := r.ref(2)
		oldGen, _ := wire.Find(oldRef.Value.Fields, 2)
		newGen, _ := wire.Find(newRef.Value.Fields, 2)
		if newGen.U <= oldGen.U {
			r.t.Fatal("reference counter reset")
		}
		// Participant numbering restarts; raw keys and raw attempt numbers do not.
		r.p.Send(r.intent(1, 1, wire.At(11, true, 1, 2, 3)))
		r.expect(1, 1, 1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
		log = r.log()
		if len(log) != 2 || log[1].N != 2 || log[1].Command.Key != 2 {
			r.t.Fatal("raw numbering reset", log)
		}
	})
}

func TestG10RejectFalseWitnesses(t *testing.T) {
	for _, branch := range []string{"foreign-actor", "unattributed", "late", "snapshot", "ambient", "mixed", "foreign-generation"} {
		t.Run(branch, func(t *testing.T) {
			both(t, func(r *rig) {
				verb := uint64(5)
				ref := r.ref(2)
				if branch == "foreign-generation" {
					verb = 4
					ref = r.ref(9)
					ref.Number = 20
				}
				r.p.Send(r.intent(1, verb, ref))
				r.p.Result(1, wire.StageSent)
				log := r.log()
				if len(log) != 1 {
					r.t.Fatal(log)
				}
				e := meaning.Event{Kind: meaning.Spoke, ID: 2, Text: "Independent"}
				if branch == "foreign-actor" {
					e.ID = 1
				}
				if branch == "unattributed" {
					r.source.Dialogue = false
				}
				switch branch {
				case "late":
					r.advance(5 * time.Second)
					r.reply(log[0], wire.StageDone, wire.ReasonNone, e)
				case "ambient":
					// An unproven batch leaves Client only at its original proof timeout.
					// The fixture transfers the same batch after that barrier.
					if r.client {
						r.emit(e)
						r.advance(5 * time.Second)
					} else {
						at := time.Now()
						r.advance(5 * time.Second)
						r.send(bridge.Batch{Records: []wire.Record{r.fact(e)}, ReceivedAt: []time.Time{at}})
					}
				case "snapshot":
					r.send(bridge.Batch{Records: []wire.Record{cp(r.epoch, wire.CheckpointBegin, false)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
					r.send(bridge.Batch{Records: []wire.Record{r.fact(e)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
					r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{})
					r.send(bridge.Batch{Records: []wire.Record{cp(r.epoch, wire.CheckpointEnd, true)}, ReceivedAt: []time.Time{time.Now()}, Snapshot: true})
				case "mixed":
					r.source.Dialogue = false
					r.reply(log[0], wire.StageDone, wire.ReasonNone, e)
				case "foreign-generation":
					// Ground leaves and re-enters; a same-ID transfer has a different Ref.
					r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Gone, ID: 9}, itemEffect())
					r.emit(meaning.Event{Kind: meaning.Dropped, ID: 9, Role: meaning.RoleDrop})
					r.emit(meaning.Event{Kind: meaning.Took, ID: 1, Target: 9})
				default:
					r.reply(log[0], wire.StageDone, wire.ReasonNone, e)
				}
				if branch != "late" && branch != "ambient" {
					r.advance(5 * time.Second)
				}
				r.expect(1, verb, verb, wire.StageUnknown, wire.ReasonTimeout, wire.DeliveryBackend, 0)
				if len(r.log()) != 1 {
					r.t.Fatal("proof reexecuted business command")
				}
			})
		})
	}
}

func TestG02ControlTransferBeforeEffect(t *testing.T) {
	both(t, func(r *rig) {
		r.p.Send(r.intent(1, 11, r.ref(2)))
		r.sync()
		log := r.log()
		if len(log) != 1 {
			r.t.Fatal(log)
		}
		other := gatewaytest.Join(r.t, r.g, r.c, "one", wire.ModeAct, false)
		r.sync() // Join's end frame must also finish the actor's checkpoint drain.
		other.Send(wire.Record{Type: wire.Control, Fields: []wire.Field{wire.U(1, true, uint64(wire.ControlTake))}})
		other.Until(func() bool { return other.Control != 0 })
		r.sync()
		r.p.Result(1, wire.StageUnknown)
		r.reply(log[0], wire.StageDone, wire.ReasonNone, meaning.Event{Kind: meaning.Targeted, ID: 1, Target: 2}, targetEffect(2))
		r.expect(1, 11, 5, wire.StageUnknown, wire.ReasonInterrupted, wire.DeliveryPartial, 0)
		if len(r.log()) != 1 {
			r.t.Fatal("stale control wrote effect")
		}
		other.Close()
	})
}

func TestG07TerminalSentIsImmutable(t *testing.T) {
	both(t, func(r *rig) {
		r.p.Send(r.intent(1, 1, wire.At(11, true, 1, 2, 3)))
		r.expect(1, 1, 1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
		log := r.log()
		r.reply(log[0], wire.StageRefused, wire.ReasonWorld, meaning.Event{})
		r.closeWorld(false)
		r.expect(1, 1, 1, wire.StageSent, wire.ReasonNone, wire.DeliveryBackend, 0)
		if len(r.log()) != 1 {
			r.t.Fatal("terminal close repeated write")
		}
	})
}
