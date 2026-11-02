// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package main

import (
	"context"
	"crypto/sha256"
	"encoding/csv"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net"
	"net/http"
	"os"
	"path/filepath"
	goruntime "runtime"
	"runtime/pprof"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/l2go/alatyr/engine/go/gateway"
	"github.com/l2go/alatyr/engine/go/gateway/meaning"
	"github.com/l2go/alatyr/engine/go/protocol/wire"
	"github.com/l2go/alatyr/engine/go/server/internal/migrate"
	"github.com/l2go/alatyr/engine/go/server/internal/pgstore"
	"github.com/l2go/alatyr/engine/go/server/internal/pgtest"
	"github.com/l2go/alatyr/engine/go/world"
	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/worldtest"
)

// Ordinary package tests run a short soak when PostgreSQL is configured, so the
// driver cannot rot. The manual soak workflow sets the long duration. After
// warmup, heap must stay within 64 MiB or 1.5× the post-warmup baseline,
// whichever is larger, and goroutines within 32 of that baseline. Once the
// window is at least ten minutes, heap may also rise at most 1 MiB per minute
// and goroutines at most half a goroutine per minute. A shorter window does
// not use slope: allocator noise over a minute is steeper than a real leak
// averaged over an hour. The absolute cap still rejects a large step.
const (
	soakShort      = 90 * time.Second
	soakHeapSlope  = 1 << 20
	soakHeapSlack  = 64 << 20
	soakGSlope     = 0.5
	soakGSlack     = 32
	soakQueueLimit = 4096
)

func TestTransientAdmissionIsRetried(t *testing.T) {
	if !transientAdmission(io.EOF) || !transientAdmission(gateway.ErrFull) {
		t.Fatal("disconnect while the previous game is closing must be retried")
	}
	if transientAdmission(errors.New("decode")) {
		t.Fatal("a decode failure was treated as a reconnect race")
	}
}

func transientAdmission(err error) bool {
	return errors.Is(err, io.EOF) || errors.Is(err, gateway.ErrFull)
}

func TestSoakVerdict(t *testing.T) {
	base := time.Now()
	flat := make([]soakSample, 6)
	for i := range flat {
		flat[i] = soakSample{at: base.Add(time.Duration(i) * time.Minute), heap: 40 << 20, rss: 80 << 20, goroutines: 40, queue: 3, tick: time.Millisecond}
	}
	if err := judgeSoak(flat); err != nil {
		t.Fatal(err)
	}
	leaky := make([]soakSample, 16)
	for i := range leaky {
		leaky[i] = soakSample{at: base.Add(time.Duration(i) * time.Minute), heap: uint64(40<<20) + uint64(i)<<20, rss: 80 << 20, goroutines: 40 + i, queue: 3, tick: time.Millisecond}
	}
	if err := judgeSoak(leaky); err == nil {
		t.Fatal("sustained growth was accepted")
	}
	noisy := make([]soakSample, 4)
	for i := range noisy {
		noisy[i] = soakSample{at: base.Add(time.Duration(i) * 15 * time.Second), heap: uint64(8<<20) + uint64(i)<<20, goroutines: 30, queue: 3, tick: time.Millisecond}
	}
	if err := judgeSoak(noisy); err != nil {
		t.Fatal(err)
	}
	if err := judgeSoak(flat[:2]); err == nil {
		t.Fatal("too few samples were accepted")
	}
}

