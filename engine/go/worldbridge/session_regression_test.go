// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldbridge

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/bridge/profile"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/memstore"
)

func awaitActorFact(t *testing.T, c *Client, id int32, kind uint64) wire.Record {
	t.Helper()
	for range 100 {
		u := nextPortUpdate(t, c)
		if u.Kind != bridge.LiveBatch {
			t.Fatalf("actor update: got %v, want LiveBatch", u.Kind)
		}
		for _, r := range u.Batch.Records {
			actor, _ := wire.Find(r.Fields, 64)
			if r.Type == wire.Fact && profile.U(r, 1) == kind && actor.S == int64(id) {
				return r
			}
		}
	}
	t.Fatal("actor fact missing within bounded updates")
	return wire.Record{}
}

func TestWorldPortGaitKeepsObservedPosition(t *testing.T) {
	cat := authoredCatalog(t)
	for _, who := range []string{"self", "other_player"} {
		t.Run(who, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				cfg, w := startPortWorld(t, cat, memstore.New())
				c, _ := openPort(t, cfg)
				owner := c.Owner()
				if who == "other_player" {
					char := sessionCharacter()
					char.ObjectID, char.Name = 2, "Peer"
					entry, err := w.Enter(t.Context(), char)
					if err != nil {
						t.Fatal(err)
					}
					owner = entry.Self.Entity
					t.Cleanup(func() {
						ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
						defer cancel()
						if err := w.Leave(ctx, owner); err != nil {
							t.Error(err)
						}
					})
					awaitActorFact(t, c, owner.ID, 1)
				}
				to := world.Point{X: -71027, Y: 257883, Z: -3104}
				if err := w.MoveTo(t.Context(), owner.ID, to); err != nil {
					t.Fatal(err)
				}
				stopped := awaitActorFact(t, c, owner.ID, 3)
				scene, err := w.Capture(t.Context(), owner)
				if err != nil || scene.Self.Char.X != to.X {
					t.Fatalf("movement: got %+v/%v, want X=%d", scene.Self.Char, err, to.X)
				}
				if err := w.SetRunning(t.Context(), owner.ID, !scene.Self.Running); err != nil {
					t.Fatal(err)
				}
				gait := awaitActorFact(t, c, owner.ID, 1)
				pos, _ := wire.Find(gait.Fields, 13)
				if pos.XYZ != [3]int32{to.X, to.Y, to.Z} || profile.U(gait, 2) <= profile.U(stopped, 2) {
					t.Fatalf("gait position/sequence: got %v/%d, want %+v/after %d", pos.XYZ, profile.U(gait, 2), to, profile.U(stopped, 2))
				}
			})
		})
	}
}

func TestGaitProjectionUsesSourceDeltas(t *testing.T) {
	pos := world.Point{X: 10, Y: 20, Z: 30}
	cases := []struct {
		name  string
		event world.Event
		dead  bool
	}{
		{"moving", world.Moving{ObjectID: 1, From: pos}, false},
		{"following", world.Following{ObjectID: 1, From: pos}, false},
		{"stopped", world.Stopped{ObjectID: 1, Pos: pos}, false},
		{"position", world.PositionObserved{ObjectID: 1, Pos: pos}, false},
		{"teleport", world.Teleported{ObjectID: 1, Pos: pos}, false},
		{"target", world.TargetChosen{ObjectID: 1, Pos: pos}, false},
		{"clear_target", world.TargetCleared{ObjectID: 1, Pos: pos}, false},
		{"posture", world.PostureChanged{ObjectID: 1, Pos: pos}, false},
		{"attack", world.AttackStarted{AttackerID: 1, From: pos}, false},
		{"cast", world.SkillCast{CasterID: 1, From: pos}, false},
		{"attack_target", world.AttackStarted{AttackerID: 2, TargetID: 1, TargetPos: pos}, false},
		{"cast_target", world.SkillCast{CasterID: 2, TargetID: 1, TargetPos: pos}, false},
		{"death", world.Died{ObjectID: 1}, true},
		{"revival", world.Revived{ObjectID: 1}, false},
	}
	for _, actor := range []string{"self", "player", "npc"} {
		t.Run(actor, func(t *testing.T) {
			for _, tc := range cases {
				t.Run(tc.name, func(t *testing.T) {
					self := world.Self{Entity: world.Ref{ID: 1, Gen: 1}, Char: sessionCharacter(), WalkSpeed: 10, RunSpeed: 20, Dead: tc.name == "revival"}
					s := session{scene: world.Scene{Self: self}, tr: translator{}}
					if actor != "self" {
						s.scene.Self.Entity = world.Ref{ID: 2, Gen: 2}
						var state world.Event = world.PlayerSeen{Player: self}
						if actor == "npc" {
							state = world.NPCSeen{NPC: world.NPCView{Entity: self.Entity, ObjectID: 1, Dead: self.Dead, Template: catalog.NPCTemplate{ID: 1, WalkSpeed: 10, RunSpeed: 20}}}
						}
						s.scene.Objects = []world.Observation{{Entity: self.Entity, State: state}}
					}
					if err := s.project(tc.event); err != nil {
						t.Fatal(err)
					}
					gait := world.GaitChanged{ObjectID: 1, Running: true}
					if err := s.project(gait); err != nil {
						t.Fatal(err)
					}
					fs, err := s.tr.events(gait, s.scene, 0)
					if err != nil || len(fs) != 1 {
						t.Fatalf("gait facts: got %v/%v, want one", fs, err)
					}
					alive, _ := wire.Find(fs[0].fields, 70)
					if (alive.U == 0) != tc.dead {
						t.Fatalf("alive: got %d, want dead=%v", alive.U, tc.dead)
					}
					if tc.name != "death" && tc.name != "revival" {
						p, _ := wire.Find(fs[0].fields, 13)
						if p.XYZ != [3]int32{pos.X, pos.Y, pos.Z} {
							t.Fatalf("position: got %v, want %+v", p.XYZ, pos)
						}
					}
				})
			}
		})
	}
}

