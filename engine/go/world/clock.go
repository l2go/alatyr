// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import "time"

const (
	gameStartMinute = 23*60 + 45
	timeMulti       = 6
	gameMinute      = time.Minute / timeMulti
)

// GameTime reports the world's accelerated game-clock minute.
func (w *World) GameTime() int32 {
	elapsed := int(time.Since(w.started) / gameMinute)
	return int32((gameStartMinute + 1 + elapsed) % (24 * 60))
}

// GameClock announces a game-clock hour boundary and its time multiplier.
type GameClock struct {
	stamp
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
			s.group(nil, Fact{Property: PropertyGameClock, Before: IntValue(int64((minute + 23*60) % (24 * 60))), After: IntValue(int64(minute))})
			for _, p := range s.players {
				p.send(GameClock{Minute: minute, Multiplier: timeMulti})
			}
		}
		s.afterRule(time.Until(w.started.Add(time.Duration(k)*gameMinute)), RuleGameClock, run)
	}
	s.afterRule(0, RuleGameClock, run)
}

func (GameClock) event() {}