func TestSoak(t *testing.T) {
	dsn := pgtest.Database(t)
	duration := soakShort
	if raw := os.Getenv("SOAK_DURATION"); raw != "" {
		parsed, err := time.ParseDuration(raw)
		if err != nil || parsed < time.Second {
			t.Fatalf("SOAK_DURATION: %v", err)
		}
		duration = parsed
	}
	bots := 2
	if duration >= 10*time.Minute {
		bots = 8
	}
	if raw := os.Getenv("SOAK_BOTS"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil || n < 1 || n > 64 {
			t.Fatalf("SOAK_BOTS: %q", raw)
		}
		bots = n
	}
	seed := int64(1)
	if raw := os.Getenv("SOAK_SEED"); raw != "" {
		n, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			t.Fatal(err)
		}
		seed = n
	}
	dir := os.Getenv("SOAK_ARTIFACTS")
	if dir == "" {
		dir = t.TempDir()
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	store, err := pgstore.Open(ctx, dsn)
	if err == nil {
		err = migrate.Apply(ctx, dsn)
	}
	cancel()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := store.Close(); err != nil {
			t.Error(err)
		}
	})
	root, err := filepath.Abs("../../../../../world")
	if err != nil {
		t.Fatal(err)
	}
	cat, err := catalog.Load(root)
	if err != nil {
		t.Fatal(err)
	}
	participants, tokens := soakParticipants(bots, seed)
	for i, p := range participants {
		header := byte(1)
		if i == 0 {
			header = 3
		}
		c := worldtest.SequenceCharacter([]byte{header})
		c.ObjectID = int32(i + 1)
		c.Account, c.Name = p.Account, p.Character
		c.X += int32(i * 40)
		c.Items[0].ObjectID = int32(1000 + i*10)
		c.Items[1].ObjectID = int32(1001 + i*10)
		c.Items[2].ObjectID = int32(1002 + i*10)
		if err := store.PutCharacter(t.Context(), c); err != nil {
			t.Fatal(err)
		}
	}
	cfg := Config{WorldPath: root, WorldName: "authored", ShutdownSeconds: 5, DatabaseSeconds: 3, Participants: participants}
	httpAddr, err := soakListen()
	if err != nil {
		t.Fatal(err)
	}
	streamAddr, err := soakListen()
	if err != nil {
		t.Fatal(err)
	}
	cfg.HTTPAddress, cfg.StreamAddress = httpAddr, streamAddr

	var commits []world.Commit
	var commitMu sync.Mutex
	var paceTick atomic.Int64
	var paceQueued atomic.Int64
	var paceSeen atomic.Bool
	rootCtx, stopRoot := context.WithCancel(context.Background())
	t.Cleanup(stopRoot)
	started := time.Now()
	var logFailures atomic.Int64
	var logged []string
	var logMu sync.Mutex
	hostLog := slog.New(soakLog{slog.NewTextHandler(t.Output(), nil), &logFailures, func(msg string) {
		logMu.Lock()
		logged = append(logged, msg)
		logMu.Unlock()
		t.Logf("host log %s: %s", time.Since(started).Round(time.Millisecond), msg)
	}})
	runHost := func(life context.Context) error {
		return serve(life, cfg, cat, store, hostLog, world.WithCommits(func(c world.Commit) {
			commitMu.Lock()
			commits = append(commits, c)
			commitMu.Unlock()
		}), world.WithPace(func(tick time.Duration, queued int) {
			paceTick.Store(int64(tick))
			paceQueued.Store(int64(queued))
			paceSeen.Store(true)
		}))
	}
	life, stopHost := context.WithCancel(rootCtx)
	hostDone := make(chan error, 1)
	go func() { hostDone <- runHost(life) }()
	soakWaitReady(t, cfg.HTTPAddress)

	var failMu sync.Mutex
	var failures []string
	note := func(format string, args ...any) {
		msg := fmt.Sprintf("%s %s", time.Since(started).Round(time.Millisecond), fmt.Sprintf(format, args...))
		t.Log(msg)
		failMu.Lock()
		if len(failures) < 8 {
			failures = append(failures, msg)
		}
		failMu.Unlock()
	}
	cohort := startSoakBots(t, cfg, tokens, seed, note, false, duration >= 10*time.Minute)
	samples := soakObserve(t, duration, cohort, &commitMu, &commits, note, &paceTick, &paceQueued, &paceSeen)
	cohort.stop()
	stopHost()
	if err := <-hostDone; err != nil {
		note("host stop: %v", err)
	}
	saved := map[string]world.Character{}
	for _, p := range participants {
		c, err := store.Character(t.Context(), p.Account, p.Character)
		if err != nil {
			note("read %s: %v", p.Character, err)
			continue
		}
		if err := worldtest.CheckCharacter(c); err != nil {
			note("character %s: %v", p.Character, err)
		}
		saved[p.ID] = c
	}
	life, stopHost = context.WithCancel(rootCtx)
	go func() { hostDone <- runHost(life) }()
	soakWaitReady(t, cfg.HTTPAddress)
	restored := startSoakBots(t, cfg, tokens, seed, note, true, false)
	deadline := time.Now().Add(10 * time.Second)
	for restored.readyCount() < len(participants) && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
	}
	if restored.readyCount() != len(participants) {
		note("restart restored %d of %d participants", restored.readyCount(), len(participants))
	}
	restored.stop()
	stopHost()
	if err := <-hostDone; err != nil {
		note("restart stop: %v", err)
	}
	for _, b := range restored.bots {
		seen, ok := b.view()
		want := saved[b.id]
		if !ok {
			note("participant %s saw no snapshot after restart", b.id)
			continue
		}
		if seen.x != want.X || seen.y != want.Y {
			note("participant %s position got %d,%d want %d,%d", b.id, seen.x, seen.y, want.X, want.Y)
		}
		if seen.hp != int64(math.Round(want.CurHP)) || seen.mp != int64(math.Round(want.CurMP)) {
			note("participant %s vitals got %d/%d want %d/%d", b.id, seen.hp, seen.mp, int64(math.Round(want.CurHP)), int64(math.Round(want.CurMP)))
		}
		if seen.coin != itemCount(want, 57) {
			note("participant %s coins got %d want %d", b.id, seen.coin, itemCount(want, 57))
		}
		if !sameItems(seen.items, want.Items) {
			note("participant %s inventory got %v", b.id, seen.items)
		}
	}
	if n := logFailures.Load(); n > 0 {
		logMu.Lock()
		first := ""
		if len(logged) > 0 {
			first = logged[0]
		}
		logMu.Unlock()
		note("logged errors: %d, first %q", n, first)
	}
	unknown := 0
	for _, b := range cohort.bots {
		unknown += int(b.unknown.Load())
	}
	if unknown != 0 {
		note("unknown outcomes: %d", unknown)
	}
	if err := cohort.classesHeld(); err != nil {
		note("alphabet: %v", err)
	}
	failMu.Lock()
	failCopy := append([]string(nil), failures...)
	failMu.Unlock()
	writeSoakArtifacts(t, dir, samples, failCopy, unknown, cohort.classText())
	if len(failCopy) > 0 {
		writeGoroutineProfile(dir)
		t.Fatal(failCopy[0])
	}
	if err := judgeSoak(samples); err != nil {
		writeGoroutineProfile(dir)
		t.Fatal(err)
	}
}

