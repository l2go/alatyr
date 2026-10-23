// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package gateway

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Journal accepts only numeric operational metadata. It has no API for raw
// frames, identities, text, saved actions or headers. Disk IO is off the writer.
type Journal struct {
	dir              string
	bytes, fileBytes int64
	age, cleanup     time.Duration
	queue            chan Summary
	done             chan struct{}
	once             sync.Once
	wg               sync.WaitGroup
	errors           atomic.Uint64
	now              func() time.Time
	mu               sync.Mutex
	closed           bool
	err              error
}

// JournalConfig supplies explicit directory, retention, and queue limits.
// There are no defaults; NewJournal rejects invalid limits before opening files.
type JournalConfig struct {
	// Directory is the host-owned journal directory. It is created with mode 0700
	// if absent; the caller is responsible for existing directory permissions.
	Directory string
	// Bytes is the required positive total retained journal size in bytes.
	Bytes int64
	// FileBytes is the required positive per-file size limit in bytes, at most Bytes.
	// A single encoded record larger than this limit is rejected.
	FileBytes int64
	// Age is the required positive maximum retained file age.
	Age time.Duration
	// Cleanup is the required positive interval for age-based cleanup when idle.
	Cleanup time.Duration
	// Queued is the required positive record capacity of the asynchronous queue.
	// Record rejects overflow instead of blocking the session loop.
	Queued int
}

// NewJournal starts a bounded numeric journal with byte and age retention.
// The caller owns the directory and must call Close to drain the accepted queue.
func NewJournal(c JournalConfig) (*Journal, error) {
	if c.Queued < 1 || c.Bytes < 1 || c.FileBytes < 1 || c.FileBytes > c.Bytes || c.Age <= 0 || c.Cleanup <= 0 {
		return nil, fmt.Errorf("gateway: invalid diagnostic limits")
	}
	if err := os.MkdirAll(c.Directory, 0700); err != nil {
		return nil, err
	}
	j := &Journal{dir: c.Directory, bytes: c.Bytes, fileBytes: c.FileBytes, age: c.Age, cleanup: c.Cleanup, queue: make(chan Summary, c.Queued), done: make(chan struct{}), now: time.Now}
	j.wg.Add(1)
	go j.run()
	return j, nil
}
func (j *Journal) failure() {
	for {
		v := j.errors.Load()
		if v == ^uint64(0) || j.errors.CompareAndSwap(v, v+1) {
			return
		}
	}
}

// Record enqueues numeric metadata without waiting for disk I/O.
// Queue overflow and records after Close increment Errors.
func (j *Journal) Record(s Summary) {
	j.mu.Lock()
	defer j.mu.Unlock()
	if j.closed {
		j.failure()
		return
	}
	select {
	case j.queue <- s:
	default:
		j.failure()
	}
}

// Errors returns the saturating count of rejected records and failed operations.
func (j *Journal) Errors() uint64 { return j.errors.Load() }

// Close prevents new records, drains the bounded queue, and returns the first
// disk error. It is safe to call concurrently and more than once.
func (j *Journal) Close() error {
	j.once.Do(func() {
		j.mu.Lock()
		j.closed = true
		close(j.done)
		j.mu.Unlock()
	})
	j.wg.Wait()
	return j.err
}

func (j *Journal) diskError(err error) {
	j.failure()
	if j.err == nil {
		j.err = fmt.Errorf("gateway: journal: %w", err)
	}
}

type logFile struct {
	path string
	size int64
	time time.Time
}

func (j *Journal) files() ([]logFile, int64, error) {
	entries, err := os.ReadDir(j.dir)
	if err != nil {
		return nil, 0, err
	}
	var out []logFile
	var total int64
	for _, e := range entries {
		if e.Type().IsRegular() && strings.HasPrefix(e.Name(), "events-") && strings.HasSuffix(e.Name(), ".jsonl") {
			info, err := e.Info()
			if err != nil {
				return nil, 0, err
			}
			created := info.ModTime()
			parts := strings.Split(e.Name(), "-")
			if len(parts) == 3 {
				if ns, err := strconv.ParseInt(parts[1], 10, 64); err == nil {
					created = time.Unix(0, ns)
				}
			}
			out = append(out, logFile{filepath.Join(j.dir, e.Name()), info.Size(), created})
			total += info.Size()
		}
	}
	sort.Slice(out, func(a, b int) bool { return out[a].path < out[b].path })
	return out, total, nil
}
func (j *Journal) clean(extra int64) error {
	files, total, err := j.files()
	if err != nil {
		return err
	}
	for _, f := range files {
		if j.now().Sub(f.time) >= j.age || total+extra > j.bytes {
			if err := os.Remove(f.path); err != nil {
				return err
			}
			total -= f.size
		}
	}
	return nil
}
func (j *Journal) run() {
	defer j.wg.Done()
	tick := time.NewTicker(j.cleanup)
	defer tick.Stop()
	var path string
	var size int64
	var serial uint64
	appendSummary := func(s Summary) {
		b, err := json.Marshal(s)
		if err != nil {
			j.diskError(err)
			return
		}
		b = append(b, '\n')
		if int64(len(b)) > j.fileBytes {
			j.failure()
			return
		}
		if err := j.clean(int64(len(b))); err != nil {
			j.diskError(err)
			return
		}
		if _, err := os.Stat(path); errors.Is(err, os.ErrNotExist) || path == "" {
			path = ""
			size = 0
		} else if err != nil {
			j.diskError(err)
			return
		}
		if path == "" || size+int64(len(b)) > j.fileBytes {
			serial++
			path = filepath.Join(j.dir, fmt.Sprintf("events-%020d-%08d.jsonl", j.now().UnixNano(), serial))
			size = 0
		}
		file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
		if err != nil {
			j.diskError(err)
			return
		}
		n, err := file.Write(b)
		if err == nil && n != len(b) {
			err = io.ErrShortWrite
		}
		err = errors.Join(err, file.Close())
		if err != nil {
			j.diskError(err)
			return
		}
		size += int64(n)
	}
	for {
		select {
		case s := <-j.queue:
			appendSummary(s)
		case <-tick.C:
			if err := j.clean(0); err != nil {
				j.diskError(err)
			}
		case <-j.done:
			for {
				select {
				case s := <-j.queue:
					appendSummary(s)
				default:
					return
				}
			}
		}
	}
}
