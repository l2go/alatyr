// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"errors"
	"math"
	"reflect"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func loseObservation(t *testing.T, c *Client) {
	t.Helper()
	if _, _, err := c.cfg.World.Subscribe(t.Context(), c.Owner()); err != nil {
		t.Fatal(err)
	}
	for {
		if nextPortUpdate(t, c).Kind == bridge.Detached {
			return
		}
	}
}

func TestWorldPortOverflowResumeDoesNotExecuteAgain(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		c, snap := openPort(t, cfg)
		var admitted uint64
		for key := uint64(1); key <= 2*maxBacklogUpdates; key++ {
			n, err := c.Write(t.Context(), portWalk(key))
			if n != 0 {
				admitted = n
			}
			if err != nil {
				break
			}
		}
		if admitted == 0 || admitted >= 2*maxBacklogUpdates {
			t.Fatal("backlog did not detach within its bound")
		}
		u := nextPortUpdate(t, c)
		if u.Kind != bridge.Detached || u.FailureKind != bridge.Capacity {
			t.Fatalf("overflow update: %+v", u)
		}
		before, err := w.Capture(t.Context(), c.Owner())
		if err != nil {
			t.Fatal(err)
		}
		recovery, err := c.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		after, err := w.Capture(t.Context(), c.Owner())
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(before.Self, after.Self) || before.Begin.Sequence != after.Begin.Sequence {
			t.Fatal("Resume changed gameplay or commitment sequence")
		}
		if recovery.Snapshot.FenceSeq <= snap.FenceSeq || len(recovery.Receipts) != maxRetainedReceipts {
			t.Fatalf("recovery bounds: %+v", recovery)
		}
		for i, r := range recovery.Receipts {
			if !r.Replay || r.N != admitted-uint64(maxRetainedReceipts-1-i) || r.GatewayEpoch != 1 {
				t.Fatalf("replay identity: %+v", r)
			}
		}
		if n, err := c.Write(t.Context(), portWalk(1)); n != 0 || err == nil {
			t.Fatal("evicted receipt made original key reusable")
		}
	})
}

func TestWorldPortCheckpointBeginAppendKeepsEnd(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		if _, err := w.Execute(t.Context(), world.Request{Owner: c.Owner(), Verb: world.VerbTeleport, Position: world.Point{X: -71027, Y: 257883, Z: -3104}}); err != nil {
			t.Fatal(err)
		}
		var begin bridge.Update
		for range maxBacklogUpdates {
			u := nextPortUpdate(t, c)
			if u.Kind != bridge.LiveBatch {
				t.Fatalf("teleport update: got %v, want LiveBatch", u.Kind)
			}
			if len(u.Batch.Records) == 1 && u.Batch.Records[0].Type == wire.Checkpoint && profile.U(u.Batch.Records[0], 1) == uint64(wire.CheckpointBegin) {
				begin = u
				break
			}
		}
		if !begin.Batch.Snapshot {
			t.Fatal("checkpoint Begin missing within bounded updates")
		}

		// The unread facts batch keeps End queued. Hold its owner while the
		// consumer appends, so this tests storage ownership, not scheduling.
		held, release := make(chan struct{}), make(chan struct{})
		done := make(chan result, 1)
		go func() {
			done <- c.submit(t.Context(), func(context.Context, *session) result {
				close(held)
				<-release
				return result{}
			})
		}()
		<-held
		begin.Batch.Records = append(begin.Batch.Records, wire.Record{Type: wire.Fact})
		close(release)
		if r := <-done; r.err != nil {
			t.Fatal(r.err)
		}

		facts := nextPortUpdate(t, c)
		if facts.Kind != bridge.LiveBatch || !facts.Batch.Snapshot || len(facts.Batch.Records) == 0 {
			t.Fatalf("checkpoint facts: got %+v, want nonempty snapshot batch", facts)
		}
		end := nextPortUpdate(t, c)
		if end.Kind != bridge.LiveBatch || !end.Batch.Snapshot || len(end.Batch.Records) != 1 {
			t.Fatalf("checkpoint End: got %+v, want single snapshot marker", end)
		}
		r := end.Batch.Records[0]
		if r.Type != wire.Checkpoint || profile.U(r, 1) != uint64(wire.CheckpointEnd) || profile.U(r, 2) != begin.GatewayEpoch || profile.U(r, 3) != 1 || r.EncodedBytes == 0 {
			t.Fatalf("checkpoint End after Begin append: got %+v, want encoded ready End in epoch %d", r, begin.GatewayEpoch)
		}
	})
}