type soakSample struct {
	at                          time.Time
	heap, rss                   uint64
	goroutines, sessions, queue int
	tick                        time.Duration
	inflight                    int
}

func soakObserve(t *testing.T, duration time.Duration, bots *soakCohort, mu *sync.Mutex, commits *[]world.Commit, note func(string, ...any), paceTick, paceQueued *atomic.Int64, paceSeen *atomic.Bool) []soakSample {
	t.Helper()
	interval := time.Minute
	if duration < 10*time.Minute {
		interval = max(duration/5, time.Second)
	}
	var samples []soakSample
	start := time.Now()
	take := func() {
		var ms goruntime.MemStats
		goruntime.ReadMemStats(&ms)
		if !paceSeen.Load() {
			note("world pace unavailable")
		}
		tick, queued := time.Duration(paceTick.Load()), int(paceQueued.Load())
		// timers counts scheduled world timers, including those not yet due.
		// The authored meadow plus a handful of participants stays far below
		// 4096; a climb past that is a leaked schedule, not a command backlog.
		if queued > soakQueueLimit {
			note("world timers %d", queued)
		}
		mu.Lock()
		err := worldtest.CheckEconomy(append([]world.Commit(nil), *commits...))
		mu.Unlock()
		if err != nil {
			note("economy: %v", err)
		}
		samples = append(samples, soakSample{at: time.Now(), heap: ms.HeapInuse, rss: processRSS(), goroutines: goruntime.NumGoroutine(), sessions: bots.sessions(), queue: queued, tick: tick, inflight: bots.inflight()})
	}
	take()
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	end := time.NewTimer(duration)
	defer end.Stop()
	for {
		select {
		case <-ticker.C:
			take()
		case <-end.C:
			take()
			if time.Since(start) < duration/2 {
				note("soak ended early")
			}
			return samples
		}
	}
}

func judgeSoak(samples []soakSample) error {
	if len(samples) < 4 {
		return fmt.Errorf("soak recorded %d samples, want at least 4", len(samples))
	}
	warm := len(samples) / 10
	if warm < 1 {
		warm = 1
	}
	body := samples[warm:]
	if len(body) < 3 {
		return fmt.Errorf("soak left %d samples after warmup", len(body))
	}
	base := body[0]
	last := body[len(body)-1]
	minutes := last.at.Sub(base.at).Minutes()
	if minutes <= 0 {
		return errors.New("soak samples share a timestamp")
	}
	xs := make([]float64, len(body))
	heaps := make([]float64, len(body))
	gs := make([]float64, len(body))
	for i, s := range body {
		xs[i] = s.at.Sub(base.at).Minutes()
		heaps[i] = float64(s.heap)
		gs[i] = float64(s.goroutines)
	}
	heapSlope := regression(xs, heaps)
	gSlope := regression(xs, gs)
	heapLimit := max(base.heap*3/2, base.heap+soakHeapSlack)
	// A few minutes of allocator noise looks like a steep slope. Slope is a
	// sustained-growth test, so it applies only once the window is long enough
	// for a leak to separate from startup. The absolute cap always applies.
	maxHeap, maxG := base.heap, base.goroutines
	maxRSS := base.rss
	for _, s := range body {
		maxHeap = max(maxHeap, s.heap)
		maxG = max(maxG, s.goroutines)
		maxRSS = max(maxRSS, s.rss)
	}
	long := minutes >= 10
	if long && heapSlope > soakHeapSlope || maxHeap > heapLimit {
		return fmt.Errorf("heap growth: slope %.0f bytes/min, peak %d, limit %d", heapSlope, maxHeap, heapLimit)
	}
	if long && gSlope > soakGSlope || maxG > base.goroutines+soakGSlack {
		return fmt.Errorf("goroutine growth: slope %.2f/min, peak %d, baseline %d", gSlope, maxG, base.goroutines)
	}
	if base.rss > 0 && maxRSS > 0 {
		rssSlope := regression(xs, floatSamples(body, func(s soakSample) float64 { return float64(s.rss) }))
		rssLimit := max(base.rss*3/2, base.rss+soakHeapSlack)
		if long && rssSlope > float64(soakHeapSlope) || maxRSS > rssLimit {
			return fmt.Errorf("rss growth: slope %.0f bytes/min, peak %d, limit %d", rssSlope, maxRSS, rssLimit)
		}
	}
	return nil
}

