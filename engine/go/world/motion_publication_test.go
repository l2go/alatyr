// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"testing"
	"testing/synctest"
	"time"
)

func TestSynchronousArrivalKeepsStopped(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		c := syntheticCharacter(1)
		c.X, c.Y, c.Z = 10, 20, -5
		entry := enterTest(t, w, ctx, c)
		_, box, err := w.Observe(ctx, entry.Self.Entity, 64, 4)
		if err != nil {
			t.Fatal(err)
		}
		here := Point{X: c.X, Y: c.Y, Z: c.Z}
		if err := w.MoveTo(ctx, c.ObjectID, here); err != nil {
			t.Fatal(err)
		}
		stopped, moving, following := drainMotion(box)
		if !oneStop(stopped, c.ObjectID, here) || len(moving) != 0 || len(following) != 0 {
			t.Fatalf("arrival stopped=%+v moving=%+v following=%+v", stopped, moving, following)
		}
		neighbor := syntheticCharacter(2)
		neighbor.X, neighbor.Y, neighbor.Z = here.X, here.Y, here.Z
		other := enterTest(t, w, ctx, neighbor)
		_, otherBox, err := w.Observe(ctx, other.Self.Entity, 64, 4)
		if err != nil {
			t.Fatal(err)
		}
		if err := w.MoveTo(ctx, c.ObjectID, here); err != nil {
			t.Fatal(err)
		}
		stopped, moving, following = drainMotion(otherBox)
		if !oneStop(stopped, c.ObjectID, here) || len(moving) != 0 || len(following) != 0 {
			t.Fatalf("observer arrival stopped=%+v moving=%+v following=%+v", stopped, moving, following)
		}
	})
}

func TestMovingPublishesGaitSpeed(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		_, box, err := w.Observe(ctx, entry.Self.Entity, 64, 4)
		if err != nil {
			t.Fatal(err)
		}
		runSpeed, walkSpeed := entry.Self.RunSpeed, entry.Self.WalkSpeed
		if runSpeed <= 0 || walkSpeed <= 0 || walkSpeed >= runSpeed {
			t.Fatalf("speeds run=%d walk=%d", runSpeed, walkSpeed)
		}
		if err := w.MoveTo(ctx, 1, Point{X: 400}); err != nil {
			t.Fatal(err)
		}
		_, moving, following := drainMotion(box)
		if len(moving) != 1 || moving[0].ObjectID != 1 || moving[0].From == moving[0].To || moving[0].Speed != runSpeed || len(following) != 0 {
			t.Fatalf("run moving=%+v following=%+v", moving, following)
		}
		if err := w.SetRunning(ctx, 1, false); err != nil {
			t.Fatal(err)
		}
		if err := w.MoveTo(ctx, 1, Point{X: 800}); err != nil {
			t.Fatal(err)
		}
		_, moving, _ = drainMotion(box)
		if len(moving) != 1 || moving[0].Speed != walkSpeed || moving[0].From == moving[0].To {
			t.Fatalf("walk moving=%+v, want speed %d", moving, walkSpeed)
		}
		mob := objectOf(t, w, ctx, 9101)
		trainer := objectOf(t, w, ctx, 9102)
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			for _, id := range []int32{mob, trainer} {
				n := s.npcs[id]
				if !p.known[id] && !w.reveal(s, p, id, n.pos, time.Now()) {
					t.Fatalf("player cannot see %d", id)
				}
			}
			w.moveToPawn(s, p, mob, 36)
			n := s.npcs[mob]
			if n.running {
				t.Fatal("monster spawned running")
			}
			w.aiMoveTo(s, n, Point{X: n.pos.X + 40, Y: n.pos.Y, Z: n.pos.Z})
			teacher := s.npcs[trainer]
			if !teacher.running {
				t.Fatal("trainer spawned walking")
			}
			w.aiMoveTo(s, teacher, Point{X: teacher.pos.X + 40, Y: teacher.pos.Y, Z: teacher.pos.Z})
		}); err != nil {
			t.Fatal(err)
		}
		_, moving, following = drainMotion(box)
		if len(following) != 1 || following[0].ObjectID != 1 || following[0].Speed != walkSpeed || following[0].From == following[0].To {
			t.Fatalf("follow=%+v, walk %d", following, walkSpeed)
		}
		if speed := movingSpeed(moving, mob); speed != 30 {
			t.Fatalf("monster speed=%d moving=%+v", speed, moving)
		}
		if speed := movingSpeed(moving, trainer); speed != 60 {
			t.Fatalf("trainer speed=%d moving=%+v", speed, moving)
		}
	})
}

func TestRevealedMotionCarriesSpeed(t *testing.T) {
	cat := syntheticCatalog(t)
	synctest.Test(t, func(t *testing.T) {
		w, _, ctx := startTestWorld(t, cat)
		entry := enterTest(t, w, ctx, syntheticCharacter(1))
		far := syntheticCharacter(2)
		far.X = 20000
		other := enterTest(t, w, ctx, far)
		_, box, err := w.Observe(ctx, other.Self.Entity, 64, 4)
		if err != nil {
			t.Fatal(err)
		}
		if err := w.MoveTo(ctx, 1, Point{X: 400}); err != nil {
			t.Fatal(err)
		}
		if err := w.do(ctx, func(s *state) {
			p := s.players[1]
			q := s.players[2]
			if q.known[1] {
				t.Fatal("distant player already sees the mover")
			}
			if !w.reveal(s, q, 1, p.pos, time.Now()) {
				t.Fatal("reveal")
			}
		}); err != nil {
			t.Fatal(err)
		}
		_, moving, _ := drainMotion(box)
		if len(moving) != 1 || moving[0].ObjectID != 1 || moving[0].Speed != entry.Self.RunSpeed || moving[0].From == moving[0].To || moving[0].To.X != 400 {
			t.Fatalf("revealed moving=%+v, want speed %d", moving, entry.Self.RunSpeed)
		}
	})
}

func oneStop(stopped []Stopped, id int32, at Point) bool {
	n := 0
	for _, e := range stopped {
		if e.ObjectID == id {
			n++
			if e.Pos != at {
				return false
			}
		}
	}
	return n == 1
}

func movingSpeed(moving []Moving, id int32) int32 {
	for _, e := range moving {
		if e.ObjectID == id && e.From != e.To {
			return e.Speed
		}
	}
	return -1
}

func drainMotion(box *Mailbox) (stopped []Stopped, moving []Moving, following []Following) {
	for {
		p, ok := box.Next()
		if !ok {
			return stopped, moving, following
		}
		switch e := p.Event.(type) {
		case Stopped:
			stopped = append(stopped, e)
		case Moving:
			moving = append(moving, e)
		case Following:
			following = append(following, e)
		}
	}
}
