// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"container/heap"
	"time"
)

type task struct {
	at  time.Time
	seq uint64
	run func(*state)
}

type tasks []task

func (q tasks) Len() int { return len(q) }
func (q tasks) Less(i, j int) bool {
	if !q[i].at.Equal(q[j].at) {
		return q[i].at.Before(q[j].at)
	}
	return q[i].seq < q[j].seq
}
func (q tasks) Swap(i, j int) { q[i], q[j] = q[j], q[i] }
func (q *tasks) Push(x any)   { *q = append(*q, x.(task)) }
func (q *tasks) Pop() any {
	old := *q
	t := old[len(old)-1]
	*q = old[:len(old)-1]
	return t
}

func (s *state) after(d time.Duration, f func(*state)) {
	s.afterRule(d, RuleTimer, f)
}
func (s *state) afterRule(d time.Duration, rule Rule, f func(*state)) {
	if s.seq == ^uint64(0) {
		s.err = ErrExhausted
		return
	}
	s.seq++
	heap.Push(&s.timers, task{at: time.Now().Add(d), seq: s.seq, run: func(s *state) { s.rule(rule, func() { f(s) }) }})
}

func (s *state) runDue() (time.Duration, bool) {
	for s.timers.Len() > 0 {
		next := s.timers[0]
		if wait := time.Until(next.at); wait > 0 {
			return wait, true
		}
		heap.Pop(&s.timers)
		next.run(s)
		if s.err != nil {
			return 0, false
		}
	}
	return 0, false
}