func regression(x, y []float64) float64 {
	var sx, sy float64
	for i := range x {
		sx += x[i]
		sy += y[i]
	}
	n := float64(len(x))
	var num, den float64
	for i := range x {
		dx := x[i] - sx/n
		num += dx * (y[i] - sy/n)
		den += dx * dx
	}
	if den == 0 {
		return 0
	}
	return num / den
}

func floatSamples(samples []soakSample, f func(soakSample) float64) []float64 {
	out := make([]float64, len(samples))
	for i, s := range samples {
		out[i] = f(s)
	}
	return out
}

func writeSoakArtifacts(t *testing.T, dir string, samples []soakSample, failures []string, unknown int, classes string) {
	t.Helper()
	f, err := os.Create(filepath.Join(dir, "soak.csv"))
	if err != nil {
		t.Error(err)
		return
	}
	defer f.Close()
	w := csv.NewWriter(f)
	if err := w.Write([]string{"unix_ms", "heap_inuse", "rss", "goroutines", "sessions", "timers", "tick_ns", "inflight"}); err != nil {
		t.Error(err)
	}
	for _, s := range samples {
		row := []string{
			strconv.FormatInt(s.at.UnixMilli(), 10),
			strconv.FormatUint(s.heap, 10),
			strconv.FormatUint(s.rss, 10),
			strconv.Itoa(s.goroutines),
			strconv.Itoa(s.sessions),
			strconv.Itoa(s.queue),
			strconv.FormatInt(int64(s.tick), 10),
			strconv.Itoa(s.inflight),
		}
		if err := w.Write(row); err != nil {
			t.Error(err)
		}
	}
	w.Flush()
	summary := fmt.Sprintf("samples %d\nunknown %d\n%sfailures %d\n", len(samples), unknown, classes, len(failures))
	for _, msg := range failures {
		summary += msg + "\n"
	}
	if len(samples) > 0 {
		last := samples[len(samples)-1]
		summary += fmt.Sprintf("last heap %d rss %d goroutines %d sessions %d timers %d tick %s inflight %d\n", last.heap, last.rss, last.goroutines, last.sessions, last.queue, last.tick, last.inflight)
	}
	if err := os.WriteFile(filepath.Join(dir, "soak.txt"), []byte(summary), 0o644); err != nil {
		t.Error(err)
	}
}

func writeGoroutineProfile(dir string) {
	f, err := os.Create(filepath.Join(dir, "goroutine.pprof"))
	if err != nil {
		return
	}
	defer f.Close()
	_ = pprof.Lookup("goroutine").WriteTo(f, 2)
}

func processRSS() uint64 {
	b, err := os.ReadFile("/proc/self/statm")
	if err != nil {
		return 0
	}
	fields := strings.Fields(string(b))
	if len(fields) < 2 {
		return 0
	}
	pages, err := strconv.ParseUint(fields[1], 10, 64)
	if err != nil {
		return 0
	}
	return pages * uint64(os.Getpagesize())
}

func soakListen() (string, error) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return "", err
	}
	addr := l.Addr().String()
	return addr, l.Close()
}

