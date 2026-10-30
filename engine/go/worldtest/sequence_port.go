// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package worldtest

import (
	"context"
	"slices"
	"sync"
	"testing/synctest"
	"time"

	"github.com/l2go/alatyr/engine/go/bridge"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
)

// SequencePort schedules real raw observations at explicit test barriers. It
// adds no producer goroutine and never fabricates a record or receipt. Writes,
// recovery and closure still execute on the underlying production port.
// Flush runs only on the test goroutine inside its synctest bubble.
type SequencePort struct {
	bridge.Port
	mu      sync.Mutex
	updates chan bridge.Update
	closed  bool
	trace   []bridge.Update
	writes  []sequenceWrite
	fault   byte
	held    []bridge.Update
}

// NewSequencePort wraps a real port before backend.New takes ownership of it.
func NewSequencePort(p bridge.Port) *SequencePort {
	return &SequencePort{Port: p, updates: make(chan bridge.Update, 8)}
}

// Updates exposes observations released by Flush.
func (p *SequencePort) Updates() <-chan bridge.Update { return p.updates }

// Flush releases available updates in their original order, allowing the whole
// pipeline to become quiescent between updates. A closed scope receives no tail.
func (p *SequencePort) Flush() {
	for {
		synctest.Wait()
		p.mu.Lock()
		if p.closed {
			p.mu.Unlock()
			return
		}
		var u bridge.Update
		if p.fault != 4 && len(p.held) > 0 {
			u = p.held[0]
			p.held = p.held[1:]
		} else {
			select {
			case next, ok := <-p.Port.Updates():
				if !ok {
					p.closed = true
					close(p.updates)
					p.mu.Unlock()
					return
				}
				if next.Kind == bridge.Detached || next.Kind == bridge.Closed {
					p.held = nil
				}
				u = p.disturb(next)
				if p.fault == 4 && u.Kind == bridge.LiveBatch {
					if len(p.held) < 1024 {
						p.held = append(p.held, u)
					}
					p.mu.Unlock()
					continue
				}
			default:
				p.mu.Unlock()
				return
			}
		}
		p.trace = append(p.trace, u)
		p.updates <- u
		p.mu.Unlock()
	}
}

// Close joins the underlying port and closes the scheduled stream once.
func (p *SequencePort) Close() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	err := p.Port.Close()
	if !p.closed {
		p.closed = true
		p.held = nil
		close(p.updates)
	}
	return err
}

type sequenceWrite struct {
	command bridge.Command
	at      time.Time
}

// Write records the real contact time before forwarding the command.
func (p *SequencePort) Write(ctx context.Context, c bridge.Command) (uint64, error) {
	p.mu.Lock()
	p.writes = append(p.writes, sequenceWrite{c, time.Now()})
	p.mu.Unlock()
	return p.Port.Write(ctx, c)
}

// Fault chooses observation loss: 0 live, 1 receipts only, 2 facts only,
// 3 snapshots, or 4 held delivery. Returning to 0 releases held observations.
func (p *SequencePort) Fault(mode byte) { p.mu.Lock(); p.fault = mode; p.mu.Unlock() }

func (p *SequencePort) disturb(u bridge.Update) bridge.Update {
	if u.Kind != bridge.LiveBatch || u.Batch.Snapshot {
		return u
	}
	switch p.fault {
	case 1:
		var rs []wire.Record
		var ats []time.Time
		for i, r := range u.Batch.Records {
			kind := gatewayU(r, 1)
			if r.Type == wire.Fact && (kind == 7 || kind == 9 || kind == 11 || kind == 12 || kind == 13 || kind == 14 || kind == 15 || kind == 22 || kind == 23) {
				continue
			}
			rs = append(rs, r)
			ats = append(ats, u.Batch.ReceivedAt[i])
		}
		u.Batch.Records, u.Batch.ReceivedAt = rs, ats
	case 2:
		u.Batch.Receipts = nil
	case 3:
		u.Batch.Snapshot = true
	}
	return u
}
func (p *SequencePort) audit() ([]bridge.Update, []sequenceWrite) {
	p.mu.Lock()
	defer p.mu.Unlock()
	return slices.Clone(p.trace), slices.Clone(p.writes)
}
func gatewayU(r wire.Record, n uint32) uint64 { f, _ := wire.Find(r.Fields, n); return f.U }
