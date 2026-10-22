// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import "time"

const (
	gameStartMinute = 23*60 + 45
	timeMulti       = 6
	gameMinute      = time.Minute / timeMulti
)

func (w *World) GameTime() int32 {
	elapsed := int(time.Since(w.started) / gameMinute)
	return int32((gameStartMinute + 1 + elapsed) % (24 * 60))
}

type GameClock struct {
	Minute     int32
	Multiplier int32
}

func (w *World) startGameClock(s *state) {
	var k int
	var run func(*state)
	run = func(s *state) {
		k++
		minute := int32((gameStartMinute + k) % (24 * 60))
		if minute%60 == 0 {
			for _, p := range s.players {
				p.send(GameClock{Minute: minute, Multiplier: timeMulti})
			}
		}
		s.after(time.Until(w.started.Add(time.Duration(k)*gameMinute)), run)
	}
	s.after(0, run)
}

func (GameClock) event() {}