func TestWorldPortFailedOpenRetainsCleanupOwner(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		failure := errors.New("save unavailable")
		store := &failingPortStore{failure: failure}
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		cfg, w := startPortWorld(t, cat, store, world.WithCommits(func(c world.Commit) {
			if c.Intent.Verb == world.VerbEnter {
				cancel()
			}
		}))
		c, snap, err := Open(ctx, cfg, bridge.Identity{Account: "example", Character: "Walker"})
		var opening *OpenError
		if c != nil || snap.Ready || !errors.As(err, &opening) || !validRef(opening.Owner) || !errors.Is(err, failure) || !errors.Is(err, context.Canceled) || store.saveCalls != 1 {
			t.Fatalf("failed Open: got client=%v ready=%v error=%v saves=%d, want retryable cleanup error", c, snap.Ready, err, store.saveCalls)
		}
		store.failure = nil
		if err := w.Leave(t.Context(), opening.Owner); err != nil {
			t.Fatal(err)
		}
		if store.saveCalls != 2 {
			t.Fatalf("saves: got %d, want 2", store.saveCalls)
		}
		cfg.Load = func(context.Context, bridge.Identity) (world.Character, error) { return sessionCharacter(), nil }
		// The observer only cancels the old opening context, not this request.
		retry, _, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
		if err != nil {
			t.Fatal(err)
		}
		if retry.Owner() == opening.Owner {
			t.Fatal("successful reentry reused the failed incarnation")
		}
		var stale *world.RefusalError
		if err := w.Leave(t.Context(), opening.Owner); !errors.As(err, &stale) || stale.Reason != world.ReasonStale {
			t.Fatalf("old retry: got %v, want stale refusal", err)
		}
		if err := retry.Close(); err != nil {
			t.Fatal(err)
		}
	})
}

func TestOpenCleanupPreservesEveryFailure(t *testing.T) {
	cat := authoredCatalog(t)
	for _, tc := range []struct {
		name  string
		cause error
	}{
		{"subscribe", fmt.Errorf("worldbridge: subscribe: %w", context.Canceled)},
		{"checkpoint_error", errCapacity},
		{"not_ready", errSource},
		{"late_cancellation", context.Canceled},
	} {
		t.Run(tc.name, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				cause, save := tc.cause, errors.New("save unavailable")
				store := &failingPortStore{failure: save}
				cfg, w := startPortWorld(t, cat, store)
				cfg.CloseBudget = 100 * time.Millisecond
				entry, err := w.Enter(t.Context(), sessionCharacter())
				if err != nil {
					t.Fatal(err)
				}
				var opening *OpenError
				err = cleanupOpen(cfg, entry.Self.Entity, cause)
				if !errors.As(err, &opening) || opening.Owner != entry.Self.Entity || !errors.Is(err, cause) || !errors.Is(err, save) {
					t.Fatalf("cleanup: got %v, want both causes and owner %+v", err, entry.Self.Entity)
				}
				store.failure = nil
				if err := w.Leave(t.Context(), opening.Owner); err != nil {
					t.Fatal(err)
				}
			})
		})
	}
}

