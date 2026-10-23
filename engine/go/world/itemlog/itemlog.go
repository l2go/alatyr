// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package itemlog

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"
)

// Journal operations.
const (
	Create = "CREATE" // an item instance was created
	Change = "CHANGE" // an item's count changed
	Delete = "DELETE" // an item instance was removed
)

// Entry is one journal record. Op is Create, Change or Delete; Process names
// the gameplay process, such as Loot, Pickup or Decay. Player and Ref name the
// owner and the reference, or Null. EtcType selects silent consumables and is
// not written.
type Entry struct {
	Op, Process string
	ObjectID    int32
	Item        string
	Enchant     int32
	Count       int64
	Player      string
	Ref         string
	EtcType     string
}

// Null marks an absent player or reference.
const Null = "null"

// Log writes entries as journal lines and serializes concurrent writes.
type Log struct {
	mu  sync.Mutex
	w   io.Writer
	now func() time.Time
}

// Discard is a Log that records nothing.
var Discard = &Log{}

// New returns a Log that writes to w and stamps lines with now. If w is not
// nil, now must not be nil. A Log with a nil writer records nothing.
func New(w io.Writer, now func() time.Time) *Log { return &Log{w: w, now: now} }

// Open creates path's directory if needed, opens path for appending and
// returns a Log over it. The caller closes the returned file.
func Open(path string) (*Log, *os.File, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return nil, nil, err
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return nil, nil, err
	}
	return New(f, time.Now), f, nil
}

func silent(e Entry) bool {
	switch e.EtcType {
	case "shot", "arrow", "bolt", "herb":
		return true
	}
	return e.Process == "Consume"
}

// Write appends one line for e. Consumables (shots, arrows, bolts, herbs) and
// the Consume process are skipped, and a nil Log records nothing. The journal
// is diagnostic: a write error is dropped so that it cannot stop gameplay.
func (l *Log) Write(e Entry) {
	if l == nil || l.w == nil || silent(e) {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	_, _ = io.WriteString(l.w, Line(l.now(), e))
}

// Line formats e as one CRLF-terminated journal line at t in UTC, with
// millisecond precision. A positive enchant level prefixes the item name.
func Line(t time.Time, e Entry) string {
	t = t.UTC()
	item := e.Item
	if e.Enchant > 0 {
		item = "+" + strconv.Itoa(int(e.Enchant)) + " " + item
	}
	return fmt.Sprintf("%02d %s %02d:%02d:%02d,%03d [%s:%s, item %d: %s(%d), player %s, %s]\r\n",
		t.Day(), t.Month().String()[:3], t.Hour(), t.Minute(), t.Second(), t.Nanosecond()/1e6,
		e.Op, e.Process, e.ObjectID, item, e.Count, e.Player, e.Ref)
}