func TestWorldPortTeleportFenceAndLiveRevival(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		cfg.Load = func(context.Context, bridge.Identity) (world.Character, error) {
			char := sessionCharacter()
			char.CurHP = 0
			return char, nil
		}
		c, snap := openPort(t, cfg)
		_, err := w.Execute(t.Context(), world.Request{Owner: c.Owner(), Verb: world.VerbTeleport, Position: world.Point{X: -71027, Y: 257883, Z: -3104}, Revive: true})
		if err != nil {
			t.Fatal(err)
		}
		last := snap.FenceSeq
		corrected, revived, begin, end := 0, 0, 0, 0
		for end == 0 {
			u := nextPortUpdate(t, c)
			if u.Kind != bridge.LiveBatch {
				t.Fatalf("teleport update: %+v", u)
			}
			for _, r := range u.Batch.Records {
				if r.Type == wire.Checkpoint {
					if profile.U(r, 1) == uint64(wire.CheckpointBegin) {
						begin++
					} else {
						if profile.U(r, 3) != 1 {
							t.Fatal("checkpoint not ready")
						}
						end++
					}
					continue
				}
				if r.Type != wire.Fact {
					continue
				}
				seq := profile.U(r, 2)
				if seq <= last {
					t.Fatalf("nonmonotonic sequence: %d after %d", seq, last)
				}
				last = seq
				if profile.Has(r, 67) {
					corrected++
					if profile.U(r, 66) != 1 || u.Batch.Snapshot {
						t.Fatal("correction missing exact live fence")
					}
				}
				if profile.U(r, 1) == 7 {
					revived++
					if u.Batch.Snapshot {
						t.Fatal("revival fabricated in checkpoint")
					}
				}
			}
		}
		if corrected != 1 || revived != 1 || begin != 1 || end != 1 {
			t.Fatalf("teleport trace: correction %d revival %d begin %d end %d", corrected, revived, begin, end)
		}
		scene, err := w.Capture(t.Context(), c.Owner())
		if err != nil || scene.Self.Teleporting || scene.Self.Dead || !scene.End.Ready {
			t.Fatalf("final scene: %+v/%v", scene.End, err)
		}
	})
}

type failingPortStore struct {
	memstore.Store
	failure               error
	blockSkill            chan struct{}
	skillCalls, saveCalls int
}

func (s *failingPortStore) PutCharacter(ctx context.Context, c world.Character) error {
	s.saveCalls++
	if s.failure != nil {
		return s.failure
	}
	return s.Store.PutCharacter(ctx, c)
}
func (s *failingPortStore) PutSkill(ctx context.Context, id int32, k world.Skill) error {
	s.skillCalls++
	if s.blockSkill != nil {
		close(s.blockSkill)
		<-ctx.Done()
		return ctx.Err()
	}
	return s.failure
}

func TestWorldPortFailedCloseHasStableResultAndHostRetry(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		failure := errors.New("save unavailable")
		store := &failingPortStore{failure: failure}
		cfg, w := startPortWorld(t, cat, store)
		c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			t.Fatal(err)
		}
		if err := c.Close(); !errors.Is(err, failure) {
			t.Fatalf("Close: %v", err)
		}
		if err := c.Close(); !errors.Is(err, failure) || store.saveCalls != 1 {
			t.Fatalf("repeated Close: %v calls=%d", err, store.saveCalls)
		}
		store.failure = nil
		if err := w.Leave(t.Context(), c.Owner()); err != nil {
			t.Fatal(err)
		}
		if store.saveCalls != 2 {
			t.Fatal("host retry did not reach store")
		}
	})
}