func soakWaitReady(t *testing.T, httpAddr string) {
	t.Helper()
	deadline := time.Now().Add(20 * time.Second)
	client := &http.Client{Timeout: 200 * time.Millisecond}
	for time.Now().Before(deadline) {
		resp, err := client.Get("http://" + httpAddr + "/readyz")
		if err == nil {
			resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				return
			}
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("host did not become ready")
}

func soakParticipants(n int, seed int64) ([]Participant, []string) {
	out := make([]Participant, n)
	tokens := make([]string, n)
	for i := 0; i < n; i++ {
		raw := sha256.Sum256([]byte(fmt.Sprintf("soak-%d-%d", seed, i)))
		token := hex.EncodeToString(raw[:])
		sum := sha256.Sum256([]byte(token))
		out[i] = Participant{ID: fmt.Sprintf("p%d", i), Account: fmt.Sprintf("acct%d", i), Character: fmt.Sprintf("Walker%d", i), TokenSHA256: hex.EncodeToString(sum[:])}
		tokens[i] = token
	}
	return out, tokens
}

type soakLog struct {
	next  slog.Handler
	count *atomic.Int64
	line  func(string)
}

func (s soakLog) Enabled(ctx context.Context, level slog.Level) bool { return true }
func (s soakLog) Handle(ctx context.Context, r slog.Record) error {
	msg := r.Level.String() + " " + r.Message
	if s.line != nil {
		s.line(msg)
	}
	if r.Level >= slog.LevelError {
		s.count.Add(1)
	}
	if s.next == nil {
		return nil
	}
	return s.next.Handle(ctx, r)
}
func (s soakLog) WithAttrs(a []slog.Attr) slog.Handler {
	next := s.next
	if next != nil {
		next = next.WithAttrs(a)
	}
	return soakLog{next, s.count, s.line}
}
func (s soakLog) WithGroup(name string) slog.Handler {
	next := s.next
	if next != nil {
		next = next.WithGroup(name)
	}
	return soakLog{next, s.count, s.line}
}

type soakCohort struct {
	bots []*soakBot
	long bool
}

func (c *soakCohort) sessions() int {
	n := 0
	for _, b := range c.bots {
		if b.open.Load() {
			n++
		}
	}
	return n
}
func (c *soakCohort) inflight() int {
	n := 0
	for _, b := range c.bots {
		n += b.inflight()
	}
	return n
}
func (c *soakCohort) readyCount() int {
	n := 0
	for _, b := range c.bots {
		if b.ready.Load() {
			n++
		}
	}
	return n
}
func (c *soakCohort) stop() {
	for _, b := range c.bots {
		b.stop()
	}
	for _, b := range c.bots {
		b.wg.Wait()
	}
}
func (c *soakCohort) classesHeld() error {
	totals := map[string]int{}
	for _, b := range c.bots {
		b.mu.Lock()
		for k, n := range b.accepted {
			totals[k] += n
		}
		b.mu.Unlock()
	}
	required := []string{"move", "combat", "dialogue", "use"}
	// Shop, return and a drop need world state the short window does not wait for.
	if c.long {
		required = append(required, "pickup", "shop", "return")
	}
	for _, class := range required {
		if totals[class] == 0 {
			return fmt.Errorf("class %s had no admitted intent", class)
		}
	}
	return nil
}
func (c *soakCohort) classText() string {
	totals := map[string]int{}
	for _, b := range c.bots {
		b.mu.Lock()
		for k, n := range b.accepted {
			totals[k] += n
		}
		b.mu.Unlock()
	}
	return fmt.Sprintf("classes move=%d combat=%d pickup=%d dialogue=%d shop=%d use=%d return=%d\n", totals["move"], totals["combat"], totals["pickup"], totals["dialogue"], totals["shop"], totals["use"], totals["return"])
}
func startSoakBots(t *testing.T, cfg Config, tokens []string, seed int64, note func(string, ...any), passive, long bool) *soakCohort {
	t.Helper()
	c := &soakCohort{long: long}
	for i, token := range tokens {
		b := &soakBot{addr: cfg.StreamAddress, token: token, id: cfg.Participants[i].ID, seed: seed + int64(i), note: note, stopCh: make(chan struct{}), accepted: map[string]int{}, goods: map[int32]int64{}}
		b.passive.Store(passive)
		c.bots = append(c.bots, b)
		b.wg.Add(1)
		go func() {
			defer b.wg.Done()
			b.loop()
		}()
	}
	return c
}

type liveObj struct {
	ref  meaning.Ref
	role string
}

type soakBot struct {
	addr     string
	token    string
	id       string
	seed     int64
	note     func(string, ...any)
	stopCh   chan struct{}
	open     atomic.Bool
	ready    atomic.Bool
	passive  atomic.Bool
	unknown  atomic.Int64
	wg       sync.WaitGroup
	mu       sync.Mutex
	control  uint64
	selfX    int32
	selfY    int32
	hp, mp   int64
	vitals   bool
	coin     int64
	coinOK   bool
	dead     bool
	live     map[int32]liveObj
	dialog   meaning.DecodedEvent
	shop     meaning.DecodedEvent
	offer    meaning.DecodedEvent
	practice meaning.DecodedEvent
	items    []int64
	goods    map[int32]int64
	pending  map[uint64]string
	accepted map[string]int
	seq      uint64
	closed   bool
	needPong bool
	pong     uint64
}

func (b *soakBot) inflight() int {
	b.mu.Lock()
	defer b.mu.Unlock()
	return len(b.pending)
}

func (b *soakBot) stop() {
	b.mu.Lock()
	if !b.closed {
		close(b.stopCh)
		b.closed = true
	}
	b.mu.Unlock()
}

type soakView struct {
	x, y   int32
	hp, mp int64
	coin   int64
	items  map[int32]int64
	ok     bool
}

func (b *soakBot) view() (soakView, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	items := map[int32]int64{}
	for id, n := range b.goods {
		items[id] = n
	}
	v := soakView{x: b.selfX, y: b.selfY, hp: b.hp, mp: b.mp, coin: b.coin, items: items, ok: b.ready.Load() && b.vitals && b.coinOK}
	return v, v.ok
}

func (b *soakBot) loop() {
	codec, err := meaning.NewCodec(limits(1))
	if err != nil {
		b.note("%s codec: %v", b.id, err)
		return
	}
	rng := b.seed
	next := func() uint64 {
		rng = rng*6364136223846793005 + 1
		return uint64(rng)
	}
	reconnect := time.NewTimer(20 * time.Second)
	defer reconnect.Stop()
	failures := 0
	for {
		select {
		case <-b.stopCh:
			return
		default:
		}
		err := b.session(codec, next, reconnect)
		if err == nil {
			failures = 0
			continue
		}
		select {
		case <-b.stopCh:
			return
		default:
		}
		// A normal hangup detaches the backend. The next bind can meet ErrFull
		// until that game finishes, and the host then closes the socket.
		if transientAdmission(err) {
			failures++
			if failures > 100 {
				b.note("%s session: %v after %d retries", b.id, err, failures)
				return
			}
			select {
			case <-b.stopCh:
				return
			case <-time.After(20 * time.Millisecond):
			}
			continue
		}
		b.note("%s session: %v", b.id, err)
		return
	}
}

func (b *soakBot) session(codec *meaning.Codec, next func() uint64, reconnect *time.Timer) error {
	conn, err := net.DialTimeout("tcp", b.addr, 2*time.Second)
	if err != nil {
		return err
	}
	stream := &gateway.Stream{Conn: conn, Limit: 8192}
	sessionDone := make(chan struct{})
	readerDone := make(chan struct{})
	defer func() {
		close(sessionDone)
		conn.Close()
		<-readerDone
	}()
	if _, err := conn.Write([]byte(b.token + "\n")); err != nil {
		return err
	}
	b.open.Store(true)
	defer b.open.Store(false)
	hello := wire.Record{Type: wire.Hello, Fields: []wire.Field{wire.U(1, true, 1), wire.U(2, true, 1), wire.Data(3, true, meaning.Meaning), wire.U(4, true, uint64(wire.ParticipantPerson)), wire.U(5, true, uint64(wire.ModeAct))}}
	if err := writeFrame(codec, stream, hello); err != nil {
		return err
	}
	b.mu.Lock()
	b.pending = map[uint64]string{}
	b.live = map[int32]liveObj{}
	b.ready.Store(false)
	b.mu.Unlock()
	frames := make(chan []wire.Record, 32)
	readErr := make(chan error, 1)
	go func() {
		defer close(readerDone)
		for {
			rs, err := readFrame(codec, stream, time.Now().Add(time.Minute))
			if err != nil {
				select {
				case readErr <- err:
				case <-sessionDone:
				}
				return
			}
			select {
			case frames <- rs:
			case <-sessionDone:
				return
			}
		}
	}()
	act := time.NewTicker(400 * time.Millisecond)
	defer act.Stop()
	for {
		select {
		case <-b.stopCh:
			return nil
		case <-reconnect.C:
			if b.passive.Load() {
				continue
			}
			reconnect.Reset(20 * time.Second)
			return nil
		case err := <-readErr:
			select {
			case <-b.stopCh:
				return nil
			default:
				return err
			}
		case rs := <-frames:
			b.consume(rs)
			if b.passive.Load() && b.ready.Load() {
				act.Stop()
			}
		case <-act.C:
			b.mu.Lock()
			pong, need := b.pong, b.needPong
			b.needPong = false
			b.mu.Unlock()
			if need {
				if err := writeFrame(codec, stream, wire.Record{Type: wire.Pong, Fields: []wire.Field{wire.U(1, true, pong)}}); err != nil {
					b.note("%s pong: %v", b.id, err)
				}
			}
			if b.passive.Load() || !b.ready.Load() {
				continue
			}
			b.act(codec, stream, next)
		}
	}
}

func (b *soakBot) consume(rs []wire.Record) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for _, r := range rs {
		switch r.Type {
		case wire.Checkpoint:
			if fieldU(r, 1) == uint64(wire.CheckpointEnd) && fieldU(r, 3) == 1 {
				b.ready.Store(true)
			}
		case wire.Ping:
			b.needPong = true
			b.pong = fieldU(r, 1)
		case wire.Control:
			if fieldU(r, 1) == uint64(wire.ControlGranted) {
				b.control = fieldU(r, 2)
			}
		case wire.Result:
			n := fieldU(r, 1)
			stage := wire.Stage(fieldU(r, 2))
			class := b.pending[n]
			delete(b.pending, n)
			if stage == wire.StageUnknown {
				b.unknown.Add(1)
			} else if class != "" && (stage == wire.StageDone || stage == wire.StageRefused) {
				b.accepted[class]++
			}
		case wire.Fact:
			ev, err := meaning.EventValue(r)
			if err != nil {
				b.note("%s fact: %v", b.id, err)
				continue
			}
			b.absorb(ev)
		}
	}
}