func TestWorldPortCanceledWriteKeepsNotificationContext(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		store := &failingPortStore{blockSkill: make(chan struct{})}
		cfg, w := startPortWorld(t, cat, store)
		c, _ := openPort(t, cfg)
		command := portLesson(t, c, w)
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()
		done := make(chan result, 1)
		go func() {
			n, err := c.Write(ctx, command)
			done <- result{n: n, err: err}
		}()
		<-store.blockSkill
		if err := w.Teleport(t.Context(), c.Owner().ID, world.Point{X: -71027, Y: 257883, Z: -3104}, false); err != nil {
			t.Fatal(err)
		}
		cancel()
		r := <-done
		if r.n != 3 || !errors.Is(r.err, context.Canceled) {
			t.Fatalf("canceled skill write: got %+v, want attempt 3/Canceled", r)
		}
		ready, receipt := false, false
		for range maxBacklogUpdates {
			u := nextPortUpdate(t, c)
			if u.Kind != bridge.LiveBatch {
				t.Fatalf("notification after cancellation: got %v, want LiveBatch", u.Kind)
			}
			for _, r := range u.Batch.Records {
				ready = ready || r.Type == wire.Checkpoint && profile.U(r, 1) == uint64(wire.CheckpointEnd) && profile.U(r, 3) == 1
			}
			for _, r := range u.Batch.Receipts {
				if r.Key == command.Key {
					receipt = true
					if r.Stage != wire.StageUnknown || r.Reason != wire.ReasonBackendError {
						t.Fatalf("skill receipt: got %+v, want unknown durability", r)
					}
				}
			}
			if ready && receipt {
				break
			}
		}
		if !ready || !receipt {
			t.Fatalf("notification trace: got ready=%v receipt=%v, want both", ready, receipt)
		}
		scene, err := w.Capture(t.Context(), c.Owner())
		if err != nil || scene.Self.Teleporting || !scene.End.Ready || store.skillCalls != 1 {
			t.Fatalf("post-cancel scene: got %+v/%v skill calls=%d, want ready and one call", scene.End, err, store.skillCalls)
		}
		if _, err := c.Write(t.Context(), portWalk(4)); err != nil {
			t.Fatal(err)
		}
		awaitReceipt(t, c, 4)
	})
}

func TestWorldPortResumeCallerDeadlineDoesNotRenewLossBudget(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		var delayed atomic.Bool
		cfg, w := startPortWorld(t, cat, memstore.New(), world.WithCommits(func(c world.Commit) {
			if c.Intent.Verb == world.VerbAppear && delayed.Load() {
				<-time.After(500 * time.Millisecond)
			}
		}))
		cfg.ResumeBudget = time.Second
		c, _ := openPort(t, cfg)
		loseObservation(t, c)
		if err := w.Teleport(t.Context(), c.Owner().ID, world.Point{X: -71027, Y: 257883, Z: -3104}, false); err != nil {
			t.Fatal(err)
		}
		delayed.Store(true)
		ctx, cancel := context.WithTimeout(t.Context(), 250*time.Millisecond)
		defer cancel()
		recovery, err := c.Resume(ctx)
		if !errors.Is(err, context.DeadlineExceeded) || recovery.Snapshot.Ready {
			t.Fatalf("caller deadline: got %+v/%v, want no recovery/DeadlineExceeded", recovery.Snapshot, err)
		}
		// The admitted Appear completed, but recovery failed. The original loss
		// deadline still expires at one second, not one second after this failure.
		<-time.After(500 * time.Millisecond)
		synctest.Wait()
		if recovery, err := c.Resume(t.Context()); err == nil || recovery.Snapshot.Ready {
			t.Fatalf("original expiry: got %+v/%v, want no recovery", recovery.Snapshot, err)
		}
		if err := c.Close(); err != nil {
			t.Fatal(err)
		}
		if u := nextPortUpdate(t, c); u.Kind != bridge.Closed || u.FailureKind != bridge.Timeout || u.GatewayEpoch != 1 {
			t.Fatalf("close: got %+v, want original epoch/Timeout", u)
		}
	})
}