func portLesson(t *testing.T, c *Client, w *world.World) bridge.Command {
	t.Helper()
	scene, err := w.Capture(t.Context(), c.Owner())
	if err != nil {
		t.Fatal(err)
	}
	var target world.Ref
	for _, o := range scene.Objects {
		if n, ok := o.State.(world.NPCSeen); ok && c.cfg.Catalog.CanTeach(n.NPC.Template.ID, scene.Self.Char.ClassID) {
			target = o.Entity
		}
	}
	if target == (world.Ref{}) {
		t.Fatal("no authored trainer")
	}
	if _, err := c.Write(t.Context(), bridge.Command{Key: 1, Verb: 5, Payload: []wire.Field{wire.S(64, true, int64(target.ID))}}); err != nil {
		t.Fatal(err)
	}
	awaitReceipt(t, c, 1)
	scene, err = w.Capture(t.Context(), c.Owner())
	if err != nil {
		t.Fatal(err)
	}
	_, options, err := choices(scene.Dialogue)
	if err != nil || len(options) == 0 {
		t.Fatalf("trainer choices: %v", err)
	}
	action, _ := wire.Find(options[0].Fields, 3)
	if _, err := c.Write(t.Context(), bridge.Command{Key: 2, Verb: 6, Payload: []wire.Field{wire.S(64, true, int64(target.ID)), wire.Data(68, true, string(action.Data))}}); err != nil {
		t.Fatal(err)
	}
	awaitReceipt(t, c, 2)
	scene, err = w.Capture(t.Context(), c.Owner())
	if err != nil || len(scene.Dialogue.Affordances) == 0 {
		t.Fatalf("trainer lessons: %v", err)
	}
	id, level, ok := lesson(scene.Dialogue.Affordances[0])
	if !ok {
		t.Fatal("invalid lesson")
	}
	return bridge.Command{Key: 3, Verb: 13, Payload: []wire.Field{wire.S(14, true, 0), wire.Obj(18, true, []wire.Field{wire.S(1, true, int64(id)), wire.S(2, true, int64(level))})}}
}

func TestWorldPortSkillWriteFailureKeepsCommitAndUnknownReceipt(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		failure := errors.New("skill unavailable")
		store := &failingPortStore{failure: failure}
		cfg, w := startPortWorld(t, cat, store)
		c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			t.Fatal(err)
		}
		defer func() {
			store.failure = nil
			if err := c.Close(); err != nil {
				t.Error(err)
			}
		}()
		command := portLesson(t, c, w)
		before, err := w.Capture(t.Context(), c.Owner())
		if err != nil {
			t.Fatal(err)
		}
		n, err := c.Write(t.Context(), command)
		if n != 3 || !errors.Is(err, failure) {
			t.Fatalf("learn handoff: %d/%v", n, err)
		}
		r := awaitReceipt(t, c, 3)
		if r.Stage != wire.StageUnknown || r.Reason != wire.ReasonBackendError || r.EffectsPresent {
			t.Fatalf("durability receipt: %+v", r)
		}
		after, err := w.Capture(t.Context(), c.Owner())
		if err != nil || len(after.Skills) <= len(before.Skills) {
			t.Fatalf("in-memory skill absent: %v", err)
		}
		loseObservation(t, c)
		recovery, err := c.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		if store.skillCalls != 1 || len(recovery.Receipts) != 3 || recovery.Receipts[2].Stage != wire.StageUnknown {
			t.Fatal("recovery rewrote or repeated failed skill write")
		}
	})
}

func TestWorldPortCloseCancelsActiveWriteAndRejectsConcurrentWrite(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := &failingPortStore{blockSkill: make(chan struct{})}
		cfg, w := startPortWorld(t, cat, store)
		c, _ := openPort(t, cfg)
		command := portLesson(t, c, w)
		done := make(chan result, 1)
		go func() { n, err := c.Write(t.Context(), command); done <- result{n: n, err: err} }()
		<-store.blockSkill
		if _, err := c.Write(t.Context(), portWalk(4)); err == nil {
			t.Fatal("concurrent Write admitted")
		}
		if err := c.Close(); err != nil {
			t.Fatal(err)
		}
		r := <-done
		if r.n != 3 || !errors.Is(r.err, context.Canceled) {
			t.Fatalf("active canceled write: %+v", r)
		}
		if store.skillCalls != 1 || store.saveCalls != 1 {
			t.Fatal("write or save executed more than once")
		}
	})
}

func TestWorldPortCounterAndKeyBounds(t *testing.T) {
	cat := authoredCatalog(t)
	for _, which := range []string{"attempt", "keys", "facts", "epoch"} {
		t.Run(which, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				cfg, _ := startPortWorld(t, cat, memstore.New())
				c, _ := openPort(t, cfg)
				if which == "epoch" {
					loseObservation(t, c)
				}
				c.submit(t.Context(), func(_ context.Context, s *session) result {
					switch which {
					case "attempt":
						s.nextAttempt = 0
					case "keys":
						for i := uint64(1); i <= maxSessionKeys; i++ {
							s.keys[i] = true
						}
					case "facts":
						s.nextFact = math.MaxUint64
					case "epoch":
						s.epoch = math.MaxUint64
					}
					return result{}
				})
				if which == "epoch" {
					if _, err := c.Resume(t.Context()); err == nil {
						t.Fatal("epoch wrapped")
					}
					return
				}
				if _, err := c.Write(t.Context(), portWalk(5000)); err == nil {
					t.Fatal("counter or capacity admitted")
				}
			})
		})
	}
}