func (b *soakBot) absorb(ev meaning.DecodedEvent) {
	switch ev.Event.Kind {
	case meaning.Appeared, meaning.Dropped:
		if !ev.Event.Mine {
			b.live[ev.Event.ID] = liveObj{ref: ev.Context.Ref, role: ev.Event.Role}
		} else {
			b.selfX, b.selfY = ev.Event.X, ev.Event.Y
		}
	case meaning.Gone:
		delete(b.live, ev.Event.ID)
	case meaning.Moved, meaning.Stopped, meaning.Corrected:
		if ev.Event.Mine {
			b.selfX, b.selfY = ev.Event.X, ev.Event.Y
		}
	case meaning.Vitals:
		if ev.Event.Stats != nil && ev.Event.Stats.HP != nil {
			b.hp = *ev.Event.Stats.HP
			b.dead = b.hp == 0
			b.vitals = true
		}
		if ev.Event.Stats != nil && ev.Event.Stats.MP != nil {
			b.mp = *ev.Event.Stats.MP
		}
	case meaning.Died:
		if ev.Event.Mine {
			b.dead = true
		}
	case meaning.Revived:
		if ev.Event.Mine {
			b.dead = false
		}
	case meaning.Spoke:
		b.dialog = ev
	case meaning.Shop:
		b.shop = ev
	case meaning.Offer:
		b.offer = ev
	case meaning.Practice:
		b.practice = ev
	case meaning.Carried:
		b.items = b.items[:0]
		b.goods = map[int32]int64{}
		if ev.Event.Coin != nil {
			b.coin = *ev.Event.Coin
			b.coinOK = true
			if *ev.Event.Coin < 0 {
				b.note("%s negative inventory", b.id)
			}
		}
		for _, g := range ev.Event.Goods {
			if g.Count < 0 {
				b.note("%s negative inventory", b.id)
			}
			b.goods[g.ID] += g.Count
			b.items = append(b.items, int64(g.Object))
		}
	}
}

