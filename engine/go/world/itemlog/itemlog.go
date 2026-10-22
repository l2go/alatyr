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

const (
	Create = "CREATE"
	Change = "CHANGE"
	Delete = "DELETE"
)

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

const Null = "null"

type Log struct {
	mu  sync.Mutex
	w   io.Writer
	now func() time.Time
}

var Discard = &Log{}

func New(w io.Writer, now func() time.Time) *Log { return &Log{w: w, now: now} }

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

func (l *Log) Write(e Entry) {
	if l == nil || l.w == nil || silent(e) {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	io.WriteString(l.w, Line(l.now(), e))
}

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