func TestWorldPortResumeBudgetAndDeparture(t *testing.T) {
	cat := authoredCatalog(t)
	for _, which := range []string{"budget", "leave"} {
		t.Run(which, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				cfg, w := startPortWorld(t, cat, memstore.New())
				cfg.ResumeBudget = time.Second
				c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
				if err != nil {
					t.Fatal(err)
				}
				if which == "leave" {
					if err := w.Leave(t.Context(), c.Owner()); err != nil {
						t.Fatal(err)
					}
				} else {
					loseObservation(t, c)
					<-time.After(time.Second)
					synctest.Wait()
				}
				if _, err := c.Resume(t.Context()); err == nil {
					t.Fatal("Resume restored departed or expired session")
				}
				if err := c.Close(); err != nil {
					t.Fatal(err)
				}
			})
		})
	}
}

func TestWorldPortMoveDeadlineStopsWithoutRecoveryExtension(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		command := portWalk(1)
		command.Payload = []wire.Field{wire.At(11, true, -68057, 257883, -3104)}
		command.QueueTTLMS = new(uint64(100))
		if _, err := c.Write(t.Context(), command); err != nil {
			t.Fatal(err)
		}
		awaitReceipt(t, c, 1)
		loseObservation(t, c)
		<-time.After(200 * time.Millisecond)
		recovery, err := c.Resume(t.Context())
		if err != nil {
			t.Fatal(err)
		}
		scene, err := w.Capture(t.Context(), c.Owner())
		if err != nil {
			t.Fatal(err)
		}
		if scene.Moving || scene.Self.Char.X == -68057 || !recovery.Snapshot.Ready {
			t.Fatal("recovery extended or replayed expired movement")
		}
		position := scene.Self.Char.X
		<-time.After(200 * time.Millisecond)
		scene, err = w.Capture(t.Context(), c.Owner())
		if err != nil || scene.Self.Char.X != position {
			t.Fatal("movement continued after deadline")
		}
	})
}

func TestWorldPortSourceOverflowKeepsIncarnation(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
		go func() {
			c.submit(t.Context(), func(context.Context, *session) result { close(entered); <-release; return result{} })
			close(done)
		}()
		<-entered
		for i := range 80 {
			if err := w.SetRunning(t.Context(), c.Owner().ID, i%2 == 0); err != nil {
				t.Fatal(err)
			}
		}
		close(release)
		<-done
		for {
			u := nextPortUpdate(t, c)
			if u.Kind == bridge.Detached {
				if u.FailureKind != bridge.Capacity {
					t.Fatal("source loss not Capacity")
				}
				break
			}
		}
		scene, err := w.Capture(t.Context(), c.Owner())
		if err != nil || scene.Self.Entity != c.Owner() {
			t.Fatal("overflow removed incarnation")
		}
		if recovery, err := c.Resume(t.Context()); err != nil || !recovery.Snapshot.Ready {
			t.Fatalf("source overflow Resume: %v", err)
		}
	})
}

func TestWorldPortStopCannotResumeOrClaimSaving(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := memstore.New()
		w := world.New(cat, store, world.WithReservedIDs([]int32{1}))
		ctx, cancel := context.WithCancel(context.Background())
		done := make(chan error, 1)
		go func() { done <- w.Run(ctx) }()
		cfg := Config{World: w, Catalog: cat, Load: func(context.Context, bridge.Identity) (world.Character, error) { return sessionCharacter(), nil }}
		c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			cancel()
			<-done
			t.Fatal(err)
		}
		cancel()
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		if _, err := c.Resume(t.Context()); err == nil {
			t.Fatal("Resume restored stopped world")
		}
		if err := c.Close(); !errors.Is(err, world.ErrStopped) {
			t.Fatalf("stopped Close: %v, want honest missing-save error", err)
		}
		chars, err := store.Characters(t.Context(), "example")
		if err != nil || len(chars) != 0 {
			t.Fatal("stop implicitly saved character")
		}
	})
}