func (b *soakBot) act(codec *meaning.Codec, stream *gateway.Stream, next func() uint64) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.control == 0 || len(b.pending) >= 1 {
		return
	}
	b.seq++
	n := b.seq
	verb := meaning.Walk
	if b.dead {
		verb = meaning.Rise
	} else {
		order := []string{meaning.Walk, meaning.Aim, meaning.Strike, meaning.Take, meaning.Talk, meaning.Say, meaning.Buy, meaning.Sell, meaning.Learn, meaning.UseItem, meaning.Focus, meaning.Engage, meaning.Cast}
		verb = order[next()%uint64(len(order))]
	}
	code, ok := meaning.VerbByCommand(verb)
	if !ok {
		b.note("%s missing verb %s", b.id, verb)
		return
	}
	fields := []wire.Field{wire.U(1, true, n), wire.U(2, true, code.Code), wire.U(3, true, b.control)}
	switch verb {
	case meaning.Walk:
		x := b.selfX + int32(next()%21) - 10
		y := b.selfY + int32(next()%21) - 10
		if b.selfX == 0 && b.selfY == 0 {
			x, y = -71057, 257883
		}
		fields = append(fields, wire.At(11, true, x, y, -3104))
	case meaning.Aim, meaning.Strike, meaning.Talk, meaning.Focus, meaning.Engage, meaning.Cast:
		ref, ok := b.pick(verb, next)
		if !ok {
			return
		}
		fields = append(fields, wire.Obj(10, true, []wire.Field{wire.S(1, true, int64(ref.ID)), wire.U(2, true, ref.Gen)}))
		if verb == meaning.Cast {
			fields = append(fields, wire.Obj(18, true, []wire.Field{wire.S(1, true, 3), wire.S(2, true, 1)}))
		}
	case meaning.Take:
		ref, ok := b.pick(verb, next)
		if !ok {
			return
		}
		fields = append(fields, wire.Obj(20, true, []wire.Field{wire.S(1, true, int64(ref.ID)), wire.U(2, true, ref.Gen)}))
	case meaning.Say:
		if b.dialog.Context.Dialog == 0 || len(b.dialog.Context.Options) == 0 {
			return
		}
		option := b.dialog.Context.Options[next()%uint64(len(b.dialog.Context.Options))].ID
		fields = append(fields, wire.U(12, true, b.dialog.Context.Dialog), wire.U(13, true, option))
	case meaning.Buy, meaning.Sell, meaning.Learn:
		src := b.shop
		if verb == meaning.Sell {
			src = b.offer
		}
		if verb == meaning.Learn {
			src = b.practice
		}
		offer := uint64(1)
		price := int64(1)
		if len(src.Event.Goods) == 0 || len(src.Context.Offers) == 0 {
			return
		}
		if len(src.Event.Goods) > 0 {
			i := int(next() % uint64(len(src.Event.Goods)))
			if i < len(src.Context.Offers) {
				offer = src.Context.Offers[i]
			}
			price = src.Event.Goods[i].Price
		}
		fields = append(fields, wire.S(14, true, int64(src.Event.List)), wire.U(15, true, offer))
		if verb != meaning.Learn {
			fields = append(fields, wire.S(16, true, 1), wire.S(17, true, price))
		}
	case meaning.UseItem:
		if len(b.items) == 0 {
			return
		}
		id := b.items[next()%uint64(len(b.items))]
		fields = append(fields, wire.S(21, true, id))
	}
	b.pending[n] = soakClass(verb)
	if err := writeFrame(codec, stream, wire.Record{Type: wire.Intent, Fields: fields}); err != nil {
		delete(b.pending, n)
		b.note("%s write: %v", b.id, err)
	}
}