func TestBacklogBoundaryAfterConsumerRead(t *testing.T) {
	for _, size := range []int{0, 4000} {
		t.Run(fmt.Sprint(size), func(t *testing.T) {
			rs, _, err := factRecords([]fact{{kind: 18, domain: "dialogue", fields: []wire.Field{wire.Data(11, true, strings.Repeat("x", size))}}}, 1, 1, 0, 0)
			if err != nil {
				t.Fatal(err)
			}
			batch := bridge.Batch{Records: rs}
			s := session{client: &Client{updates: make(chan bridge.Update, 1)}}
			if err := s.enqueue(batch); err != nil {
				t.Fatal(err)
			}
			// Transfer one real encoded update to the exposed slot, as run does.
			s.client.updates <- s.queue[0].update
			s.deliveredBytes = s.queue[0].bytes
			s.queueBytes -= s.queue[0].bytes
			s.queue = s.queue[1:]
			for range maxBacklogUpdates {
				if err := s.enqueue(batch); err != nil {
					if !errors.Is(err, errCapacity) {
						t.Fatal(err)
					}
					break
				}
			}
			count, bytes := len(s.queue)+len(s.client.updates), s.queueBytes+s.deliveredBytes
			if count > maxBacklogUpdates || bytes > maxBacklogBytes || size == 0 && count != maxBacklogUpdates {
				t.Fatalf("boundary: got %d updates/%d bytes, want <=%d/<=%d", count, bytes, maxBacklogUpdates, maxBacklogBytes)
			}
			if err := s.enqueue(batch); !errors.Is(err, errCapacity) {
				t.Fatalf("over boundary: got %v, want Capacity", err)
			}
			<-s.client.updates
			if err := s.enqueue(batch); err != nil {
				t.Fatalf("consumed slot: got %v, want capacity restored", err)
			}
			if s.deliveredBytes != 0 || len(s.queue) != count {
				t.Fatalf("read accounting: got %d delivered bytes/%d queued, want 0/%d", s.deliveredBytes, len(s.queue), count)
			}
		})
	}
}

func TestWorldPortOpenCheckpointFailureRetainsOwner(t *testing.T) {
	cat := authoredCatalog(t)
	for _, which := range []string{"capacity", "incomplete"} {
		t.Run(which, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				failure := errors.New("save unavailable")
				store := &failingPortStore{failure: failure}
				cfg, w := startPortWorld(t, cat, store)
				cfg.Load = func(context.Context, bridge.Identity) (world.Character, error) {
					char := sessionCharacter()
					if which == "capacity" {
						for i := range maxRows + 1 {
							char.Items = append(char.Items, world.Item{ObjectID: 40000 + int32(i), TemplateID: 1060, Count: 1, Slot: -1})
						}
					} else {
						char.Skills = []world.Skill{{ID: 99999, Level: 1}}
					}
					return char, nil
				}
				c, snap, err := Open(t.Context(), cfg, bridge.Identity{Account: "example", Character: "Walker"})
				cause := errSource
				if which == "capacity" {
					cause = errCapacity
				}
				var opening *OpenError
				if c != nil || snap.Ready || !errors.As(err, &opening) || !validRef(opening.Owner) || !errors.Is(err, cause) || !errors.Is(err, failure) || store.saveCalls != 1 {
					t.Fatalf("checkpoint failure: got %v ready=%v saves=%d, want retryable %v", err, snap.Ready, store.saveCalls, cause)
				}
				store.failure = nil
				if err := w.Leave(t.Context(), opening.Owner); err != nil {
					t.Fatal(err)
				}
			})
		})
	}
}