func TestWorldPortOpenRejectsUninitializedWorld(t *testing.T) {
	cfg := Config{World: new(world.World), Catalog: authoredCatalog(t), Load: func(context.Context, bridge.Identity) (world.Character, error) {
		t.Fatal("Load called before lifecycle validation")
		return world.Character{}, nil
	}}
	if c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"}); c != nil || !errors.Is(err, world.ErrUninitialized) {
		t.Fatalf("uninitialized world: %v", err)
	}
}

func TestWorldPortCanceledResumeKeepsDetachedSession(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, _ := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		loseObservation(t, c)
		ctx, cancel := context.WithCancel(t.Context())
		cancel()
		if _, err := c.Resume(ctx); !errors.Is(err, context.Canceled) {
			t.Fatalf("canceled Resume: %v", err)
		}
		if recovery, err := c.Resume(t.Context()); err != nil || !recovery.Snapshot.Ready || recovery.Snapshot.GatewayEpoch != 2 {
			t.Fatalf("subsequent Resume: %+v/%v", recovery.Snapshot, err)
		}
	})
}

func TestWorldPortConcurrentResumeRejected(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, _ := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		loseObservation(t, c)
		entered, release, ownerDone := make(chan struct{}), make(chan struct{}), make(chan struct{})
		go func() {
			c.submit(t.Context(), func(context.Context, *session) result { close(entered); <-release; return result{} })
			close(ownerDone)
		}()
		<-entered
		resumed := make(chan error, 1)
		go func() { _, err := c.Resume(t.Context()); resumed <- err }()
		synctest.Wait()
		if _, err := c.Resume(t.Context()); err == nil {
			t.Fatal("concurrent Resume admitted")
		}
		close(release)
		<-ownerDone
		if err := <-resumed; err != nil {
			t.Fatal(err)
		}
	})
}

func TestWorldPortCloseDoesNotSaveReplacement(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := memstore.New()
		cfg, w := startPortWorld(t, cat, store)
		c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			t.Fatal(err)
		}
		entered, release, done := make(chan struct{}), make(chan struct{}), make(chan struct{})
		go func() {
			c.submit(t.Context(), func(context.Context, *session) result { close(entered); <-release; return result{} })
			close(done)
		}()
		<-entered
		if err := w.Leave(t.Context(), c.Owner()); err != nil {
			t.Fatal(err)
		}
		char := sessionCharacter()
		char.SP = 777
		replacement, err := w.Enter(t.Context(), char)
		if err != nil {
			t.Fatal(err)
		}
		close(release)
		<-done
		var refusal *world.RefusalError
		if err := c.Close(); !errors.As(err, &refusal) || refusal.Reason != world.ReasonStale {
			t.Fatalf("stale Close: %v", err)
		}
		scene, err := w.Capture(t.Context(), replacement.Self.Entity)
		if err != nil || scene.Self.Char.SP != 777 {
			t.Fatal("old port removed or changed replacement")
		}
		chars, err := store.Characters(t.Context(), "example")
		if err != nil || len(chars) != 1 || chars[0].SP == 777 {
			t.Fatal("old port saved replacement")
		}
		if err := w.Leave(t.Context(), replacement.Self.Entity); err != nil {
			t.Fatal(err)
		}
	})
}

type deadlineSaveStore struct {
	memstore.Store
	calls int
}

func (s *deadlineSaveStore) PutCharacter(ctx context.Context, c world.Character) error {
	s.calls++
	if s.calls == 1 {
		<-ctx.Done()
		return ctx.Err()
	}
	return s.Store.PutCharacter(ctx, c)
}
func TestWorldPortCloseBudgetAndExplicitRetry(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := new(deadlineSaveStore)
		cfg, w := startPortWorld(t, cat, store)
		c, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			t.Fatal(err)
		}
		started := time.Now()
		if err := c.Close(); !errors.Is(err, context.DeadlineExceeded) {
			t.Fatalf("bounded Close: %v", err)
		}
		if elapsed := time.Since(started); elapsed != 100*time.Millisecond {
			t.Fatalf("close budget: got %v, want 100ms", elapsed)
		}
		if err := w.Leave(t.Context(), c.Owner()); err != nil {
			t.Fatal(err)
		}
		if store.calls != 2 {
			t.Fatal("unexpected background retry")
		}
	})
}