func soakClass(verb string) string {
	switch verb {
	case meaning.Walk:
		return "move"
	case meaning.Aim, meaning.Strike, meaning.Focus, meaning.Engage, meaning.Cast:
		return "combat"
	case meaning.Take:
		return "pickup"
	case meaning.Talk, meaning.Say:
		return "dialogue"
	case meaning.Buy, meaning.Sell, meaning.Learn:
		return "shop"
	case meaning.UseItem:
		return "use"
	case meaning.Rise:
		return "return"
	default:
		return ""
	}
}

func itemCount(c world.Character, template int32) int64 {
	var n int64
	for _, it := range c.Items {
		if it.TemplateID == template {
			n += it.Count
		}
	}
	return n
}

func sameItems(got map[int32]int64, items []world.Item) bool {
	want := map[int32]int64{}
	for _, it := range items {
		if it.TemplateID == 57 {
			continue
		}
		want[it.TemplateID] += it.Count
	}
	if len(got) != len(want) {
		return false
	}
	for id, n := range want {
		if got[id] != n {
			return false
		}
	}
	return true
}

func (b *soakBot) pick(verb string, next func() uint64) (meaning.Ref, bool) {
	var matches []liveObj
	for _, o := range b.live {
		switch verb {
		case meaning.Take:
			if o.role == meaning.RoleDrop {
				matches = append(matches, o)
			}
		case meaning.Strike, meaning.Cast:
			if o.role == "creature" {
				matches = append(matches, o)
			}
		default:
			if o.role == "tutor" || o.role == "provisioner" || o.role == "creature" {
				matches = append(matches, o)
			}
		}
	}
	if len(matches) == 0 {
		return meaning.Ref{}, false
	}
	return matches[next()%uint64(len(matches))].ref, true
}

func writeFrame(codec *meaning.Codec, stream *gateway.Stream, r wire.Record) error {
	buf, err := codec.Encode([]wire.Record{r})
	if err != nil {
		return err
	}
	return stream.Write(buf, time.Now().Add(time.Second))
}

func readFrame(codec *meaning.Codec, stream *gateway.Stream, deadline time.Time) ([]wire.Record, error) {
	buf, err := stream.Read(deadline)
	if err != nil {
		return nil, err
	}
	return codec.Decode(buf)
}

func fieldU(r wire.Record, n uint32) uint64 {
	v, _ := wire.Find(r.Fields, n)
	return v.U
}

type failingConn struct{}

func (failingConn) Read([]byte) (int, error)         { return 0, io.EOF }
func (failingConn) Write([]byte) (int, error)        { return 0, io.ErrClosedPipe }
func (failingConn) Close() error                     { return errors.New("close") }
func (failingConn) LocalAddr() net.Addr              { return failingAddr{} }
func (failingConn) RemoteAddr() net.Addr             { return failingAddr{} }
func (failingConn) SetDeadline(time.Time) error      { return nil }
func (failingConn) SetReadDeadline(time.Time) error  { return nil }
func (failingConn) SetWriteDeadline(time.Time) error { return nil }

type failingAddr struct{}

func (failingAddr) Network() string { return "tcp" }
func (failingAddr) String() string  { return "soak" }

// TestHostLogDuringRunDoesNotHang writes through the logger serve gives the
// host. The handler goes to the test output, not the process default, so a
// log while the host is running returns.
func TestHostLogDuringRunDoesNotHang(t *testing.T) {
	log := slog.New(slog.NewTextHandler(t.Output(), nil))
	r := &runtime{log: log}
	done := make(chan struct{})
	go func() {
		defer close(done)
		r.closeConn(failingConn{})
		log.Warn("participant connection close failed")
	}()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("host log write hung")
	}
}