func TestWorldPortOpenRejectsLateLoad(t *testing.T) {
	cat := authoredCatalog(t)
	for _, which := range []string{"canceled", "deadline"} {
		t.Run(which, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				store := new(failingPortStore)
				var entered atomic.Int32
				cfg, w := startPortWorld(t, cat, store, world.WithCommits(func(c world.Commit) {
					if c.Intent.Verb == world.VerbEnter {
						entered.Add(1)
					}
				}))
				ctx, cancel := context.WithTimeout(t.Context(), time.Second)
				defer cancel()
				cfg.Load = func(context.Context, bridge.Identity) (world.Character, error) {
					if which == "canceled" {
						cancel()
					} else {
						// A bounded but late host resolver cannot admit a character
						// after the original request's authority expires.
						<-time.After(2 * time.Second)
					}
					return sessionCharacter(), nil
				}
				c, _, err := Open(ctx, cfg, bridge.Identity{Account: "example", Character: "Walker"})
				cause := context.Canceled
				if which == "deadline" {
					cause = context.DeadlineExceeded
				}
				if c != nil || !errors.Is(err, cause) || entered.Load() != 0 || store.saveCalls != 0 {
					t.Fatalf("late Load: got %v entries=%d saves=%d, want %v without entry", err, entered.Load(), store.saveCalls, cause)
				}
				if _, err := w.Capture(t.Context(), world.Ref{ID: 1, Gen: 1}); !errors.Is(err, world.ErrNotPresent) {
					t.Fatalf("late Load left character: %v", err)
				}
			})
		})
	}
}

func TestWorldPortResumeCompletionHonorsOriginalExpiry(t *testing.T) {
	cat := authoredCatalog(t)
	for _, delay := range []time.Duration{500 * time.Millisecond, time.Second, 2 * time.Second} {
		t.Run(delay.String(), func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var delayed atomic.Bool
				cfg, w := startPortWorld(t, cat, memstore.New(), world.WithCommits(func(c world.Commit) {
					if c.Intent.Verb == world.VerbAppear && delayed.Load() {
						<-time.After(delay)
					}
				}))
				cfg.ResumeBudget = time.Second
				c, _ := openPort(t, cfg)
				loseObservation(t, c)
				if _, err := w.Execute(t.Context(), world.Request{Owner: c.Owner(), Verb: world.VerbTeleport, Position: world.Point{X: -71027, Y: 257883, Z: -3104}}); err != nil {
					t.Fatal(err)
				}
				delayed.Store(true)
				recovery, err := c.Resume(t.Context())
				if delay < cfg.ResumeBudget {
					if err != nil || !recovery.Snapshot.Ready || recovery.Snapshot.GatewayEpoch != 2 {
						t.Fatalf("in-budget Resume: got %+v/%v, want ready epoch 2", recovery.Snapshot, err)
					}
					return
				}
				if err == nil || recovery.Snapshot.Ready || recovery.Snapshot.GatewayEpoch != 0 {
					t.Fatalf("expired Resume: got %+v/%v, want no recovery", recovery.Snapshot, err)
				}
				if _, err := c.Resume(t.Context()); err == nil {
					t.Fatal("expired Resume renewed authority")
				}
				if err := c.Close(); err != nil {
					t.Fatal(err)
				}
				if u := nextPortUpdate(t, c); u.Kind != bridge.Closed || u.FailureKind != bridge.Timeout || u.GatewayEpoch != 1 {
					t.Fatalf("expired close: got %+v, want Timeout in epoch 1", u)
				}
			})
		})
	}
}

func TestWorldPortBacklogCountsUnreadUpdate(t *testing.T) {
	cat := authoredCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		cfg, w := startPortWorld(t, cat, memstore.New())
		c, _ := openPort(t, cfg)
		if _, err := c.Write(t.Context(), portWalk(1)); err != nil {
			t.Fatal(err)
		}
		synctest.Wait()
		scene, err := w.Capture(t.Context(), c.Owner())
		if err != nil {
			t.Fatal(err)
		}
		running := scene.Self.Running
		reached := false
		for range maxBacklogUpdates + 2 {
			r := c.submit(t.Context(), func(ctx context.Context, s *session) result {
				if !s.detached.IsZero() {
					return result{err: errCapacity}
				}
				err := s.drain(ctx)
				count := len(s.queue) + len(c.updates)
				if count > maxBacklogUpdates {
					t.Errorf("pending updates: got %d, want <=%d", count, maxBacklogUpdates)
				}
				return result{n: uint64(count), err: err}
			})
			if r.err != nil {
				if !reached {
					t.Fatal("detached before reaching exact update limit")
				}
				if u := nextPortUpdate(t, c); u.Kind != bridge.Detached || u.FailureKind != bridge.Capacity {
					t.Fatalf("overflow: got %+v, want Capacity detach", u)
				}
				return
			}
			reached = reached || r.n == maxBacklogUpdates
			running = !running
			if err := w.SetRunning(t.Context(), c.Owner().ID, running); err != nil {
				t.Fatal(err)
			}
		}
		t.Fatal("overflow not detected at exact limit")
	})
}
